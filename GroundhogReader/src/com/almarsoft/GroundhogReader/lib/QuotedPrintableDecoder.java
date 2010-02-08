package com.almarsoft.GroundhogReader.lib;

import java.io.UnsupportedEncodingException;

public class QuotedPrintableDecoder {
	
	// ==================================================================================
	// Decode a quoted-printable text. See: http://en.wikipedia.org/wiki/Quoted-printable
	// ==================================================================================
	
	public static final String decode(String input) throws UnsupportedEncodingException {
		
		StringBuffer buf = new StringBuffer(input.length());
		char c;
		int inputLen = input.length();
		
		for (int i=0; i<inputLen; i++) {
			c = input.charAt(i);
			
			if ((c == '=') && (inputLen > i+1)) { // Take the following two 
				if (input.charAt(i+1) == '\n') {
					// quoted-printable soft line break: =\n; just skip it 
					i+=1; 
					continue;
				}
				if (inputLen > i + 2) { 
					// Normal quoted-printable non-ascii char
					c = (char)Integer.parseInt( input.substring(i+1, i+3), 16 );
					i+=2;
				}
			} 
			buf.append(c);
		}
		return buf.toString();
	}
}
