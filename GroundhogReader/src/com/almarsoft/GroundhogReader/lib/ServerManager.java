package com.almarsoft.GroundhogReader.lib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.commons.net.io.DotTerminatedMessageReader;
import org.apache.commons.net.nntp.Article;
import org.apache.commons.net.nntp.NewsgroupInfo;

import android.content.Context;
import android.content.SharedPreferences;
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
	
	
	public ServerManager(Context callerContext) {

		mContext = callerContext;
		
		// XXX YYY ZZZ: Ver si esto se puede hacer fuera de aqui o pasarselo... solo se usa en un sitio
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
		
		int port          = new Integer(prefs.getString("port", "119").trim());
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
	
	
	// XXX YYY ZZZ: Ver las llamadas que usan selectNewsGroup para ver si comprueban el valor devuelto
	// XXX YYY ZZZ: mBannedThreadSet, seria conveniente que se leyera externamente y se le pasara como argumento
	public boolean selectNewsGroup(String group, boolean offlineMode) throws ServerAuthException, IOException {

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

	public Vector<Long> getArticleNumbers(long firstMsg, int limit) throws IOException, ServerAuthException, UsenetReaderException {
		clientConnectIfNot();
		
		Vector<Long> msgNumbers = new Vector<Long>(limit);
		long[] allNumbers;
	
		try {
			allNumbers = mClient.listGroup(mGroup, firstMsg, limit);
		} catch (IOException e) {
			e.printStackTrace();
			Log.d("Groundhog", "Connection seems lost, reconnecting");
			connect();
			allNumbers = mClient.listGroup(mGroup, firstMsg, limit);
		}
		
		if (allNumbers == null)
			throw new UsenetReaderException("Error requesting message list, maybe the group doesn't exist on this server?");

		int countSaved = 0;
		int init;
		
		if (firstMsg == -1 && allNumbers.length > limit) {
			// Special case for first time entering a group; instead of getting the 100 (or limit) 
			// older we get the 100 newer
			init = allNumbers.length - limit;
			
		} else {
			init = 0;
		}
		
		int allNumbersLen = allNumbers.length;
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
	// database. Return the msgId of the article.
	// ===============================================================================

	public Vector<Object> getAndInsertArticleInfo(long articleNumber) throws IOException, UsenetReaderException, ServerAuthException {
		clientConnectIfNot();
		
		String decodedfrom;

		// Get the article information (shorter than the header; we'll fetch the body and the
		// body when the user clicks on an article.)
		Article articleInfo = getArticleInfo(articleNumber);
		long ddbbId = -1;

		if (articleInfo != null) {
			
			decodedfrom = MiniMime.decodemime(articleInfo.getFrom(), true);
			
			if (  (!mBannedThreadsSet.contains(articleInfo.simplifiedSubject())) 
			    &&(!mBannedTrollsSet.contains(decodedfrom))) {
				
				ddbbId = insertArticleInDB(articleInfo, articleNumber, decodedfrom);
			} 
		}
		
		Vector<Object> ret = new Vector<Object>(2);
		ret.add(ddbbId);
		ret.add(articleInfo.getArticleId());
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
			String theInfo = ServerManager.readerToString(reader);

			if (theInfo.trim().length() == 0) {
				return null;
			}
			
			StringTokenizer st = new StringTokenizer(theInfo, "\n");

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
			
			String refsStr = stt.nextToken();
			
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


	
	private static String readerToString(Reader reader) throws IOException {
		BufferedReader bufReader = new BufferedReader(reader);
		StringBuilder sb = new StringBuilder();
		String temp = bufReader.readLine();
		
		while (temp != null) {
			sb.append(temp);
			sb.append("\n");
			temp = bufReader.readLine();
		}

		return sb.toString();
	}

	
	// ======================================================================
	// Decode, process and insert the articleInfo into the DB, return the _id
	// ======================================================================
	
	private long insertArticleInDB(Article articleInfo, long articleNumber, String decodedfrom) throws UsenetReaderException {

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
		String finalSubject = MiniMime.decodemime(articleInfo.getSubject(), false);
		finalSubject = finalSubject.replaceAll(" +", " ");

		// Now insert the Article into the DB
		return DBUtils.insertArticleToGroupID(mGroupID, articleInfo, finalRefs, decodedfrom, finalSubject, mContext);
	}
	
	
	// ====================================================================================
	// Get a message part (body or header) from the server and store it in the sdcard cache
	// ====================================================================================
	
	private String writeMessagePartToCache(String type, long headerTableId, String msgId, String group) 
									throws ServerAuthException, IOException, UsenetReaderException {
		clientConnectIfNot();
		
		Reader reader = null;
		String thePart = null;
		
		try {
			if (type.equalsIgnoreCase("header")) {
				reader = (DotTerminatedMessageReader) mClient.retrieveArticleHeader(msgId);
			}
			else {
				reader = (DotTerminatedMessageReader) mClient.retrieveArticleBody(msgId);
			}
		} catch (IOException  e) {
			connect();
			selectNewsGroup(mGroup, false);
			if (type.equalsIgnoreCase("header")) {
				reader = (DotTerminatedMessageReader) mClient.retrieveArticleHeader(msgId);
			}
			else {
				reader = (DotTerminatedMessageReader) mClient.retrieveArticleBody(msgId);
			}
		}		
		
		if (reader == null) 
			return null;

		thePart = ServerManager.readerToString(reader);
		
		if (thePart == null) 
			return null;
		
		// Now we have the header from the server, store it into the sdcard
		String outputPath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/" + type + "/";
		FSUtils.writeStringToDiskFile(thePart, outputPath, Long.toString(headerTableId));
		DBUtils.setMessageCatched(headerTableId, true, mContext);
		return thePart;
	}

	
	// ===================================================
	// Read a message part (body or header) from the cache
	// ===================================================
	private String readMessagePartFromCache(String type, long id, String msgId, String group) 
	                                       throws UsenetReaderException, IOException, ServerAuthException {
	
		String part = null;
		
		String partFilePath = UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups/" + group + "/" + type + "/" + id;
		File f = new File(partFilePath);
		
		if (!f.exists()) {
			// For some odd reason, its not on the disk, fetch it from the net and write it to the cache
			Log.d("Groundhog", "Message supposedly catched wasn't; catching from the net");
			part = writeMessagePartToCache(type, id, msgId, group);
		}
		else {
			part = FSUtils.loadStringFromDiskFile(partFilePath, true);
		}
		
		return part;
	}
	
	
	// ================================================================================================================
	// Get a header for and article id (with msgId given too). If the article is not in the cache, it will request it 
	// for the server to be stored into the cache (if we're not in offline mode.) If the article is in the cache, this
	// will just read the article
	// ================================================================================================================
	
	public HashMap<String, String> getHeader(long id, String msgId, boolean isoffline, boolean iscatched) 
											 throws UsenetReaderException, ServerAuthException, IOException {
		
		String header = null;
		HashMap<String, String> headerTable = null;
		
		if (!iscatched) { // Not catched, cache it and get the result
			if (isoffline) 
				throw new UsenetReaderException("Offline mode enabled but header " + id + " not catched");
			else {
				header = writeMessagePartToCache("header", id, msgId, mGroup);
			}
			
		} else { // Catched, read if from the cache
			header = readMessagePartFromCache("header", id, msgId, mGroup);
		}
		
		if (header == null || header.trim().length() == 0)  
			return null;
		
		// Now that we have read the header part, split it into a HashTable
		String[] headerFields = header.split("\n");
		String[] fieldValue = null;
		headerTable = new HashMap<String, String>(headerFields.length);
		String lastField = "";
		int headerFieldsLen = headerFields.length;
		String tmp;
		
		for (int i = 0; i < headerFieldsLen; i++) {
			
			fieldValue = headerFields[i].split(":", 2);
			
			// First line of a header (or single line if, like most headers, it's just a line)
			if (fieldValue.length == 2) {
				
				if (fieldValue[0].equalsIgnoreCase("From") || fieldValue[0].equalsIgnoreCase("Subject")) {
					tmp = MiniMime.decodemime(fieldValue[1].trim(), false);
					headerTable.put(fieldValue[0], tmp.replaceAll(" +", " "));
				}
				else
					headerTable.put(fieldValue[0], fieldValue[1].trim());
				
				lastField = fieldValue[0];				
				
			// fieldValue.length == 1, following parts of a multiline header
			} else {
				
				if (lastField.equalsIgnoreCase("From") || lastField.equalsIgnoreCase("Subject")) {
					tmp = headerTable.get(lastField) + MiniMime.decodemime(fieldValue[0].trim(), false);
					headerTable.put(lastField, tmp.replaceAll(" +", " "));
				}
				else
					headerTable.put(lastField, headerTable.get(lastField) + fieldValue[0].trim());
			}
		}		
		
		return headerTable;
	}
	
	
	// ===================================================================================
	// See the comment on getHeader, this is the same but getting the body and returning a
	// String instead of a Hashtable
	// ===================================================================================
	public String getBody(long id, String msgId, boolean isoffline, boolean iscatched) 
	                      throws UsenetReaderException, ServerAuthException, IOException {		
		String body = null;
		
		if (!iscatched) {
			if (isoffline)
				throw new UsenetReaderException("Offline mode enabled but bodytext for " + id + " not catched");
			else {
				body = writeMessagePartToCache("body", id, msgId, mGroup);
			}
		} else {
			body = readMessagePartFromCache("body", id, msgId, mGroup);
		}
		return body;
	}


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
			offlineMode = prefs.getBoolean("offlineMode", false);
		
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
		
		fullMessage.append(header);
		fullMessage.append("\n\n");
		fullMessage.append(body);
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

		
}
