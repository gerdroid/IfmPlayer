package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.util.Log;

public class PushNotificationReceiver implements Runnable {

	private boolean mIsRunning;
	private IfmService mService;

	public PushNotificationReceiver(IfmService service) {
		mService = service;
	}
	
	public void start()
	{
		new Thread(this).start();
	}
	
	@Override
	public void run() {
		mIsRunning = true;
		listen();
	}

	public void stop() {
		mIsRunning = false;
	}
		
	private void listen() {
		try {
			Log.w("IFM", "Trying to connect...");
			Socket socket = new Socket("ec2-79-125-57-87.eu-west-1.compute.amazonaws.com", 8142);

			BufferedReader input = new BufferedReader(new InputStreamReader(
					socket.getInputStream()), 1024);

			String line = null;
			while (((line = input.readLine()) != null) && (mIsRunning)) {
				parseJson(line);				
			}

			socket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String parseJson(String jason) {
		String result = "";
		try {
			JSONObject channelInfo = new JSONObject(jason);
			int channelIndex = channelInfo.getInt("channel");
			JSONObject channelDetails = channelInfo.getJSONObject("infos");
			String path = channelDetails.getString("path").trim();
			String track = channelDetails.getString("track").trim();
			String label = channelDetails.getString("label").trim();

			Log.w("IFM", "Channel "+channelIndex+": ");
			Log.w("IFM", "   path: "+path);
			Log.w("IFM", "   track: "+track);
			Log.w("IFM", "   label: "+label);
			
			
			ChannelInfo infoObject = new ChannelInfo(track, label, Uri.parse(IfmService.COVERART_URL + path));
			mService.updateChannelInfo(channelIndex, infoObject);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return result;
	}
}
