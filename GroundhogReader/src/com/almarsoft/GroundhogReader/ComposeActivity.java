package com.almarsoft.GroundhogReader;

import java.io.IOException;
import java.net.SocketException;

import org.apache.commons.codec.EncoderException;
import org.apache.james.mime4j.message.Header;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.almarsoft.GroundhogReader.lib.MessagePoster;
import com.almarsoft.GroundhogReader.lib.MessageTextProcessor;
import com.almarsoft.GroundhogReader.lib.ServerAuthException;
import com.almarsoft.GroundhogReader.lib.UsenetReaderException;

public class ComposeActivity extends Activity {
	
	private static final int ID_DIALOG_POSTING = 0;
	
	private EditText mEdit_Groups;
	private EditText mEdit_Subject;
	private EditText mEdit_Body;
	
	private boolean mIsNew;
	private String mPostingErrorMessage = null;
	private String mCurrentGroup = null;
	private String mMessageID = null;
	private String mReferences = null;
	
	private final Handler mHandler = new Handler();
	private SharedPreferences mPrefs;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		setContentView(R.layout.compose);

		mEdit_Groups  = (EditText) this.findViewById(R.id.edit_groups);
		mEdit_Subject = (EditText) this.findViewById(R.id.edit_subject);
		mEdit_Body    = (EditText) this.findViewById(R.id.edit_body);
		
        this.setComposeSizeFromPrefs(0);

		// Get the header passed from the ; for the moment we only need the newsgroups and subject,
		// but we later will need more parts for posting
		
        Bundle extras = getIntent().getExtras();
		mIsNew        = extras.getBoolean("isNew");
		mCurrentGroup = extras.getString("group");
		
		Toast.makeText(getApplicationContext(), getString(R.string.encoding) + ": " + mPrefs.getString("postCharset", "UTF-8") + getString(R.string.change_encoding_tip), Toast.LENGTH_SHORT).show();
		
		if (mIsNew) {
			mEdit_Groups.setText(mCurrentGroup);
			mEdit_Subject.requestFocus();
		} 
		
