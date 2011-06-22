package com.we.android.ifm;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class IfmSchedule extends ListActivity {

	class ScheduleItem {
		private static final String ZERO_DAY = "";
		private String mTitle;
		private Time mFrom;
		private Time mTo;
		private String mDay = ZERO_DAY;

		public ScheduleItem(String title, Time from, Time to) {
			mTitle = title;
			mFrom = from;
			mTo = to;
		}
		
		public String getStart() {
			return DateFormat.getTimeFormat(getApplicationContext()).format(new Date(mFrom.toMillis(false)));
		}
		
		public String getEnd() {
			return DateFormat.getTimeFormat(getApplicationContext()).format(new Date(mTo.toMillis(false)));
		}
		
		public String getTitle() {
			return mTitle;
		}
		
		/**
		 * Constructor for Seperator in schedule table
		 * @param day
		 */
		public ScheduleItem(String day) {
			mDay = day;
		}
		
		public boolean isSeperator() {
			return mDay != ZERO_DAY;
		}
		
		public String getDay() {
			if (mDay != ZERO_DAY) {
				return mDay;
			}
			String day = DateUtils.getDayOfWeekString(mFrom.weekDay + 1, DateUtils.LENGTH_LONG);
			if (DateUtils.isToday(mFrom.toMillis(false))) {
				day = new String("Today");
			}
			return day;
		}

		@Override
		public String toString() {
			return mTitle + ": " + mFrom + " - " + mTo;
		}
	}

	class ScheduleListAdapter extends BaseAdapter {
		
		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return false;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ScheduleItem item = mSchedule.get(position);
			View itemView = null;
			if (item.isSeperator()) {
				itemView = LayoutInflater.from(IfmSchedule.this).inflate(R.layout.schedule_sep, parent, false);
				TextView textView = (TextView) itemView.findViewById(R.id.schedult_item);
				textView.setText(item.getDay());
				textView.setTextSize(20);
				textView.setTextScaleX(1.4f);
				itemView.setBackgroundResource(R.color.ifm1);
				textView.setTextColor(Color.WHITE);
			} else {
				itemView = LayoutInflater.from(IfmSchedule.this).inflate(R.layout.schedule_item, parent, false);
				String from = mSchedule.get(position).getStart();
				String to = mSchedule.get(position).getEnd();
				Spanned str = Html.fromHtml("<b>" + from + " - " + to + "</b>  " + mSchedule.get(position).getTitle());
				((TextView) itemView.findViewById(R.id.schedult_item)).setText(str);
			}

			return itemView;
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
			JSONArray content = getContent(params[0]);
			List<ScheduleItem> list = new ArrayList<ScheduleItem>();
			if (content != null) {
				list = buildTable(content);
			}
			return list;
		}

		private List<ScheduleItem> buildTable(JSONArray content) {
			List<ScheduleItem> schedule = new ArrayList<ScheduleItem>();
			for (int i=0; i<content.length(); i++) {
				try {
					JSONObject item = content.getJSONObject(i);
					JSONObject date = item.getJSONObject("date");
					String dateStr = date.getString("day");
					Time from = getLocalTime(dateStr, date.getString("start"));
					Time to = getLocalTime(dateStr, date.getString("end"));
					Time now = new Time();
					now.setToNow();
					if (to.after(now)) {
						schedule.add(new ScheduleItem(
								item.getString("title"),
								from, to));
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			
			List<ScheduleItem> newSchedule = new ArrayList<ScheduleItem>();
			String day = "";
			for (ScheduleItem item : schedule) {
				String d = item.getDay();
				if (!d.equals(day)) {
					newSchedule.add(new ScheduleItem(d));
					day = d;
				}
				newSchedule.add(item);
			}
			
			return newSchedule;
		}
		
		private Time getLocalTime(String date, String time) {
			Time t = new Time("CET");
			try {
				Date d = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm").parse(date + "T" + time);
				t.set(0, d.getMinutes(), d.getHours(), d.getDate(), d.getMonth(), d.getYear() + 1900);
				t.set(t.normalize(false));
			} catch (ParseException e) {
				e.printStackTrace();
			}
			t.switchTimezone(Time.getCurrentTimezone());
			return t;
		}

		@Override
		protected void onPostExecute(List<ScheduleItem> result) {
			super.onPostExecute(result);
			mSchedule.clear();
			mSchedule.addAll(result);
			mProgress.cancel();
			mScheduleListAdapter.notifyDataSetChanged();
		}

		private JSONArray getContent(String url) {
			JSONArray content = null;
			HttpGet get = new HttpGet(url);
			try {
				HttpResponse response = mHttpClient.execute(get);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						content = new JSONArray(Util.readString(entity.getContent()));
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
	private DefaultHttpClient mHttpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.schedule);
		
		getListView().setFocusable(false);
		getListView().setDividerHeight(0);

		mProgress = new ProgressDialog(this);
		mProgress.setMessage("Loading Schedule...");
		mProgress.show();

		mHttpClient = new DefaultHttpClient();
		new ScheduleQuery().execute("http://" + Constants.IFM_NODE_URL + ":8080/upcoming");
		setListAdapter(mScheduleListAdapter);
	}

	@Override
	protected void onDestroy() {
		mHttpClient.getConnectionManager().shutdown();
		super.onDestroy();
	}
}
