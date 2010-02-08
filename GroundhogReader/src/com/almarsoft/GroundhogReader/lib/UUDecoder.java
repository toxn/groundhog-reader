package com.almarsoft.GroundhogReader.lib;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;


public class UUDecoder {

	// To get a string without the first n words in string str.
	public static String skipWords(String str, int n) {

		int i = 0;

		while (i < str.length() && Character.isSpaceChar(str.charAt(i))) {
			i++;
		}

		while (n > 0) {
			while (i < str.length() && !Character.isSpaceChar(str.charAt(i))) {
				i++;
			}
			while (i < str.length() && Character.isSpaceChar(str.charAt(i))) {
				i++;
			}
			n--;
		}

		return (str.substring(i));
	}

	
	// To get the first word in a string. Returns a string with all characters
	// found before the first space character.
	public static String getFirstWord(String str) {
		int i = 0;
		while (i < str.length() && !Character.isSpaceChar(str.charAt(i))) {
			i++;
		}
		return (str.substring(0, i));
	}

	
	public static String getWord(String str, int n) {
		return (getFirstWord(skipWords(str, n)));
	}

	
	static void printBin8(int d, ByteArrayOutputStream output) throws IOException {
		for (int i = 0; i < 8; i++) {
			output.write((((d << i) & 0x80) == 0) ? '0' : '1');
		}
		output.write(' ');
	}

	
	static void decodeString3(String str, ByteArrayOutputStream output) {
		int c0 = str.charAt(0) ^ 0x20;
		int c1 = str.charAt(1) ^ 0x20;
		int c2 = str.charAt(2) ^ 0x20;
		int c3 = str.charAt(3) ^ 0x20;

		output.write(((c0 << 2) & 0xfc) | ((c1 >> 4) & 0x3));
		output.write(((c1 << 4) & 0xf0) | ((c2 >> 2) & 0xf));
		output.write(((c2 << 6) & 0xc0) | ((c3) & 0x3f));
	}

	
	static void decodeString2(String str, ByteArrayOutputStream output) {
		int c0 = str.charAt(0) ^ 0x20;
		int c1 = str.charAt(1) ^ 0x20;
		int c2 = str.charAt(2) ^ 0x20;

		output.write(((c0 << 2) & 0xfc) | ((c1 >> 4) & 0x3));
		output.write(((c1 << 4) & 0xf0) | ((c2 >> 2) & 0xf));
	}

	
	static void decodeString1(String str, ByteArrayOutputStream output) {
		int c0 = str.charAt(0) ^ 0x20;
		int c1 = str.charAt(1) ^ 0x20;

		output.write(((c0 << 2) & 0xfc) | ((c1 >> 4) & 0x3));
	}

	
	public byte[] decode(String inputfile) throws IOException, UsenetReaderException {

		BufferedReader in = new BufferedReader(new FileReader(inputfile));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		String str;

		boolean more = true;
		int n = 0;

		try {

			while (more) {
				// read in a line
				str = in.readLine();
				if (str == null) {
					more = false;
					break;
				}

				if (str.startsWith("begin ")) {
					String fileName = getWord(str, 2);
					// debug(fileName);
					if (fileName.length() == 0)
						break;

					for (;;) {
						str = in.readLine();
						if (str == null) {
							more = false;
							break;
						}
						if (str.equals("end"))
							break;

						int pos = 1;
						int d = 0;

						int len = ((str.charAt(0) & 0x3f) ^ 0x20);

						while ((d + 3 <= len) && (pos + 4 <= str.length())) {
							decodeString3(str.substring(pos, pos + 4), out);
							pos += 4;
							d += 3;
						}

						if ((d + 2 <= len) && (pos + 3 <= str.length())) {
							decodeString2(str.substring(pos, pos + 3), out);
							pos += 3;
							d += 2;
						}

						if ((d + 1 <= len) && (pos + 2 <= str.length())) {
							decodeString1(str.substring(pos, pos + 2), out);
							pos += 2;
							d += 1;
						}

						if (d != len) {
							throw new UsenetReaderException("Short file");
						}
					}

					out.close();

					n++;
				}
			}

		} finally {
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
		}

		return out.toByteArray();
	}
}
