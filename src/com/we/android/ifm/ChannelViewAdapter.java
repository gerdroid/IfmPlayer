package com.we.android.ifm;

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
  private final int mChannelColor[] = new int[]{R.color.ifm1, R.color.ifm2, R.color.ifm3, R.color.ifm4};
  private final String mChannelName[] = new String[]{"MurderCapital FM", "Intergalactic Classix", "The Dream Machine", "Rap Attack"};
  private int mChannelPlaying = Constants.NONE;
  private ChannelInfo[] mChannelInfos = new ChannelInfo[Constants.NUMBER_OF_CHANNELS];
  private Bitmap[] mChannelBitmaps = new Bitmap[Constants.NUMBER_OF_CHANNELS];
  private Bitmap[] mDefaultBitmaps = new Bitmap[Constants.NUMBER_OF_CHANNELS];
  private LayoutInflater mInflater;

  public ChannelViewAdapter(LayoutInflater inflater, Context context) {
    mDefaultBitmaps[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm1);
    mDefaultBitmaps[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm2);
    mDefaultBitmaps[2] = BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm3);
    mDefaultBitmaps[3] = BitmapFactory.decodeResource(context.getResources(), R.drawable.ifm4);    
    for (int i=0; i<Constants.NUMBER_OF_CHANNELS; i++) {
      mChannelInfos[i] = ChannelInfo.NO_INFO;
      mChannelBitmaps[i] = mDefaultBitmaps[i];
    }
    mInflater = inflater;
  }

  public void updateChannelInfo(int channel, ChannelInfo info) {
    mChannelInfos[channel] = info;
    notifyDataSetChanged();
  }

  public void updateBitmap(int channel, Bitmap bitmap) {
    if (bitmap == null) {
      mChannelBitmaps[channel] = mDefaultBitmaps[channel];
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
    View channelView;
    if (convertView == null) {
      channelView = mInflater.inflate(R.layout.channel, parent, false);
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
