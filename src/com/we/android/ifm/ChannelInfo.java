package com.we.android.ifm;

import java.net.MalformedURLException;
import java.net.URL;

class ChannelInfo {
  private String mArtist;
  private String mLabel;
  private URL mCoverUrl;

  public ChannelInfo(String artist, String label, URL coverUrl) {
    mArtist = artist;
    mLabel = label;
    mCoverUrl = coverUrl;
  }
  
  public ChannelInfo() {
    mArtist = "";
    mLabel = "";
    try {
      mCoverUrl = new URL("");
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  public String getArtist() {
    return mArtist;
  }

  public String getLabel() {
    return mLabel;
  }

  public URL getCoverUrl() {
    return mCoverUrl;
  }

  @Override
  public String toString() {
    return "artist: " + mArtist + " label: " + mLabel;
  }
}