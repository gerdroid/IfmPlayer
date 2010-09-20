package com.we.android.ifm;

public interface IPlayer {
  public void play(final int channel);
  
  public void stop();
  
  public void cancel();

  public int getPlayingChannel();
  
  public boolean isPlaying();
  
  public boolean isPreparing();

  public void registerStateListener(IPlayerStateListener stateListener);
  
  public ChannelInfo[] getChannelInfo();
}
