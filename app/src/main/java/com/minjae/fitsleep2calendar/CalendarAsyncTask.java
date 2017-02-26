package com.minjae.fitsleep2calendar;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Minjae on 2017-02-27.
 */

/**
 * An asynchronous task that handles the Google Calendar API call.
 * Placing the API calls in their own task ensures the UI stays responsive.
 */
public class CalendarAsyncTask extends AsyncTask<String, Void, List<String>> {
    static final String TAG = "F2C-FitAsyncTask";
    private com.google.api.services.calendar.Calendar mService = null;
    private Exception mLastError = null;
    private TextView mOutputText;
    private ProgressDialog mProgress;
    private MainActivity mActivityCompat;

    CalendarAsyncTask(GoogleAccountCredential credential, MainActivity activityCompat, ProgressDialog progressDialog, TextView textView) {
        mActivityCompat = activityCompat;
        mProgress = progressDialog;
        mOutputText = textView;
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.calendar.Calendar.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("Fitsleep2Calendar")
                .build();
    }

    /**
     * Background task to call Google Calendar API.
     *
     * @param params no parameters needed for this task.
     */
    @Override
    protected List<String> doInBackground(String... params) {
        try {
            //버튼에서 나오는 태그로 액션 판별
            List<String> list = new ArrayList<>();

            if (params[0].equals("GetCalendarList")) {
                //캘린더 목록 받아오기
                return getCalendarList();
            } else if (params[0].equals("CallAPI")) {
                //캘린더 내용 받아오기(샘플코드)
                return getDataFromApi();
            } else if (params[0].equals("mAddEvent")) {
                //캘린더에 일정 등록
                //Sleep Summary의 캘린더ID를 받아온다.
                String calendarID = getCalendarID("Sleep");
                if (calendarID == null) {
                    //만약 Sleep이라는 Summary의 캘린더가 없으면 만든다.
                    Calendar calendar = new Calendar();
                    calendar.setSummary("Sleep");
                    calendar.setTimeZone("Asia/Seoul");
                    Calendar createdCalendar = mService.calendars().insert(calendar).execute();
                    calendarID = createdCalendar.getId();
                    list.add("Calendar Created : " + calendarID);
                }
                //이벤트 생성
                Event event = makeEvent("sleep", null, "2017-02-23T03:00:00+09:00", "2017-02-23T11:00:00+09:00", "Asia/Seoul");
                //addEvent에 캘린더ID, 이벤트 전달. 이벤트가 중복인지 확인한다
                list.add(addEvent(calendarID, event, true));
                return list;
            } else if (params[0].equals("mDeleteEvent")) {
                Event event = makeEvent("sleep", null, "2017-02-23T03:00:00+09:00", "2017-02-23T11:00:00+09:00", "Asia/Seoul");
                if (deleteEvent(getCalendarID("Sleep"), event)) {
                    list.add(event.getSummary() + event.getStart().toString() + event.getEnd().toString() + " : is deleted");
                } else {
                    list.add("Fail to delete : " + event.getSummary() + event.getStart().toString() + event.getEnd().toString());
                }

                return list;
            }

        } catch (Exception e) {
            mLastError = e;
            cancel(true);
        }
        return null;
    }

    //CalendarList를 받아옴

    private List<String> getCalendarList() throws IOException {
        List<String> list = new ArrayList<>();
        CalendarList calendarList = mService.calendarList().list().execute();
        List<CalendarListEntry> items = calendarList.getItems();
        for (CalendarListEntry calendarListEList : items) {
            list.add(String.format("%s : %s", calendarListEList.getSummary(), calendarListEList.getId()));
        }
        return list;
    }

    //Summary명으로 캘린더 ID를 받아옴
    private String getCalendarID(String summaryName) throws IOException {
        CalendarList calendarList = mService.calendarList().list().execute();
        List<CalendarListEntry> items = calendarList.getItems();
        for (CalendarListEntry calendarListEList : items) {
            if (calendarListEList.getSummary().equals(summaryName)) {
                return calendarListEList.getId();
            }
        }
        return null;
    }

    private boolean deleteEvent(String calendarID, Event event) throws IOException {
        String eventID, token = null;
        List<Event> items;
        if (calendarID == null) calendarID = "primary";
        Events events = mService.events().list(calendarID).execute();
        do {
            items = events.getItems();
            for (Event gettedEvent : items) {
                //이름, 시작/종료가 같은 이벤트가 있는지
                if (gettedEvent.getSummary().equals(event.getSummary())
                        && gettedEvent.getStart().equals(event.getStart())
                        && gettedEvent.getEnd().equals(event.getEnd())) {
                    eventID = gettedEvent.getId();
                    mService.events().delete(calendarID, eventID).execute();
                    Log.d(TAG, "deleteEvent: success");
                    return true;
                }
                token = events.getNextPageToken();
            }
        } while (token != null);
        return false;
    }

