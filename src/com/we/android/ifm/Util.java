package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

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
    
    public static HttpClient createThreadSaveHttpClient(int timeoutInSec) {
	SchemeRegistry schemeRegistry = new SchemeRegistry();
	schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
	HttpParams params = new BasicHttpParams();
	HttpConnectionParams.setConnectionTimeout(params, timeoutInSec * 1000);
	HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
	return new DefaultHttpClient(cm, params);
    }
}
