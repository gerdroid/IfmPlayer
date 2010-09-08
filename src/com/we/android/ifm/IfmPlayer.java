package com.we.android.ifm;

import java.io.InputStream;
import java.net.URL;

import android.app.AlertDialog;
import android.app.ListActivity;
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
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
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

  class ChannelViewAdapter extends BaseAdapter {
    private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
    private final String mChannelName[] = new String[]{"MurderCapital FM", "Intergalactic Classix", "The Dream Machine", "Cybernetic Broadcasting"};
    private int mChannelPlaying = NONE;
    private ChannelInfo[] mChannelInfos = new ChannelInfo[Constants.NUMBER_OF_CHANNELS];
    private Bitmap[] mChannelBitmaps = new Bitmap[Constants.NUMBER_OF_CHANNELS];

    public ChannelViewAdapter() {
      for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
        mChannelInfos[i] = ChannelInfo.NO_INFO;
        mChannelBitmaps[i] = mBlanco;
      }
    }

    public void updateChannelInfo(int channel, ChannelInfo info) {
      mChannelInfos[channel] = info;
      notifyDataSetChanged();
    }

    public void updateBitmap(int channel, Bitmap bitmap) {
      mChannelBitmaps[channel] = bitmap;
      notifyDataSetChanged();
    }

    public void setChannelPlaying(int channel) {
      mChannelPlaying = channel;
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return Constants.NUMBER_OF_CHANNELS;
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

  class UpdateCoverImage {
    int mChannel;
    Uri mCoverUri;

    public UpdateCoverImage(int channel, Uri coverUri) {
      mChannel = channel;
      mCoverUri = coverUri;
    }
  }

  class CoverImageLoader extends AsyncTask<UpdateCoverImage, Void, Bitmap> {
    private int mChannel;

    @Override
    protected Bitmap doInBackground(UpdateCoverImage... updates) {
      mChannel = updates[0].mChannel;
      return getBitmap(updates[0].mCoverUri);
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      mChannelViewAdapter.updateBitmap(mChannel, result);
      super.onPostExecute(result);
    }

    private Bitmap getBitmap(Uri coverUri) {
      Bitmap bitmap = null;
      try {
        URL url = new URL(coverUri.toString());
        InputStream stream = url.openStream();
        bitmap = BitmapFactory.decodeStream(stream);
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (bitmap == null) {
        bitmap = mBlanco;
      }
      return bitmap;
    }
  }

  private static final int MENU_FLATTR = 0;
  private static final int MENU_INFO = 1;
  private static final int MENU_SETTINGS = 2;

  private ProgressDialog mMediaPlayerProgress;

  private static final int NONE = Integer.MAX_VALUE;
  private int mSelectedChannel = Adapter.NO_SELECTION;

  private Bitmap mBlanco;

  private Vibrator mVibratorService;
  private ChannelViewAdapter mChannelViewAdapter;
  private IPlayer mPlayer;

  private final IPlayerStateListener mPlayerStateListener = new IPlayerStateListener() {
    @Override
    public void onChannelStarted(final int channel) {
      if (mMediaPlayerProgress != null) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
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

    @Override
    public void onChannelInfoChanged(int channel, ChannelInfo channelInfo) {
      if (channelInfo != ChannelInfo.NO_INFO) {
        if (mChannelViewAdapter != null) {
          mChannelViewAdapter.updateChannelInfo(channel, channelInfo);
        }
        new CoverImageLoader().execute(new UpdateCoverImage(channel, channelInfo.getCoverUri()));
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setContentView(R.layout.main);
    restoreState(savedInstanceState);
    mBlanco = BitmapFactory.decodeResource(getResources(), R.drawable.blanco);
    mChannelViewAdapter = new ChannelViewAdapter();
    setListAdapter(mChannelViewAdapter);

    startService(new Intent(IfmService.class.getName()));
    bindService(new Intent(IfmService.class.getName()), this, Context.BIND_AUTO_CREATE);

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
        vibrate();
        try {
          if ((mPlayer != null) && mPlayer.isPlaying()) {
            if (mPlayer.getPlayingChannel() == pos) {
              mSelectedChannel = Adapter.NO_SELECTION;
              mChannelViewAdapter.setChannelPlaying(NONE);
              mPlayer.stop();
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

      private void vibrate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(IfmPlayer.this);
        boolean vibrate = prefs.getBoolean("vibrate", true);
        if (vibrate) { 
          mVibratorService.vibrate(80);
        }
      }
    });

    mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
  }

  @Override
  protected void onResume() {
    if (mPlayer != null) {
      mPlayer.setVisible(true);
      updateChannelInfosFromService();
    }
    super.onResume();
  }

  private void updateChannelInfosFromService() {
    ChannelInfo[] infos = mPlayer.getChannelInfo();
    for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
      if (infos[i] != ChannelInfo.NO_INFO) {
        mChannelViewAdapter.updateChannelInfo(i, infos[i]);
        new CoverImageLoader().execute(new UpdateCoverImage(i, infos[i].getCoverUri()));
      }
    }
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
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("channelSelected", mSelectedChannel);
  }

  @Override
  protected void onDestroy() {
    unbindService(this);
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    Editor editor = getPreferences(MODE_PRIVATE).edit();
    editor.putInt("channelSelected", mSelectedChannel);
    editor.commit();
    if (mPlayer != null) {
      mPlayer.setVisible(false);
    }
    super.onPause();
  }

  private void showConnectionProblem(Context context) {
    Toast toast = Toast.makeText(context, "Connection Problem", Toast.LENGTH_LONG);
    toast.show();
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
    mPlayer.setVisible(true);
    updateChannelInfosFromService();
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
    menu.getItem(MENU_SETTINGS).setEnabled(true);
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, MENU_FLATTR, 1, "Flattr").setEnabled(true);
    menu.add(Menu.NONE, MENU_INFO, 2, "Info").setEnabled(true);
    menu.add(Menu.NONE, MENU_SETTINGS, 3, "Settings").setEnabled(true);
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
    case MENU_SETTINGS:
      startActivity(new Intent(this, PreferencesEditor.class));
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
