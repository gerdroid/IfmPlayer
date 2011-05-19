package com.we.android.ifm;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class ChannelViewAdapter extends BaseAdapter {
	private final int mChannelColor[] = new int[] { R.color.ifm1, R.color.ifm2, R.color.ifm3 };
	private final int mSeperatorColor[] = new int[] { R.color.ifm1sep, R.color.ifm2sep, R.color.ifm3sep };;
	private final String mChannelName[] = new String[] { "MurderCapital FM", "Intergalactic Classix", "The Dream Machine" };
	private int mChannelPlaying = Constants.NONE;
	private ChannelInfo[] mChannelInfos = new ChannelInfo[Constants.NUMBER_OF_CHANNELS];
	private Bitmap[] mChannelBitmaps = new Bitmap[Constants.NUMBER_OF_CHANNELS];
	private List<Bitmap> mDefaultBitmaps = new ArrayList<Bitmap>();
	private LayoutInflater mInflater;

	public ChannelViewAdapter(LayoutInflater inflater, Context context) {
		mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm1));
		mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm2));
		mDefaultBitmaps.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm3));
		for (int i = 0; i < Constants.NUMBER_OF_CHANNELS; i++) {
			mChannelInfos[i] = ChannelInfo.NO_INFO;
			mChannelBitmaps[i] = mDefaultBitmaps.get(i);
		}
		mInflater = inflater;
	}
	
	public void pause() {
		setChannelPlaying(Constants.NONE);
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
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.channel, parent, false); 
		}
		updateView(convertView, position, mChannelInfos[position]);
		return convertView;
	}

	private void updateView(View view, int channel, ChannelInfo info) {
		view.setBackgroundResource(mChannelColor[channel]);
		((TextView) view.findViewById(R.id.channel_name)).setText(mChannelName[channel]);
		view.findViewById(R.id.titleseperator).setBackgroundResource(mSeperatorColor[channel]);
		LevelAnimation levelImage1 = (LevelAnimation) view.findViewById(R.id.playindicator1);
		LevelAnimation levelImage2 = (LevelAnimation) view.findViewById(R.id.playindicator2);
		if (channel == mChannelPlaying) {
			start(levelImage1);
			start(levelImage2);
		} else {
			stop(levelImage1);
			stop(levelImage2);
		}
		((ImageView) view.findViewById(R.id.cover)).setImageBitmap(mChannelBitmaps[channel]);
		((TextView) view.findViewById(R.id.artist)).setText(info.getArtist());
		((TextView) view.findViewById(R.id.label)).setText(info.getLabel());
	}
	
	private void start(LevelAnimation levelAnimation) {
		levelAnimation.setVisibility(View.VISIBLE);
		levelAnimation.start();
	}
	
	private void stop(LevelAnimation levelAnimation) {
		levelAnimation.setVisibility(View.INVISIBLE);
		levelAnimation.stop();
	}
}
