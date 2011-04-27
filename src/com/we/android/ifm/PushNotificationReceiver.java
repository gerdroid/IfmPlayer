package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.util.Log;

public class PushNotificationReceiver {

	private IfmService mService;
	private NotificationWorker mWorker;

	private class NotificationWorker extends Thread {
		private static final int SOCKET_TIMEOUT = 10 * 60 * 1000;
		private IfmService mService;
		private Socket mSocket;

		public NotificationWorker(IfmService service) {
			mService = service;
		}

		public void run() {
			listen();
		}

		public void stopListening() {
			try {
				interrupt();
				if (mSocket != null) {
					mSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void listen() {
			try {
				mSocket = new Socket(Constants.IFM_NODE_URL, Constants.IFM_NODE_PORT);
				mSocket.setSoTimeout(SOCKET_TIMEOUT);

				Log.w("IFM", "Connected...");

				BufferedReader input = new BufferedReader(new InputStreamReader(mSocket.getInputStream()), 1024);

				String line = null;
				while (((line = input.readLine()) != null) && (!isInterrupted())) {
					parseJson(line);
				}
			} catch (SocketTimeoutException e) {
				if (!isInterrupted()) {
					mWorker = new NotificationWorker(mService);
					mWorker.start();
				}
			} catch (Exception e) {
				if (!isInterrupted()) {
					e.printStackTrace();
					Log.w("IFM", "Notification error occurred");
					mService.pushNotificationErrorOccurred();
				}
			} finally {
				Log.w("IFM", "Finally!");
				if (mSocket != null) {
					try {
						mSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public PushNotificationReceiver(IfmService service) {
		mService = service;
	}

	public void start() {
		if ((mWorker == null) || mWorker.isInterrupted()) {
			mWorker = new NotificationWorker(mService);
			mWorker.start();
		}
	}

	public void stop() {
		if (mWorker != null) {
			mWorker.stopListening();
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

			ChannelInfo infoObject = new ChannelInfo(track, label, Uri.parse(Constants.IFM_URL + path));
			mService.updateChannelInfo(channelIndex, infoObject);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return result;
	}

}