		else {

			String prevFrom   = extras.getString("From");
			String prevDate   = extras.getString("Date");
			String newsgroups = extras.getString("Newsgroups");			
			mMessageID = extras.getString("Message-ID");
			if (extras.containsKey("References"))
				mReferences = extras.getString("References");
			
			if (extras.containsKey("Subject")) {
				String prevSubject = extras.getString("Subject");
				if (!prevSubject.toLowerCase().contains("re:")) {
					prevSubject = "Re: " + prevSubject;
				}
				mEdit_Subject.setText(prevSubject);
			}
			
			String followupOption = extras.getString("multipleFollowup");
			
			if (followupOption == null || !followupOption.equalsIgnoreCase("CURRENT"))
				mEdit_Groups.setText(newsgroups);
			else
				mEdit_Groups.setText(mCurrentGroup);
				
			mEdit_Body.setText("");
			
			// Get the quoted bodytext, set it and set the cursor at the configured position
			String bodyText = (String) extras.getString("bodytext");
			boolean replyCursorStart = mPrefs.getBoolean("replyCursorPositionStart", false);
			
			String quoteheader = mPrefs.getString("authorline", "On [date], [user] said:");
			String quotedBody = MessageTextProcessor.quoteBody(bodyText, quoteheader, prevFrom, prevDate);
			
			if (bodyText != null && bodyText.length() > 0) {
				
				if (replyCursorStart) {
					mEdit_Body.setText("\n\n" + quotedBody);
					mEdit_Body.setSelection(1);
				}
				else {
					mEdit_Body.setText(quotedBody + "\n\n");
					mEdit_Body.setSelection(mEdit_Body.getText().length());
				}
			}
			
			mEdit_Body.requestFocus();
		} // End else isNew

	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == ID_DIALOG_POSTING){
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage(getString(R.string.posting_message));
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

	// ================================================
	// Menu setting
	// ================================================
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(getApplication()).inflate(R.menu.composemenu, menu);
		return(super.onCreateOptionsMenu(menu));

	}

	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.compose_menu_send:
				postMessage();
				return true;
			case R.id.compose_menu_cancel:
				finish();
				return true;
			case R.id.compose_menu_deletetext:
				mEdit_Body.setText("");
				return true;
            case R.id.compose_menu_bigtext:
                setComposeSizeFromPrefs(1);
                return true;
            case R.id.compose_menu_smalltext:
                setComposeSizeFromPrefs(-1);
                return true;
            case R.id.compose_menu_charset:
            	startActivity(new Intent(ComposeActivity.this, CharsetActivity.class));
            	return true;

		}
		return false;
	}


	// ====================================================================================
	// Post the message. This is a set of the following operations:
	// - Get the reference list of the original message and add its msgid at the end
	// - Add the "From" with reference to ourselves
	// - Generate our own msgid
	// - Add the signatura (if any)
	// - Create the dialog
	// - Create the thread that do the real posting
	// - Make the thread update the dialog using an UIupdater like the other views
	// - At the end, make the uiupdater show a popup of confirmation (or error); if it's 
	//   confirmation go back to the MessageActivity. If its error go back to the composing
	//   view
	// =====================================================================================
    
	private void postMessage() {
		
		Thread messagePosterThread = new Thread() {
			
			public void run() {

				MessagePoster poster = new MessagePoster(mCurrentGroup, 
						                                 mEdit_Groups.getText().toString(), 
						                          		 mEdit_Body.getText().toString(), 
						                                 mEdit_Subject.getText().toString(), 
						                                 mReferences, mMessageID, ComposeActivity.this);
				
				try {
					
					poster.postMessage();
				} catch (SocketException e) {
					e.printStackTrace();
					mPostingErrorMessage = e.toString();
				} catch (EncoderException e) {
					e.printStackTrace();
					mPostingErrorMessage = e.toString();
				} catch (IOException e) {
					e.printStackTrace();
					mPostingErrorMessage = e.toString();
				} catch (ServerAuthException e) {
					e.printStackTrace();
					mPostingErrorMessage = e.toString();
				} catch (UsenetReaderException e) {
					e.printStackTrace();
					mPostingErrorMessage = e.toString();
				}
				
				mHandler.post(new Runnable() { public void run() { updateResultsInUi(); } });
			}
		};
		
		String groups = mEdit_Groups.getText().toString();
		
		if (groups == null || groups.trim().length() == 0) {
			new AlertDialog.Builder(ComposeActivity.this) .setTitle(getString(R.string.empty_groups))
			    .setMessage(getString(R.string.must_select_group))
			    .setNeutralButton("Close", null) .show();
		}
		else {
	    	messagePosterThread.start();
	    	showDialog(ID_DIALOG_POSTING);
		}
	}
	
	
	private void updateResultsInUi() {
		dismissDialog(ID_DIALOG_POSTING);
		
		if (mPostingErrorMessage != null)  {
			new AlertDialog.Builder(ComposeActivity.this) .setTitle(getString(R.string.error_posting))
			                        .setMessage(mPostingErrorMessage).setNeutralButton(getString(R.string.close), null) .show();
			mPostingErrorMessage = null;
		} 
		else {
			setResult(RESULT_OK);
			finish();
		}		
	}
	
	private void setComposeSizeFromPrefs(int increase) {
    	
    	int textSize = mPrefs.getInt("composeViewTextSize", 14);
    	
    	if (increase > 0) {  
    			textSize++;    		
    	} else if (increase < 0) {
    			textSize--;
    	
    	}
    	
		Editor editor = mPrefs.edit();
		editor.putInt("composeViewTextSize", textSize);
		editor.commit();    
		
		mEdit_Groups.setTextSize(textSize);
        mEdit_Subject.setTextSize(textSize);
        mEdit_Body.setTextSize(textSize);		

	}	
}
