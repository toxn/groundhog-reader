package com.almarsoft.GroundhogReader.lib;

import java.lang.reflect.Method;
import android.content.Context;

public class AsyncTaskProxy {

	protected Method mPreCallback          = null;
	protected Method mProgressCallback = null;
	protected Method mPostCallback        = null;
	protected Object  mCallerInstance       = null;
	protected Context mContext                = null;

	public AsyncTaskProxy(Object callerInstance, Method preCallback, Method progressCallback, Method postCallback, Context context) {
		
		mCallerInstance        = callerInstance;
		mPreCallback     	     = preCallback;
		mProgressCallback   = progressCallback;
		mPostCallback          = postCallback;
		mContext                  = context;
	}
	
}
