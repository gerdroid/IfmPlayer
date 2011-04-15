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

public class PushNotificationReceiver extends Thread {

	private IfmService mService;
	private Socket mSocket;

	public PushNotificationReceiver(IfmService service) {
		mService = service;
	}
	
	public void run()
	{
		listen();
	}
	
	public void stopListening() {
		Log.w("IFM", "Stopping...");
		try {
			interrupt();
			if (mSocket != null) {
				mSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.w("IFM", "Stopped.");
	}
		
	private void listen() {
		try {
			Log.w("IFM", "Trying to connect...");
			mSocket = new Socket("ec2-79-125-57-87.eu-west-1.compute.amazonaws.com", 8142);

			BufferedReader input = new BufferedReader(new InputStreamReader(
					mSocket.getInputStream()), 1024);

			String line = null;
			while (((line = input.readLine()) != null) && (!isInterrupted())) {
				parseJson(line);				
			}
			mSocket.close();
		} catch (Exception e) {
			if (isInterrupted()) {
				Log.w("IFM", "Interrupted - Socket closed");
			} else {
				e.printStackTrace();
				Log.w("IFM", "Notification error occurred");
				mService.pushNotificationErrorOccurred();
			}
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

			ChannelInfo infoObject = new ChannelInfo(track, label, Uri.parse(IfmService.COVERART_URL + path));
			mService.updateChannelInfo(channelIndex, infoObject);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return result;
	}
}
