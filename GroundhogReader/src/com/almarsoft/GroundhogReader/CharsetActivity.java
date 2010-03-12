package com.almarsoft.GroundhogReader;


import android.os.Bundle;
import android.preference.PreferenceActivity;

public class CharsetActivity extends PreferenceActivity {

	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.layout.optionscharset);

	}

}
