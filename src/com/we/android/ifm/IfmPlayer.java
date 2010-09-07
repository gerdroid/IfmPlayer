package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
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
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

  class UpdateChannel {
    ChannelInfo mChannelInfo;
    int mChannel;

    public UpdateChannel(ChannelInfo info, int channel) {
      mChannelInfo = info;
      mChannel = channel;
    }
  }

  class AsyncChannelQuery extends AsyncTask<Integer, Void, List<UpdateChannel>> {

    @Override
    protected List<UpdateChannel> doInBackground(Integer... params) {
      int channel = params[0];
      List<UpdateChannel> updates = new ArrayList<UpdateChannel>();
      ChannelInfo channelInfo = queryBlackHole(channel);
      if (!mChannelInfos[channel].getArtist().equals(channelInfo.getArtist())) {
        mChannelBitmaps[channel] = getBitmap(channelInfo.getCoverUri());
        updates.add(new UpdateChannel(channelInfo, channel));
      }
      return updates;
    }

    @Override
    protected void onPostExecute(List<UpdateChannel> updates) {
      for (UpdateChannel update : updates) {
        mChannelInfos[update.mChannel] = update.mChannelInfo;
      }
      mChannelViewAdapter.notifyDataSetChanged();
      super.onPostExecute(updates);
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

    private Bitmap getBitmap(Uri coverUri) {
      Bitmap bitmap = null;
      try {
        URL url = new URL(coverUri.toString());
        InputStream stream = url.openStream();
        bitmap =  BitmapFactory.decodeStream(stream);
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (bitmap == null) {
        bitmap = mBlanco;
      }
      return bitmap;
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

  class ChannelViewAdapter extends BaseAdapter {
    private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
    private final String mChannelName[] = new String[]{"MurderCapital FM", "Intergalactic Classix", "The Dream Machine", "Cybernetic Broadcasting"};
    private int mChannelPlaying = NONE;

    public void setChannelPlaying(int channel) {
      mChannelPlaying = channel;
      notifyDataSetChanged();
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
      updateView(channelView, position, mChannelInfos[position]);
      return channelView;
    }

    private void updateView(View channelView, int channel, ChannelInfo info) {
      channelView.setBackgroundResource(mChannelColor[channel]);
      ((TextView) channelView.findViewById(R.id.channel_name)).setText(mChannelName[channel]);
      if (channel == mChannelPlaying) {
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setVisibility(View.VISIBLE);
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setImageResource(R.drawable.play_indicator);
      } else {
        ((ImageView) channelView.findViewById(R.id.playIndicator)).setVisibility(View.INVISIBLE);
      }
      ((ImageView) channelView.findViewById(R.id.cover)).setImageBitmap(mChannelBitmaps[channel]);
      ((TextView) channelView.findViewById(R.id.artist)).setText(info.getArtist());
      ((TextView) channelView.findViewById(R.id.label)).setText(info.getLabel());
    }
  }

  private static final int SECOND_IN_MICROSECONDS = 1000;
  private static final String IFM_URL = "http://intergalacticfm.com";
  private static final int CHANNEL_UPDATE_FREQUENCY = 20 * SECOND_IN_MICROSECONDS;
  private static final int IFM_NOTIFICATION = 0;
  private static final int NUMBER_OF_CHANNELS = 4;
  private static final int MENU_FLATTR = 0;
  private static final int MENU_INFO = 1;

  private ProgressDialog mMediaPlayerProgress;

  private static final int NONE = Integer.MAX_VALUE;
  private int mSelectedChannel = Adapter.NO_SELECTION;
  private ChannelInfo[] mChannelInfos;
  private Bitmap[] mChannelBitmaps = new Bitmap[NUMBER_OF_CHANNELS];

  private final Handler mHandler = new Handler();
  private Vibrator mVibratorService;
  private NotificationManager mNotificationManager;
  private Bitmap mBlanco;
  private ChannelViewAdapter mChannelViewAdapter;
  private IfmService mPlayer;

  private final Runnable mCyclicChannelUpdater = new Runnable() {
    @Override
    public void run() {
      for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
        new AsyncChannelQuery().execute(i);
      }
      mHandler.postDelayed(this, CHANNEL_UPDATE_FREQUENCY);
    }
  };

  private final IPlayerStateListener mPlayerStateListener = new IPlayerStateListener() {
    @Override
    public void onChannelStarted(final int channel) {
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
    public void onChannelError() {
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

    startService(new Intent(IfmService.class.getName()));
    bindService(new Intent(IfmService.class.getName()), this, Context.BIND_AUTO_CREATE);

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
    mChannelInfos = (ChannelInfo[]) getLastNonConfigurationInstance();
    if (mChannelInfos == null) {
      mChannelInfos = new ChannelInfo[NUMBER_OF_CHANNELS];
      ChannelInfo defaultChannelInfo = new ChannelInfo("", "", null);
      for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
        mChannelInfos[i] = defaultChannelInfo;
      }
    }
    mBlanco = BitmapFactory.decodeResource(getResources(), R.drawable.blanco);
    for (int i=0; i<NUMBER_OF_CHANNELS; i++) {
      mChannelBitmaps[i] = mBlanco;
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
    unbindService(this);
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    Editor editor = getPreferences(MODE_PRIVATE).edit();
    editor.putInt("channelSelected", mSelectedChannel);
    editor.commit();
    mHandler.removeCallbacks(mCyclicChannelUpdater);
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mHandler.post(mCyclicChannelUpdater);
    Log.d("IFM", "version: "+getVersionName());
  }

  private void doNotification() {
    int channelPlaying = NONE;
    if (mPlayer != null) {
      channelPlaying = mPlayer.getPlayingChannel();
    }
    if (channelPlaying != NONE) {
      Intent intent = new Intent(this, IfmPlayer.class);
      intent.setAction(Intent.ACTION_VIEW);
      String artist = mChannelInfos[channelPlaying].getArtist();
      Notification notification = new Notification(R.drawable.ifm, "Playing "+artist, System.currentTimeMillis());
      notification.flags |= Notification.FLAG_NO_CLEAR;
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
        if (mPlayer != null) {
          mPlayer.cancel();
        }
      }
    });
    mMediaPlayerProgress.show();
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    mPlayer = ((IfmService.LocalBinder) service).getService();
    mPlayer.registerStateListener(mPlayerStateListener);
    if (mPlayer.isPreparing()) {
      showProgress();
    }
    if (mPlayer.isPlaying()) {
      mChannelViewAdapter.setChannelPlaying(mPlayer.getPlayingChannel());
    }
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.getItem(MENU_FLATTR).setEnabled(true);
    menu.getItem(MENU_INFO).setEnabled(true);		
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, MENU_FLATTR, 1, "Flattr").setEnabled(true);
    menu.add(Menu.NONE, MENU_INFO, 2, "Info").setEnabled(true);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case MENU_FLATTR:
      Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://flattr.com/thing/48747/Intergalactic-FM-Music-For-The-Galaxy"));
      startActivity(viewIntent);
      break;
    case MENU_INFO:
      showVersionAlert();
      break;				
    }
    return super.onOptionsItemSelected(item);
  }

  void showVersionAlert() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("IfmPlayer")
    .setMessage("by Outer Rim Soft\n\nBeta Version "+getVersionName())
    .setCancelable(false)
    .setPositiveButton("OK", new OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        // nothing to do
      }
    });
    AlertDialog alert = builder.create();
    alert.show();
  }

  private String getVersionName() {
    ComponentName comp = new ComponentName(this, IfmPlayer.class);
    try {
      PackageInfo pinfo;
      pinfo = getPackageManager().getPackageInfo(comp.getPackageName(), 0);
      return pinfo.versionName;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
      return "unknown version";
    }
  }
}
