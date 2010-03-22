package com.almarsoft.GroundhogReader;

import java.lang.reflect.Method;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.Dialog;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.almarsoft.GroundhogReader.lib.DBHelper;
import com.almarsoft.GroundhogReader.lib.DBUtils;
import com.almarsoft.GroundhogReader.lib.FSUtils;
import com.almarsoft.GroundhogReader.lib.ServerManager;
import com.almarsoft.GroundhogReader.lib.UsenetConstants;

public class GroupListActivity extends ListActivity {
    /** Activity showing the list of subscribed groups. */
	
	//private final String MAGIC_GROUP_ADD_STRING = "Subscribe to group...";
	
	private static final int MENU_ITEM_MARKALLREAD = 1;
	private static final int MENU_ITEM_UNSUBSCRIBE = 2;

	private static final int ID_DIALOG_DELETING = 0;
	private static final int ID_DIALOG_UNSUBSCRIBING = 1;
	private static final int ID_DIALOG_MARKREAD = 2;

	// Real name of the groups, used for calling the MessageListActivity with the correct name
	private String[] mGroupsArray;
	private String mTmpSelectedGroup;
	
	// Name of the group + unread count, used for the listView arrayAdapter
	private String[] mGroupsWithUnreadCountArray;
	
	// This is a member so we can interrupt its operation, but be carefull to create it just
	// before the operation and assign to null once it has been used (at the start of the callback, not in the next line!!!)
	private GroupMessagesDownloadDialog mDownloader = null;
	private ServerManager mServerManager;
	
	private Button addButton;
	private Button settingsButton;

	private boolean mOfflineMode;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	ListView thisListView = this.getListView();
		registerForContextMenu(thisListView);
		mServerManager = new ServerManager(getApplicationContext());
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// Detect first-time usage and show help
		boolean firstTime = prefs.getBoolean("firstTime", true);
		
		if (firstTime) {
			Editor ed = prefs.edit();
			ed.putBoolean("firstTime", false);
			ed.commit();
			startActivity(new Intent(GroupListActivity.this, HelpActivity.class));
		}
		
		mOfflineMode = prefs.getBoolean("offlineMode", true);
		
		if (mOfflineMode) 
			setTitle(getString(R.string.group_offline_mode));
		else 
			setTitle(getString(R.string.group_online_mode));
		
		// Add the buttons
		addButton        = new Button(this);
		addButton.setText(getString(R.string.grouplist_add_groups));
		addButton.setOnClickListener(	
					new Button.OnClickListener() {
						public void onClick(View v) {
							startActivity(new Intent(GroupListActivity.this, SubscribeActivity.class));
						}
					}
		);
		thisListView.addHeaderView(addButton);
		
