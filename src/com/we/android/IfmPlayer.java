package com.we.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class IfmPlayer extends ListActivity {

	private static final int SECOND_IN_MICROSECONDS = 1000;
	private static final String IFM_URL = "http://intergalacticfm.com";
	private static final int CHANNEL_UPDATE_FREQUENCY = 20 * SECOND_IN_MICROSECONDS;
	private static final int IFM_NOTIFICATION = 0;
	private static int NUMBER_OF_CHANNELS = 4;

	final static MediaPlayer mMediaPlayer = new MediaPlayer();
	private ProgressDialog mProgress;

	private static final int NONE = Integer.MAX_VALUE;
	private static final int CONNECTION_TIMEOUT = 20 * 	SECOND_IN_MICROSECONDS;
	private int mChannelPlaying;
	private boolean mIsPreparing;
	private int mSelectedChannel;
	private Timer mTimer;
	private ChannelInfo[] mChannelInfos;
	private Uri[] mChannelUris = new Uri[NUMBER_OF_CHANNELS];
	private boolean mChannelUrisResolved = false;

	Handler mChannelUpdateHandler = new Handler();
	private Vibrator mVibratorService;
	private NotificationManager mNotificationManager;
	private Bitmap mBlanco;
	private SharedPreferences mPreferences;
	private ChannelViewAdapter mChannelViewAdapter;

	class ChannelInfo {
		private String mArtist;
		private String mLabel;
		private Bitmap mBitmap;

		public ChannelInfo(String artist, String label, Bitmap bitmap) {
			mArtist = artist;
			mLabel = label;
			mBitmap = bitmap;
		}

		public String getArtist() {
			return mArtist;
		}

		public String getLabel() {
			return mLabel;
		}

		public Bitmap getBitmap() {
			return mBitmap;
		}
		
		@Override
		public String toString() {
			return "artist: " + mArtist + " label: " + mLabel;
		}
	}

	class UpdateView implements Runnable {

		private ChannelInfo mChannelInfo;
		private int mChannel;

		public UpdateView(ChannelInfo info, int channel) {
			mChannelInfo = info;
			mChannel = channel;
		}

		@Override
		public void run() {
			mChannelInfos[mChannel] = mChannelInfo;
			mChannelViewAdapter.notifyDataSetChanged();
		}
	}

	class ChannelUpdater extends TimerTask {
		@Override
		public void run() {
			for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
				String channelInfo = queryBlackHole(i);
				String artist = getArtist(channelInfo);
				if (!mChannelInfos[i].getArtist().equals(artist)) {
					Bitmap bitmap = null;
					URL coverUrl = getCoverArt(channelInfo);
					if (coverUrl != null) {
						try {
							bitmap = BitmapFactory.decodeStream(coverUrl.openStream());
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						Log.e("IFM", "NPE: coverart");
					}

					ChannelInfo info = new ChannelInfo(artist, getLabel(channelInfo), bitmap);
					mChannelUpdateHandler.post(new UpdateView(info, i));
				}
			}
		}
		
		private String queryBlackHole(int channel) {
			try {
				URL url = new URL(IFM_URL + "/blackhole/homepage.php?channel=" + (channel+1));
				InputStream is = url.openStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				reader.readLine(); // skip one line
				String line = reader.readLine();
				if (line == null) {
					line = "";
				}
				return line;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return "";
		}	
		
		private String getArtist(String channelInfo) {
			String tag = "<div id=\"track-info-trackname\">";
			int from = channelInfo.indexOf(tag);
			int p = channelInfo.indexOf(">", from + tag.length());
			return channelInfo.substring(p+2, channelInfo.indexOf("</a>", from));
		}

		private String getLabel(String channelInfo) {
			String tag = "<div id=\"track-info-label\">";
			int from = channelInfo.indexOf(tag);
			return channelInfo.substring(from + tag.length(), channelInfo.indexOf("</div>", from));
		}
		
		private URL getCoverArt(String channelInfo) {
			URL url = null;
			String searchterm = "img src=";
			int indexOf = channelInfo.indexOf(searchterm);
			if (indexOf != -1) {
				int from = indexOf + searchterm.length() + 1;
				String pathToImage = channelInfo.substring(from, channelInfo.indexOf("\"", from));
				try {
					url = new URL(IFM_URL + pathToImage);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			return url;
		}
	}

	class ChannelViewAdapter extends BaseAdapter {
		private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
		private final String mChannelName[] = new String[]{"Westcoast Sound of Holland", "Intergalactic Classix", "The Dream Machine", "Cybernetic Broadcasting"};
		
		@Override
		public int getCount() {
			return NUMBER_OF_CHANNELS;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View channelView;
			if (convertView == null) {
				channelView = getLayoutInflater().inflate(R.layout.channel, parent, false);
			} else {
				channelView = convertView;
			}
			updateChannelView(channelView, position, mChannelInfos[position]);
			return channelView;
		}
		
		private void updateChannelView(View channelView, int channel, ChannelInfo channelInfo) {
			channelView.setBackgroundResource(mChannelColor[channel]);
			
			((TextView) channelView.findViewById(R.id.channel_name)).setText(mChannelName[channel]);
			setChannelInfo(channelView, channelInfo);
			PlayButton button = (PlayButton) channelView.findViewById(R.id.play);
			if (mChannelPlaying == channel) {
				button.play();
			} else {
				button.stop();
			}
			button.setBackgroundResource(mChannelColor[channel]);
			button.setTag(new Integer(channel));
			button.setHapticFeedbackEnabled(true);
		}
		
		private void setChannelInfo(View channel, ChannelInfo info) {
			try {
				if (info.getBitmap() != null) {
					((ImageView) channel.findViewById(R.id.cover)).setImageBitmap(info.getBitmap());
				} else {
					((ImageView) channel.findViewById(R.id.cover)).setImageBitmap(mBlanco);
				}
				((TextView) channel.findViewById(R.id.artist)).setText(info.getArtist());
				((TextView) channel.findViewById(R.id.label)).setText(info.getLabel());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.main);
		restoreState(savedInstanceState);
		mChannelViewAdapter = new ChannelViewAdapter();
		setListAdapter(mChannelViewAdapter);
		mProgress = new ProgressDialog(this);
		requestChannelUris();

		getListView().setDivider(null);

		setupMediaPlayer();
		
		mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			Log.d("IFM", "backkkkkk");
			if (mIsPreparing) {
				mMediaPlayer.reset();
				mChannelPlaying = NONE;
				mIsPreparing = false;
				mChannelViewAdapter.notifyDataSetChanged();
			}
	    }
	    return super.onKeyDown(keyCode, event);
	}

	private void setupMediaPlayer() {
		mMediaPlayer.setOnPreparedListener(new OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				Log.d("IFM", "onPrepared" + Thread.currentThread());
				mProgress.cancel();
				mMediaPlayer.start();
				mIsPreparing = false;
				doNotification();
			}
		});

		mMediaPlayer.setOnErrorListener(new OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				Log.e("IFM", "MediaPlayer Error: " + what);
				if (mp != null) {
					mp.stop();
					mp.release();
				}
				return false;
			}
		});
	}

	private void restoreState(Bundle savedInstanceState) {
		mPreferences = getPreferences(MODE_PRIVATE);
		if (savedInstanceState != null) {
			mChannelPlaying = savedInstanceState.getInt("channelPlaying", NONE);
			mSelectedChannel = savedInstanceState.getInt("channelSelected", NONE);
			Parcelable[] parcelableArray = savedInstanceState.getParcelableArray("channelUris");
			if (parcelableArray != null) {
				Log.d("IFM", "" + parcelableArray.getClass());
				mChannelUris = (Uri[]) parcelableArray;
			}
		} else {
			mChannelPlaying = mPreferences.getInt("channelPlaying", NONE);
			mSelectedChannel = mPreferences.getInt("channelSelected", NONE);
		}
		mBlanco = BitmapFactory.decodeResource(getResources(), R.drawable.blanco);
		mChannelInfos = (ChannelInfo[]) getLastNonConfigurationInstance();
		if (mChannelInfos == null) {
			mChannelInfos = new ChannelInfo[NUMBER_OF_CHANNELS];
			ChannelInfo defaultChannelInfo = new ChannelInfo("", "", mBlanco);
			for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
				mChannelInfos[i] = defaultChannelInfo;
			}
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mChannelInfos;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("channelPlaying", mChannelPlaying);
		outState.putInt("channelSelected", mSelectedChannel);
		outState.putParcelableArray("channelUris", mChannelUris);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mNotificationManager.cancel(IFM_NOTIFICATION);
		mTimer.cancel();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Editor editor = mPreferences.edit();
		editor.putInt("channelPlaying", mChannelPlaying);
		editor.putInt("channelSelected", mSelectedChannel);
		editor.commit();
		mTimer.cancel();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mTimer = new Timer();
		mTimer.schedule(new ChannelUpdater(), 0, CHANNEL_UPDATE_FREQUENCY);
	}

	private void doNotification() {
		if (mChannelPlaying != NONE) {
			Intent intent = new Intent(this, IfmPlayer.class);
			intent.setAction(Intent.ACTION_VIEW);
			Notification notification = new Notification(R.drawable.ifm, "", System.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR;
			String artist = mChannelInfos[mChannelPlaying].getArtist();
			notification.setLatestEventInfo(IfmPlayer.this, "IFM Player","playing " + artist, 
					PendingIntent.getActivity(this.getBaseContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
			mNotificationManager.notify(IFM_NOTIFICATION, notification);
		}
	}

	private void requestChannelUris() {
		mProgress.setMessage("Connecting to Blackhole...");
		mProgress.show();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (!mChannelUrisResolved) {
					mProgress.cancel();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							showConectionAlert();
						}
					});
				}
			}
		}, CONNECTION_TIMEOUT);
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
					if (mChannelUris[i] == null) {
						mChannelUris[i] = getChannelUri(i);
					}
				}
				mChannelUrisResolved = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mProgress.cancel();
					}
				});
			}
		}).start();
	}
	
	private void showConectionAlert() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Connection Problem")
		       .setCancelable(false)
		       .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   requestChannelUris();
		           }
		       })
		       .setNegativeButton("Finish", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                finish();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	private Uri getChannelUri(int channel) {
		try {
			URL url = new URL("http://radio.intergalacticfm.com/" + (channel+1) + ".m3u");
			//			URL url = new URL("http://radio.intergalacticfm.com/" + (channel+1) + "aacp.m3u");
			InputStream is = url.openStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = reader.readLine();
			return Uri.parse(line);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void myClickHandler(View button) {
		mVibratorService.vibrate(80);
		int selectedChannel = (Integer) button.getTag();
		if (mChannelPlaying == NONE) {
			playSelectedChannel(button.getContext(), selectedChannel);
		} else if (mChannelPlaying != selectedChannel) {
			stop();
			playSelectedChannel(button.getContext(), selectedChannel);
		} else {
			mChannelPlaying = NONE;
			stop();
		}
		mChannelViewAdapter.notifyDataSetChanged();
	}
	
	private void playSelectedChannel(Context context, int selectedChannel) {
		mChannelPlaying = selectedChannel;
		Uri channelUri = mChannelUris[selectedChannel];
		if (channelUri != null) {
			try {
				mProgress.setMessage("Buffering. Please wait...");
				mProgress.show();
				mMediaPlayer.setDataSource(context, channelUri);
				mIsPreparing = true;
				Log.d("IFM", "playSelectedChannel" + Thread.currentThread());
				mMediaPlayer.prepareAsync();
			} catch (Exception e) {
				showConnectionProblem(context);
				e.printStackTrace();
			}
		} else {
			showConnectionProblem(context);
		}
	}
	
	private void showConnectionProblem(Context context) {
		Toast toast = Toast.makeText(context, "Connection Problem", Toast.LENGTH_LONG);
		toast.show();
	}

	private void stop() {
		mMediaPlayer.stop();
		mMediaPlayer.reset();
		mNotificationManager.cancel(IFM_NOTIFICATION);
	}
}
