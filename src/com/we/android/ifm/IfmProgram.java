package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.ListActivity;
import android.os.Bundle;

public class IfmProgram extends ListActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String content;
    try {
      content = getContent(new URL(Constants.IFM_URL));
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  private String getContent(URL url) {
    StringBuilder builder = new StringBuilder();
    try {
      InputStream stream = url.openStream();
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
      String line = null;
      while ((line = bufferedReader.readLine()) != null) {
        builder.append(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return builder.toString();
  }
}
