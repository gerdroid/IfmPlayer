package com.we.android.ifm;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

public class AsyncCoverLoader {
	static class UpdateCoverImage {
		int mChannel;
		Uri mCoverUri;

		public UpdateCoverImage(int channel, Uri coverUri) {
			mChannel = channel;
			mCoverUri = coverUri;
		}
	}

	class CoverImageLoader extends AsyncTask<UpdateCoverImage, Void, Bitmap> {
		private int mChannel;
		private Uri mUri;
		
		@Override
		protected Bitmap doInBackground(UpdateCoverImage... updates) {
			mUri = updates[0].mCoverUri;
			mChannel = updates[0].mChannel;
			return getBitmap(updates[0].mCoverUri);
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mCoverCache[mChannel] = mUri;
			mChannelViewAdapter.updateBitmap(mChannel, result);
			mUpdated[mChannel] = true;
			checkProgress();
			super.onPostExecute(result);
		}

		private Bitmap getBitmap(Uri coverUri) {
			Bitmap bitmap = null;
			try {
				HttpGet get = new HttpGet(coverUri.toString());
				HttpResponse response = mHttpClient.execute(get);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						InputStream is = entity.getContent();
						try {
							try {
								bitmap = BitmapFactory.decodeStream(is);
							} finally {
								is.close();
							}
						} catch (IOException e) {
							Log.w("IFM", "problems decoding Coverart: " + e.toString());
						}
					}
				} else {
					Log.w("IFM", "ServerResponse: " + response.getStatusLine());
				}
			} catch (Exception e) {
				Log.w("IFM", e.toString());
			}
			return bitmap;
		}
	}
	
	private HttpClient mHttpClient;
	private ChannelViewAdapter mChannelViewAdapter;
	private Uri[] mCoverCache = new Uri[Constants.NUMBER_OF_CHANNELS];
	private View mProgress;
	private boolean[] mUpdated = new boolean[Constants.NUMBER_OF_CHANNELS];
	
	public AsyncCoverLoader(HttpClient httpClient, ChannelViewAdapter channelViewAdapter, View progress) {
		mHttpClient = httpClient;
		mProgress = progress;
		mChannelViewAdapter = channelViewAdapter;
	}
	
	public void loadCover(UpdateCoverImage update) {
		if ((mCoverCache[update.mChannel] == null) || !mCoverCache[update.mChannel].equals(update.mCoverUri)) {
			new CoverImageLoader().execute(update);
		} else {
			mUpdated[update.mChannel] = true;
		}
		checkProgress();
	}
	
	public void loadCovers(List<UpdateCoverImage> updates) {
		for (UpdateCoverImage update : updates) {
			loadCover(update);
		}
	}
	
	private boolean isProgressShowing() {
		return mProgress.getVisibility() == View.VISIBLE;
	}
	
	public void showProgress() {
		for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
			mUpdated[i] = false;
		}
		mProgress.setVisibility(View.VISIBLE);
	}
	
	private void hideProgress() {
		mProgress.setVisibility(View.INVISIBLE);
	}
	
	private void checkProgress() {
		if (!isProgressShowing()) return;
		
		boolean b = true;
		for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
			b = b & mUpdated[i];
		}
		
		if (b) hideProgress();
	}
}
