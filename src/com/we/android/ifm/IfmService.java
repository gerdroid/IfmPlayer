package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class IfmService extends Service implements IPlayer {

	private static Uri BLACKHOLE = Uri.parse("http://radio.intergalacticfm.com");
	private static final int IFM_NOTIFICATION = 0;

	private WifiLock mWifiLock;
	private WakeLock mWakeLock;

	private Uri[] mChannelUris;
	private MediaPlayer mMediaPlayer;

	private int mChannelPlaying = Constants.NONE;

	private Handler mAsyncHandler;

	private NotificationManager mNotificationManager;
	private PhoneStateReceiver mPhoneStateReceiver;
	private final IPlayerStateListener mNullPlayerStateListener = new IPlayerStateListener() {
		@Override
		public void onChannelStarted(int channel) {
		}

		@Override
		public void onChannelInfoChanged(int channel, ChannelInfo channelInfo) {
		}

		@Override
		public void onChannelError() {
		}
	};
	private IPlayerStateListener mStateListener = mNullPlayerStateListener;

	class LocalBinder extends Binder {
		public IPlayer getService() {
			return IfmService.this;
		}
	}

	private final LocalBinder mBinder = new LocalBinder();

	private enum PlayerState {
		IDLE, PREPARING, PREPARED, RUNNING
	};

	private PlayerState mState;
	private PushNotificationReceiver mPushNotificationReceiver;
	private CyclicChannelUpdater mCyclicChannelUpdater;
	private boolean mUsePushNotification;
	private ChannelInfo[] mChannelInfos;

	class AsyncStateHandler extends Handler {

		public AsyncStateHandler(Looper looper) {
			super(looper);
			mState = PlayerState.IDLE;
		}

		private void setState(PlayerState requestedState) {
			if (requestedState == PlayerState.IDLE) {
				if (mState == PlayerState.PREPARING) {
					// nothing todo
				} else if (mState == PlayerState.PREPARED) {
					mMediaPlayer.reset();
				} else if (mState == PlayerState.RUNNING) {
					mMediaPlayer.stop();
					mMediaPlayer.reset();
				} else {
					Log.d("IFM", "throw away: " + requestedState);
					return;
				}
			} else if (requestedState == PlayerState.PREPARING) {
				if (mState == PlayerState.IDLE) {
					mAsyncHandler.sendEmptyMessage(PlayerState.PREPARED.ordinal());
				} else {
					Log.d("IFM", "throw away: " + requestedState);
					return;
				}
			} else if (requestedState == PlayerState.PREPARED) {
				if (mState == PlayerState.PREPARING) {
					try {
						doPreparation();
						mMediaPlayer.prepare();
						mAsyncHandler.sendEmptyMessage(PlayerState.RUNNING.ordinal());
					} catch (Exception e) {
						releaseLocks();
						mChannelPlaying = Constants.NONE;
						stopNotification();
						mStateListener.onChannelError();
						mAsyncHandler.sendEmptyMessage(PlayerState.IDLE.ordinal());
						Log.e("IFM", "connection error: " + e.getMessage());
						;
					}
				} else {
					Log.d("IFM", "throw away: " + requestedState);
					return;
				}
			} else if (requestedState == PlayerState.RUNNING) {
				if (mState == PlayerState.PREPARED) {
					mMediaPlayer.start();
					updateNotification();
					mStateListener.onChannelStarted(mChannelPlaying);
				} else {
					Log.d("IFM", "throw away: " + requestedState);
					return;
				}
			}
			Log.d("IFM", "from: " + mState + " to: " + requestedState);
			mState = requestedState;
		}

		@Override
		public void handleMessage(Message msg) {
			try {
				if (msg.what == PlayerState.IDLE.ordinal()) {
					setState(PlayerState.IDLE);
				} else if (msg.what == PlayerState.PREPARING.ordinal()) {
					setState(PlayerState.PREPARING);
				} else if (msg.what == PlayerState.PREPARED.ordinal()) {
					setState(PlayerState.PREPARED);
				} else {
					setState(PlayerState.RUNNING);
				}
			} catch (IllegalStateException exception) {
				mStateListener.onChannelError();
			}
			super.handleMessage(msg);
		}
	}

	class MyPhoneStateListener extends PhoneStateListener {
		private AudioManager mAudioManager;

		public MyPhoneStateListener(AudioManager audioManager) {
			mAudioManager = audioManager;
		}

		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
				Log.d("IFM", "IDLE");
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
			case TelephonyManager.CALL_STATE_RINGING:
				mAsyncHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mState != PlayerState.IDLE) {
							mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
						}
					}
				});
				Log.d("IFM", "RINGING");
				break;
			}
		}
	}

	public class PhoneStateReceiver extends BroadcastReceiver {
		private MyPhoneStateListener mPhoneListener;

		public PhoneStateReceiver(AudioManager audioManager) {
			mPhoneListener = new MyPhoneStateListener(audioManager);
		}

		public void onReceive(Context context, Intent intent) {
			TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			telephony.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
		}
	}

	public int getPlayingChannel() {
		return mChannelPlaying;
	}

	public boolean isPlaying() {
		return (mChannelPlaying != Constants.NONE);
	}

	public void stop() {
		releaseLocks();
		if (mChannelPlaying != Constants.NONE) {
			mChannelPlaying = Constants.NONE;
			Log.d("IFM", "stop");
			stopNotification();
			requestState(PlayerState.IDLE);
		}
	}

	public void cancel() {
		releaseLocks();
		mChannelPlaying = Constants.NONE;
		Log.d("IFM", "cancel");
		requestState(PlayerState.IDLE);
	}

	public void play(final int channel) {
		acquireLocks();
		Log.d("IFM", "play: " + channel);
		mChannelPlaying = channel;
		mCyclicChannelUpdater.setChannelFilter(mChannelPlaying);
		requestState(PlayerState.PREPARING);
	}

	private void requestState(PlayerState state) {
		mAsyncHandler.sendEmptyMessage(state.ordinal());
	}

	public boolean isPreparing() {
		return (mState == PlayerState.PREPARING) || (mState == PlayerState.PREPARED);
	}

	public void registerStateListener(IPlayerStateListener stateListener) {
		mStateListener = stateListener;
	}

	public ChannelInfo[] getChannelInfo() {
		return mChannelInfos;
	}

	public void updateChannelInfo(int channelId, ChannelInfo info) {
		if ((mChannelInfos[channelId] == ChannelInfo.NO_INFO) || (info != ChannelInfo.NO_INFO)) {
			if (!mChannelInfos[channelId].getArtist().equals(info.getArtist())
					|| !mChannelInfos[channelId].getLabel().equals(info.getLabel())) {
				mChannelInfos[channelId] = info;
				updateNotification();
				if (mStateListener != mNullPlayerStateListener) {
					mStateListener.onChannelInfoChanged(channelId, info);
				}
			}
		}
	}

	public void pushNotificationErrorOccurred() {
		mUsePushNotification = false;
		int channelFilter = Constants.NONE;
		if (mStateListener == mNullPlayerStateListener) {
			channelFilter = mChannelPlaying;
		}
		mCyclicChannelUpdater.start(channelFilter);
	}

	private void doPreparation() throws Exception {
		if (mChannelUris[mChannelPlaying] == null) {
			mChannelUris[mChannelPlaying] = getChannelUri(BLACKHOLE, mChannelPlaying);
		}
		Log.d("IFM", "channelUir: " + mChannelUris[mChannelPlaying]);
		mMediaPlayer.setDataSource(getBaseContext(), mChannelUris[mChannelPlaying]);
	}

	private Uri getChannelUri(Uri baseUri, int channel) throws Exception {
		URL url = new URL(Uri.withAppendedPath(baseUri, (channel + 1) + ".m3u").toString());
		BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
		String line = reader.readLine();
		return Uri.parse(line);
	}

	@Override
	public void onCreate() {
		mPhoneStateReceiver = new PhoneStateReceiver((AudioManager) getSystemService(AUDIO_SERVICE));
		registerReceiver(mPhoneStateReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
		mMediaPlayer = new MediaPlayer();
		setupMediaPlayer();
		mChannelUris = new Uri[Constants.NUMBER_OF_CHANNELS];
		mChannelInfos = new ChannelInfo[Constants.NUMBER_OF_CHANNELS];
		for (int i = 0; i < Constants.NUMBER_OF_CHANNELS; i++) {
			mChannelInfos[i] = ChannelInfo.NO_INFO;
		}

		setupLock();

		HandlerThread handlerThread = new HandlerThread("IFMServiceWorker");
		handlerThread.start();
		mAsyncHandler = new AsyncStateHandler(handlerThread.getLooper());

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mUsePushNotification = true;
		mPushNotificationReceiver = new PushNotificationReceiver(this);
		mCyclicChannelUpdater = new CyclicChannelUpdater(this);
		super.onCreate();
	}

	private void setupLock() {
		mWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"IfmWakeLock");
		mWifiLock = ((WifiManager) getSystemService(WIFI_SERVICE)).createWifiLock("IntergalacticFM");
	}

	private void updateNotification() {
		if (mChannelPlaying != Constants.NONE) {
			Intent intent = new Intent(this, IfmPlayer.class);
			intent.setAction(Intent.ACTION_VIEW);
			String artist = mChannelInfos[mChannelPlaying].getArtist();
			Notification notification = new Notification(R.drawable.ifm, "Playing " + artist, System
					.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR;
			notification.setLatestEventInfo(this, "IFM Player", "playing " + artist, PendingIntent.getActivity(this
					.getBaseContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
			mNotificationManager.notify(IFM_NOTIFICATION, notification);
		}
	}

	private void stopNotification() {
		mNotificationManager.cancel(IFM_NOTIFICATION);
	}

	private void setupMediaPlayer() {
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
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

	@Override
	public void onDestroy() {
		releaseLocks();
		mMediaPlayer.release();
		unregisterReceiver(mPhoneStateReceiver);
		stopNotification();
		mPushNotificationReceiver.stop();
		mCyclicChannelUpdater.stop();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (mUsePushNotification) {
			mPushNotificationReceiver.start();
		} else {
			mCyclicChannelUpdater.start(Constants.NONE);
		}
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		mStateListener = mNullPlayerStateListener;
		if (!isPlaying()) {
			mPushNotificationReceiver.stop();
			mCyclicChannelUpdater.stop();
		} else {
			mCyclicChannelUpdater.setChannelFilter(mChannelPlaying);
		}
		return super.onUnbind(intent);
	}

	private void acquireLocks() {
		Log.d("IFM", "acquire Lock");
		mWifiLock.acquire();
		mWakeLock.acquire();
	}

	private void releaseLocks() {
		Log.d("IFM", "release Lock");
		if (mWifiLock.isHeld()) {
			mWifiLock.release();
		}
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}
}