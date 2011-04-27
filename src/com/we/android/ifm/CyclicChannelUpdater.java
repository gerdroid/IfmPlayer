package com.we.android.ifm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

public class CyclicChannelUpdater {

	private static final int SECOND_IN_MICROSECONDS = 1000;
	private static final int CHANNEL_UPDATE_FREQUENCY = 20 * SECOND_IN_MICROSECONDS;
	private static String CHANNEL_QUERY = "https://intergalactic.fm/blackhole/homepage.php?channel=";
	
	private IfmService mService;
	private HttpClient mHttpClient;
	private Handler mHandler;
	private Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			startPolling();
		}
	};
	private int mChannelFilter;

	class AsyncChannelQuery extends AsyncTask<Integer, Void, ChannelInfo> {
		private int mChannel;

		@Override
		protected ChannelInfo doInBackground(Integer... params) {
			mChannel = params[0];
			return queryBlackHole(mChannel);
		}

		@Override
		protected void onPostExecute(ChannelInfo info) {
			mService.updateChannelInfo(mChannel, info);
			super.onPostExecute(info);
		}

		private ChannelInfo queryBlackHole(int channel) {
			ChannelInfo info = ChannelInfo.NO_INFO;
			HttpGet get = new HttpGet(CHANNEL_QUERY + (channel + 1));
			try {
				HttpResponse response = mHttpClient.execute(get);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						String channelInfoString = Util.readString(entity
								.getContent());
						info = createChannelInfo(channelInfoString);
						Log.d("IFM", "ChannelInfo: " + info);
					}
				} else {
					Log.w("IFM", "http request failed: "
							+ response.getStatusLine());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return info;
		}

		private ChannelInfo createChannelInfo(String channelInfo) {
			String tag1 = "img src=";
			String tag2 = "<div id=\"track-info-trackname\">";
			String tag3 = "<div id=\"track-info-label\">";
			Pattern p = Pattern.compile(".*" + tag1 + "\"(.*?)\".*" + tag2
					+ "\\s*<.*?>(.*?)</a>.*" + tag3 + "(.*?)</div>.*");
			Matcher m = p.matcher(channelInfo);
			if (m.matches()) {
				String pathToImage = m.group(1);
				String artist = m.group(2).trim();
				String label = m.group(3).trim();
				return new ChannelInfo(artist, label, Uri.parse(Constants.IFM_URL + pathToImage));
			}
			return ChannelInfo.NO_INFO;
		}
	}
	
	public CyclicChannelUpdater(IfmService service) {
		mService = service;
		mHandler = new Handler();
	}
	
	public void start(int channelFilter) {
		mChannelFilter = channelFilter;
		mHttpClient = Util.createThreadSaveHttpClient(20);
		mHandler.post(mRunnable);
	}

	public void stop() {
		if (mHttpClient != null) {
			mHttpClient.getConnectionManager().shutdown();
		}
		mHandler.removeCallbacks(mRunnable);
	}

	public void setChannelFilter(int channelFilter) {
		mChannelFilter = channelFilter;
	}
	
	private void startPolling() {
		if (mChannelFilter == Constants.NONE) {
			for (int i = 0; i < Constants.NUMBER_OF_CHANNELS; i++) {
				new AsyncChannelQuery().execute(i);
			}
		} else {
			new AsyncChannelQuery().execute(mChannelFilter);
		}
		mHandler.removeCallbacks(mRunnable);
		mHandler.postDelayed(mRunnable, CHANNEL_UPDATE_FREQUENCY);
	}
	
}
