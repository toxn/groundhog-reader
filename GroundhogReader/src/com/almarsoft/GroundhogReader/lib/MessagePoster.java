package com.almarsoft.GroundhogReader.lib;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.net.nntp.SimpleNNTPHeader;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.CharsetUtil;
import org.apache.james.mime4j.util.ContentUtil;

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
	private String mPostCharset;
	
	SharedPreferences mPrefs;
	Context mContext;
	
	
	public MessagePoster(String currentGroup, String groups, String body, String subject, 
			                String references, String prevMsgId, Context context){
	
		mCurrentGroup = currentGroup;
		mContext = context;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);		
		mGroups = groups.trim();
		mBody = body;
		mSubject = subject.trim();
		mPostCharset = mPrefs.getString("postCharset", "UTF-8");		
		
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
		Charset charset = CharsetUtil.getCharset(mPostCharset);		
		ByteSequence bytebody = ContentUtil.encode(charset, mBody);
		mBody = new String(bytebody.toByteArray(), "ISO-8859-1");
		
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
        String references, from, name, email, date;

        Date now = new Date();
        Format formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
        date = formatter.format(now);
         
        Charset headerCharset = CharsetUtil.getCharset(mPostCharset);
        
        String tmpName = mPrefs.getString("name", "anonymous");        
        if (EncoderUtil.hasToBeEncoded(tmpName, 0)) {
        	name = EncoderUtil.encodeEncodedWord(tmpName, EncoderUtil.Usage.TEXT_TOKEN, 0, headerCharset, null);
        } else 
        	name = tmpName;
		//name = EncoderUtil.encodeIfNecessary(mPrefs.getString("name", "anonymous"), EncoderUtil.Usage.TEXT_TOKEN, 0);
        
		email = "<" + mPrefs.getString("email", "nobody@nobody.no").trim() + ">";
		from = name + " " + email;		
		
		if (EncoderUtil.hasToBeEncoded(mSubject, 0)) {
			mSubject = EncoderUtil.encodeEncodedWord(mSubject, EncoderUtil.Usage.TEXT_TOKEN, 0, headerCharset, null);
		}
		//mSubject = EncoderUtil.encodeIfNecessary(mSubject, EncoderUtil.Usage.TEXT_TOKEN, 0);
		
		SimpleNNTPHeader header = new SimpleNNTPHeader(from, mSubject);
		String[] groups = mGroups.trim().split(",");
		int mgroupslen = groups.length;
		
		for(int i=0; i<mgroupslen; i++) {
			header.addNewsgroup(groups[i]);
		}
		header.addHeaderField("Date", date);		
		header.addHeaderField("Content-Type", "text/plain; charset=" + CharsetUtil.toMimeCharset(mPostCharset) +"; format=flowed");
		header.addHeaderField("Content-Transfer-Encoding", "8bit");	

        if (mReferences != null) {
            if (mPrevMsgId != null) {
                references = mReferences + " " + mPrevMsgId;
                header.addHeaderField("In-Reply-To", mPrevMsgId);                
            }
            else {
                references = mReferences;
            }            
            header.addHeaderField("References", references);
        }

        mMyMsgId = generateMsgId();
        header.addHeaderField("Message-ID", mMyMsgId);
        header.addHeaderField("User-Agent", "Groundhog Newsreader for Android");        
        Log.d("groundhog", "Header es|\n" + header.toString());
        return header.toString();
	}
	
	
	private String generateMsgId() {
		String host = mPrefs.getString("host", "noknownhost.com").trim();
		Random rand = new Random();
		String randstr = Long.toString(Math.abs(rand.nextLong()), 72);
		
		return "<" + "almarsoft." + randstr + "@" + host + ">";
		
	}

}
