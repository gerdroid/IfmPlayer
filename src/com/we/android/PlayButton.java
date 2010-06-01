package com.we.android;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

public class PlayButton extends ImageButton {
	private boolean mIsPlaying;
	
	
	public PlayButton(Context context) {
		super(context);
		setImageResource(R.drawable.play);
	}

	public PlayButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setImageResource(R.drawable.play);
	}

	public void play() {
		mIsPlaying = true;
		setImageResource(R.drawable.stop);	
	}
	
	public void stop() {
		mIsPlaying = false;
		setImageResource(R.drawable.play);	
	}
	
	public void select() {
		if (mIsPlaying) {
			setImageResource(R.drawable.stop_selected);
		} else {
			setImageResource(R.drawable.play_selected);
		}
	}
	
	public void unSelect() {
		if (mIsPlaying) {
			setImageResource(R.drawable.stop);
		} else {
			setImageResource(R.drawable.play);
		}
	}
}
