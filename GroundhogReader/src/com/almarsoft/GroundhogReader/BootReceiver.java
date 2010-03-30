package com.almarsoft.GroundhogReader;

import com.almarsoft.GroundhogReader.lib.UsenetConstants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		boolean alarm = prefs.getBoolean("enableNotifications", false);
		
		if (!alarm)
			return;
		
		Log.i(UsenetConstants.APPNAME, "Reinstalling background check alarms");
		long        alarmPeriod = new Long(prefs.getString("notifPeriod", "3600000")).longValue();
		
		Intent alarmIntent = new Intent(context, GroupsCheckAlarmReceiver.class);
		PendingIntent sender = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()+alarmPeriod, alarmPeriod, sender);
	}
}
