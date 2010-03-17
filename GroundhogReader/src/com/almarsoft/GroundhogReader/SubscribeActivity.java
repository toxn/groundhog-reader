package com.almarsoft.GroundhogReader;

import java.io.IOException;

import org.apache.commons.net.nntp.NewsgroupInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.almarsoft.GroundhogReader.lib.DBUtils;
import com.almarsoft.GroundhogReader.lib.ServerAuthException;
import com.almarsoft.GroundhogReader.lib.ServerManager;
import com.almarsoft.GroundhogReader.lib.UsenetConstants;

public class SubscribeActivity extends Activity {
	
	
    /** Called when the activity is first created. */	

	private static final int ID_DIALOG_SEARCHING = 0;
	
	private static final int FINISHED_OK = 1;
	private static final int FINISHED_ERROR = 2;
	private static final int FINISHED_AUTH_ERROR = 3;
	private static final int NOT_CONFIGURED = 4;
	
	final Handler mHandler = new Handler();	
	private ListView mView_Results;
	private EditText mSearchText;		
	private Button mButton_Search;
	private String[] mSearchResultsStr;
	private ServerManager mServerManager;
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	setContentView(R.layout.subscribe);
    	
    	mServerManager = new ServerManager(SubscribeActivity.this);
    	
        mView_Results = (ListView) this.findViewById(R.id.search_results);
        mButton_Search = (Button) this.findViewById(R.id.btn_search);
        mSearchText = (EditText) this.findViewById(R.id.searchGroups);
        mButton_Search.setOnClickListener(mSearchListener);
        
        //mView_Results.setOnItemSelectedListener(mItemSelectedListener);
        mView_Results.setOnItemClickListener(mItemClickListener);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	
    	Log.d(UsenetConstants.APPNAME, "SubscribeActivity onPause");

    	if (mServerManager != null)
    		mServerManager.stop();
    	mServerManager = null;
    	
