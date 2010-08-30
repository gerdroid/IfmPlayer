package com.we.android.ifm;

import com.we.android.ifm.IPlayerStateListener;

interface IPlayer {
	void play(int channel);
	void stop();	
	void cancel();
	boolean isPlaying();
	boolean isPreparing();
	int getPlayingChannel();
	void registerStateListener(IPlayerStateListener callback);
}