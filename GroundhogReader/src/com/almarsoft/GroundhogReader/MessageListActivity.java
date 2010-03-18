package com.almarsoft.GroundhogReader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;
import java.util.Vector;

import org.apache.commons.net.nntp.Article;
import org.apache.commons.net.nntp.Threader;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.almarsoft.GroundhogReader.lib.DBHelper;
import com.almarsoft.GroundhogReader.lib.DBUtils;
import com.almarsoft.GroundhogReader.lib.FSUtils;
import com.almarsoft.GroundhogReader.lib.HeaderItemClass;
import com.almarsoft.GroundhogReader.lib.MessageTextProcessor;
import com.almarsoft.GroundhogReader.lib.MiniHeader;
import com.almarsoft.GroundhogReader.lib.ServerManager;
import com.almarsoft.GroundhogReader.lib.UsenetConstants;

public class MessageListActivity extends ListActivity {

	//private static final int NOT_FINISHED = 0;
	private static final int DBGETTER_FINISHED_OK = 1;
	private static final int FINISHED_INTERRUPTED = 2;

	private static final int MENU_ITEM_MARKTHREADREAD = 1;
	private static final int MENU_ITEM_MARKTHREADUNREAD = 2;
	private static final int MENU_ITEM_STARTHREAD = 3;
	private static final int MENU_ITEM_BANTHREAD = 4;
	private static final int MENU_ITEM_BANUSER = 5;

	// packagevisibility because it used by inner class (see dev guide)
	String mGroup;
	private int mGroupID;

	private int mNumUnread;
	private ArrayList<HeaderItemClass> mHeaderItemsList = null;
	private long[] mNumbersArray;
	
	// packagevisibility because it used by inner class (see dev guide)
	HashSet<String> mFavoritesSet;
	HashSet<String> mMyPostsSet;
	HashSet<String> mReadSet;
	
	private Thread mDbGetterThread;
	
	// packagevisibility because it used by inner class (see dev guide)
	SharedPreferences mPrefs;
	private PowerManager.WakeLock mWakeLock = null;

	private ServerManager mServerManager;
	private GroupMessagesDownloadDialog mDownloader = null;
	private boolean mOfflineMode;
	

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		
		Context context = getApplicationContext();

		mNumUnread = 0; // Loaded in OnResume || threadMessagesFromDB()
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mOfflineMode = mPrefs.getBoolean("offlineMode", false);

		registerForContextMenu(getListView());

		// Get the selected group from the GroupListActivity-passed bundle
		mGroup = getIntent().getExtras().getString("selectedGroup");
		mGroupID = DBUtils.getGroupIdFromName(mGroup, context);
		
