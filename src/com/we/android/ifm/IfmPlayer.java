package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class IfmPlayer extends ListActivity implements ServiceConnection {

  private static final int SECOND_IN_MICROSECONDS = 1000;
  private static final String IFM_URL = "http://intergalacticfm.com";
  private static final int CHANNEL_UPDATE_FREQUENCY = 20 * SECOND_IN_MICROSECONDS;
  private static final int IFM_NOTIFICATION = 0;
  private static int NUMBER_OF_CHANNELS = 4;

  private ProgressDialog mMediaPlayerProgress;

  private static final int NONE = Integer.MAX_VALUE;
  private int mSelectedChannel = Adapter.NO_SELECTION;
  private Timer mTimer;
  private ChannelInfo[] mChannelInfos;

  Handler mHandler = new Handler();
  private Vibrator mVibratorService;
  private NotificationManager mNotificationManager;
  private Bitmap mBlanco;
  private ChannelViewAdapter mChannelViewAdapter;
  private IPlayer mPlayer;

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
              InputStream stream = coverUrl.openStream();
              if (stream == null) {
                Log.e("IFM", "no cover stream");
              }
              bitmap = BitmapFactory.decodeStream(stream);
              if (bitmap == null) {
                Log.e("IFM", "could not decode image");
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else {
            Log.e("IFM", "NPE: coverart");
          }
          if (bitmap == null) {
            bitmap = mBlanco;
          }

          ChannelInfo info = new ChannelInfo(artist, getLabel(channelInfo), bitmap);
          mHandler.post(new UpdateView(info, i));
        }
      }
    }

    private String queryBlackHole(int channel) {
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
//        Log.d("IFM", "blackhole response: " + line);
        return line;
      } catch (Exception e) {
        e.printStackTrace();
      }
      return "";
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

    private URL getCoverArt(String channelInfo) {
      URL url = null;
      String searchterm = "img src=";
      int indexOf = channelInfo.indexOf(searchterm);
      if (indexOf != -1) {
        int from = indexOf + searchterm.length() + 1;
        int to = channelInfo.indexOf("\"", from);
        String pathToImage = extractSubstring(channelInfo, from, to);
        try {
          url = new URL(IFM_URL + pathToImage);
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
      }
      return url;
    }

    private String extractSubstring(String str, int from, int to) {
      if ((from < str.length()) && (to < str.length()) && (from < to)) {
        return str.substring(from, to);
      } else {
        return "";
      }
    }
  }

  class ChannelViewAdapter extends BaseAdapter {
    private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
    private final String mChannelName[] = new String[]{"Murder Capital FM", "Intergalactic Classix", "The Dream Machine", "Cybernetic Broadcasting"};
    private int mChannelPlaying = NONE;

    public void setChannelPlaying(int channel) {
      mChannelPlaying = channel;
      notifyDataSetChanged();
    }

    public void setChannelSelected(int channel) {

    }

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
      return position;
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
      if (channel == mChannelPlaying) {
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setVisibility(View.VISIBLE);
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setImageResource(R.drawable.play_indicator);
      } else {
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setVisibility(View.INVISIBLE);
      }
      updateChannelInfo(channelView, channelInfo);
    }

    private void updateChannelInfo(View channel, ChannelInfo info) {
      ((ImageView) channel.findViewById(R.id.cover)).setImageBitmap(info.getBitmap());
      ((TextView) channel.findViewById(R.id.artist)).setText(info.getArtist());
      ((TextView) channel.findViewById(R.id.label)).setText(info.getLabel());
    }
  }

  IPlayerStateListener.Stub mPlayerStateListener = new IPlayerStateListener.Stub() {
    @Override
    public void onChannelStarted(final int channel) throws RemoteException {
      if (mMediaPlayerProgress != null) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            doNotification();
            mChannelViewAdapter.setChannelPlaying(channel);
            mMediaPlayerProgress.dismiss();
          }
        });
      }
    }

    @Override
    public void onChannelError() throws RemoteException {
      if (mMediaPlayerProgress != null) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            mMediaPlayerProgress.dismiss();
            showConnectionProblem(getApplicationContext());
          }
        });
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setContentView(R.layout.main);
    restoreState(savedInstanceState);
    mChannelViewAdapter = new ChannelViewAdapter();
    setListAdapter(mChannelViewAdapter);

    startService(new Intent(IPlayer.class.getName()));
    bindService(new Intent(IPlayer.class.getName()), this, Context.BIND_AUTO_CREATE);

    //		getListView().setSelection(mSelectedChannel);
    getListView().setDivider(null);

    getListView().setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> view, View child, int pos, long id) {
        mSelectedChannel = pos;
        mChannelViewAdapter.notifyDataSetChanged();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });

    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> view, View child, int pos, long id) {
        mVibratorService.vibrate(80);
        try {
          if ((mPlayer != null) && mPlayer.isPlaying()) {
            if (mPlayer.getPlayingChannel() == pos) {
              mSelectedChannel = Adapter.NO_SELECTION;
              mChannelViewAdapter.setChannelPlaying(NONE);
              mPlayer.stop();
              stopNotification();
            } else {
              mChannelViewAdapter.setChannelPlaying(NONE);
              mPlayer.stop();
              playChannel(pos);
            }
          } else {
            playChannel(pos);
          }
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      }
    });

    mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
  }

  private void playChannel(int channel) throws RemoteException {
    showProgress();
    mSelectedChannel = channel;
    if (mPlayer != null) {
      mPlayer.play(channel);
    }
  }

  private void restoreState(Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      mSelectedChannel = savedInstanceState.getInt("channelSelected", NONE);
    } else {
      SharedPreferences preferences = getPreferences(MODE_PRIVATE);
      mSelectedChannel = preferences.getInt("channelSelected", NONE);
    }
    getListView().setSelection(mSelectedChannel);
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
    super.onSaveInstanceState(outState);
    outState.putInt("channelSelected", mSelectedChannel);
  }

  @Override
  protected void onDestroy() {
    mNotificationManager.cancel(IFM_NOTIFICATION);
    mTimer.cancel();
    unbindService(this);
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    Editor editor = getPreferences(MODE_PRIVATE).edit();
    editor.putInt("channelSelected", mSelectedChannel);
    editor.commit();
    mTimer.cancel();
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mTimer = new Timer();
    mTimer.schedule(new ChannelUpdater(), 0, CHANNEL_UPDATE_FREQUENCY);
  }

  private void doNotification() {
    int channelPlaying = NONE;
    if (mPlayer != null) {
      try {
        channelPlaying = mPlayer.getPlayingChannel();
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
    if (channelPlaying != NONE) {
      Intent intent = new Intent(this, IfmPlayer.class);
      intent.setAction(Intent.ACTION_VIEW);
      Notification notification = new Notification(R.drawable.ifm, "", System.currentTimeMillis());
      notification.flags |= Notification.FLAG_NO_CLEAR;
      String artist = mChannelInfos[channelPlaying].getArtist();
      notification.setLatestEventInfo(IfmPlayer.this, "IFM Player","playing " + artist, 
          PendingIntent.getActivity(this.getBaseContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
      mNotificationManager.notify(IFM_NOTIFICATION, notification);
    }
  }

  private void showConnectionProblem(Context context) {
    Toast toast = Toast.makeText(context, "Connection Problem", Toast.LENGTH_LONG);
    toast.show();
  }

  private void stopNotification() {
    mNotificationManager.cancel(IFM_NOTIFICATION);
  }

  private void showProgress() {
    mMediaPlayerProgress = new ProgressDialog(this);
    mMediaPlayerProgress.setMessage("Buffering. Please wait...");
    mMediaPlayerProgress.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        try {
          if (mPlayer != null) {
            mPlayer.cancel();
          }
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      }
    });
    mMediaPlayerProgress.show();
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    mPlayer = IPlayer.Stub.asInterface(service);
    try {
      mPlayer.registerStateListener(mPlayerStateListener);
      if (mPlayer.isPreparing()) {
        showProgress();
      }
      if (mPlayer.isPlaying()) {
        mChannelViewAdapter.setChannelPlaying(mPlayer.getPlayingChannel());
      }
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
  }
}
