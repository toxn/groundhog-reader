package com.almarsoft.GroundhogReader.lib;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

import org.apache.commons.net.nntp.NNTPNoSuchMessageException;

import com.almarsoft.GroundhogReader.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;


public class ServerMessageGetter extends AsyncTaskProxy {

	
	private ServerManager mServerManager = null;
	private int mLimit;
	private boolean mOfflineMode;
	private AsyncTask<Vector<String>, Integer, Integer> mTask  = null;
	
	public ServerMessageGetter(Object callerInstance, Method preCallback, Method progressCallback, Method postCallback, 
			                                       Context context, ServerManager serverManager, int limit, boolean offlineMode) {
		
		super(callerInstance, preCallback, progressCallback, postCallback, context);
		
		mServerManager = serverManager;
		mLimit = limit;
		mOfflineMode = offlineMode;
	}
	
	
	public void interrupt() {
		if (mTask != null	&& mTask.getStatus() != AsyncTask.Status.FINISHED) {
			mTask.cancel(false);
		}
	}	
	
	
	@SuppressWarnings("unchecked")
	public void execute(Vector<String> groupList) {
		mTask = new ServerMessageGetterTask();
		mTask.execute(groupList);
	}
	
	
	// ====================================================================
	// Get the articles or full messages (depending on the current mode) from the server and store them on the
	// DB and disk
	// ====================================================================
	
	private class ServerMessageGetterTask extends AsyncTask<Vector<String>, Integer, Integer> {

		private String mStatusMsg             = null;
		private String mCurrentGroup        = null;
		private Vector<String> groups      = null;
		
		private static final int FINISHED_ERROR = 1;
		protected static final int FINISHED_ERROR_AUTH = 2;
		protected static final int FETCH_FINISHED_OK = 3;
		private static final int FINISHED_INTERRUPTED = 4;
		
		
		@Override
		protected void onPreExecute() {
			if (mCallerInstance != null && mPreCallback != null) {
				try {
					Object[] noparams = new Object[0];
					mPreCallback.invoke(mCallerInstance, noparams);
			   } catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}		
		
		
		@Override
		protected Integer doInBackground(Vector<String>...groupsmult) {
			groups = groupsmult[0];
			mCurrentGroup = mContext.getString(R.string.group);
			String typeFetch;
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
			
			try {
				int groupslen = groups.size();
				
				String group = null;
				for (int i=0; i<groupslen; i++) {
					mCurrentGroup = groups.get(i);
					group = mCurrentGroup;
					
					mStatusMsg = mContext.getString(R.string.asking_new_articles);
					publishProgress(0, mLimit);
					mServerManager.selectNewsGroupConnecting(group);

					long lastFetched, firstToFetch;
					lastFetched = DBUtils.getGroupLastFetchedNumber(group, mContext);
					
					// First time for this group, keep the -1 so getArticleNumbers knows what to do, but if it's not the 
					// first time, get the lastFetched + 1 as the firstToFetch
					if (lastFetched == -1) 
						firstToFetch = lastFetched;
					else 
						firstToFetch = lastFetched + 1; 
					
					Vector<Long> articleList = mServerManager.getArticleNumbers(firstToFetch, mLimit);
					
					if (mOfflineMode) {
						// Get a vector with the server article numbers of articleInfos downloaded (and unread) but
						// not catched, then join the two vectors. It's very important than the newly adquired articles
						// from the server go at the end because the number of the last fetched message
						// is taken from the last element. This is done so we also get the content of these messages
						// when the user syncs in offline mode
						Vector<Long> alreadyGotArticleList = DBUtils.getUnreadNoncatchedArticleList(group, mContext);
						
						for (Long artNumber2 : articleList) 
							alreadyGotArticleList.add(artNumber2);
						
						articleList = alreadyGotArticleList;
						typeFetch = mContext.getString(R.string.full_messages);
					}
					else
						typeFetch = mContext.getString(R.string.headers);
						
		    		String msg = mContext.getString(R.string.getting_something);
		    		mStatusMsg = java.text.MessageFormat.format(msg, typeFetch);
		    		
					int len = articleList.size();
					publishProgress(0, len);
					
					long msgid, number;
					String server_msg_id;
					Vector<Object> offlineData;
					int articleListLen = articleList.size();
					
					for (int j=0; j < articleListLen; j++) {
						
						number = articleList.get(j);
						
						if (isCancelled()) {
							if (j > 0) 
								DBUtils.storeGroupLastFetchedMessageNumber(group, lastFetched, mContext);
							
							return FINISHED_INTERRUPTED;
						}
						publishProgress(j, len);
						
						// Check if the articleInfo is already on the DB (this can happen when the user has 
						// selected sync after a non-offline "Get New Messages"; in this case we download only
						// the content but don't do the fetching-and-inserting operation, obviously
						offlineData = DBUtils.isHeaderInDatabase(number, group, mContext);
						
						// Wasn't on the DB, get and insert it
						if (offlineData == null) { 
							
							offlineData = mServerManager.getAndInsertArticleInfo(number, prefs.getString("readDefaultCharset", "ISO8859-15"));
						}
						
						// Offline mode: save also the article contents to the cache
						if (mOfflineMode) {
							msgid = (Long) offlineData.get(0);
							server_msg_id = (String) offlineData.get(1);
							
							try {
								mServerManager.getHeader(msgid, server_msg_id, false, false);
								mServerManager.getBody  (msgid, server_msg_id, false, false);
							} catch (NNTPNoSuchMessageException e) {
								// Message not in server, mark as read and ignore
								e.printStackTrace();
								DBUtils.markAsRead(number, mContext);
								mServerManager.selectNewsGroupConnecting(group);
								continue;
							}
						}
						
						lastFetched = number;
					}

					if (articleListLen > 0) 
						DBUtils.storeGroupLastFetchedMessageNumber(group, articleList.lastElement(), mContext);
					
					if (groups.lastElement().equalsIgnoreCase(group))
						return FETCH_FINISHED_OK;
				}
			} catch (IOException e) {
				mStatusMsg = mContext.getString(R.string.error_post_check_settings) + ": " + e.toString() + " " + mCurrentGroup;
				e.printStackTrace();
				return FINISHED_ERROR;
				
			} catch (UsenetReaderException e) {
				mStatusMsg = mContext.getString(R.string.error_post_check_settings) + ": " + e.toString() + " " + mCurrentGroup;
				e.printStackTrace();
				return FINISHED_ERROR;
				
			} catch (ServerAuthException e) {
				mStatusMsg = mContext.getString(R.string.error_authenticating_check_pass) + ": " + e.toString() + " " + mCurrentGroup;;
				e.printStackTrace();
				return FINISHED_ERROR_AUTH;
			}
			
			return FETCH_FINISHED_OK;
		}
		
		
		@Override
		protected void onProgressUpdate(Integer...progress)  {
			
			if (mCallerInstance != null && mProgressCallback != null) {
				try {
					Object[] progressParams = new Object[4];
					
					progressParams[0] = mStatusMsg;
					progressParams[1] = mCurrentGroup;
					progressParams[2] = progress[0];
					progressParams[3] = progress[1];
					
					mProgressCallback.invoke(mCallerInstance, progressParams);

			   } catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}
		
		
		@Override
		protected void onPostExecute(Integer resultObj) {

			if (mCallerInstance != null && mPostCallback != null) {
				try {
					Object[] postParams = new Object[2];
					postParams[0] = mStatusMsg;
					postParams[1] = resultObj;
					
					mPostCallback.invoke(mCallerInstance, postParams);

			   } catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
			mTask = null;
		}
	}


}