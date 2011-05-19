package com.we.android.ifm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

public class LevelAnimation extends ImageView implements Runnable {
	private Random mRandom = new Random();
	private int mMaxLevel;
	private int mLevel;
	private boolean mRising;
	private static List<Drawable> mPeaks;

	public LevelAnimation(Context context, AttributeSet set) {
		super(context, set);
		if (mPeaks == null) {
			mPeaks = new ArrayList<Drawable>();
			mPeaks.add(context.getResources().getDrawable(R.drawable.peak_1));
			mPeaks.add(context.getResources().getDrawable(R.drawable.peak_2));
			mPeaks.add(context.getResources().getDrawable(R.drawable.peak_3));
			mPeaks.add(context.getResources().getDrawable(R.drawable.peak_4));
			mPeaks.add(context.getResources().getDrawable(R.drawable.peak_5));
		}
		mRising = true;
		mLevel = 0;
		setImageResource(R.drawable.peak_1);
	}
	
	public void start() {
		removeCallbacks(this);
		post(this);
	}
	
	public void stop() {
		removeCallbacks(this);
	}
	
	@Override
	public void run() {
		if (getVisibility() == View.INVISIBLE) {
			stop();
			return;
		}
		
		if (mLevel == 0) {
			mMaxLevel = mRandom.nextInt(4) + 1;
			mRising = true;
		}
		setBackgroundDrawable(mPeaks.get(mLevel));
		if (mRising) {
			if (mLevel == mMaxLevel) {
				mRising = false;
				mLevel--;
				postDelayed(this, 100);
			} else {
				mLevel++;
				postDelayed(this, 100);
			}
		} else {
			if (mLevel > 0) {
				mLevel--;
				postDelayed(this, 100);
			}
		}
	}
}
