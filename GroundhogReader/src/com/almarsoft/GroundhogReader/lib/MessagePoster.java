package com.almarsoft.GroundhogReader.lib;

import java.io.IOException;
import java.net.SocketException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.commons.codec.EncoderException;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class MessagePoster {

	private String mCurrentGroup;
	private String mGroups;
	private String mBody;
	private String mSubject;
	private String mReferences;
	private String mPrevMsgId;
	private String mMyMsgId;
	
	SharedPreferences mPrefs;
	Context mContext;
	
	// XXX YYY ZZZ: Cambiar cuando se haga configurable el charset
	private final String charset = "ISO-8859-15";
	MiniMime m;
	
	
	public MessagePoster(String currentGroup, String groups, String body, String subject, 
			                String references, String prevMsgId, Context context){
	
		mCurrentGroup = currentGroup;
		mContext = context;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		m = new MiniMime();
		mGroups = groups.trim();
		mBody = body;
		mSubject = subject.trim();
		
		// Reply to non-first post in a thread
		if (references != null && references.length() > 0) 
			mReferences = references.trim();
		else 
			mReferences = null;
		
		
		// Reply to a thread
		if (prevMsgId != null && prevMsgId.length() > 0)
			mPrevMsgId = prevMsgId.trim();
		else
			mPrevMsgId = null; // Message starting new thread
		
		// Reply to the first post in thread
		if (mReferences == null && mPrevMsgId != null) {
			mReferences = mPrevMsgId;
		}
		
	}

	
	public void postMessage() throws EncoderException, SocketException, IOException, ServerAuthException, UsenetReaderException {
		
		String headerText = createHeaderData();
		String signature  = getSignature();

		ServerManager serverMgr = new ServerManager(mContext);	
		mBody = MessageTextProcessor.shortenPostLines(mBody);	
		serverMgr.postArticle(headerText, mBody, signature);
		
		// Log the message to check against future replies in the MessageList
		if (mMyMsgId != null && mCurrentGroup != null)
			DBUtils.logSentMessage(mMyMsgId, mCurrentGroup, mContext);
	}

	
	private String getSignature() {
		String signature = mPrefs.getString("signature", "");
		if (signature.length() > 0) {
			signature = "\n\n" + "--\n" + signature; 
		}
		
		return signature;
	}
	
	private String createHeaderData() throws EncoderException {
		
		StringBuilder buf = new StringBuilder();
		String references, name, email, date;
		
		Date now = new Date();
		Format formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
		date = formatter.format(now);
		
		name = mPrefs.getString("name", "anonymous");
		email = "<" + mPrefs.getString("email", "nobody@nobody.no").trim() + ">";
		
		buf.append("From: " + MiniMime.mencodemime_ifneeded(name + " " + email, charset) + "\n"); 
		buf.append("Date: " + date + "\n"); 
		
		buf.append("Content-Type: text/plain; charset=\"iso-8859-15\"" + "\n");
		buf.append("Content-Transfer-Encoding: 8bit" + "\n");
		
		buf.append("Newsgroups: " + mGroups + "\n");
		buf.append("Subject: " + MiniMime.mencodemime_ifneeded(mSubject, charset) + "\n");
		
		
		if (mReferences != null) {
			if (mPrevMsgId != null) {
				references = mReferences + " " + mPrevMsgId;
				buf.append("In-Reply-To: " + mPrevMsgId + "\n");
			}
			else {
				references = mReferences;
			}
			buf.append("References: " + references + "\n");
		}
		
		mMyMsgId = generateMsgId();
		buf.append("Message-ID: " + mMyMsgId + "\n");
		buf.append("User-Agent: Groundhog Newseader for Android" + "\n");
		return buf.toString();
	}
	
	
	private String generateMsgId() {
		String host = mPrefs.getString("host", "noknownhost.com").trim();
		Random rand = new Random();
		String randstr = Long.toString(Math.abs(rand.nextLong()), 72);
		
		return "<" + "almarsoft." + randstr + "@" + host + ">";
		
	}

}
