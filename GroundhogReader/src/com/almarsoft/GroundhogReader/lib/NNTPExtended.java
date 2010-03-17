package com.almarsoft.GroundhogReader.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

import org.apache.commons.net.io.DotTerminatedMessageReader;
import org.apache.commons.net.nntp.ArticlePointer;
import org.apache.commons.net.nntp.NNTPClient;
import org.apache.commons.net.nntp.NNTPCommand;
import org.apache.commons.net.nntp.NNTPReply;
import org.apache.commons.net.nntp.NewsgroupInfo;

import android.util.Log;

// Extends the apache commons NNTPClient to add support for the 
public class NNTPExtended extends NNTPClient {
	
	
	private long[] __parseListGroup() throws NumberFormatException, IOException {
		
		String line;
		Vector<Long> list;
		BufferedReader reader;
		
		reader = new BufferedReader(new DotTerminatedMessageReader(_reader_));
		list = new Vector<Long>(2048);
		
		while ((line = reader.readLine()) != null) {
			list.add(Long.parseLong(line.trim()));
		}
		
		int listSize = list.size();
		long[] retval = new long[listSize];
		
		for (int i=0; i<listSize; i++) {
			retval[i] = list.get(i);
		}
		
		return retval;
	}

	
	// ============================================================================================
	// Get the article numbers for the group. This uses the LISTGROUP command with a range if the
	// server supports it. If not, or if it's the first time we get messages from a group, it uses
	// the GROUP, NEXT, and STAT commands (slower but more compatible.)
	// ============================================================================================
	
	public long[] listGroup(String group, long fromArticle, int limit) throws IOException {
		
		String params;
		
		if (fromArticle == -1) { 
			//params = group;
			// The first time we use the slower getGroupArticles which is faster than asking for the 
			// full group listing in this case
			return getGroupArticles(group, -1, limit);
		}
		else 
			params = group + " " + Long.toString(fromArticle) + "-";
		
		if (!NNTPReply.isPositiveCompletion(sendCommand("LISTGROUP", params))) {
			// If using LISTGROUP fails, we use the slower getGroupArticles
			Log.d("Groundhog", "Server doesnt seem to support ranged LISTGROUP, using the STAT method");
			return getGroupArticles(group, fromArticle, limit);
		}
		
		return __parseListGroup();
		
	}
	
	
	// ================================================================================================
	// Select a group and an article and user "next" to get all the numbers. This is slower than using 
	// listgroup with a range but not all servers support that (mainly giganews)
	// ================================================================================================
	
	public long[] getGroupArticles(String group, long fromArticle, int limit) throws IOException {
		
		Vector<Long> list;
		long firstToGet, firstArticle, lastArticle;
		NewsgroupInfo groupInfo = new NewsgroupInfo();
		
		if (!selectNewsgroup(group, groupInfo))
			return null;
	
		firstArticle = groupInfo.getFirstArticle();
		lastArticle  = groupInfo.getLastArticle();
		
		if (firstArticle == 0 && lastArticle == 0)
			return new long[0];

		// First sync with this group; see the comment below 
		if (fromArticle == -1)
			firstToGet = firstArticle;
		
		else {
			if (fromArticle > lastArticle) { // No new articles
				return new long[0];
		   }
			else {
				firstToGet = fromArticle;
			}
		}
		
		// Now select the first article and start looping until limit or last article reached
		ArticlePointer art = new ArticlePointer();

		list = new Vector<Long>(limit);
		
		// FIRST CONNECTION TO THE GROUP
		// If this is the first connection we only want the last "limit" articles from the group, 
		// so we ask for the last message and go backwards until we have "limit" articles or 
		// the first one.
		if (fromArticle == -1) {
			if (!selectArticle(lastArticle, art))
				return new long[0];
			
			for (int i=0; i<limit; i++) {
				list.insertElementAt((long)art.articleNumber, 0);
				
				if (art.articleNumber == firstToGet)
					break;
				
				if (!selectPreviousArticle(art))
					break;
			}	
		}
		
		// NON-FIRST CONNECTION TO THE GROUP
		// For normal non-first connection we start with the last article we got on the previous session and advance from that
		// until limit or last article reached
		else {
			if (!selectArticle(firstToGet, art))
				return new long[0];
			
			for (int i=0; i<=limit; i++) {
				list.add((long)art.articleNumber);
				
				if (art.articleNumber == lastArticle)
					break;
				
				if (!selectNextArticle(art))
					break;
			}
		}
		
		int listSize = list.size();
		long[] articleNumbers = new long[listSize];
		
		for (int i=0; i<listSize; i++) {
			articleNumbers[i] = list.get(i);
		}
		
		return articleNumbers;
	}

	
	// ===================================================================
	// Overloaded versions of the parent methods accepting long arguments
	// ===================================================================
	
	public int stat(long articleNumber) throws IOException
    {
        return sendCommand(NNTPCommand.STAT, Long.toString(articleNumber));
    }
	
	
    public boolean selectArticle(long articleNumber, ArticlePointer pointer)
    throws IOException
    {
        if (!NNTPReply.isPositiveCompletion(stat(articleNumber)))
            return false;

        if (pointer != null)
            __parseArticlePointer(getReplyString(), pointer);

        return true;
    }
	
}
