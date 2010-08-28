package com.almarsoft.GroundhogReader.lib;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.commons.net.io.DotTerminatedMessageReader;
import org.apache.commons.net.nntp.Article;
import org.apache.commons.net.nntp.ArticlePointer;
import org.apache.commons.net.nntp.NewsgroupInfo;
import org.apache.james.mime4j.message.Header;
import org.apache.james.mime4j.message.Message;
import org.apache.james.mime4j.parser.MimeEntityConfig;
import org.apache.james.mime4j.storage.DefaultStorageProvider;
import org.apache.james.mime4j.storage.TempFileStorageProvider;
import org.apache.james.mime4j.storage.ThresholdStorageProvider;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;


final public class ServerManager {

	private String mGroup;
	private int mGroupID;

	private NNTPExtended mClient = null;
	private NewsgroupInfo mGroupInfo;
	private Context mContext;
	private HashSet<String> mBannedThreadsSet;
	private HashSet<String> mBannedTrollsSet;
	private boolean mFetchLatest = false;
	
	
	public ServerManager(Context callerContext) {

		mContext = callerContext;
		// XXX: Ver si esto se puede hacer fuera de aqui o pasarselo... solo se usa en un sitio
		mBannedTrollsSet  = DBUtils.getBannedTrolls(mContext);
	}

	
	// Destructor
	protected void finalize() throws Throwable
	{
		stop();
	}
	

