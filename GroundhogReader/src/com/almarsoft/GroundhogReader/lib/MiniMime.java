package com.almarsoft.GroundhogReader.lib;

import java.util.Vector;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.BCodec;
import org.apache.commons.codec.net.QCodec;
import org.apache.commons.codec.net.QuotedPrintableCodec;

import android.util.Log;


public class MiniMime {
	
	private static QCodec qdecoder = new QCodec();
	private static BCodec bdecoder = new BCodec();
	private static QuotedPrintableCodec qpdecoder = new QuotedPrintableCodec();	
    
    private static String ltrim(String source) {
        return source.replaceAll("^\\s+", "");
    }



	public static String decodemime(String headerfield, boolean onepass) {
		
		if (onepass) {
			
			// Special case for From headers which usually doesnt have multiple encoded blocks, just one
			int idxStart = headerfield.indexOf("=?");
			int idxEnd   = headerfield.lastIndexOf("?=");
			
			if (idxStart != -1) {

				 headerfield = headerfield.substring(0, idxStart) + // First non encoded part, before the =?
	                      decodeSubString(headerfield.substring(idxStart, idxEnd+2)) +  
	                      headerfield.substring(idxEnd+2);
			}
			
		} else {
			// Shortcut in case there is nothing to decode
			if (headerfield.indexOf("=?") == -1)
				return headerfield;
			
			Vector<String> lines = splitEncodedChunks(headerfield);
			String line;
			StringBuilder result = new StringBuilder(headerfield.length());
			int linesLen = lines.size();
			
			for (int i=0; i<linesLen; i++) {
				
				line = ltrim(lines.get(i));
				
				if (line.indexOf("=?") != -1 && line.indexOf("?=") != -1)
					line = decodeSubString(line);
				result.append(line);
			}
			
			headerfield = result.toString();
		}
		
		return headerfield;
	}
	
	
	/*
	 * Take a encoded string and return an array with one chunk per item
	 */
	private static Vector<String> splitEncodedChunks(String input) {
		
		Vector<String> chunks = new Vector<String>();
		
		int startidx, endidx;
		char nextEnd;
		
		startidx = input.indexOf("=?");
		endidx = input.indexOf("?=");
		
		// Nothing to encode
		if (startidx == -1 || endidx == -1) {
			chunks.add(input);
			return chunks;
		}
		
		endidx += 2;
		
		// Add the non encoded part, if it exists
		if (startidx != 0) 
			chunks.add(input.substring(0, startidx));
		
		boolean finished = false;
		
		while (!finished) {
			
			// End of the string, add the last chunk and return 
			if (input.length() <= endidx) {
				chunks.add(input.substring(startidx, endidx));
				return chunks;
			}
			
			nextEnd = input.charAt(endidx);
			// Check for ?= that doesn't really mark the end of the chunk, like in: =?iso-8859-1?Q?=5BOT-Venta?=
			if (!Character.isWhitespace(nextEnd)) {
				endidx = endidx + input.substring(endidx).indexOf("?=") + 2;
			}
			
			chunks.add(input.substring(startidx, endidx));
			
			input = input.substring(endidx);
			
			if (input.length() == 0)
				finished = true;
			
			else {
				startidx = input.indexOf("=?");
				endidx = input.indexOf("?=") + 2;
				
				if (startidx == -1) {
					chunks.add(input);
					finished = true;
				}
			}
		}
		
		return chunks;
	}
	
	private static String decodeSubString(String substr) {
		
		if (substr == null)
			return "";
		
		try 
		{
			return qdecoder.decode(substr);
		} 
		catch (DecoderException d) 
		{
			d.printStackTrace();
			Log.d("Groundhog", "Decoder exception with qdecoder, trying with bdecoder");
				try 
				{
					return bdecoder.decode(substr);
				} catch (DecoderException e) 
				{
					e.printStackTrace();
					Log.d("Groundhog", "Decoder exception with bdecoder, trying with quoted printable");
					try 
					{
						return qpdecoder.decode(substr);
					} catch (DecoderException f) {
						f.printStackTrace();
						Log.d("Grouphog", "Decoder exception with quotedprintable, bailing out for |" + substr + "|");
					}
					
				}
		}
		return substr;
	}
}