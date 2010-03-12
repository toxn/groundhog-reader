package com.almarsoft.GroundhogReader;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class OptionsActivity extends PreferenceActivity {
	
	private SharedPreferences mPrefs; 
	
	private String oldHost;
	private String newHost;
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);			

		mPrefs = PreferenceManager.getDefaultSharedPreferences(OptionsActivity.this);
		
		addPreferencesFromResource(R.layout.options);
		
		CheckBoxPreference needsAuth = (CheckBoxPreference) findPreference("needsAuth");
		CheckBoxPreference useQuoter = (CheckBoxPreference) findPreference("useQuotingView");
		CheckBoxPreference cursorPosStart = (CheckBoxPreference) findPreference("replyCursorPositionStart");
		CheckBoxPreference postDirectly = (CheckBoxPreference) findPreference("postDirectlyInOfflineMode");
		CheckBoxPreference markReplies = (CheckBoxPreference) findPreference("markReplies");
		
		if (mPrefs.getBoolean("needsAuth", false) == false) {
			needsAuth.setChecked(false);
		} else 
			needsAuth.setChecked(true);
		
		if (mPrefs.getBoolean("useQuotingView", true) == true) {
			useQuoter.setChecked(true);
		} else
			useQuoter.setChecked(false);
		
		if (mPrefs.getBoolean("replyCursorPositionStart", false) == false) {
			cursorPosStart.setChecked(false);
		} else
			cursorPosStart.setChecked(true);
		
		if (mPrefs.getBoolean("postDirectlyInOfflineMode", true) == true) {
			postDirectly.setChecked(true);
		} else
			postDirectly.setChecked(false);
		
		if (mPrefs.getBoolean("markReplies", true) == true) {
			markReplies.setChecked(true);
		} else
			markReplies.setChecked(false);

	}
	
	// ============================================================================================
	// Save the value of host; we'll check it again onPause to see if the server changed to delete
	// the group messages and restore the article pointer to -1
	// ============================================================================================
	
	@Override
	protected void onResume() {
		oldHost = mPrefs.getString("host", null);
		super.onResume();
	}
	

	@Override
	protected void onPause() {
		newHost = mPrefs.getString("host", null);
		
		if (oldHost != null && newHost != null) {
			if (!oldHost.equalsIgnoreCase(newHost)) {
				// Host changed, store it in hostchanged so other activities can detect it
				Editor editor = mPrefs.edit();
				editor.putBoolean("hostChanged", true);
				editor.commit();
			} 
			
		}
		super.onPause();
	}
}
