package com.we.android.ifm;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.app.ListActivity;
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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
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
	    if (mShowCoverArt) {
		mChannelViewAdapter.updateBitmap(mChannel, result);
	    }
	    super.onPostExecute(result);
	}

	private Bitmap getBitmap(Uri coverUri) {
	    Bitmap bitmap = null;
	    try {
		mHttpClient = new DefaultHttpClient();
		HttpGet get = new HttpGet(coverUri.toString());
		HttpResponse response = mHttpClient.execute(get);
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
		    HttpEntity entity = response.getEntity();
		    if (entity != null) {
			InputStream is = entity.getContent();
			try {
			    try {
				bitmap = BitmapFactory.decodeStream(is);
			    } finally {
				is.close();
			    }
			} catch(IOException e) {
			    Log.w("IFM", "problems decoding Coverart: " + e.toString());
			}
		    }
		} else {
		    Log.w("IFM", "ServerResponse: " + response.getStatusLine());
		}
	    } catch (Exception e) {
		Log.w("IFM", e.toString());
	    }
	    return bitmap;
	}
    }

    private static final int MENU_SCHEDULE = 0;
    private static final int MENU_SETTINGS = 1;

    private ProgressDialog mMediaPlayerProgress;

    private int mSelectedChannel = Adapter.NO_SELECTION;

    private boolean mShowCoverArt;
    private Vibrator mVibratorService;
    private ChannelViewAdapter mChannelViewAdapter;
    private IPlayer mPlayer;
    private DefaultHttpClient mHttpClient;

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
			    		new CoverImageLoader().execute(new UpdateCoverImage(channel, channelInfo.getCoverUri()));
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

	setupHttpClient();
    }

    private void setupHttpClient() {
	SchemeRegistry schemeRegistry = new SchemeRegistry();
	schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
	HttpParams params = new BasicHttpParams();
	HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
	HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
	mHttpClient = new DefaultHttpClient(cm, params);
    }

    @Override
    protected void onResume() {
	startService(new Intent(IfmService.class.getName()));
	bindService(new Intent(IfmService.class.getName()), this, Context.BIND_AUTO_CREATE);

	boolean showCoverArtPref = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showCoverArt", false);
	if (showCoverArtPref && !mShowCoverArt) {
	    Toast.makeText(getApplicationContext(), "Loading Coverart...", 5000).show();
	}
	mShowCoverArt = showCoverArtPref;

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
	ChannelInfo[] infos = mPlayer.getChannelInfo();
	for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
	    if (infos[i] != ChannelInfo.NO_INFO) {
		mChannelViewAdapter.updateChannelInfo(i, infos[i]);
		if (mShowCoverArt) {
		    new CoverImageLoader().execute(new UpdateCoverImage(i, infos[i].getCoverUri()));
		} else {
		    mChannelViewAdapter.updateBitmap(i, null);
		}
	    }
	}
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
	menu.getItem(MENU_SCHEDULE).setEnabled(true);
	menu.getItem(MENU_SETTINGS).setEnabled(true);
	return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	menu.add(Menu.NONE, MENU_SCHEDULE, 1, "Schedule").setEnabled(true);
	menu.add(Menu.NONE, MENU_SETTINGS, 2, "Settings").setEnabled(true);
	return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	switch (item.getItemId()) {
	case MENU_SETTINGS:
	    startActivity(new Intent(this, PreferencesEditor.class));
	    break;
	case MENU_SCHEDULE:
	    startActivity(new Intent(this, IfmSchedule.class));
	    break;
	}
	return super.onOptionsItemSelected(item);
    }
}