	public void stop() {
		
		if (mClient != null && mClient.isConnected()) {
			try {
				mClient.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
		mClient = null;
	}

	
	// Connect to the server configured in the settings. Please note that mClient.isConnected
	// works when it says that you are not connected but NOT when is returns "you're connected", for
	// example after a phone sleep it will usually say true while in fact the socket died. That's the 
	// reason of the ugly try { } catch() { reconnect_and_try_again }
	public void clientConnectIfNot() throws IOException, ServerAuthException {
		
		if (mClient == null || !mClient.isConnected()) {
			connect();
		} 
	}

	
	private void connect() throws SocketException, IOException, ServerAuthException {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		String tmpPort = prefs.getString("port", "119");
		
		if (tmpPort != null) 
			tmpPort = tmpPort.trim();
		
		if (tmpPort == null || tmpPort.length() == 0) {
			// Fix for migrating f*cked config files (from a bug in a previous version)
			tmpPort = "119";
			Editor edit = prefs.edit();
			edit.putString("port", tmpPort);
			edit.commit();
		}
		
		int port          = new Integer(tmpPort);
		boolean needsAuth = prefs.getBoolean("needsAuth", false);
		
		String host = prefs.getString("host", null);
		if (host != null) host = host.trim();
		
		String clogin = prefs.getString("login", null);
		if (clogin != null) clogin = clogin.trim();
		
		String cpass = prefs.getString("pass", null);
		if (cpass != null) cpass = cpass.trim();

		mClient = new NNTPExtended();
			
		// Get the configuration host, port, username and pass
		mClient.connect(host, port);
		
		if (needsAuth) {
			if (!mClient.authenticate(clogin, cpass)) {
				throw new ServerAuthException();
			}
		}
	}

	
	public void selectNewsGroupWithoutConnect(String group) {
		mGroup = group;
	}
	
	
	public boolean selectNewsGroupConnecting(String group) throws IOException, ServerAuthException {
		mGroup = group;
		mGroupID = DBUtils.getGroupIdFromName(group, mContext);
		mBannedThreadsSet = DBUtils.getBannedThreads(group, mContext);
		
		clientConnectIfNot();
		
		mGroupInfo = new NewsgroupInfo();
		
		try {
			return mClient.selectNewsgroup(mGroup, mGroupInfo);
		} catch (IOException e) {
			connect();
			return mClient.selectNewsgroup(mGroup, mGroupInfo);
		}		
	}
	
	/**
	 * Catchup with the server, so only messages from now on will be downloaded 
	 * 
	 */
	public void catchupGroup(String group) throws IOException, ServerAuthException {
		
		if (mGroup == null || mGroup.compareTo(group) != 0 || mGroupInfo == null || mGroupInfo.getNewsgroup().compareTo(group) != 0) {
			selectNewsGroupConnecting(group);
		}
		DBUtils.storeGroupLastFetchedMessageNumber(group, mGroupInfo.getLastArticle(), mContext);
	}
	
	
	// XXX: Ver las llamadas que usan selectNewsGroup para ver si comprueban el valor devuelto
	// XXX: mBannedThreadSet, seria conveniente que se leyera externamente y se le pasara como argumento
	public boolean selectNewsGroup(String group, boolean offlineMode) 
	throws ServerAuthException, IOException {

		mGroup = group;
		
		if (offlineMode) {
			return true;
		}
		
		clientConnectIfNot();
		
		if (group == mGroup && mClient.isConnected()) {
			return true;
		}
		
		return selectNewsGroupConnecting(group);
	}

	
	// =====================================================================================
	// Retrieve the list of article numbers from the server and return a vector
	// of articles
	// considering the user limit
	// =====================================================================================
	
	public Vector<Long> selectGroupAndgetArticleNumbersReverse(String group, long lastFetched, int limit)
	throws IOException, ServerAuthException, UsenetReaderException {
		clientConnectIfNot();
		
		// This also loads the mGroupInfo class
		if (!selectNewsGroupConnecting(group)) {
			throw new UsenetReaderException("Could not select group " + group);
		}
		
		Vector<Long> msgNumbers = new Vector<Long>(limit);
		
		// Select the last article and retrieve its data
		ArticlePointer artPointer = new ArticlePointer();
		mClient.selectArticle(mGroupInfo.getLastArticle(), artPointer);
		
		for (int i=0;i<limit;i++) {
			
			if (lastFetched >= artPointer.articleNumber)
				break;
			
			if (!mClient.selectPreviousArticle(artPointer))
				break;
			
			msgNumbers.add(artPointer.articleNumber);
		}
		
		Collections.reverse(msgNumbers);
		return msgNumbers;
	}

	public Vector<Long> selectGroupAndGetArticleNumbers(String group, long firstMsg, int limit) 
	throws IOException, ServerAuthException, UsenetReaderException {
		clientConnectIfNot();
		
		selectNewsGroupConnecting(group);
		Vector<Long> msgNumbers = new Vector<Long>(limit);
		long[] allNumbers;
	
		try {
			allNumbers = mClient.listGroup(mGroup, firstMsg, limit);			
		} catch (IOException e) {
			e.printStackTrace();
			Log.d(UsenetConstants.APPNAME, "Connection seems lost, reconnecting");
			connect();
			allNumbers = mClient.listGroup(mGroup, firstMsg, limit);
		}
		
		if (allNumbers == null)
			throw new UsenetReaderException("Error requesting message list, maybe the group doesn't exist on this server?");

		int countSaved = 0;
		int init;
		
		int allNumbersLen = allNumbers.length;
		if (firstMsg == -1 && allNumbersLen > limit) {
			// Special case for first time entering a group; instead of getting the 100 (or limit) 
			// older we get the 100 newer
			init = allNumbersLen - limit;
			
		} else {
			init = 0;
		}
		
		
		long currentNum;

		for (; init < allNumbersLen; init++) {
			currentNum = allNumbers[init];
			
			if (currentNum < firstMsg)
				continue;
			if (countSaved > limit)
				break;
			msgNumbers.add(new Long(currentNum));
			countSaved++;
		}
			
		return msgNumbers;
	}

	// ===============================================================================
	// Retrieve and article by number from the server and store it into the
	// database. Return the msgId of the article. Can accept an already created SQLiteDatabase
	// object to avoid too many object/database open/database close inside loop (or
	// can be null to let the DBUtils create it every time.)
	// ===============================================================================

	public Vector<Object> getAndInsertArticleInfo(long articleNumber, String charset, SQLiteDatabase catchedDB) 
	throws IOException, UsenetReaderException, ServerAuthException {
		clientConnectIfNot();
		

		long ddbbId = -1;
		Vector<Object> ret = null;
		
		// Get the article information (shorter than the header; we'll fetch the body and the
		// body when the user clicks on an article.)
		Article articleInfo = getArticleInfo(articleNumber);
		
		if (articleInfo != null) {
			String from = articleInfo.getFrom();
			if (  (!mBannedThreadsSet.contains(articleInfo.simplifiedSubject())) 
			    &&(!mBannedTrollsSet.contains(from))) {
				
				ddbbId = insertArticleInDB(articleInfo, articleNumber, from, charset, catchedDB);				
			} 
			
			ret = new Vector<Object>(2);
			ret.add(ddbbId);
			ret.add(articleInfo.getArticleId());
		}

		if (ret == null) {
			throw new IOException("getAndInsertArticleInfo: could not get or insert the article");
		}
		return ret;
	}

	
	private Article getArticleInfo(long articleNumber) throws IOException, ServerAuthException {
		clientConnectIfNot();
		
		Article article = null;
		Reader reader;
		
		try {
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleInfo(articleNumber);
		} catch (IOException e) {
			connect();
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleInfo(articleNumber);
		}

		if (reader != null) {
			String theInfo = MessageTextProcessor.readerToString(reader);

			if (theInfo.trim().length() == 0) {
				return null;
			}
			
			StringTokenizer st = new StringTokenizer(theInfo, "\n");
			String refsStr = null;

			try {
				// Extract the article information
				// Mandatory format (from NNTP RFC 2980) is :
				// Subject\tAuthor\tDate\tID\tReference(s)\tByte Count\tLine Count
			
				StringTokenizer stt = new StringTokenizer(st.nextToken(), "\t");
				article = new Article();
				article.setArticleNumber(Integer.parseInt(stt.nextToken()));
				article.setSubject(stt.nextToken());
				article.setFrom(stt.nextToken());
				article.setDate(stt.nextToken());
				article.setArticleId(stt.nextToken());			
			
				refsStr = stt.nextToken();
			}
			 catch (NoSuchElementException e) {
					Log.w(UsenetConstants.APPNAME, "NoSuchElementException parsing the article info, malformed or interrupted message?: " + theInfo);
					return null;
			 }
			 
			 catch (NumberFormatException e) {
				 Log.e(UsenetConstants.APPNAME, "NumberFormatException in getArticleInfo: " + theInfo + " " + e.toString());
			 }
			
			// Crappy heuristics... but nextToken skips the empty reference if it's no reference and give up the 
			// next token as reference
			if (refsStr.contains("@")) { 
				String[] refs = refsStr.split(" ");

				for (int i = 0; i < refs.length; i++) 
					article.addReference(refs[i]);
			}
		}
			
		return article;
	}


	// ======================================================================
	// Decode, process and insert the articleInfo into the DB, return the _id
	// ======================================================================
	
	private long insertArticleInDB(Article articleInfo, long articleNumber, String decodedfrom, String charset, SQLiteDatabase catchedDB) 
	throws UsenetReaderException {

		// Get the reference list as a string instead of as an array of strings
		// for insertion into the DB
		String[] references = articleInfo.getReferences();
		StringBuilder references_buff = new StringBuilder();
		references_buff.append("");
		int referencesLen = references.length;

		for (int i = 0; i < referencesLen; i++) {
			if (i == (referencesLen - 1)) {
				references_buff.append(references[i]);
			} else {
				references_buff.append(references[i]);
				references_buff.append(" ");
			}
		}
		
		String finalRefs = references_buff.toString();
		//String finalSubject = MessageTextProcessor.decodeHeaderInArticleInfo(articleInfo.getSubject(), charset);
		String subject = articleInfo.getSubject();
		subject = subject.replaceAll(" +", " ");

		// Now insert the Article into the DB
		return DBUtils.insertArticleToGroupID(mGroupID, articleInfo, finalRefs, decodedfrom, subject, mContext, catchedDB);
	}

	// ====================================================================================
	// Get a header from the server, store it in the sdcard cache and return it
	// ====================================================================================
	
	private Header GetAndCacheHeader(long headerTableId, String msgId, String group)
	throws ServerAuthException, IOException, UsenetReaderException {

		clientConnectIfNot();	
		Reader reader = null;	
		String strHeader = null;
		
		try {
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleHeader(msgId);
		} catch (IOException e) {
			// Needed now???
			connect();
			selectNewsGroup(mGroup, false);
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleHeader(msgId);
		}
		
		if (reader == null)
			return null;
		
		strHeader = MessageTextProcessor.readerToString(reader);
		
		if (strHeader == null) 
			return null;
		
		// Now we have the header from the server, store it into the sdcard
		String outputPath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/header/";
		FSUtils.writeStringToDiskFile(strHeader, outputPath, Long.toString(headerTableId));
		DBUtils.setMessageCatched(headerTableId, true, mContext);
		
		return MessageTextProcessor.strToHeader(strHeader);		
	}

	
	// ====================================================================================
	// Get a body from the server and store it in the sdcard cache
	// ====================================================================================
	
	private FileReader getAndCacheBody(long headerTableId, String msgId, String group) 
	throws ServerAuthException, IOException, UsenetReaderException {
		clientConnectIfNot();
		
		Reader reader = null;
		
		try {
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleBody(msgId);
		} catch (IOException  e) {
			connect();
			selectNewsGroup(mGroup, false);
			reader = (DotTerminatedMessageReader) mClient.retrieveArticleBody(msgId);
		}		
		
		if (reader == null) 
			return null;

		// Now we have the header from the server, store it into the sdcard
		String outputPath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/body/";
		String fileName = Long.toString(headerTableId);
		FSUtils.writeReaderToDiskFile(reader, outputPath, fileName);
		DBUtils.setMessageCatched(headerTableId, true, mContext);
		
		return FSUtils.getReaderFromDiskFile(outputPath + "/" + fileName, true);
	}
	
	
	// ===================================================
	// Read a header from the cache
	// ===================================================
	private Header readHeaderFromCache(long id, String msgId, String group)
	throws UsenetReaderException, IOException, ServerAuthException {
		
		String header = null;
		String headerFilePath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/header/" + id;
		File f = new File(headerFilePath);
		
		if (!f.exists()) {
			// For some odd reason, its not on the disk, fetch it from the net and write it to the cache
			Log.d(UsenetConstants.APPNAME, "Message supposedly catched wasn't; catching from the net");
			return GetAndCacheHeader(id, msgId, group);
		}
		else {
			header = FSUtils.loadStringFromDiskFile(headerFilePath, true);
		}
	
		return MessageTextProcessor.strToHeader(header);
	}

	
	// ===================================================
	// Read a body from the cache
	// ===================================================
	private FileReader getBodyReaderFromCache(long id, String msgId, String group) 
	throws UsenetReaderException, IOException, ServerAuthException {
	
		FileReader bodyReader = null;
		String partFilePath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/body/" + id;
		File f = new File(partFilePath);
		
		if (!f.exists()) {
			// For some odd reason, its not on the disk, fetch it from the net and write it to the cache
			Log.d(UsenetConstants.APPNAME, "Message supposedly catched wasn't; catching from the net");
			bodyReader = getAndCacheBody(id, msgId, group);
		}
		else {
			//body = FSUtils.loadStringFromDiskFile(partFilePath, true);
			bodyReader = new FileReader(partFilePath);
		}
		
		Log.d("XXX", "Path: " + partFilePath);
		
		return bodyReader;
	}

	
	// ================================================================================================================
	// Get a header for and article id (with msgId given too). If the article is not in the cache, it will request it 
	// for the server to be stored into the cache (if we're not in offline mode.) If the article is in the cache, this
	// will just read the article
	// ================================================================================================================
	
	public Header getHeader(long id, String msgId, boolean isoffline, boolean iscatched) 
	throws UsenetReaderException, ServerAuthException, IOException {
		
		Header headerObj = null;
		//String header = null;
		//HashMap<String, String> headerTable = null;
		
		if (!iscatched) { // Not catched, cache it and get the result
			if (isoffline) 
				throw new UsenetReaderException("Offline mode enabled but header " + id + " not catched");
			else
				headerObj = GetAndCacheHeader(id, msgId, mGroup);
		
		} else // Catched, read if from the cache
			headerObj = readHeaderFromCache(id, msgId, mGroup);
		
		return headerObj;
	}

	
	
	// ===================================================================================
	// See the comment on getHeader, this is the same but getting the body and returning a
	// String instead of a Hashtable
	// ===================================================================================
	public Reader getBody(long id, String msgId, boolean isoffline, boolean iscatched) 
	throws UsenetReaderException, ServerAuthException, IOException {		
		Reader body = null;
		
		if (!iscatched) {
			if (isoffline)
				throw new UsenetReaderException("Offline mode enabled but bodytext for " + id + " not catched");
			else {
				body = getAndCacheBody(id, msgId, mGroup);
			}
		} else {
			body = getBodyReaderFromCache(id, msgId, mGroup);
		}
		return body;
	}


	// =============================================================================================
	// Construct a Message object with the given Header and the body taken from the cache or the net
	// =============================================================================================
	public Message getMessage(Header header, long id, String msgId, boolean isoffline, 
			                   boolean iscatched, String charset, File internalCacheDir)
	
	throws UsenetReaderException, ServerAuthException, IOException {
		
		Message message = null;	
		
		Reader bodyReader = getBody(id, msgId, isoffline, iscatched);
		if (bodyReader == null || header == null)
			throw new UsenetReaderException("Error getting body or header");
		
		//String messageStr = header.toString() + "\r\n" + strBody;
		StringReader headerReader = new StringReader(header.toString() + "\r\n");
		Vector<Reader> readers = new Vector<Reader>();
		readers.add(headerReader);
		readers.add(bodyReader);
		MergeReader messageReader = new MergeReader(readers);

		// Store in memory until 2MB of message size, then use the disk
		TempFileStorageProvider cacheProvider = new TempFileStorageProvider(internalCacheDir);
		ThresholdStorageProvider storageProvider = new ThresholdStorageProvider(cacheProvider, 2097152);
		DefaultStorageProvider.setInstance(storageProvider);
		
		MimeEntityConfig mimeConfig = new MimeEntityConfig();
		mimeConfig.setMaxLineLen(-1);
		ReaderInputStream msgStream = new  ReaderInputStream(messageReader);
		message = new Message(msgStream, mimeConfig, charset);
		
		return message;
	}
	
	// ========================
	// List newsgroups
	// ========================
	public NewsgroupInfo[] listNewsgroups(String wildmat) throws IOException, ServerAuthException {
		clientConnectIfNot();

		NewsgroupInfo[] retVal = null;
		try {	
			retVal = mClient.listNewsgroups(wildmat);
		} catch (IOException e) {
			connect();
			retVal = mClient.listNewsgroups(wildmat);
		}
		
		return retVal;
	}
	
	
	public void postArticle(String fullMessage, boolean forceOnline)
	                       throws IOException, ServerAuthException, UsenetReaderException {
		
		String error = null;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		boolean offlineMode;
		
		if (forceOnline)
			offlineMode = false;
		else
			offlineMode = prefs.getBoolean("offlineMode", true);
		
		boolean postInOffline = prefs.getBoolean("postDirectlyInOfflineMode", true);
		boolean saveToOutbox = offlineMode && !postInOffline;

		if (!saveToOutbox) {
			connect();
			
			Writer writer = mClient.postArticle();
			
			if (writer != null) {
				writer.write(fullMessage);
				writer.close();
				if (!mClient.completePendingCommand()) {
					error = mClient.getReplyString();
					saveToOutbox = true; // This will make it try to save to the outbox
				}
			}
		}
			
		if (saveToOutbox) {
			String saveError = ServerManager.saveMessageToOutbox(fullMessage, mContext);

			if (saveError != null) {
				if (error != null) 
					error = error + "; and saving error: " + saveError;
				else
					error = saveError;
			}
		}
		
		if (error != null)
			throw new UsenetReaderException(error);
		
	}
	
	
	public void postArticle(String header, String body, String signature) 
	                        throws IOException, ServerAuthException, UsenetReaderException {
		StringBuilder fullMessage = new StringBuilder();
		
		fullMessage.append(header.trim());
		fullMessage.append("\r\n\r\n");
		fullMessage.append(body.trim());
		fullMessage.append(signature);
		postArticle(fullMessage.toString(), false);
	}

	
	private static String saveMessageToOutbox(String fullMessage, Context context) {
		
		String error = null;
		
		long outid = DBUtils.insertOfflineSentPost(context);
		
		String outputDir = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/outbox/";
		File outDir = new File(outputDir);
		if (!outDir.exists()) outDir.mkdirs();
		
		File outFile = new File(outDir, Long.toString(outid));
		BufferedWriter out = null;
		
		try {
			FileWriter writer = new FileWriter(outFile);
			out = new BufferedWriter(writer);
			out.write(fullMessage);
			out.flush();
		} catch (IOException e) {
			error = e.getMessage();
		} finally {
			try {
				if (out != null) out.close();
			} catch (IOException e) {}
		}
		
		return error;
	}


	public void setFetchLatest(boolean getLatestOption) {
		/* Enable or disable the fetching of the newest messages in the group (getNewOption=true) or the oldest */
		this.mFetchLatest = getLatestOption;
		
	}
	
	public boolean getFetchLatest() {
		return this.mFetchLatest;
	}

		
}
