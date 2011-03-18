package com.we.android.ifm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class ChannelViewAdapter extends BaseAdapter {
    private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
    private final String mChannelName[] = new String[]{"MurderCapital FM", "Intergalactic Classix", "The Dream Machine", "Rap Attack"};
    private int mChannelPlaying = Constants.NONE;
    private ChannelInfo[] mChannelInfos = new ChannelInfo[Constants.NUMBER_OF_CHANNELS];
    private Bitmap[] mChannelBitmaps = new Bitmap[Constants.NUMBER_OF_CHANNELS];
    private List<Bitmap> mDefaultBitmaps = new ArrayList<Bitmap>();
    private LayoutInflater mInflater;
    private Random mRandom = new Random();
    private AnimationDrawable mPeakAnimation = new AnimationDrawable();
    private List<Drawable> mPeaks = new ArrayList<Drawable>();
    private Handler mHandler;
    private ImageView mPlayIndicator;

    private final Runnable mAnimator = new Runnable() {
	public void run() {
	    int index = mRandom.nextInt(5);
	    mPeakAnimation = new AnimationDrawable();
	    mPeakAnimation.setOneShot(true);
	    for (int i=0; i<=index; i++) {
		mPeakAnimation.addFrame(mPeaks.get(i), 100);
	    }
	    if (mPlayIndicator != null) {
		mPlayIndicator.setBackgroundDrawable(mPeakAnimation);
	    }
	    mPeakAnimation.run();
	    mHandler.postDelayed(this, index * 100);
	}
    };

    public ChannelViewAdapter(LayoutInflater inflater, Context context) {
	mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm1));
	mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm2));
	mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm3));
	mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm4));    
	for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
	    mChannelInfos[i] = ChannelInfo.NO_INFO;
	    mChannelBitmaps[i] = mDefaultBitmaps.get(i);
	}
	mInflater = inflater;
	mPeaks.add(context.getResources().getDrawable(R.drawable.peak_1));
	mPeaks.add(context.getResources().getDrawable(R.drawable.peak_2));
	mPeaks.add(context.getResources().getDrawable(R.drawable.peak_3));
	mPeaks.add(context.getResources().getDrawable(R.drawable.peak_4));
	mPeaks.add(context.getResources().getDrawable(R.drawable.peak_5));

	mHandler = new Handler();
	mHandler.post(mAnimator);
    }

    public void updateChannelInfo(int channel, ChannelInfo info) {
	mChannelInfos[channel] = info;
	notifyDataSetChanged();
    }

    public void updateBitmap(int channel, Bitmap bitmap) {
	if (bitmap == null) {
	    mChannelBitmaps[channel] = mDefaultBitmaps.get(channel);
	} else {
	    mChannelBitmaps[channel] = bitmap;
	}
	notifyDataSetChanged();
    }

    public void setChannelPlaying(int channel) {
	mChannelPlaying = channel;
	notifyDataSetChanged();
    }

    @Override
    public int getCount() {
	return Constants.NUMBER_OF_CHANNELS + 1;
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
    public boolean isEnabled(int position) {
	if (position == Constants.NUMBER_OF_CHANNELS) {
	    return false;
	}
	return true;
    }
    
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
	View channelView;
	if (position == Constants.NUMBER_OF_CHANNELS) {
	    channelView = mInflater.inflate(R.layout.rip, parent, false);
	} else {
	    channelView = mInflater.inflate(R.layout.channel, parent, false);
	    updateView(channelView, position, mChannelInfos[position]);
	}
	return channelView;
    }

    private void updateView(View channelView, int channel, ChannelInfo info) {
	channelView.setBackgroundResource(mChannelColor[channel]);
	((TextView) channelView.findViewById(R.id.channel_name)).setText(mChannelName[channel]);
	ImageView playIndicator = (ImageView) channelView.findViewById(R.id.playindicator);
	if (channel == mChannelPlaying) {
	    mPlayIndicator = playIndicator;
	    playIndicator.setVisibility(View.VISIBLE);
	} else {
	    playIndicator.setVisibility(View.INVISIBLE);
	}
	((ImageView) channelView.findViewById(R.id.cover)).setImageBitmap(mChannelBitmaps[channel]);
	((TextView) channelView.findViewById(R.id.artist)).setText(info.getArtist());
	((TextView) channelView.findViewById(R.id.label)).setText(info.getLabel());
    }
}