    	try {
    		dismissDialog(ID_DIALOG_SEARCHING);
    	} catch (NullPointerException e) {}
    	  catch (IllegalArgumentException e) {}
    
    	
    }
    
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == ID_DIALOG_SEARCHING){
			ProgressDialog searchDialog = new ProgressDialog(this);
			searchDialog.setMessage("Searching matching groups...");
			searchDialog.setIndeterminate(true);
			searchDialog.setCancelable(true);
			return searchDialog;
		}

		return super.onCreateDialog(id);
	}
    
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	Log.d(UsenetConstants.APPNAME, "SubscribeActivity onResume");
    	if (mServerManager == null) 
    		mServerManager = new ServerManager(SubscribeActivity.this);
    }
    
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation change because it would cause the message list to
		// be reloaded
		super.onConfigurationChanged(newConfig);
	}

    // ==================================================================================================
    // OnItem Clicked Listener (add the group) ==========================================================
    // ==================================================================================================
    
    OnItemClickListener mItemClickListener = new OnItemClickListener() {    	
    	
    	String mGroupName;

		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			
			mGroupName = mSearchResultsStr[position];
			String msg = getResources().getString(R.string.subscribe_question); // contains {0}, {1}, and {2} in the XML file
			msg = java.text.MessageFormat.format(msg, mGroupName);
			
			// Show a confirmation dialog		
			new AlertDialog.Builder(SubscribeActivity.this)
			.setTitle(getString(R.string.add_group))
			.setMessage(msg)    							    		 
		    .setPositiveButton(getString(R.string.yes), 
		    	new DialogInterface.OnClickListener(){
		    		public void onClick(DialogInterface dlg, int sumthin){ subscribeGroup(mGroupName); } 
		        } 
		     )		     
		     .setNegativeButton(getString(R.string.no), null)		     		    		 
		     .show();		
        				
		}
		
		//====================================================
		// Called when the user confirms a group subscription
		//====================================================
		
		private void subscribeGroup(String groupName) {
			
			// First check that the user is not already subscribed to the group
			if (DBUtils.isGroupSubscribed(mGroupName, getApplicationContext())) {
				String msg = getResources().getString(R.string.already_subscribed_long);
				msg = java.text.MessageFormat.format(msg, mGroupName);
				
				// Already subscribed
				new AlertDialog.Builder(SubscribeActivity.this)
				.setTitle(getString(R.string.already_subscribed_excl))
				.setMessage(msg)    							    		 
			    .setNeutralButton("Close", null)  
			    .show();
				return;
			}
			
			DBUtils.subscribeGroup(mGroupName, getApplicationContext());
			String msg2 = getResources().getString(R.string.subscribed_ok);
			msg2 = java.text.MessageFormat.format(msg2, mGroupName); 
			Toast.makeText(SubscribeActivity.this, msg2, Toast.LENGTH_LONG).show();
			
		}
    	
    };
    
    // ==================================================================================================
    // Search Listener ==================================================================================
    // ==================================================================================================
    
    OnClickListener mSearchListener = new OnClickListener() {
    	
    	private NewsgroupInfo[] mSearchResults;
    	
    	String mTmpSearchText;
    	
    	//private ProgressDialog mSearchProgress;
    	private Thread mConnectionThread;

		public void onClick(View v) {
			String searchText = mSearchText.getText().toString();
			
			// Check that the text it's not too short 
			if (searchText.length() < 3) {
				
				new AlertDialog.Builder(SubscribeActivity.this)
				.setTitle(getString(R.string.too_short_excl))
				.setMessage(getString(R.string.too_short_three_chars))    							    		 
			    .setNeutralButton(getString(R.string.close), null)
			    .show();			
			     								
			} else {

				searchGroups(searchText);
				Log.d(UsenetConstants.APPNAME, "SearchButton:" + searchText);
			}
		}
		
		// Shortcut to log and update the UI
	    private void updateStatus(final String textStatus, final int threadStatus) {
	    	mHandler.post(new Runnable() {
		        public void run() {
		            updateResultsInUi(textStatus, threadStatus);
		        }
		    });
	    }
			
		// Creates the "Please wait" dialog, create the thread, closes dialog
	    // all while updating the UI ussing a Runnable
		private void searchGroups(String searchText) {
			
			// This is because I'm lazy and I don't want to subclass the thread just to pass
			// arguments :)
			mTmpSearchText = searchText;
			
			// This thread is where the connection to the se
			mConnectionThread = new Thread() {
				
			    // Main thread code --------------------
			    public void run() {			    	
			    				    	
			    	try {

			    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SubscribeActivity.this);
			    		
		        		String host = prefs.getString("host", null);
		        		if (host == null) {
		        			updateStatus(getString(R.string.not_configured), NOT_CONFIGURED);
		        			return;
		        		} 
		        			
		        		mSearchResults = mServerManager.listNewsgroups("*" + mTmpSearchText + "*");
		        		
		        		if (mSearchResults == null) {
		        			throw new IOException();
		        		}
		        		
		        		mTmpSearchText = null;		        		
		        		updateStatus(getString(R.string.finished), FINISHED_OK);		        			
		        		
			    	} catch (IOException e) {
		        		updateStatus(getString(R.string.error), FINISHED_ERROR);
		        		e.printStackTrace();
		        	} catch (ServerAuthException e) {
		        		updateStatus(getString(R.string.auth_error), FINISHED_AUTH_ERROR);
		        	}
			    	
			    }
				
			};
			
			mConnectionThread.start();
			showDialog(ID_DIALOG_SEARCHING);
			
		}
		
		
	    // Check if the thread has finished and then close the dialog and
	    // update the search results ListView
	    private void updateResultsInUi(String status, int threadStatus) {
	    	
	    	dismissDialog(ID_DIALOG_SEARCHING);
	    	
	    	if (threadStatus == FINISHED_ERROR) {
	    		
				new AlertDialog.Builder(SubscribeActivity.this)
				.setTitle(getString(R.string.error))
				.setMessage(getString(R.string.error_retrieving_check_connection))    							    		 
			    .setPositiveButton(getString(R.string.yes), 
				    	new DialogInterface.OnClickListener(){
				    		public void onClick(DialogInterface dlg, int sumthin){ 
								startActivity(new Intent(SubscribeActivity.this, OptionsActivity.class)); 
				    		} 
				        } 
				     )		     
			    .setNegativeButton(getString(R.string.no), null)		     		    		 
			    .show();			
				
	    	}
	    	if (threadStatus == FINISHED_AUTH_ERROR) {
	    		
				new AlertDialog.Builder(SubscribeActivity.this)
				.setTitle(getString(R.string.auth_error))
				.setMessage(getString(R.string.error_auth_check_pass_go_settings))    							    		 
			    .setPositiveButton(getString(R.string.yes), 
				    	new DialogInterface.OnClickListener(){
				    		public void onClick(DialogInterface dlg, int sumthin){ 
								startActivity(new Intent(SubscribeActivity.this, OptionsActivity.class)); 
				    		} 
				        } 
				     )		     
			    .setNegativeButton(getString(R.string.no), null)		     		    		 
			    .show();		
				
	    	}
	    		    	
	    	else if (threadStatus == FINISHED_OK) {
	    		
	    		if (mSearchResults != null && mSearchResults.length > 0) {	    			
		    		
		    		String[] searchResultsStrProxy = new String[mSearchResults.length];
		    		NewsgroupInfo[] searchResultsProxy = mSearchResults;
		    		int searchLen = searchResultsProxy.length;
		    		
		    		for (int i = 0; i < searchLen; i++) {
		    			searchResultsStrProxy[i] = searchResultsProxy[i].getNewsgroup();		    			
		    		}
		    		
		    		mSearchResultsStr = searchResultsStrProxy;
		    		
		            mView_Results.setAdapter(new ArrayAdapter<String>(SubscribeActivity.this, 
		            		android.R.layout.simple_list_item_1, mSearchResultsStr));	            
	    		}	    		
	    		
	    	} else if (threadStatus == NOT_CONFIGURED) {
	    		
	    		// The host is not configured
				new AlertDialog.Builder(SubscribeActivity.this)
				.setTitle(getString(R.string.go_to_settings))
				.setMessage(getString(R.string.hostname_not_configured_goto_settings))    							    		 
			    .setPositiveButton(getString(R.string.yes), 
			    	new DialogInterface.OnClickListener(){
			    		public void onClick(DialogInterface dlg, int sumthin){ 
							startActivity(new Intent(SubscribeActivity.this, OptionsActivity.class)); 
			    		} 
			        } 
			     )		     
			     .setNegativeButton(getString(R.string.no), null)		     		    		 
			     .show();			    		
	    	}
	    }
	    
    };
}

