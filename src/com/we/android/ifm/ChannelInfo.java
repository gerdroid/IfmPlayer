package com.we.android.ifm;

import android.graphics.Bitmap;

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