    //이벤트를 캘린더에 등록하는 함수. 이벤트의 등록 결과를 String형태로 반환한다.
    //캘린더 ID, 이벤트, 중복 이벤트 체크여부를 받아옴.
    private String addEvent(String calendarID, Event event, boolean duplicationCheck) throws IOException {
        //ID가 없으면 주 캘린더에 등록한다
        if (calendarID == null) calendarID = "primary";

        //중복체크여부와 이벤트의 중복여부를 구분한다.
        if (duplicationCheck && isEventExist(calendarID, event)) {
            //중복 체크를 하고, 일정이 중복될경우
            return String.format("%s : %s~%s isExist", event.getSummary(), event.getStart().getDateTime().toString(), event.getEnd().getDateTime().toString());
        } else {
            //중복되지 않거나 중복체크를 안할경우.
            event = mService.events().insert(calendarID, event).execute();
            return String.format("%s : %s~%s is Success", event.getSummary(), event.getStart().getDateTime().toString(), event.getEnd().getDateTime().toString());
        }
    }

    //같은 일정이 존재하는지 체크.
    private boolean isEventExist(String calendarID, Event event) throws IOException {
        Events events = mService.events().list(calendarID).execute();
        List<Event> items = events.getItems();
        for (Event gettedEvent : items) {
            //이름, 시작/종료가 같은 이벤트가 있는지
            if (gettedEvent.getSummary().equals(event.getSummary())
                    && gettedEvent.getStart().equals(event.getStart())
                    && gettedEvent.getEnd().equals(event.getEnd())) {
                return true;
            }
        }
        return false;
    }

    //Event에 이름, 설명, 시작/끝/타임존을 받아옴.
    private Event makeEvent(String name, String description, String startTime, String endTime, String timeZone) {
        Event event = new Event().setSummary(name).setDescription(description);
        DateTime startDateTime = new DateTime(startTime);
        event.setStart(new EventDateTime().setDateTime(startDateTime).setTimeZone(timeZone));
        DateTime endDateTime = new DateTime(endTime);
        event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone(timeZone));

        return event;
    }

    /**
     * Fetch a list of the next 10 events from the primary calendar.
     *
     * @return List of Strings describing returned events.
     * @throws IOException
     */
    private List<String> getDataFromApi() throws IOException {
        // List the next 10 events from the primary calendar.
//            DateTime now = new DateTime(System.currentTimeMillis());
        DateTime now = new DateTime("2017-02-01T00:00:00+09:00");
        List<String> eventStrings = new ArrayList<String>();
        //Sleep캘린더 정보를 가져옴
        Events events = mService.events().list("primary")
                .setMaxResults(100)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        List<Event> items = events.getItems();

        for (Event event : items) {
            DateTime start = event.getStart().getDateTime();
            if (start == null) {
                // All-day events don't have start times, so just use
                // the start date.
                start = event.getStart().getDate();
            }
            eventStrings.add(
                    String.format("%s (%s)", event.getSummary(), start));
        }
        return eventStrings;
    }


    @Override
    protected void onPreExecute() {
        mOutputText.setText("");
        mProgress.setMessage("Calling Google Calendar API ...");
        mProgress.show();
    }

    @Override
    protected void onPostExecute(List<String> output) {
        mProgress.hide();
        if (output == null || output.size() == 0) {
            mOutputText.setText("No results returned.");
        } else {
            output.add(0, "Data retrieved using the Google Calendar API:");
            mOutputText.setText(TextUtils.join("\n", output));
        }
    }

    @Override
    protected void onCancelled() {
        mProgress.hide();
        if (mLastError != null) {
            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                mActivityCompat.showGooglePlayServicesAvailabilityErrorDialog(
                        ((GooglePlayServicesAvailabilityIOException) mLastError)
                                .getConnectionStatusCode());
            } else if (mLastError instanceof UserRecoverableAuthIOException) {
                mActivityCompat.startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        MainActivity.REQUEST_AUTHORIZATION);
            } else {
                mOutputText.setText("The following error occurred:\n"
                        + mLastError.getMessage());
            }
        } else {
            mOutputText.setText("Request cancelled.");
        }
    }
}