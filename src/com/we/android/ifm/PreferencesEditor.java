package com.we.android.ifm;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PreferencesEditor extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);

	addPreferencesFromResource(R.xml.preferences);
    }
}