		settingsButton = new Button(this);
		settingsButton.setText(getString(R.string.global_settings));
		settingsButton.setOnClickListener(
					new Button.OnClickListener() {
						public void onClick(View v) {
							startActivity(new Intent(GroupListActivity.this, OptionsActivity.class));
						}
					}
		);
		thisListView.addFooterView(settingsButton);
    }

    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	Log.d(UsenetConstants.APPNAME, "GroupList onResume");
		
		// =====================================================
        // Try to detect server hostname changes in the settings
    	// =====================================================
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (prefs.getBoolean("hostChanged", false)) {
			// The host  has changed in the prefs, show the dialog and clean the group headers
			new AlertDialog.Builder(GroupListActivity.this)
			.setTitle(getString(R.string.group_headers))
			.setMessage(getString(R.string.server_change_detected))
		    .setNeutralButton("Close", null)
		    .show();	
			
			DBUtils.restartAllGroupsMessages(getApplicationContext());
			
			// Finally remote the "dirty" mark and repaint the screen
			Editor editor = prefs.edit();
			editor.putBoolean("hostChanged", false);
			editor.commit();
			
		}
			
		Log.d(UsenetConstants.APPNAME, "onResume, recreating ServerManager");
		if (mServerManager == null)
			mServerManager = new ServerManager(getApplicationContext());
		
        //=======================================================================
        // Load the group names and unreadcount from the subscribed_groups table
        //=======================================================================
    	updateGroupList();
		
    }
    
	@Override
	protected void onPause() {
		super.onPause();
	
		Log.d(UsenetConstants.APPNAME, "GroupListActivity onPause");
		
		if (mDownloader != null) 
			mDownloader.interrupt();
		
		
    	if (mServerManager != null) 
    		mServerManager.stop();
    	mServerManager = null;
	}    
	
	
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == ID_DIALOG_DELETING){
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage(getString(R.string.deleting_d));
			loadingDialog.setIndeterminate(true);
			loadingDialog.setCancelable(true);
			return loadingDialog;
			
		} else if(id == ID_DIALOG_UNSUBSCRIBING){
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage(getString(R.string.unsubscribing_deleting_caches));
			loadingDialog.setIndeterminate(true);
			loadingDialog.setCancelable(true);
			return loadingDialog;
			
		} else if(id == ID_DIALOG_MARKREAD){
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage(getString(R.string.marking_read_deleting_caches));
			loadingDialog.setIndeterminate(true);
			loadingDialog.setCancelable(true);
			return loadingDialog;
		}  		

		return super.onCreateDialog(id);
	}
	
    
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation change because it would cause the message list to
		// be reloaded
		super.onConfigurationChanged(newConfig);
	}

    
    public void updateGroupList() {
    	
    	// We're probably called from mDownloader, so clear it
    	if (mDownloader != null) 
    		mDownloader = null;
    	
		DBHelper db = new DBHelper(getApplicationContext());
		SQLiteDatabase dbWrite = db.getWritableDatabase();
		
		Cursor cur = dbWrite.rawQuery("SELECT name FROM subscribed_groups", null);
	
		cur.moveToFirst();
		int count = cur.getCount();
		
		String[] proxyGroupsArray = new String[count];
		String[] proxyGroupsUnreadCount = new String[count];
		
		String curGroupName;
		int unread;
		
		StringBuilder builder = new StringBuilder(80);
		
		for (int i = 0; i < count; i++) {
			curGroupName = cur.getString(0);
			proxyGroupsArray[i] = curGroupName;
			//unread = cur.getInt(1);
			unread = DBUtils.getGroupUnreadCount(curGroupName, getApplicationContext());
			
			if (unread == -1) 
				proxyGroupsUnreadCount[i] = proxyGroupsArray[i];
			else {              
				proxyGroupsUnreadCount[i] = builder
			                                .append(proxyGroupsArray[i])
			                                .append(" (")
			                                .append(unread)
			                                .append(')')
			                                .toString();
				builder.delete(0, builder.length());
			}
			cur.moveToNext();
		}
		
		cur.close(); dbWrite.close(); db.close();
		
		mGroupsWithUnreadCountArray = proxyGroupsUnreadCount;
		mGroupsArray = proxyGroupsArray;
		
		// Finally fill the list
        setListAdapter(new ArrayAdapter<String>(this, R.layout.grouplist_item, mGroupsWithUnreadCountArray));
        getListView().invalidateViews();
    }
    
	// ================================================
	// Menu setting
	// ================================================
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		new MenuInflater(getApplication()).inflate(R.menu.grouplistmenu, menu);
		return(super.onCreateOptionsMenu(menu));
		
	}
	
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {		
		
		MenuItem getAll = menu.findItem(R.id.grouplist_menu_getall);
		MenuItem offline = menu.findItem(R.id.grouplist_menu_offline);
		
		if (mOfflineMode) {
			getAll.setTitle(getString(R.string.sync_messages));
			getAll.setIcon(android.R.drawable.ic_menu_upload);
			offline.setTitle(getString(R.string.set_online_mode));
			offline.setIcon(android.R.drawable.presence_online);
			
		} else {
			getAll.setTitle(getString(R.string.get_all_headers));
			getAll.setIcon(android.R.drawable.ic_menu_set_as);
			offline.setTitle(getString(R.string.set_offline_mode));
			offline.setIcon(android.R.drawable.presence_offline);
		}
		return (super.onPrepareOptionsMenu(menu));
	}
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.grouplist_menu_addgroups:
				startActivity(new Intent(GroupListActivity.this, SubscribeActivity.class));
				return true;
				
			case R.id.grouplist_menu_settings:
				startActivity(new Intent(GroupListActivity.this, OptionsActivity.class));
				return true;
				
			case R.id.grouplist_menu_getall:
				getAllMessages();
				return true;
				
			case R.id.grouplist_menu_offline:
				mOfflineMode = !mOfflineMode;
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
				Editor editor = prefs.edit();
				editor.putBoolean("offlineMode", mOfflineMode);
				editor.commit();
				
				if (mOfflineMode) 
					setTitle(getString(R.string.group_offline_mode));
				else 
					setTitle(getString(R.string.group_online_mode));
				return true;
				
			case R.id.grouplist_menu_clearcache:
				showClearCacheDialog();
				return true;
				
			case R.id.grouplist_menu_quickhelp:
				startActivity(new Intent(GroupListActivity.this, HelpActivity.class));
				return true;
		}
		return false;
	}
	
	
	private void showClearCacheDialog() {
		new AlertDialog.Builder(GroupListActivity.this)
		.setTitle(getString(R.string.clear_cache))
		.setMessage(getString(R.string.confirm_delete_cache))
	    .setPositiveButton("Yes", 
	    	new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dlg, int sumthin) { 
	    			clearCache();
	    		} 
	        } 
	     )		     
	     .setNegativeButton("No", null)		     		    		 
	     .show();		
	}
	
	
	private void clearCache() {
		
		AsyncTask<Void, Void, Void> cacheDeleterTask = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... arg0) {
				DBUtils.deleteAllMessages(GroupListActivity.this.getApplicationContext());
				FSUtils.deleteDirectory(UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/offlinecache/groups");
				FSUtils.deleteDirectory(UsenetConstants.EXTERNALSTORAGE + "/" + UsenetConstants.APPNAME + "/attachments");
				return null;
			}
			
			protected void onPostExecute(Void arg0) {
				updateGroupList();
				dismissDialog(ID_DIALOG_DELETING);
			}

		};
		
		showDialog(ID_DIALOG_DELETING);
		cacheDeleterTask.execute();
	}

	
	@SuppressWarnings("unchecked")
	private void getAllMessages() {
		
		int groupslen = mGroupsArray.length;
		
		if (groupslen == 0) 
			return;
		
		Vector<String> groupVector = new Vector<String>(groupslen);
		
		for (int i=0; i < groupslen; i++) {
			groupVector.add(mGroupsArray[i]);
			Log.d("XXX", "Aniadiendo " + mGroupsArray[i]);
		}
		
		Class[] noargs = new Class[0];
		
		try {
			
			Method callback = this.getClass().getMethod("updateGroupList", noargs);
			
			mDownloader = new GroupMessagesDownloadDialog(mServerManager, this);
			mDownloader.synchronize(mOfflineMode, groupVector, callback, this);
			
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}		
	}
    
	// ==============================
	// Contextual menu on group
	// ==============================
	
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	new MenuInflater(getApplicationContext()).inflate(R.menu.grouplist_item_menu, menu);
    	menu.setHeaderTitle(getString(R.string.group_menu));
    	super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        //HeaderItemClass header = mHeaderItemsList.get(info.position);
        final String groupname = mGroupsArray[info.position-1];
        int order = item.getOrder();
        
    	// "Mark all as read" => Show confirm dialog and call markAllRead
    	if (order == MENU_ITEM_MARKALLREAD) {
    		String msg = getString(R.string.mark_read_question);
    		msg = java.text.MessageFormat.format(msg, groupname);   
    		
			new AlertDialog.Builder(GroupListActivity.this)
			.setTitle(getString(R.string.mark_all_read))
			.setMessage(msg)
		    .setPositiveButton(getString(R.string.yes), 
		    	new DialogInterface.OnClickListener() {
		    		public void onClick(DialogInterface dlg, int sumthin) { 
		    			markAllRead(groupname);
		    		} 
		        } 
		     )		     
		     .setNegativeButton(getString(R.string.no), null)		     		    		 
		     .show();	
    		return true;
    	}
    	
    	// "Unsubscribe group" => Show confirm dialog and call unsubscribe
    	if (order == MENU_ITEM_UNSUBSCRIBE) {
    		String msg = getString(R.string.unsubscribe_question);
    		msg = java.text.MessageFormat.format(msg, groupname);     		
    		
			new AlertDialog.Builder(GroupListActivity.this)
			.setTitle(getString(R.string.unsubscribe))
			.setMessage(msg)
		    .setPositiveButton(getString(R.string.yes), 
		    	new DialogInterface.OnClickListener() {
		    		public void onClick(DialogInterface dlg, int sumthin) { 
		    			unsubscribe(groupname);
		    		} 
		        } 
		     )		     
		     .setNegativeButton(getString(R.string.no), null)		     		    		 
		     .show();	
    		return true;
    	}
        return false;
    }
    
    
    private void markAllRead(final String group) {
    	
		AsyncTask<Void, Void, Void> markAllReadTask = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... arg0) {
	    		DBUtils.groupMarkAllRead(group, GroupListActivity.this.getApplicationContext());
	    		DBUtils.deleteReadMessages(GroupListActivity.this.getApplicationContext());
				return null;
			}
			
			protected void onPostExecute(Void arg0) {
				updateGroupList();
				dismissDialog(ID_DIALOG_MARKREAD);
			}

		};
		
		showDialog(ID_DIALOG_MARKREAD);
		markAllReadTask.execute();
    }
    
    
    private void unsubscribe(final String group) {

		AsyncTask<Void, Void, Void> unsubscribeTask = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... arg0) {
    			DBUtils.unsubscribeGroup(group, GroupListActivity.this.getApplicationContext());
				return null;
			}
			
			protected void onPostExecute(Void arg0) {
				updateGroupList();
				dismissDialog(ID_DIALOG_UNSUBSCRIBING);
			}

		};
		
		showDialog(ID_DIALOG_UNSUBSCRIBING);
		unsubscribeTask.execute();    	
    }
    
    // ==================================================================================================
    // OnItem Clicked Listener (start the MessageListActivity and pass the clicked group name
    // ==================================================================================================

    
    // Dialog code in swing/android is soooooooooooooooooo ugly :(
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		final String groupName = mGroupsArray[position-1];
		
		mTmpSelectedGroup = groupName;
		
		// If in offlinemode, offer to synchronize uncatched messages first, if there is any
		Context context = getApplicationContext();
		
		if (mOfflineMode) {
			// -------------------------------------------------------------------------------------------------
			// If we've headers downloaded in online mode offer to download the bodies before entering the group
			// -------------------------------------------------------------------------------------------------
			if (DBUtils.groupHasUncatchedMessages(mTmpSelectedGroup, context)) {
				new AlertDialog.Builder(GroupListActivity.this)
				.setTitle(getString(R.string.get_new))
				.setMessage(getString(R.string.warning_online_to_offline_sync))
				
			    .setPositiveButton(getString(R.string.yes_sync), 
			    	new DialogInterface.OnClickListener() {
			    	
			    		@SuppressWarnings("unchecked")
						public void onClick(DialogInterface dlg, int sumthin) {
			    			Vector<String> groupVector = new Vector<String>(1);
			    			groupVector.add(mTmpSelectedGroup);
			    			
							try {
								Class[] noargs = new Class[0];
								// This will be called after the synchronize from mDownloader:
								Method callback = GroupListActivity.this.getClass().getMethod("fetchFinishedStartMessageList", noargs);
								mDownloader    = new GroupMessagesDownloadDialog(mServerManager, GroupListActivity.this);
								mDownloader.synchronize(true, groupVector, callback, GroupListActivity.this);
							} catch (SecurityException e) {
								e.printStackTrace();
							} catch (NoSuchMethodException e) {
								e.printStackTrace();
							}
			    			
			    		} 
			        } 
			     )		     
			     .setNegativeButton(getString(R.string.no_enter_anyway),
			        new DialogInterface.OnClickListener() {
			    	 	public void onClick(DialogInterface dlg, int sumthin) {
			    	 		fetchFinishedStartMessageList();
			    	 	}
			     	}
			     )		     		    		 
			     .show();
			}
			// -----------------------------------------------------------------------------
			// If there are 0 unread messages on offline mode offer to synchronice the group
			// -----------------------------------------------------------------------------
			else if (DBUtils.getGroupUnreadCount(mTmpSelectedGroup, context) == 0) {
				new AlertDialog.Builder(GroupListActivity.this)
				.setTitle(getString(R.string.get_new))
				.setMessage(getString(R.string.offline_group_has_no_messages_sync))
				
			    .setPositiveButton(getString(R.string.yes_sync), 
			    	new DialogInterface.OnClickListener() {
			    	
			    		@SuppressWarnings("unchecked")
						public void onClick(DialogInterface dlg, int sumthin) {
			    			Vector<String> groupVector = new Vector<String>(1);
			    			groupVector.add(mTmpSelectedGroup);
			    			
							try {
								Class[] noargs = new Class[0];
								// This will be called after the synchronize from mDownloader:
								Method callback = GroupListActivity.this.getClass().getMethod("fetchFinishedStartMessageList", noargs);
								mDownloader    = new GroupMessagesDownloadDialog(mServerManager, GroupListActivity.this);
								mDownloader.synchronize(true, groupVector, callback, GroupListActivity.this);
							} catch (SecurityException e) {
								e.printStackTrace();
							} catch (NoSuchMethodException e) {
								e.printStackTrace();
							}
			    			
			    		} 
			        } 
			     )		     
			     .setNegativeButton(getString(R.string.no_enter_anyway),
			        new DialogInterface.OnClickListener() {
			    	 	public void onClick(DialogInterface dlg, int sumthin) {
			    	 		fetchFinishedStartMessageList();
			    	 	}
			     	}
			     )		     		    		 
			     .show();
				
			} else {
				fetchFinishedStartMessageList();
			}
			
		} else {
			// ==========================================
			// Online mode, ask about getting new headers
			// ==========================================
    		String msg = getString(R.string.fetch_headers_question);
    		msg = java.text.MessageFormat.format(msg, mTmpSelectedGroup);
    		
			new AlertDialog.Builder(GroupListActivity.this)
			.setTitle(getString(R.string.get_new))
			.setMessage(msg)
			
		    .setPositiveButton(getString(R.string.yes), 
		    	new DialogInterface.OnClickListener() {
		    		@SuppressWarnings("unchecked")
					public void onClick(DialogInterface dlg, int sumthin) {
		    			Vector<String> groupVector = new Vector<String>(1);
		    			groupVector.add(mTmpSelectedGroup);
		    			
						try {
							Class[] noargs = new Class[0];
							Method callback = GroupListActivity.this.getClass().getMethod("fetchFinishedStartMessageList", noargs);
							mDownloader    = new GroupMessagesDownloadDialog(mServerManager, GroupListActivity.this);
							mDownloader.synchronize(false, groupVector, callback, GroupListActivity.this);
						} catch (SecurityException e) {
							e.printStackTrace();
						} catch (NoSuchMethodException e) {
							e.printStackTrace();
						}
		    			
		    		} 
		        } 
		     )		     
		     .setNegativeButton("No",
		        new DialogInterface.OnClickListener() {
		    	 	public void onClick(DialogInterface dlg, int sumthin) {
		    	 		fetchFinishedStartMessageList();
		    	 	}
		     	}
		     )		     		    		 
		     .show();	
		}
    }
    
    
    public void fetchFinishedStartMessageList() {
    	if (mDownloader != null)
    		mDownloader = null;
    	Intent msgList = new Intent(GroupListActivity.this, MessageListActivity.class);
    	msgList.putExtra("selectedGroup", mTmpSelectedGroup);
    	startActivity(msgList);
    }
}

