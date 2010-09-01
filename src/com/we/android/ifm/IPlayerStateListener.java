package com.we.android.ifm;

interface IPlayerStateListener {
	void onChannelStarted(int channel);
	void onChannelError();
}