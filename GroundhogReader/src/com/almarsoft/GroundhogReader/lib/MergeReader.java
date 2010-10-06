package com.almarsoft.GroundhogReader.lib;

import java.io.IOException;
import java.io.Reader;
import java.util.Vector;

public class MergeReader extends Reader{

	private Reader currentReader = null;
	private Vector<Reader> readers = null;
	private int currentPos = 0;
	
	public MergeReader(Vector<Reader> readers) {
		this.readers = readers;
		reset();
	}
	
	@Override
	public void close() throws IOException {
		for (Reader r : readers) {
			r.close();
		}
	}

	@Override
	public int read(char[] buf, int offset, int count) throws IOException {
		int charsread = 0;
		
		charsread = currentReader.read(buf, offset, count);
		
		// End of this reader?
		if (charsread == -1) {
			// Are there more readers?
			if (readers.size() > currentPos + 1) {
				currentPos++;
				currentReader = readers.elementAt(currentPos);
				charsread = currentReader.read(buf, offset, count);
			}
		}
		
		return charsread;
	}
	
	@Override
	public void reset() {
		currentReader = readers.firstElement();
		currentPos = 0;
	}
}
