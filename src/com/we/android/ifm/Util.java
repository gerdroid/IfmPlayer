package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.util.Log;

public class Util {
    public static String readString(InputStream input) {
	BufferedReader reader = new BufferedReader(new InputStreamReader(input));
	StringBuilder builder = new StringBuilder();
	try {
	    try {
		String str = null;
		while ((str = reader.readLine()) != null) {
		    builder.append(str);
		}
	    } finally {
		reader.close();
	    }
	} catch (IOException e) {
	    Log.w("IFM", "Evil things happen here: " + e.toString());
	}
	return builder.toString();
    }
}
