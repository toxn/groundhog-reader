package com.almarsoft.GroundhogReader;

import java.lang.reflect.Method;
import java.util.Vector;

import com.almarsoft.GroundhogReader.lib.DBUtils;
import com.almarsoft.GroundhogReader.lib.ServerManager;
import com.almarsoft.GroundhogReader.lib.ServerMessageGetter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class GroupsCheckAlarmReceiver extends BroadcastReceiver{
	
	private final int CHECK_FINISHED_OK = 5;
	
	ServerMessageGetter mServerMessageGetter = null;
	Context mContext = null;
	
	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Context context, Intent intent) {
    	Log.d("XXX", "GroupsCheckAlarmReceiver.onReceive");
    	try{
    		mContext = context;
			Class postPartypes[] = new Class[2];
			postPartypes[0] = String.class;
			postPartypes[1] = Integer.class;
			Method postCallback = this.getClass().getMethod("postCheckMessagesCallBack", postPartypes);
		
			Log.d("XXX", "Iniciando ServerMessageGetter");
			ServerManager myServerManager = new ServerManager(context);
			mServerMessageGetter = new ServerMessageGetter(this, null, null, postCallback,  context, myServerManager, 100, false, true);
			
			String[] groupsarr = DBUtils.getSubscribedGroups(context);
			Vector<String> groups = new Vector<String>(groupsarr.length);
			for(String group: groupsarr) {
				groups.add(group);
			}
			mServerMessageGetter.execute(groups);
    	} catch(NoSuchMethodException e) {
			e.printStackTrace();
		} catch(SecurityException e) {
			e.printStackTrace();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	

	public void postCheckMessagesCallBack(String status, Integer resultObj) {
		Log.d("XXX", "En postCheckMessagesCallback");
		
		int result = resultObj.intValue();

		if (result == CHECK_FINISHED_OK) {		
			if (status != null) {
				Log.d("XXX", "Status:");
				Log.d("XXX", status);
			} else Log.d("XXX", "Status es null!"); 
		} else{
			Log.d("XXX", "Otro result: " + result);
			return;
		}

		mServerMessageGetter = null;
		
        NotificationManager nm = (NotificationManager)  mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notif = new Notification(R.drawable.icon, status, System.currentTimeMillis());
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (prefs.getBoolean("notif_useSound", true)) 
        	notif.defaults |= Notification.DEFAULT_SOUND;
        if (prefs.getBoolean("notif_useVibration", true))
        	notif.defaults |= Notification.DEFAULT_VIBRATE;
        if (prefs.getBoolean("notif_useLight", true))
        	notif.defaults |= Notification.DEFAULT_LIGHTS;
        
        notif.flags      |= Notification.FLAG_AUTO_CANCEL;
        
        // XXX YYY ZZZ: Parsear status para ponerlo mas bonito
        String [] groupsInfo = status.split(";");
        StringBuffer text = new StringBuffer();
        boolean hasSome = false;
        for(String groupMsgs : groupsInfo) {
        	String [] nameMsgs = groupMsgs.split(":");
        	int newmsgs = new Integer(nameMsgs[1]).intValue();
        	if (newmsgs > 0) {
        		hasSome = true;
        		text.append(newmsgs);
        		text.append(" new for " + nameMsgs[1] + "\n");
        	}
        }

        if (hasSome) {
        	Intent notifyIntent     = new Intent(mContext, GroupListActivity.class);
        	notifyIntent.putExtra("fromNotify", true);
        	PendingIntent intent = PendingIntent.getActivity(mContext, 0, notifyIntent, android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        	notif.setLatestEventInfo(mContext, "New Messages", status, intent);
        	nm.notify(12345, notif);
        }
	}	

}
