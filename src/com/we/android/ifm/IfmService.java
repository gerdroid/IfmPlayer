package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.InputStream;
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
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class IfmService extends Service implements IPlayer {
  
  class AsyncChannelQuery extends AsyncTask<Integer, Void, ChannelInfo> {
    private int mChannel;

    @Override
    protected ChannelInfo doInBackground(Integer... params) {
      mChannel = params[0];
      return queryBlackHole(mChannel);
    }

    @Override
    protected void onPostExecute(ChannelInfo info) {
      if (!mChannelInfos[mChannel].getArtist().equals(info.getArtist())) {
        mChannelInfos[mChannel] = info;
        updateNotification();
        if (mStateListener != null) {
          mStateListener.onChannelInfoChanged(mChannel, info);
        }
      }
      super.onPostExecute(info);
    }

    private ChannelInfo queryBlackHole(int channel) {
      ChannelInfo info = ChannelInfo.NO_INFO;
      try {
        URL url = new URL(IFM_URL + "/blackhole/homepage.php?channel=" + (channel+1));
        InputStream is = url.openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line = reader.readLine(); // skip one line
        if (!line.startsWith("<div id=\"thumb\">")) {
          line = reader.readLine();
        }
        if (line == null) {
          line = "";
        }
        Log.d("IFM", "blackhole response: " + line);
        info = new ChannelInfo(getArtist(line), getLabel(line), getCoverUri(line));
      } catch (Exception e) {
        e.printStackTrace();
      }
      return info;
    }

    private String getArtist(String channelInfo) {
      String tag = "<div id=\"track-info-trackname\">";
      int startSearchFrom = channelInfo.indexOf(tag);
      int from = channelInfo.indexOf(">", startSearchFrom + tag.length()) + 2;
      int to = channelInfo.indexOf("</a>", from);
      return extractSubstring(channelInfo, from, to);
    }

    private String getLabel(String channelInfo) {
      String tag = "<div id=\"track-info-label\">";
      int from = channelInfo.indexOf(tag) + tag.length();
      int to = channelInfo.indexOf("</div>", from);
      return extractSubstring(channelInfo, from, to);
    }

    private Uri getCoverUri(String channelInfo) {
      Uri uri = Uri.EMPTY;
      String searchterm = "img src=";
      int indexOf = channelInfo.indexOf(searchterm);
      if (indexOf != -1) {
        int from = indexOf + searchterm.length() + 1;
        int to = channelInfo.indexOf("\"", from);
        String pathToImage = extractSubstring(channelInfo, from, to);
        uri = Uri.parse(IFM_URL + pathToImage);
      }
      return uri;
    }

    private String extractSubstring(String str, int from, int to) {
      if ((from < str.length()) && (to < str.length()) && (from < to)) {
        return str.substring(from, to);
      } else {
        return "";
      }
    }
  }

  private static final int SECOND_IN_MICROSECONDS = 1000;
  private static final int CHANNEL_UPDATE_FREQUENCY = 20 * SECOND_IN_MICROSECONDS;
  private static String IFM_URL = "http://intergalacticfm.com";
  private static Uri BLACKHOLE = Uri.parse("http://radio.intergalacticfm.com");
  private static final int IFM_NOTIFICATION = 0;

  private ChannelInfo[] mChannelInfos;
  
  private Uri[] mChannelUris;
  private MediaPlayer mMediaPlayer;

  private int mChannelPlaying = Constants.NONE; 
  private boolean mIsVisible;

  private Handler mAsyncHandler;
  private Handler mHandler;
  
  private NotificationManager mNotificationManager;
  private PhoneStateReceiver mPhoneStateReceiver;
  private IPlayerStateListener mStateListener;

  class LocalBinder extends Binder {
    public IPlayer getService() {
      return IfmService.this;
    }
  }

  private final LocalBinder mBinder = new LocalBinder();

  private final Runnable mCyclicChannelUpdater = new Runnable() {
    @Override
    public void run() {
      if (mIsVisible) {
        for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
          new AsyncChannelQuery().execute(i);
        }
      } else if (mChannelPlaying != Constants.NONE) {
        new AsyncChannelQuery().execute(mChannelPlaying);
      }
      mHandler.removeCallbacks(this);
      mHandler.postDelayed(this, CHANNEL_UPDATE_FREQUENCY);
    }
  };

  private enum PlayerState { IDLE, PREPARING, PREPARED, RUNNING };
  private PlayerState mState;
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
        } else  {
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
            if (mStateListener != null) {
              mChannelPlaying = Constants.NONE;
              stopNotification();
              mStateListener.onChannelError();
            }
            mAsyncHandler.sendEmptyMessage(PlayerState.IDLE.ordinal());
            Log.e("IFM", "connection error: " + e.getMessage());;
          }
        } else {
          Log.d("IFM", "throw away: " + requestedState);
          return;
        }
      } else if (requestedState == PlayerState.RUNNING) {
        if (mState == PlayerState.PREPARED) {
          mMediaPlayer.start();
          if (mStateListener != null) {
            updateNotification();
            mStateListener.onChannelStarted(mChannelPlaying);
          }
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
      if (msg.what == PlayerState.IDLE.ordinal()) {
        setState(PlayerState.IDLE);
      } else if (msg.what == PlayerState.PREPARING.ordinal()) {
        setState(PlayerState.PREPARING);
      } else if (msg.what == PlayerState.PREPARED.ordinal()) {
        setState(PlayerState.PREPARED);
      } else {
        setState(PlayerState.RUNNING);
      }
      super.handleMessage(msg);
    }
  }

  class MyPhoneStateListener extends PhoneStateListener {
    private AudioManager mAudioManager;

    public MyPhoneStateListener(AudioManager audioManager) {
      mAudioManager = audioManager;
    }

    public void onCallStateChanged(int state,String incomingNumber){
      switch(state){
      case TelephonyManager.CALL_STATE_IDLE:
        mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        Log.d("IFM", "IDLE");
        break;
      case TelephonyManager.CALL_STATE_RINGING:
        mAsyncHandler.post(new Runnable() {
          @Override
          public void run() {
            if(mState != PlayerState.IDLE) {
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
      TelephonyManager telephony = 
        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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
    if (mChannelPlaying != Constants.NONE) {
      mChannelPlaying = Constants.NONE;
      Log.d("IFM", "stop");
      stopNotification();
      requestState(PlayerState.IDLE);
    }
  }

  public void cancel() {
    mChannelPlaying = Constants.NONE;
    Log.d("IFM", "cancel");
    requestState(PlayerState.IDLE);
  }

  public void play(final int channel) {
    Log.d("IFM", "play: " + channel);
    mChannelPlaying = channel;
    requestState(PlayerState.PREPARING);
  }
  
  public void setVisible(boolean visible) {
    mIsVisible = visible;
    mHandler.post(mCyclicChannelUpdater);
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

  private void doPreparation() throws Exception {
    if (mChannelUris[mChannelPlaying] == null) {
      mChannelUris[mChannelPlaying] = getChannelUri(BLACKHOLE, mChannelPlaying); 
    }
    Log.d("IFM", "channelUir: " + mChannelUris[mChannelPlaying]);
    mMediaPlayer.setDataSource(getBaseContext(), mChannelUris[mChannelPlaying]);
  }

  private Uri getChannelUri(Uri baseUri, int channel) throws Exception {
    URL url = new URL(Uri.withAppendedPath(baseUri, (channel+1) + ".m3u").toString());
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
    for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
      mChannelInfos[i] = ChannelInfo.NO_INFO;
    }
    
    HandlerThread handlerThread = new HandlerThread("IFMServiceWorker");
    handlerThread.start();
    mAsyncHandler = new AsyncStateHandler(handlerThread.getLooper());
    
    mHandler = new Handler();
    
    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    super.onCreate();
  }
  
  private void updateNotification() {
    if (mChannelPlaying != Constants.NONE) {
      Intent intent = new Intent(this, IfmPlayer.class);
      intent.setAction(Intent.ACTION_VIEW);
      String artist = mChannelInfos[mChannelPlaying].getArtist();
      Notification notification = new Notification(R.drawable.ifm, "Playing " + artist, System.currentTimeMillis());
      notification.flags |= Notification.FLAG_NO_CLEAR;
      notification.setLatestEventInfo(this, "IFM Player","playing " + artist, 
          PendingIntent.getActivity(this.getBaseContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
      mNotificationManager.notify(IFM_NOTIFICATION, notification);
    }
  }
  
  private void stopNotification() {
    mNotificationManager.cancel(IFM_NOTIFICATION);
  }

  private void setupMediaPlayer() {
    mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
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

    mMediaPlayer.setOnBufferingUpdateListener(new OnBufferingUpdateListener() {
      @Override
      public void onBufferingUpdate(MediaPlayer mp, int percent) {
        Log.d("IFM", "percent: " + percent);
      }
    });
  }

  @Override
  public void onDestroy() {
    unregisterReceiver(mPhoneStateReceiver);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    mStateListener = null;
    return super.onUnbind(intent);
  }
}