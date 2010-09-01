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
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
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

  private Handler mHandler;

  private PhoneStateReceiver mPhoneStateReceiver;

  private IPlayerStateListener mStateListener;

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
          mHandler.sendEmptyMessage(PlayerState.PREPARED.ordinal());
        } else {
          Log.d("IFM", "throw away: " + requestedState);
          return;
        }
      } else if (requestedState == PlayerState.PREPARED) {
        if (mState == PlayerState.PREPARING) {
          try {
            doPreparation();
            mMediaPlayer.prepare();
            mHandler.sendEmptyMessage(PlayerState.RUNNING.ordinal());
          } catch (Exception e) {
            mHandler.sendEmptyMessage(PlayerState.IDLE.ordinal());
            e.printStackTrace();
          }
        } else {
          Log.d("IFM", "throw away: " + requestedState);
          return;
        }
      } else if (requestedState == PlayerState.RUNNING) {
        if (mState == PlayerState.PREPARED) {
          mMediaPlayer.start();
          try {
            if (mStateListener != null) {
              mStateListener.onChannelStarted(mChannelPlaying);
            }
          } catch (RemoteException e) {
            e.printStackTrace();
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
        mHandler.post(new Runnable() {
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
      TelephonyManager telephony = (TelephonyManager) 
      context.getSystemService(Context.TELEPHONY_SERVICE);
      telephony.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
  }

  private final IPlayer.Stub mBinder = new IPlayer.Stub() {

    @Override
    public int getPlayingChannel() throws RemoteException {
      return mChannelPlaying;
    }

    @Override
    public boolean isPlaying() throws RemoteException {
      return (mChannelPlaying != NONE);
    }

    @Override
    public void stop() throws RemoteException {
      if (mChannelPlaying != NONE) {
        mChannelPlaying = NONE;
        Log.d("IFM", "stop");
        requestState(PlayerState.IDLE);
      }
    }

    @Override
    public void cancel() throws RemoteException {
      mChannelPlaying = NONE;
      Log.d("IFM", "cancel");
      requestState(PlayerState.IDLE);
    }

    @Override
    public void play(final int channel) throws RemoteException {
      Log.d("IFM", "play: " + channel);
      mChannelPlaying = channel;
      requestState(PlayerState.PREPARING);
    }
    
    
    private void requestState(PlayerState state) {
      mHandler.sendEmptyMessage(state.ordinal());
    }

    @Override
    public boolean isPreparing() throws RemoteException {
      return (mState == PlayerState.PREPARING) || (mState == PlayerState.PREPARED);
    }

    @Override
    public void registerStateListener(IPlayerStateListener stateListener) throws RemoteException {
      mStateListener = stateListener;
    }
  };

  public IfmService() {
    HandlerThread handlerThread = new HandlerThread("IFMServiceWorker");
    handlerThread.start();
    mHandler = new AsyncStateHandler(handlerThread.getLooper());
  }

  private void doPreparation() {
    if (mChannelUris[mChannelPlaying] == null) {
      mChannelUris[mChannelPlaying] = getChannelUri(BLACKHOLE, mChannelPlaying); 
    }
    if (mChannelUris[mChannelPlaying] != null) {
      try {
        mMediaPlayer.setDataSource(getBaseContext(), mChannelUris[mChannelPlaying]);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      try {
        if (mStateListener != null) {
          mStateListener.onChannelError();
        }
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }
  
  private Uri getChannelUri(Uri baseUri, int channel) {
    try {
      URL url = new URL(Uri.withAppendedPath(baseUri, (channel+1) + ".m3u").toString());
      // URL url = new URL("http://radio.intergalacticfm.com/" + (channel+1) + "aacp.m3u");
      BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
      String line = reader.readLine();
      return Uri.parse(line);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
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