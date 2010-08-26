package com.almarsoft.GroundhogReader.lib;

import java.lang.reflect.Method;
import android.content.Context;

public class AsyncTaskProxy {

	protected Method mPreCallback          = null;
	protected Method mProgressCallback = null;
	protected Method mPostCallback        = null;
	protected Method mCancelCallback	   = null;
	protected Object  mCallerInstance       = null;
	protected Context mContext                = null;

	public AsyncTaskProxy(Object callerInstance, Method preCallback, Method progressCallback, Method postCallback, Method cancelCallback, Context context) {
		
		mCallerInstance        = callerInstance;
		mPreCallback     	     = preCallback;
		mProgressCallback   = progressCallback;
		mPostCallback          = postCallback;
		mCancelCallback  		= cancelCallback;
		mContext                  = context;
	}
	
}
