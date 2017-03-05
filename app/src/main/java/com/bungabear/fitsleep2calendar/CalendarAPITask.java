package com.bungabear.fitsleep2calendar;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.util.Log;

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
 * 캘린더 API를 이용하는 스레드
 */

/**
 * An asynchronous task that handles the Google Calendar API call.
 * Placing the API calls in their own task ensures the UI stays responsive.
 */
class CalendarAPITask extends AsyncTask<List<Event>, Void, List<String>> {
    private static final String TAG = "F2C-FitAPITask";

    private com.google.api.services.calendar.Calendar mService = null;
    private Exception mLastError = null;
    private Snackbar mSnackbar;
    private ProgressDialog mProgress;
    private MainActivity mActivityCompat;
    private String sleepCalendarID;

    CalendarAPITask(GoogleAccountCredential credential, MainActivity activityCompat, ProgressDialog progressDialog, Snackbar snackbar) {
        mActivityCompat = activityCompat;
        mProgress = progressDialog;
        mSnackbar = snackbar;
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.calendar.Calendar.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("Fitsleep2Calendar")
                .build();
    }

    @Override
    protected List<String> doInBackground(List<Event>... params) {
        List<Event> eventList = params[0];
        //저장된 캘린더 ID를 가져옴.
        SharedPreferences preferences = mActivityCompat.getPreferences(Context.MODE_PRIVATE);
        sleepCalendarID = preferences.getString("sleepCalendarID","");

        try{
            //저장된 캘린더ID가 없으면 찾아온다.
            if(sleepCalendarID.equals("")){
                Log.d(TAG, "CalendarAPITask: saved CalendarID is Empty");
                sleepCalendarID = getCalendarID("Sleep");

                //찾아도 없으면 만든다.
                if (sleepCalendarID == null) {
                    Calendar calendar = new Calendar();
                    calendar.setSummary("Sleep");
                    calendar.setTimeZone("Asia/Seoul");
                    Calendar createdCalendar = mService.calendars().insert(calendar).execute();
                    sleepCalendarID = createdCalendar.getId();
                }
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("sleepCalendarID", sleepCalendarID);
                editor.apply();
                Log.d(TAG, "CalendarAPITask: CalendarID is " + sleepCalendarID);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(int i = 0 ; i < eventList.size() ; i++){
            try {
                // Todo 완전히 똑같은 이벤트는 남기고, 시간이 다른 이벤트만 삭제후 추가 혹은 Update할 수 있도록 수정해주어야한다.
                Log.d(TAG, "doInBackground: Try add Event "+ i + " " + eventList.get(i).getStart().getDateTime().toString() + " ~ " + eventList.get(i).getEnd().getDateTime().toString());

                deleteEventList_InTime(sleepCalendarID, eventList.get(i));
                addEvent(sleepCalendarID, eventList.get(i), false);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    //CalendarList를 받아옴

    private List<String> getCalendarList() throws IOException {
        List<String> list = new ArrayList<>();
        String token = null;
        List<CalendarListEntry> items;
        do{
            CalendarList calendarList = mService.calendarList().list().setPageToken(token).execute();
            items = calendarList.getItems();
            for (CalendarListEntry calendarListEList : items) {
                list.add(String.format("%s : %s", calendarListEList.getSummary(), calendarListEList.getId()));
            }
            token = calendarList.getNextPageToken();
        }while(token != null);
        return list;
    }

    //Summary명으로 캘린더 ID를 받아옴
    private String getCalendarID(String summaryName) throws IOException {
        String pageToken = null;
        do {
            CalendarList calendarList = mService.calendarList().list().setPageToken(pageToken).execute();
            List<CalendarListEntry> items = calendarList.getItems();

            for (CalendarListEntry calendarListEntry : items) {
                if(calendarListEntry.getSummary().equals(summaryName)){
                    return calendarListEntry.getId();
                }
            }
            pageToken = calendarList.getNextPageToken();
        } while (pageToken != null);
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
                    Log.d(TAG, "deleteEvent: success " + gettedEvent.getStart().getDateTime().toString() + " ~ " + gettedEvent.getEnd().getDateTime().toString());
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
        Log.d(TAG, "addEvent: event added " + event.getStart().toString()+ " " + event.getEnd().toString());
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

    //동일한 일정이 존재하는지 체크한다.
    private boolean isEventExist(String calendarID, Event event) throws IOException {
        String eventName = event.getSummary();
        String minTime;
        String maxTime;
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

    //변동 가능한 범위 내에서 동일한 일정을 모두 삭제한다
    public void deleteEventList_InTime(String calendarID, Event event) throws IOException {
        DateTime minTime = event.getStart().getDateTime();
        DateTime maxTime = event.getEnd().getDateTime();
        Events events = mService.events().list(calendarID).setTimeMin(minTime).setTimeMax(maxTime).execute();
        List<Event> items = events.getItems();
        for (Event gettedEvent : items) {
            mService.events().delete(calendarID, gettedEvent.getId()).execute();
            Log.d(TAG, "deleteEventList_InTime : event deleted " + gettedEvent.getStart().toString()+ " " + gettedEvent.getEnd().toString());
        }
    }

    //Event에 이름, 설명, 시작/끝/타임존을 받아옴.
    public static Event makeEvent(String name, String description, String startTime, String endTime, String timeZone) {
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
        List<String> eventStrings = new ArrayList<>();
        //Sleep캘린더 정보를 가져옴
        Events events = mService.events().list("primary")
                .setMaxResults(10)
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
        mProgress.setMessage("Calling Google Calendar API ...");
        mProgress.show();
    }

    @Override
    protected void onPostExecute(List<String> output) {
        mProgress.hide();
//        if (output == null || output.size() == 0) {
//            mSnackbar.setText("No results returned.").show();
//        } else {
//            output.add(0, "Data retrieved using the Google Calendar API:");
//            mSnackbar.setText(TextUtils.join("\n", output)).show();
//        }
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
                mSnackbar.setText("The following error occurred:\n"
                        + mLastError.getMessage()).show();
            }
        } else {
            mSnackbar.setText("Request cancelled.").show();
        }
    }
}