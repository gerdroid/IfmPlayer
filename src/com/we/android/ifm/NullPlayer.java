package com.we.android.ifm;

public class NullPlayer implements IPlayer {
	@Override
	public void cancel() {
	}

	@Override
	public ChannelInfo[] getChannelInfo() {
		return new ChannelInfo[] { ChannelInfo.NO_INFO, ChannelInfo.NO_INFO, ChannelInfo.NO_INFO, ChannelInfo.NO_INFO };
	}

	@Override
	public int getPlayingChannel() {
		return Constants.NONE;
	}

	@Override
	public boolean isPlaying() {
		return false;
	}

	@Override
	public boolean isPreparing() {
		return false;
	}

	@Override
	public void play(int channel) {
	}

	@Override
	public void registerStateListener(IPlayerStateListener stateListener) {
	}

	@Override
	public void stop() {
	}
}