		mServerManager = new ServerManager(context);
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE, "GroundhogThreading");		
		
		ListView lv = getListView();
		lv.setCacheColorHint(0);
		lv.setBackgroundColor(Color.WHITE);
		
		Drawable dw = getResources().getDrawable(R.drawable.greyline2);
		lv.setDivider(dw);
		
		// Show the progress dialog, get messages from server, write to DB
		// and call the loading of message from DB and threading when it ends
		mWakeLock.acquire();
		threadMessagesFromDB();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	
		if (mWakeLock.isHeld()) mWakeLock.release();
		Log.d(UsenetConstants.APPNAME, "ListActivity onPause");

		if (mDownloader != null) 
			mDownloader.interrupt();
		
    	if (mServerManager != null) 
    		mServerManager.stop();
    	mServerManager = null;
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		if (mWakeLock.isHeld()) mWakeLock.release();
		Log.d(UsenetConstants.APPNAME, "MessageList onStop");

    	if (mDbGetterThread != null && mDbGetterThread.isAlive())
    			mDbGetterThread.interrupt();
	}
	
	
	// =======================================================================
	// Detect server changes, redraw the items read from the MessageAcitivty 
	// (using the Next and Prev buttons) and get all the favorites on a set
	// =======================================================================
	
	protected void onResume() {
		super.onResume();
		
		Log.d(UsenetConstants.APPNAME, "ListActivity onResume");
		
		// ==================================================================================
		// Detect server hostname or charset changes in the settings (if true, go to the
		// (grouplist activity which will handle better the change, cleanup the
		// headers, etc)
		// ==================================================================================

		if (mPrefs.getBoolean("hostChanged", false)) {
			// The host has changed in the prefs, go to the GroupList
			startActivity(new Intent(MessageListActivity.this, GroupListActivity.class));
		}
		
		boolean mustThread = false;
		if (mPrefs.getBoolean("readCharsetChanged", false))  {			
			mustThread = true;
			Editor editor = mPrefs.edit();
			editor.putBoolean("readCharsetChanged", false);
			editor.commit();
		}
		
		
		// ====================================================================================
		// Get all the favorites and load them to a set
		mFavoritesSet = DBUtils.getFavoriteAuthors(getApplicationContext());
		
		// Now get the unread items. Yes, the select to load mHeaderItemsList is done with
		// "WHERE read=0" but the MessageActivity can also set some messages as read using the
		// Next and Prev buttons
		mReadSet = DBUtils.getUnreadMessagesSet(mGroup, getApplicationContext());
		
		if (mHeaderItemsList != null) {
			mNumUnread = mHeaderItemsList.size() - mReadSet.size();
			setGroupTitle();
		}
		
		getListView().invalidateViews();
		
		if (mServerManager == null)
			mServerManager = new ServerManager(getApplicationContext());
		
		if (mustThread) {
			mWakeLock.acquire();
			threadMessagesFromDB();
		}
	}
	
	
	private void checkNoUnread() {
		if (mNumUnread == 0) 
			Toast.makeText(this, getString(R.string.no_unread_use_sync), Toast.LENGTH_LONG).show();
	}

	
	// ==================================================================================================
	// Header Clicked Listener (start the MessageActivity and pass the server
	// number)
	// ==================================================================================================

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		Intent intent_MsgDetail = new Intent(MessageListActivity.this, MessageActivity.class);
		intent_MsgDetail.putExtra("articleNumbers", mNumbersArray);
		intent_MsgDetail.putExtra("msgIndexInArray", position);
		intent_MsgDetail.putExtra("group", mGroup);
		startActivity(intent_MsgDetail);
	}

	
	// =======================================
	// Options menu shown with the "Menu" key
	// =======================================
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.messagelist_menu_newpost:
				Intent intent_Compose = new Intent(MessageListActivity.this, ComposeActivity.class);
				intent_Compose.putExtra("isNew", true);
				intent_Compose.putExtra("group", mGroup);
				startActivityForResult(intent_Compose, UsenetConstants.COMPOSEMESSAGEINTENT);
				return true;
	
			case R.id.messagelist_menu_getnew:
				getNewMessagesFromServer();
				return true;
	
			case R.id.messagelist_menu_refresh:
				threadMessagesFromDB();
				return true;
				
			case R.id.messagelist_menu_markread:
				markAllRead();
				return true;
				
            case R.id.messagelist_menu_charset:
            	startActivity(new Intent(MessageListActivity.this, ReadCharsetActivity.class));
            	return true;				
	
			case R.id.messagelist_menu_settings:
				startActivity(new Intent(MessageListActivity.this, OptionsActivity.class));
				return true;
	
			case R.id.messagelist_menu_groupslist:
				startActivity(new Intent(MessageListActivity.this, GroupListActivity.class));
				return true;
	
			case R.id.messagelist_menu_managebanneds:
				Intent intent_bannedthreads = new Intent(MessageListActivity.this, BannedActivity.class);
				intent_bannedthreads.putExtra("typeban", UsenetConstants.BANNEDTHREADS);
				intent_bannedthreads.putExtra("group", mGroup);
				startActivityForResult(intent_bannedthreads, UsenetConstants.BANNEDACTIVITYINTENT);
				return true;
				
			case R.id.messagelist_menu_managebannedusers:
				Intent intent_bannedusers = new Intent(MessageListActivity.this, BannedActivity.class);
				intent_bannedusers.putExtra("typeban", UsenetConstants.BANNEDTROLLS);
				intent_bannedusers.putExtra("group", mGroup);
				startActivityForResult(intent_bannedusers, UsenetConstants.BANNEDACTIVITYINTENT);
				return true;			
		}
		return false;
	}
	
	
	// Call groupMessagesDownloader to download messages from this group, 
	// and pass him the callback pointing to threadMessagesFromDB so when it 
	// finishes the messagelist get reloaded
	@SuppressWarnings("unchecked")
	private void getNewMessagesFromServer() {
		Vector<String> groupVector = new Vector<String>(1);
		groupVector.add(mGroup);
		Class[] noargs = new Class[0];
		Method callback;
		try {
			callback = this.getClass().getMethod("threadMessagesFromDB", noargs);
			
			mDownloader = new GroupMessagesDownloadDialog(mServerManager, this);
			mDownloader.synchronize(mOfflineMode, groupVector, callback, this);
			
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	
	// =============================
	// Context menu for header items
	// =============================
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		new MenuInflater(getApplicationContext()).inflate( R.menu.messagelist_item_menu, menu);
		menu.setHeaderTitle(getString(R.string.article_menu));
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	
	@Override
	public boolean onContextItemSelected(MenuItem item) {

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int order = item.getOrder();
		HeaderItemClass header = mHeaderItemsList.get(info.position);

		// "Mark thread as read"
		if (order == MENU_ITEM_MARKTHREADREAD) 
			markThreadAsReadOrUnread(header, true);

		// "Mark thread as unread"
		if (order == MENU_ITEM_MARKTHREADUNREAD) 
			markThreadAsReadOrUnread(header, false);
		

		if (order == MENU_ITEM_STARTHREAD) {
			
			ArrayList<HeaderItemClass> itemsProxy = mHeaderItemsList;
			int itemsSize = itemsProxy.size();
			String starred_thread_subject = header.getArticle().simplifiedSubject();
			
			for (int i = 0; i < itemsSize; i++) {
				if (itemsProxy.get(i).getArticle().simplifiedSubject() == starred_thread_subject) {
					itemStarClicked(i);
				}
			}
		}

		if (order == MENU_ITEM_BANTHREAD) {
			banThread(header);
			Toast.makeText( this, getString(R.string.thread_ignore), 
					        Toast.LENGTH_LONG).show();
		}

		if (order == MENU_ITEM_BANUSER) {
			banUser(header);
			Toast.makeText(this, getString(R.string.author_banned_reload_tohide), Toast.LENGTH_LONG).show();
		}
		
		
		return true;
	}
	
	
	private void banUser(HeaderItemClass header) {
		String from = header.getArticle().getFrom();
		markUserMessagesAsRead(from);
		DBUtils.banUser(from, getApplicationContext());
		setGroupTitle();
	}

	
	private void banThread(HeaderItemClass header) {
		markThreadAsReadOrUnread(header, true);
		DBUtils.banThread(mGroup, header.getArticle().simplifiedSubject(), getApplicationContext());
		setGroupTitle();
	}

	
	private void setGroupTitle() {
		if (mOfflineMode)
			setTitle(mGroup + ":" + mNumUnread + " (OFFLINE MODE)");
		else
			setTitle(mGroup + ":" + mNumUnread + " (ONLINE MODE)");
	}

	
	// setread == true: marks as read, else: marks as unread
	private void markThreadAsReadOrUnread(HeaderItemClass header, boolean setread) {

		// Proxy stuff
		String thread_subject = header.getArticle().simplifiedSubject();
		String msgId;
		Article article;
		ArrayList<HeaderItemClass> proxyHeaderItems = mHeaderItemsList;
		HashSet<String> proxyReadSet = mReadSet;
		int headerItemsSize = proxyHeaderItems.size();
		int proxyNumUnread = mNumUnread;
		// End proxy stuff
		
		for (int i=0; i<headerItemsSize; i++) {
			article = proxyHeaderItems.get(i).getArticle();
			
			if (thread_subject.equalsIgnoreCase(article.simplifiedSubject())) {
				msgId = article.getArticleId();
				if (setread) {
					DBUtils.markAsRead(msgId, getApplicationContext());
					if (!proxyReadSet.contains(msgId)) 
						proxyNumUnread--;
				} else {
					DBUtils.markAsUnread(msgId, getApplicationContext());
					if (proxyReadSet.contains(msgId))
						proxyNumUnread++;
				}
			}			
		}
		mNumUnread = proxyNumUnread;
		
		setGroupTitle();
		
		mReadSet = DBUtils.getUnreadMessagesSet(mGroup, getApplicationContext());
		DBUtils.updateUnreadInGroupsTable(mNumUnread, mGroupID, getApplicationContext());
		getListView().invalidateViews();
	}
	

	// mark all the messages from a user as read
	private void markUserMessagesAsRead(String from) {

		// Proxy stuff
		String msgId;
		Article article;
		ArrayList<HeaderItemClass> proxyHeaderItems = mHeaderItemsList;
		HashSet<String> proxyReadSet = mReadSet;
		int headerItemsSize = proxyHeaderItems.size();
		int proxyNumUnread = mNumUnread;
		// End proxy stuff
		
		for (int i=0; i<headerItemsSize; i++) {
			article = proxyHeaderItems.get(i).getArticle();
			
			if (from.equalsIgnoreCase(article.getFrom())) {
				msgId = article.getArticleId();
				DBUtils.markAsRead(msgId, getApplicationContext());
				if (!proxyReadSet.contains(msgId)) 
					proxyNumUnread--;
			}			
		}
		mNumUnread = proxyNumUnread;
		
		setGroupTitle();
		
		mReadSet = DBUtils.getUnreadMessagesSet(mGroup, getApplicationContext());
		DBUtils.updateUnreadInGroupsTable(mNumUnread, mGroupID, getApplicationContext());
		getListView().invalidateViews();
	}
	
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == UsenetConstants.COMPOSEMESSAGEINTENT) {
			if (resultCode == RESULT_OK) { // Message Send activity returns here from
									// "new post" (instead of reply)
    			if (mOfflineMode && !mPrefs.getBoolean("postDirectlyInOfflineMode", false))
    				Toast.makeText(getApplicationContext(), getString(R.string.stored_outbox_send_next_sync), Toast.LENGTH_SHORT).show();
    			else
    				Toast.makeText(getApplicationContext(), getString(R.string.message_sent), Toast.LENGTH_SHORT).show();
			}
			
		} else if (requestCode == UsenetConstants.BANNEDACTIVITYINTENT) {
			if (resultCode == RESULT_OK) {
				Toast.makeText(getApplicationContext(), getString(R.string.future_unignored_willbe_fetched), Toast.LENGTH_LONG).show();
			}

			else if (resultCode == RESULT_CANCELED) {
				Toast.makeText(getApplicationContext(), getString(R.string.nothing_to_unban), Toast.LENGTH_SHORT).show();
			}
		}

	}

	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation change because it would cause the message list to
		// be reloaded
		super.onConfigurationChanged(newConfig);
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(getApplication()).inflate(R.menu.messagelistmenu, menu);
		
		return (super.onCreateOptionsMenu(menu));
	}

	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		MenuItem getnew = menu.findItem(R.id.messagelist_menu_getnew); 
		if (mOfflineMode) {
			getnew.setTitle("Sync group Messages");
			getnew.setIcon(android.R.drawable.ic_menu_upload);
		} else {
			getnew.setTitle("Get new messages");
			getnew.setIcon(android.R.drawable.ic_menu_set_as);
		}
		return (super.onPrepareOptionsMenu(menu));
	}
	
	
	// ==========================================================================
	// Mark all the messages from the group as read (called from the menu
	// option)
	// ==========================================================================
	private void markAllRead() {
		
		String msg = getResources().getString(R.string.mark_read_question);
		msg = java.text.MessageFormat.format(msg, mGroup);
		
		new AlertDialog.Builder(this).setTitle(getString(R.string.mark_all_read)).setMessage(msg)
				.setPositiveButton(getString(R.string.yes),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
								if (mHeaderItemsList != null) {

									// Mark as read in the DB for this group
									DBUtils.setGroupAllRead(mGroup, getApplicationContext());

									// Delete all items from the list and
									// refresh
									mHeaderItemsList = new ArrayList<HeaderItemClass>();
									getListView().invalidateViews();
									threadMessagesFromDB();
								}
							}
						}).setNegativeButton(getString(R.string.no),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
								return;
							}
						}).show();
	}


	// =========================================================
	// Sent an update the the UI (progress dialog) from a thread
	// =========================================================
	// packagevisibility because it used by inner class (see dev guide)
    
	/*
	void updateStatus(final String textStatus, final int threadStatus, final int current, final int total) {
		mHandler.post(new Runnable() { 
			public void run() { 
				updateResultsInUi(textStatus, threadStatus, current, total); 
			}
		}
		);
	}
	*/
	
	
	private class LoadFromDBAndThreadTask extends AsyncTask<Void, Integer, Integer > {

		private ProgressDialog mProgress = null;
		
		@Override
		protected void onPreExecute() {
			MessageListActivity mi = MessageListActivity.this;
			mProgress = ProgressDialog.show(mi, mi.getString(R.string.message), mi.getString(R.string.threading_messages));
		}
		
		@Override
		protected Integer doInBackground(Void... arg0) {
			
			MessageListActivity act = MessageListActivity.this;
			String charset = mPrefs.getString("readDefaultCharset", "ISO8859-15");
			DBHelper dbhelper = new DBHelper(getApplicationContext());
			SQLiteDatabase db = dbhelper.getReadableDatabase();
			
			// Space cleanup: delete all read messages from the DB and catched files
			DBUtils.deleteReadMessages(getApplicationContext());
			FSUtils.deleteDirectory(UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/attachments");
			
			// Get the msgIds of all my posts to check for replies on fillListNonRecursive; load them on a set
			
			if (act.mPrefs.getBoolean("markReplies", true)) {
				act.mMyPostsSet = DBUtils.getGroupSentMessagesSet(act.mGroup, getApplicationContext());
			}
			
			// Now get all the headers with read=0
			// This is not moved to a single function in DBUtils because this way we can update realistically the 
			// progressDialog
			Cursor cur = db .rawQuery(
							"SELECT server_article_id, server_article_number, date, from_header, subject_header, reference_list, clean_subject"
									+ " FROM headers "
									+ " WHERE subscribed_group_id="
									+ mGroupID
									+ " AND read=0", null);

			int numArticles = cur.getCount();
			Article[] articles = new Article[numArticles];

			cur.moveToFirst();
			Article currentArticle;
			
			for (int i = 0; i < numArticles; i++) {
				if (isCancelled()) {
					cur.close(); db.close(); dbhelper.close();
					return FINISHED_INTERRUPTED;
				}
				
				currentArticle = new Article();
				currentArticle.setArticleId(cur.getString(0));
				currentArticle.setArticleNumber(cur.getInt(1));
				currentArticle.setDate(cur.getString(2));
				currentArticle.setFrom(MessageTextProcessor.decodeHeaderInArticleInfo(cur.getString(3), charset));
				currentArticle.setSubject(MessageTextProcessor.decodeHeaderInArticleInfo(cur.getString(4), charset));
				currentArticle.setSimplifiedSubject(cur.getString(5));

				String dbrefs = cur.getString(5);
				String[] artRefs = dbrefs.split(" ");
				int artRefsLen = artRefs.length;
				
				for (int j = 0; j < artRefsLen; j++) {
					currentArticle.addReference(artRefs[j]);
				}
				articles[i] = currentArticle;

				cur.moveToNext();
			}

			cur.close(); db.close(); dbhelper.close();
			
			mHeaderItemsList = new ArrayList<HeaderItemClass>();
			
			if (articles.length > 0) {
				Threader threader = new Threader();
				// XXX: This crash the stack if there are lots of messages, reimplement using a iterative version
				Article root = (Article) threader.thread(articles);

				fillListNonRecursive(root, 0, null);
				fillNumbersArray();
			}
			
			articles = null;
			mNumUnread = numArticles;
			DBUtils.updateUnreadInGroupsTable(mNumUnread, mGroupID, getApplicationContext());
			
			return DBGETTER_FINISHED_OK;
		}
		
		@Override
		protected void onPostExecute(Integer resultObj) {
			
			if (mWakeLock.isHeld()) mWakeLock.release();
			if (mProgress != null)   mProgress.dismiss();
			
			int result = resultObj.intValue();
			
			switch(result) {

			case DBGETTER_FINISHED_OK:
				if (mHeaderItemsList != null) {
					setListAdapter(new ArticleAdapter(MessageListActivity.this, R.layout.messagelist_item, mHeaderItemsList));
					setGroupTitle();
				}
				checkNoUnread();
				break;
				
			case FINISHED_INTERRUPTED:
				// Nothing currently done, but left as stub
				break;
			}
		}
		
	}
	
	// ========================================================
	// Get the messages from the database, thread them and
	// connect the adapter, using an async task
	// ========================================================
	public void threadMessagesFromDB() {
		
		if (mDownloader != null) 
			mDownloader = null;

		new LoadFromDBAndThreadTask().execute();
	}

	
	// =======================================================================================
	// Note: Its non recursive because the obvious recursive version (which I
	// wrote at first)
	// can tore to pieces the stack pretty easily
	// =======================================================================================	
	private void fillListNonRecursive(Article root, int depth, String replyto) {

		Stack<MiniHeader> stack = new Stack<MiniHeader>();

		boolean markReplies = mPrefs.getBoolean("markReplies", true);
		boolean finished = false;
		
		String clean_subject;
		MiniHeader tmpMiniItem;
		HeaderItemClass ih = null;
		String[] refsArray;
		String msgId;
		
		ArrayList<HeaderItemClass> nonStarredItems = new ArrayList<HeaderItemClass>();
		HashSet<String> bannedTrollsSet = DBUtils.getBannedTrolls(getApplicationContext());
		HashSet<String> starredSet      = DBUtils.getStarredSubjectsSet(getApplicationContext());

		// Proxy for speed
		HashSet<String> myPostsSetProxy = mMyPostsSet;
		ArrayList<HeaderItemClass> headerItemsListProxy = new ArrayList<HeaderItemClass>();
		int refsArrayLen;
		
		while (!finished) {

			root.setReplyTo(replyto);

			if (!root.isDummy()) {
				ih = new HeaderItemClass(root, depth);

				// Don't feed the troll
				if (!bannedTrollsSet.contains(root.getFrom())) {

					// Put the replies in red (if configured)
					if (markReplies) {
						refsArray = root.getReferences();
						refsArrayLen = refsArray.length;
						msgId = null;
						
						if (refsArray != null && refsArrayLen > 0) {
							msgId = refsArray[refsArrayLen-1]; 
						}
						
						if (msgId != null && myPostsSetProxy != null && myPostsSetProxy.contains(msgId)) 
							ih.myreply = true;
						else
							ih.myreply = false;
					}
					
					clean_subject = root.simplifiedSubject();
					if (starredSet.contains(clean_subject)) {
						ih.starred = true;
						headerItemsListProxy.add(ih); // Starred items first
					} else {
						// Nonstarred items will be added to mHeaderItemsList at the end
						nonStarredItems.add(ih);
					}
				}
			}

			if (root.next != null) {
				tmpMiniItem = new MiniHeader(root.next, depth, replyto);
				stack.push(tmpMiniItem);
			}

			if (root.kid != null) {

				replyto = root.getFrom();
				if (!root.isDummy())++depth;
				root = root.kid;

			} else if (!stack.empty()) {
				
				tmpMiniItem = stack.pop();
				root = tmpMiniItem.article;
				depth = tmpMiniItem.depth;
				replyto = tmpMiniItem.replyto;

			} else
				finished = true;

		}

		// Now add the non starred items after the starred ones
		int nonStarredItemsLen = nonStarredItems.size();
		for (int i=0; i<nonStarredItemsLen; i++) {
			headerItemsListProxy.add(nonStarredItems.get(i));
		}
		
		mHeaderItemsList = headerItemsListProxy;
		nonStarredItems = null;
	}

	// ==================================================================================
	// Numbers array is an array containing every article server number. It's
	// used for
	// passing to the MessageActivity (along with an index) so it can implement
	// the Next and Prev buttons. If I knew how to pass the HeaderItemsList to
	// it this
	// array would not be neccesary...
	// ==================================================================================
	private void fillNumbersArray() {

		ArrayList<HeaderItemClass> headerItemsProxy = mHeaderItemsList;
		int headerItemsSize = headerItemsProxy.size();
		long[] numbersArrayProxy = new long[headerItemsSize];
		
		for (int i = 0; i < headerItemsSize; i++) {
			numbersArrayProxy[i] = headerItemsProxy.get(i).getArticle().getArticleNumber();
		}
		
		mNumbersArray = numbersArrayProxy;
	}

	// ===================================================================
	// Extension of ArrayAdapter which holds and maps the article fields
	// ===================================================================
	private class ArticleAdapter extends ArrayAdapter<HeaderItemClass> {

		private ArrayList<HeaderItemClass> items;

		public ArticleAdapter(Context context, int textViewResourceId, ArrayList<HeaderItemClass> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {

			View v = convertView;
			
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.messagelist_item, null);
			}

			HeaderItemClass it = items.get(position);
            HeaderItemClass prev = null;
			
            if (position > 0)
                prev = items.get(position-1);
            else
            	prev = null;
			
			if (it != null) {
				Article article = it.getArticle();
				
				LinearLayout layout = (LinearLayout) v.findViewById(R.id.layout_item);
				TextView level = (TextView) v.findViewById(R.id.text_level);
				level.setText(it.getLevelStr(), TextView.BufferType.SPANNABLE);
				
				View subjectChangeLine = (View) v.findViewById(R.id.msglist_line);
				
				/*
				 * Check the references of the article. If the first reference of the article it's equal than the last article
				 * first reference (AKA "thread reference") then the subject didn't change
				 */
				
				String[] thisArticleReferences = article.getReferences();
				String[] prevArticleReferences = null;
				Article prevArt = null;
				
				if (prev != null ) {
					prevArt = prev.getArticle();
					prevArticleReferences = prevArt.getReferences();
				} 
				
				int subjectChangeValue = View.INVISIBLE;
				
				// If there are no references in this article or the previous it's a new subject. It is, too, 
				// if both have references but the previous article first reference it's different from this article first reference.
				if (thisArticleReferences.length == 0     ||
				    prevArticleReferences == null         ||
						(prevArticleReferences != null    && 
						 prevArticleReferences.length > 0  &&
						 !prevArticleReferences[0].equalsIgnoreCase(thisArticleReferences[0]))) {
					
					subjectChangeValue = View.VISIBLE;
				}
				
				subjectChangeLine.setVisibility(subjectChangeValue);
				
				/*
				ImageView arrow = (ImageView) v.findViewById(R.id.img_reply);
				if (it.getLevel() == 0) arrow.setVisibility(View.INVISIBLE);
				else arrow.setVisibility(View.VISIBLE);
				*/

				TextView subject = (TextView) v.findViewById(R.id.text_subject);
				subject.setText(it.getSpaceStr() + article.getSubject(), TextView.BufferType.SPANNABLE);

				TextView from = (TextView) v.findViewById(R.id.text_author);
				from.setText(it.getFromNoEmail());
				
				ImageView fav = (ImageView) v.findViewById(R.id.messagelistitem_img_love);
				
	    		// Show or hide the heart marking favorite authors
	            if (MessageListActivity.this.mFavoritesSet.contains(article.getFrom())) {
	            	fav.setImageDrawable(getResources().getDrawable(R.drawable.love));
	            } else {
	            	fav.setImageDrawable(getResources().getDrawable(R.drawable.nullimage));
	            }
				
				final ImageView star = (ImageView) v .findViewById(R.id.img_thread_star);
				star.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						itemStarClicked(position);
					}
				});

				if (MessageListActivity.this.mReadSet.contains(article.getArticleId())) {  
					//it.read = true;
					layout.setBackgroundColor(0x99aaaaaa);
				} else if (it.myreply) {
					layout.setBackgroundColor(Color.YELLOW);
				} else {
					//it.read = false;
					layout.setBackgroundColor(Color.TRANSPARENT);
				}
				

				if (it.starred)
					star.setImageDrawable(getResources().getDrawable(R.drawable.star_big_on));
				else
					star.setImageDrawable(getResources().getDrawable(R.drawable.star_big_off));
			}
			return v;
		}
	}

	
	private void itemStarClicked(int position) {

		HeaderItemClass header = mHeaderItemsList.get(position);
		boolean newValue = !header.starred;
		ArrayList<HeaderItemClass> itemsProxy = mHeaderItemsList;
		int itemsSize = itemsProxy.size();

		String starred_thread_subject = header.getArticle().simplifiedSubject();

		HeaderItemClass current;
		for (int i = 0; i < itemsSize; i++ ) {
			current = itemsProxy.get(i);
			if (starred_thread_subject.equalsIgnoreCase(current.getArticle().simplifiedSubject())) {
				current.starred = !current.starred;
			}
		}

		DBUtils.updateStarredThread(newValue, starred_thread_subject, mGroupID, getApplicationContext());
		getListView().invalidateViews();
	}


}
