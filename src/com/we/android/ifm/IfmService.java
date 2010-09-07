package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

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
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class IfmService extends Service {

  private static Uri BLACKHOLE = Uri.parse("http://radio.intergalacticfm.com");
  private static final int NUMBER_OF_CHANNELS = 4;

  private Uri[] mChannelUris;
  private MediaPlayer mMediaPlayer;

  private static final int NONE = Integer.MAX_VALUE;
  private int mChannelPlaying = NONE; 

  private Handler mAsyncHandler;

  private PhoneStateReceiver mPhoneStateReceiver;
  private IPlayerStateListener mStateListener;

  class LocalBinder extends Binder {
    public IfmService getService() {
      return IfmService.this;
    }
  }

  private final LocalBinder mBinder = new LocalBinder();

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
    return (mChannelPlaying != NONE);
  }

  public void stop() {
    if (mChannelPlaying != NONE) {
      mChannelPlaying = NONE;
      Log.d("IFM", "stop");
      requestState(PlayerState.IDLE);
    }
  }

  public void cancel() {
    mChannelPlaying = NONE;
    Log.d("IFM", "cancel");
    requestState(PlayerState.IDLE);
  }

  public void play(final int channel) {
    Log.d("IFM", "play: " + channel);
    mChannelPlaying = channel;
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

  public IfmService() {
    HandlerThread handlerThread = new HandlerThread("IFMServiceWorker");
    handlerThread.start();
    mAsyncHandler = new AsyncStateHandler(handlerThread.getLooper());
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
    mChannelUris = new Uri[NUMBER_OF_CHANNELS];
    super.onCreate();
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
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
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