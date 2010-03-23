package com.almarsoft.GroundhogReader.lib;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
	// XXX YYY ZZZ: Cambiar nombre BBDD
	private static final String DATABASE_NAME = "com.juanjux.usenetreader";
	private static final int DATABASE_VERSION = 4;
	
	public DBHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		
		// Groups we've subscribed to
		db.execSQL("CREATE TABLE subscribed_groups (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				"                                   profile_id INTEGER, " +
				"                                   name TEXT, " +
				"                                   lastFetched INTEGER," +
				"                                   unread_count INTEGER);");
		
		// Downloaded message headers
		// XXX ZZZ Hay que añadir una columna INTEGER para guardar la fecha y poder hacer comparaciones rápidas
		db.execSQL("CREATE TABLE headers (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
											 "subscribed_group_id INTEGER, " +
				                             "reference_list TEXT, " +
				                             "server_article_id TEXT, " +
				                             "date TEXT, " +
				                             "server_article_number INTEGER, " +
				                             "from_header TEXT, " +
				                             "subject_header TEXT, " +
				                             "clean_subject TEXT, " +
				                             "thread_id TEXT, " +
				                             "is_dummy INTEGER, " +
				                             "full_header TEXT, " +
				                             "starred INTEGER, " +
				                             "catched INTEGER, " + 
				                             "read INTEGER);");
		
		// Downloaded message bodies
		db.execSQL("CREATE TABLE bodies (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				                        "header_id INTEGER," +
				                        "bodytext TEXT);");
		
		// User profile, usually asociated with a server and identity
		// FIXME: Add profile support to the Preferences object
		db.execSQL("CREATE TABLE profiles (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				                          "name TEXT);");
		
		// Threads filtered or starred
		db.execSQL("CREATE TABLE starred_threads (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
					                             "subscribed_group_id INTEGER," +
				                                 "clean_subject TEXT);");
		
		// Threads filtered or starred
		db.execSQL("CREATE TABLE banned_threads (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				                                 "subscribed_group_id INTEGER," +
				                                 "bandisabled INTEGER," +
				                                 "clean_subject TEXT);");
		
		// Users filtered or starred, by name
		db.execSQL("CREATE TABLE banned_users (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				                               "name TEXT," +
				                               "bandisabled INTEGER);");
		
		db.execSQL("CREATE TABLE favorite_users (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				    " name TEXT);");
		
		db.execSQL("CREATE TABLE offline_sent_posts (_id INTEGER PRIMARY KEY AUTOINCREMENT, foo INTEGER);");
		
		// This is used to save the msgid of the messages we send so we can mark the replies easily
		// on the MessageList
		db.execSQL("CREATE TABLE sent_posts_log (_id INTEGER PRIMARY KEY AUTOINCREMENT, " + 
				   "server_article_id TEXT, " +
				   "subscribed_group_id INTEGER);");
	}
	
	
	// XXX ZZZ: Aqui hay que añadir un "if oldVersion <= 4": ADD COLUMN (la fecha como INTEGER en headers)
	// Además, habrá que hacer un proceso en ese caso para actualizar la BBDD metiendo al fecha a los mensajes
	// existentes. HAY QUE PROBAR MUY BIEN MANTENIENDO LA VERSION VIEJA!
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
		
		db.execSQL("DROP TABLE IF EXISTS subscribed_groups");
		db.execSQL("DROP TABLE IF EXISTS headers");
		db.execSQL("DROP TABLE IF EXISTS bodies");
		db.execSQL("DROP TABLE IF EXISTS profiles");
		db.execSQL("DROP TABLE IF EXISTS starred_threads");
		db.execSQL("DROP TABLE IF EXISTS banned_threads");
		db.execSQL("DROP TABLE IF EXISTS banned_users");
		db.execSQL("DROP TABLE IF EXISTS sent_posts");
		db.execSQL("DROP TABLE IF EXISTS favorite_users");
		db.execSQL("DROP TABLE IF EXISTS tmp_read_unread");
		db.execSQL("DROP TABLE IF EXISTS offline_sent_posts");
		db.execSQL("DROP TABLE IF EXISTS sent_posts_log");
		
		onCreate(db);
	}


}
