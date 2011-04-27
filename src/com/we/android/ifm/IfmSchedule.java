package com.we.android.ifm;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class IfmSchedule extends ListActivity {

	class ScheduleItem {
		public static final String ZERO_DAY = "";

		String mTitle;
		Date mFrom;
		Date mTo;
		String mDay = ZERO_DAY;

		public ScheduleItem(String title, Date from, Date to) {
			mTitle = title;
			mFrom = from;
			mTo = to;
		}

		public ScheduleItem(String day) {
			mDay = day;
		}

		@Override
		public String toString() {
			return mTitle + ": " + mFrom + " - " + mTo;
		}
	}

	class ScheduleListAdapter extends BaseAdapter {
		private int mColorCounter = 0;

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View scheduleItemView = LayoutInflater.from(IfmSchedule.this)
					.inflate(R.layout.schedule_item, parent, false);
			ScheduleItem item = mSchedule.get(position);
			if (item.mDay != ScheduleItem.ZERO_DAY) {
				TextView textView = (TextView) scheduleItemView.findViewById(R.id.schedult_item);
				textView.setText(item.mDay);
				scheduleItemView.setBackgroundColor(mColors.get(mColorCounter % mColors.size()));
				mColorCounter++;
				textView.setTextColor(Color.WHITE);
			} else {
				Date fromDate = mSchedule.get(position).mFrom;
				Date toDate = mSchedule.get(position).mTo;
				String from = "";
				String to = "";
				if ((fromDate != null) && (toDate != null)) {
					from = new SimpleDateFormat("H:mm").format(mSchedule.get(position).mFrom);
					to = new SimpleDateFormat("H:mm").format(mSchedule.get(position).mTo);
				}
				String str = from + " - " + to + "  " + mSchedule.get(position).mTitle;
				((TextView) scheduleItemView.findViewById(R.id.schedult_item)).setText(str);
			}

			return scheduleItemView;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public int getCount() {
			return mSchedule.size();
		}
	}

	class ScheduleQuery extends AsyncTask<String, Void, List<ScheduleItem>> {
		@Override
		protected List<ScheduleItem> doInBackground(String... params) {
			List<String> events = extractEvents(matchEventList(getContent(params[0])));

			List<ScheduleItem> schedule = new ArrayList<ScheduleItem>();
			for (String event : events) {
				Date[] dates = extractTime(event);
				String title = extractTitle(event);
				schedule.add(new ScheduleItem(title, dates[0], dates[1]));
			}

			List<ScheduleItem> superSchedule = new ArrayList<ScheduleItem>();
			String day = "";
			for (ScheduleItem item : schedule) {
				String newDay = day;
				if (item.mFrom != null) {
					newDay = new SimpleDateFormat("EEEE").format(item.mFrom);
				}
				if (!newDay.equals(day)) {
					superSchedule.add(new ScheduleItem(newDay));
					day = newDay;
				}
				superSchedule.add(item);
			}

			return superSchedule;
		}

		@Override
		protected void onPostExecute(List<ScheduleItem> result) {
			mSchedule.addAll(result);
			mProgress.cancel();
			mScheduleListAdapter.notifyDataSetChanged();
			super.onPostExecute(result);
		}

		private String getContent(String url) {
			String content = "";
			HttpGet get = new HttpGet(url);
			try {
				HttpResponse response = mHttpClient.execute(get);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						content = Util.readString(entity.getContent());
					}
				} else {
					Toast.makeText(getApplicationContext(), "Connection Problem", Toast.LENGTH_LONG).show();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return content;
		}
	}

	private List<ScheduleItem> mSchedule = new ArrayList<ScheduleItem>();
	private ProgressDialog mProgress;
	private BaseAdapter mScheduleListAdapter = new ScheduleListAdapter();
	private List<Integer> mColors = new ArrayList<Integer>();
	private DefaultHttpClient mHttpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getListView().setFocusable(false);
		getListView().setBackgroundColor(0xffe0e0e0);
		getListView().setDividerHeight(0);

		mProgress = new ProgressDialog(this);
		mProgress.setMessage("Loading Schedule...");
		mProgress.show();

		mColors.add(getResources().getColor(R.color.ifm1));
		mColors.add(getResources().getColor(R.color.ifm2));
		mColors.add(getResources().getColor(R.color.ifm3));
		mColors.add(getResources().getColor(R.color.ifm4));

		mHttpClient = new DefaultHttpClient();
		new ScheduleQuery().execute(Constants.IFM_URL);
		setListAdapter(mScheduleListAdapter);
	}

	@Override
	protected void onDestroy() {
		mHttpClient.getConnectionManager().shutdown();
		super.onDestroy();
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
		Log.d("IfmPlayer", "--> " + event);
		String dateFrom = dateString + " " + times[0].trim();
		String dateTo = "";
		if (times.length == 2) {
			dateTo = dateString + " " + times[1].trim();
			;
		}

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
