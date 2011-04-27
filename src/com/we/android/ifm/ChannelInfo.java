package com.we.android.ifm;

import android.net.Uri;

class ChannelInfo {
	private String mArtist;
	private String mLabel;
	private Uri mCoverUri;
	public static final ChannelInfo NO_INFO = new ChannelInfo("", "", Uri.EMPTY);

	public ChannelInfo(String artist, String label, Uri coverUri) {
		mArtist = artist;
		mLabel = label;
		mCoverUri = coverUri;
	}

	public String getArtist() {
		return mArtist;
	}

	public String getLabel() {
		return mLabel;
	}

	public Uri getCoverUri() {
		return mCoverUri;
	}

	@Override
	public String toString() {
		return "artist: " + mArtist + " label: " + mLabel;
	}
}