package com.almarsoft.GroundhogReader.lib;

public class UsenetReaderException extends Exception {
	
	private static final long serialVersionUID = 3840358264033319232L;
	String mMessage;
	
	public UsenetReaderException(String message) {
		super();
		mMessage = message;
		
	}
	
	public String getUsenetMessage() {
		return mMessage;
	}
	
	@Override
	public String toString() {
		return mMessage;
	}
	
	@Override
	public String getMessage() {
		return mMessage;
	}

}
