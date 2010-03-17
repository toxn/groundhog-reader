package com.almarsoft.GroundhogReader;


import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class ReadCharsetActivity extends PreferenceActivity {

	private SharedPreferences mPrefs; 
	
	// Used to detect changes
	private String oldReadCharset;
	private String newReadCharset;	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(ReadCharsetActivity.this);
		addPreferencesFromResource(R.layout.optionsreadcharset);

	}
	
	@Override
	protected void onResume() {		
		oldReadCharset = mPrefs.getString("readDefaultCharset", null);
		super.onResume();
	}	
	
	@Override
	protected void onPause() {
		
		newReadCharset = mPrefs.getString("readDefaultCharset", null);
		if (oldReadCharset != null && newReadCharset != null) {
			if (!oldReadCharset.equalsIgnoreCase(newReadCharset)) {
				// Charset changed, store it in charsetChanged so other activities can detect it
				Editor editor = mPrefs.edit();
				editor.putBoolean("readCharsetChanged", true);
				editor.commit();
			}
		}
		super.onPause();
	}

}
