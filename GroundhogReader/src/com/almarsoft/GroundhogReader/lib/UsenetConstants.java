package com.almarsoft.GroundhogReader.lib;

import android.os.Environment;

public final class UsenetConstants {
	
	public final static int BANNEDTHREADS = 0;
	public final static int BANNEDTROLLS  = 1;
	
	public final static int COMPOSEMESSAGEINTENT = 2;
	public final static int BANNEDACTIVITYINTENT = 3;
	public static final int QUOTINGINTENT = 4;
	
	public final static int TEXTSIZE_SMALLEST = 0;
	public final static int TEXTSIZE_SMALLER = 1;
	public final static int TEXTSIZE_NORMAL = 2;
	public final static int TEXTSIZE_LARGER = 3;
	public final static int TEXTSIZE_LARGEST = 4;
	
	public final static String APPNAME = "Groundhog";
	public final static String ATTACHMENTSDIR = "attachments";
	public static final int SENT_POSTS_LOG_LIMIT_PER_GROUP = 100;
	public static final int SENT_POST_KILL_ADITIONAL = 10;
	public static final String EXTERNALSTORAGE = Environment.getExternalStorageDirectory().getAbsolutePath();
	public static final String QUICKHELPURL = "http://almarsoft.com/groundhog_quick.htm";

}
