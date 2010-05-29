package com.we.android;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

public class PlayButton extends ImageButton {

	public PlayButton(Context context) {
		super(context);
		setImageResource(R.drawable.play_white);
	}

	public PlayButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setImageResource(R.drawable.play_white);
	}

	public void play() {
		setImageResource(R.drawable.play_red);	
	}
	
	public void stop() {
		setImageResource(R.drawable.play_white);	
	}
	
	public void select() {
		
	}
}
