package com.almarsoft.GroundhogReader;

import com.almarsoft.GroundhogReader.lib.UsenetConstants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

public class OptionsActivity extends PreferenceActivity {
	
	private SharedPreferences mPrefs; 

	// Used to detect changes
	private String oldHost;
	private boolean oldAlarm;
	private long oldAlarmPeriod;
	private String oldReadCharset;
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);			

		mPrefs = PreferenceManager.getDefaultSharedPreferences(OptionsActivity.this);		
		addPreferencesFromResource(R.layout.options);

	}
	
	// ============================================================================================
	// Save the value of host; we'll check it again onPause to see if the server changed to delete
	// the group messages and restore the article pointer to -1
	// ============================================================================================
	
	@Override
	protected void onResume() {
		oldHost             = mPrefs.getString("host", null);
		oldReadCharset = mPrefs.getString("readDefaultCharset", null);		
		oldAlarm    = mPrefs.getBoolean("enableNotifications", false);
		oldAlarmPeriod = new Long(mPrefs.getString("notifPeriod", "3600000")).longValue();
		
		super.onResume();
	}
	
	/**
	 * Check if the host changed to reset al groups messages
	 */
	protected void checkHostChanged() {
		
		String newHost = mPrefs.getString("host", null);
		if (oldHost != null && newHost != null) {
			if (!oldHost.equalsIgnoreCase(newHost)) {
				// Host changed, store it in hostchanged so other activities can detect it
				Editor editor = mPrefs.edit();
				editor.putBoolean("hostChanged", true);
				editor.commit();
			} 
		}
	}
	
	protected void checkCharsetChanged() {
		
		String newReadCharset = mPrefs.getString("readDefaultCharset", null);
		if (oldReadCharset != null && newReadCharset != null) {
			if (!oldReadCharset.equalsIgnoreCase(newReadCharset)) {
				// Charset changed, store it in charsetChanged so other activities can detect it
				Editor editor = mPrefs.edit();
				editor.putBoolean("readCharsetChanged", true);
				editor.commit();
			}
		}
	}
	
	/**
	 * Check that the alarm changed to enable/reset/disable the current alarm
	 */
	// XXX YYY ZZZ: Cambiar el setRepeating por setInexactRepeating
	protected void checkAlarmChanged() {		
		boolean newAlarm = mPrefs.getBoolean("enableNotifications", false);
		long        newAlarmPeriod = new Long(mPrefs.getString("notifPeriod", "3600000")).longValue();
		
		// XXX YYY ZZZ QUTAR
		newAlarmPeriod = 5000;

		Intent alarmIntent = new Intent(getApplicationContext(), GroupsCheckAlarmReceiver.class);
		PendingIntent sender = null;
		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);		
		//new Intent(getApplicationContext(), GroupsCheckAlarmReceiver.class);
		
		Log.d("XXX", "oldAlarm |" + oldAlarm + "| newAlarm: |" + newAlarm + "|");
		
		if (oldAlarm == false && newAlarm == true) { // User enabled the alarm
			Log.d("XXX", "XXX1");
			sender =  PendingIntent.getBroadcast(getApplicationContext(), UsenetConstants.CHECK_ALARM_CODE, alarmIntent, 0);
			//am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + newAlarmPeriod, newAlarmPeriod, sender);			
			am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + newAlarmPeriod, newAlarmPeriod, sender);
		}
		else if (oldAlarm == true && newAlarm == false) { // User disabled the alarm
			Log.d("XXX", "XXX2");
			sender =  PendingIntent.getBroadcast(getApplicationContext(), UsenetConstants.CHECK_ALARM_CODE, alarmIntent, 0);
			am.cancel(sender);
		}
		else if (newAlarm == true && (oldAlarmPeriod != newAlarmPeriod)) { // User changed the interval
			Log.d("XXX", "XXX3");
			sender =  PendingIntent.getBroadcast(getApplicationContext(), UsenetConstants.CHECK_ALARM_CODE, alarmIntent, 0);
			am.cancel(sender);
			//am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + newAlarmPeriod, newAlarmPeriod, sender);
			am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + newAlarmPeriod, newAlarmPeriod, sender);
		}
	}
	
	
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            checkHostChanged();
            checkCharsetChanged();
            checkAlarmChanged();
        }
        return super.onKeyDown(keyCode, event);
    }
	
}
