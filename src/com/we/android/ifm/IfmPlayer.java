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
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class IfmPlayer extends ListActivity implements ServiceConnection {

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
      return bitmap;
    }
  }

  private static final int MENU_FLATTR = 0;
  private static final int MENU_INFO = 1;
  private static final int MENU_SETTINGS = 2;

  private ProgressDialog mMediaPlayerProgress;

  private static final int NONE = Integer.MAX_VALUE;
  private int mSelectedChannel = Adapter.NO_SELECTION;

  private boolean mShowCoverArt;
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
            mChannelViewAdapter.setChannelPlaying(Constants.NONE);
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
        if (mShowCoverArt) {
          new CoverImageLoader().execute(new UpdateCoverImage(channel, channelInfo.getCoverUri()));
        }
      }
    }
  };
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setContentView(R.layout.main);
    restoreState(savedInstanceState);
    
    mChannelViewAdapter = new ChannelViewAdapter(getLayoutInflater(), this);
    setListAdapter(mChannelViewAdapter);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    mShowCoverArt = preferences.getBoolean("showCoverArt", false);

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

    mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    
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
  }

  @Override
  protected void onResume() {
    startService(new Intent(IfmService.class.getName()));
    bindService(new Intent(IfmService.class.getName()), this, Context.BIND_AUTO_CREATE);
    mShowCoverArt = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showCoverArt", false);
    super.onResume();
  }
  
  @Override
  protected void onPause() {
    if (mMediaPlayerProgress != null) {
      mMediaPlayerProgress.dismiss();
    }
    boolean isPlaying = mPlayer.isPlaying();
    unbindService(this);
    if (!isPlaying) {
      stopService(new Intent(IfmService.class.getName()));
    }
    Editor editor = getPreferences(MODE_PRIVATE).edit();
    editor.putInt("channelSelected", mSelectedChannel);
    editor.commit();
    super.onPause();
  }

  private void updateChannelInfos() {
    if (mPlayer != null) {
      ChannelInfo[] infos = mPlayer.getChannelInfo();
      for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
        if ((infos[i] != ChannelInfo.NO_INFO) && mShowCoverArt) {
          mChannelViewAdapter.updateChannelInfo(i, infos[i]);
          new CoverImageLoader().execute(new UpdateCoverImage(i, infos[i].getCoverUri()));
        } else {
          mChannelViewAdapter.updateBitmap(i, null);
        }
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
    updateChannelInfos();
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
