package com.we.android.ifm;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.HttpClient;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Toast;

import com.we.android.ifm.AsyncCoverLoader.UpdateCoverImage;

public class IfmPlayer extends ListActivity implements ServiceConnection {
	private static final int MENU_SETTINGS = 0;

	private ProgressDialog mMediaPlayerProgress;

	private int mSelectedChannel = Adapter.NO_SELECTION;

	private boolean mShowCoverArt;
	private Vibrator mVibratorService;
	private ChannelViewAdapter mChannelViewAdapter;
	private IPlayer mPlayer;
	private HttpClient mHttpClient;
	private AsyncCoverLoader mAsyncCoverLoader;

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
		public void onChannelInfoChanged(final int channel, final ChannelInfo channelInfo) {
			if (channelInfo != ChannelInfo.NO_INFO) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (mChannelViewAdapter != null) {
							mChannelViewAdapter.updateChannelInfo(channel, channelInfo);
						}
						if (mShowCoverArt) {
							mAsyncCoverLoader.loadCover(new UpdateCoverImage(channel, channelInfo.getCoverUri()));
						}
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

		mChannelViewAdapter = new ChannelViewAdapter(getLayoutInflater(), this);
		setListAdapter(mChannelViewAdapter);
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

		mPlayer = new NullPlayer();

		getListView().setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> view, View child, int pos, long id) {
				vibrate();
				try {
					if (mPlayer.isPlaying()) {
						if (mPlayer.getPlayingChannel() == pos) {
							mSelectedChannel = Adapter.NO_SELECTION;
							mChannelViewAdapter.setChannelPlaying(Constants.NONE);
							mPlayer.stop();
						} else {
							mChannelViewAdapter.setChannelPlaying(Constants.NONE);
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
		
		findViewById(R.id.scheduleButton).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(IfmPlayer.this, IfmSchedule.class));
			}
		});

		mHttpClient = Util.createThreadSaveHttpClient(20);
		mAsyncCoverLoader = new AsyncCoverLoader(mHttpClient, mChannelViewAdapter, findViewById(R.id.progressBar));
	}

	@Override
	protected void onResume() {
		startService(new Intent(IfmService.class.getName()));
		bindService(new Intent(IfmService.class.getName()), this, Context.BIND_AUTO_CREATE);

		mShowCoverArt = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showCoverArt", true);

		updateChannelInfos();
		super.onResume();
	}

	@Override
	protected void onPause() {
		if (mMediaPlayerProgress != null) {
			mMediaPlayerProgress.dismiss();
		}
		unbindService(this);
		if (!mPlayer.isPlaying()) {
			stopService(new Intent(IfmService.class.getName()));
		}
		mChannelViewAdapter.pause();
		Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putInt("channelSelected", mSelectedChannel);
		editor.commit();
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		mHttpClient.getConnectionManager().shutdown();
		super.onDestroy();
	}

	private void playChannel(int channel) throws RemoteException {
		showProgress();
		mSelectedChannel = channel;
		mPlayer.play(channel);
	}

	private void restoreState(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			mSelectedChannel = savedInstanceState.getInt("channelSelected", Constants.NONE);
		} else {
			SharedPreferences preferences = getPreferences(MODE_PRIVATE);
			mSelectedChannel = preferences.getInt("channelSelected", Constants.NONE);
		}
		getListView().setSelection(mSelectedChannel);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("channelSelected", mSelectedChannel);
		outState.putBoolean("showCoverArt", mShowCoverArt);
	}

	private void showConnectionProblem(Context context) {
		Toast.makeText(context, "Connection Problem", Toast.LENGTH_LONG).show();
	}

	private void showProgress() {
		mMediaPlayerProgress = new ProgressDialog(this);
		mMediaPlayerProgress.setMessage("Buffering. Please wait...");
		mMediaPlayerProgress.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mPlayer.cancel();
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

	private void updateChannelInfos() {
		if (mShowCoverArt) {
			mAsyncCoverLoader.showProgress();
		}
		ChannelInfo[] infos = mPlayer.getChannelInfo();
		List<UpdateCoverImage> updates = new ArrayList<UpdateCoverImage>();
		for (int i = 0; i < Constants.NUMBER_OF_CHANNELS; i++) {
			if (infos[i] != ChannelInfo.NO_INFO) {
				mChannelViewAdapter.updateChannelInfo(i, infos[i]);
				if (mShowCoverArt) {
					updates.add(new AsyncCoverLoader.UpdateCoverImage(i, infos[i].getCoverUri()));
				} else {
					mChannelViewAdapter.updateBitmap(i, null);
				}
			}
		}
		mAsyncCoverLoader.loadCovers(updates);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.getItem(MENU_SETTINGS).setEnabled(true);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.layout.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
			startActivity(new Intent(this, PreferencesEditor.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
