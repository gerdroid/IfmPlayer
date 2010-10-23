package com.we.android.ifm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


public class IfmSchedule extends ListActivity {

  class ScheduleItem {
    String mTitle;
    Date mFrom;
    Date mTo;
    
    public ScheduleItem(String title, Date from, Date to) {
      mTitle = title;
      mFrom = from;
      mTo = to;
    }
    
    @Override
    public String toString() {
      return mTitle + ": " + mFrom + " - " + mTo;
    }
  }

  public static String IFM_URL = "http://intergalacticfm.com";
  private ArrayList<ScheduleItem> mSchedule;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String content = "";
    try {
      content = getContent(new URL(Constants.IFM_URL));
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    
    getListView().setDividerHeight(3);
    
    List<String> events = extractEvents(matchEventList(content));

    mSchedule = new ArrayList<ScheduleItem>();
    for (String event : events) {
      Date[] dates = extractTime(event);
      String title = extractTitle(event);
      mSchedule.add(new ScheduleItem(title, dates[0], dates[1]));
//      String day = new SimpleDateFormat("EEEE").format(dates[0]);
    }
    
    setListAdapter(new BaseAdapter() {
      
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        View scheduleItemView = LayoutInflater.from(IfmSchedule.this).inflate(R.layout.schedule_item, parent, false);
        TextView timeText = (TextView) scheduleItemView.findViewById(R.id.time);
        Date fromDate = mSchedule.get(position).mFrom;
        Date toDate = mSchedule.get(position).mTo;
        String from = "";
        String to = "";
        if ((fromDate != null) && (toDate != null)) {
          from = new SimpleDateFormat("hh:mm").format(mSchedule.get(position).mFrom);
          to = new SimpleDateFormat("hh:mm").format(mSchedule.get(position).mTo);
        }
        timeText.setText(from + " - " + to);
        ((TextView) scheduleItemView.findViewById(R.id.title)).setText(mSchedule.get(position).mTitle);
        return scheduleItemView;
      }
      
      @Override
      public long getItemId(int position) {
        // TODO Auto-generated method stub
        return 0;
      }
      
      @Override
      public Object getItem(int position) {
        // TODO Auto-generated method stub
        return null;
      }
      
      @Override
      public int getCount() {
        return mSchedule.size();
      }
    });
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

  private String matchEventList(String content) {
    String tag1 = "<div class=\"moduletable-upcoming\">";
    String tag2 = "<div class=\"moduletable-tdi\">";
    Pattern p = Pattern.compile(".*" + tag1 + "(.*?)" + tag2 + ".*");
    Matcher m = p.matcher(content);
    String events = "";
    if (m.matches()) {
      events = m.group(1);
    }
    return events;
  }

  private List<String> extractEvents(String content) {
    String tag1 = "<li class=\"eventlistmod-upcoming\">";
    String tag2 = "</li>";
    Pattern p = Pattern.compile(".*?" + tag1 + "(.*?)" + tag2 + ".*?");
    Matcher m = p.matcher(content);

    List<String> events = new ArrayList<String>();
    while (m.find()) {
      events.add(m.group(1).trim());
    }
    return events;
  }

  private Date[] extractTime(String event) {
    Pattern p = Pattern.compile("(.*)<br.*");
    Matcher m = p.matcher(event);
    String events = "";
    if (m.matches()) {
      events = m.group(1).trim();
    }
    String[] splits = events.split("\\|");
    String dateString = splits[0];
    String[] times = splits[1].split("-");
    String dateFrom = dateString + " " + times[0].trim();
    String dateTo = dateString + " " + times[1].trim();
    
    Date[] dates = new Date[2];
    try {
      dates[0] = new SimpleDateFormat("dd-MM-yyyy hh:mm").parse(dateFrom);
      dates[1] = new SimpleDateFormat("dd-MM-yyyy hh:mm").parse(dateTo); 
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return dates;
  }

  private String extractTitle(String event) {
    Pattern p = Pattern.compile(".*<a.*?>(.*?)</a>");
    Matcher m = p.matcher(event);
    String title = "";
    if (m.matches()) {
      title = m.group(1).trim();
    }
    return title;
  }
}
