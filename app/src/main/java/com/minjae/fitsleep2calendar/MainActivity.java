package com.minjae.fitsleep2calendar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.CalendarScopes;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;
import com.google.api.services.calendar.model.Calendar;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static java.text.DateFormat.getTimeInstance;

//Todo 두개의 AsyncTask를 통합해야하는지 고려해봐야한다.
public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton, mGetCalendarList, mAddEvent, mGetFitData;
    private PendingResult<DataReadResult> pendingResult = null;
    private GoogleApiClient client = null;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final String TAG = "Fitsleep2Calendar";

    private static final String BUTTON_TEXT = "Call Google Calendar API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());



    }

    //ActivityView 초기화
    private void initView(){
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setTag("CallAPI");
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi((String)v.getTag());
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mGetCalendarList = new Button(this);
        mGetCalendarList.setText("GetCalendarList");
        mGetCalendarList.setTag("GetCalendarList");
        mGetCalendarList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGetCalendarList.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi((String)v.getTag());
                mGetCalendarList.setEnabled(true);
            }
        });
        activityLayout.addView(mGetCalendarList);

        mGetFitData = new Button(this);
        mGetFitData.setText("GetFitData");
        mGetFitData.setTag("GetFitData");
        mGetFitData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGetFitData.setEnabled(false);
                mOutputText.setText("");
                if(client == null){
                    connectGoogleFit();
                }
                new FitData().execute();
                mGetFitData.setEnabled(true);
            }
        });
        activityLayout.addView(mGetFitData);

        mAddEvent = new Button(this);
        mAddEvent.setText("Add Event");
        mAddEvent.setTag("mAddEvent");
        mAddEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAddEvent.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi((String)v.getTag());
                mAddEvent.setEnabled(true);
            }
        });
        activityLayout.addView(mAddEvent);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT + "\' button to test the API.");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        setContentView(activityLayout);
    }
    private void connectGoogleFit(){

        client = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Fitness.HISTORY_API).addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.

                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i
                                        == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG,
                                            "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        Toast.makeText(getApplicationContext(), "Exception while connecting to Google Play services: " + result.getErrorMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .build();
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi(String tag) {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount(tag);
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute(tag);
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount(String tag) {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi(tag);
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

//    /**
//     * Called when an activity launched here (specifically, AccountPicker
//     * and authorization) exits, giving you the requestCode you started it with,
//     * the resultCode it returned, and any additional data from it.
//     *
//     * @param requestCode code indicating which activity result is incoming.
//     * @param resultCode  code indicating the result of the incoming
//     *                    activity result.
//     * @param data        Intent (containing result data) returned by incoming
//     *                    activity result.
//     */
//    @Override
//    protected void onActivityResult(
//            int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        switch (requestCode) {
//            case REQUEST_GOOGLE_PLAY_SERVICES:
//                if (resultCode != RESULT_OK) {
//                    mOutputText.setText(
//                            "This app requires Google Play Services. Please install " +
//                                    "Google Play Services on your device and relaunch this app.");
//                } else {
//                    getResultsFromApi("CallAPI");
//                }
//                break;
//            case REQUEST_ACCOUNT_PICKER:
//                if (resultCode == RESULT_OK && data != null &&
//                        data.getExtras() != null) {
//                    String accountName =
//                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
//                    if (accountName != null) {
//                        SharedPreferences settings =
//                                getPreferences(Context.MODE_PRIVATE);
//                        SharedPreferences.Editor editor = settings.edit();
//                        editor.putString(PREF_ACCOUNT_NAME, accountName);
//                        editor.apply();
//                        mCredential.setSelectedAccountName(accountName);
//                        getResultsFromApi("CallAPI");
//                    }
//                }
//                break;
//            case REQUEST_AUTHORIZATION:
//                if (resultCode == RESULT_OK) {
//                    getResultsFromApi("CallAPI");
//                }
//                break;
//            case REQUEST_CALENDAR_LIST:
//                    getResultsFromApi("getCalendarList");
//                break;
//        }
//    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<String, Void, List<String>> {
        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
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

                if(params[0].equals("GetCalendarList")){
                    //캘린더 목록 받아오기
                    return getCalendarList();
                } else if(params[0].equals("CallAPI"))
                {
                    //캘린더 내용 받아오기(샘플코드)
                    return getDataFromApi();
                } else {
                    //캘린더에 일정 등록
                    //Sleep Summary의 캘린더ID를 받아온다.
                    String calendarID = getCalendarID("Sleep");
                    if(calendarID == null) {
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
                }

            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        //CalendarList를 받아옴
        //Todo, 토큰을 이용해 여러개 받아올 수 있도록 수정 요망.
        private List<String> getCalendarList() throws IOException {
            List<String> list = new ArrayList<>();
            CalendarList calendarList =  mService.calendarList().list().execute();
            List<CalendarListEntry> items = calendarList.getItems();
            for(CalendarListEntry calendarListEList : items){
                list.add(String.format("%s : %s",calendarListEList.getSummary(), calendarListEList.getId()));
            }
            return list;
        }

        //Summary명으로 캘린더 ID를 받아옴
        private String getCalendarID(String summaryName) throws IOException{
            CalendarList calendarList =  mService.calendarList().list().execute();
            List<CalendarListEntry> items = calendarList.getItems();
            for(CalendarListEntry calendarListEList : items){
                if (calendarListEList.getSummary().equals(summaryName)){
                    return calendarListEList.getId();
                }
            }
            return null;
        }

        //이벤트를 캘린더에 등록하는 함수. 이벤트의 등록 결과를 String형태로 반환한다.
        //캘린더 ID, 이벤트, 중복 이벤트 체크여부를 받아옴.
        private String addEvent(String calendarID, Event event, boolean duplicationCheck) throws IOException {
            //ID가 없으면 주 캘린더에 등록한다
            if (calendarID == null) calendarID = "primary";

            //중복체크여부와 이벤트의 중복여부를 구분한다.
            if (duplicationCheck && isEventExist(calendarID,event)){
                //중복 체크를 하고, 일정이 중복될경우
                return String.format("%s : %s~%s isExist",event.getSummary(), event.getStart().getDateTime().toString(), event.getEnd().getDateTime().toString());
            } else {
                //중복되지 않거나 중복체크를 안할경우.
                event = mService.events().insert(calendarID, event).execute();
                return String.format("%s : %s~%s is Success",event.getSummary(), event.getStart().getDateTime().toString(), event.getEnd().getDateTime().toString());
            }
        }

        //같은 일정이 존재하는지 체크.
        private boolean isEventExist(String calendarID, Event event) throws IOException{
            Events events = mService.events().list(calendarID).execute();
            List<Event> items = events.getItems();
            for(Event gettedEvent : items){
                //이름, 시작/종료가 같은 이벤트가 있는지
                if(gettedEvent.getSummary().equals(event.getSummary())
                        && gettedEvent.getStart().equals(event.getStart())
                        && gettedEvent.getEnd().equals(event.getEnd())){
                    return true;
                }
            }
            return false;
        }

        //Event에 이름, 설명, 시작/끝/타임존을 받아옴.
        private Event makeEvent(String name, String description, String startTime, String endTime, String timeZone){
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
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
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
    //Google Fit에서 데이터를 받아오는 스레드.
    private class FitData extends AsyncTask<Void,Void,DataSet>{

        @Override
        protected DataSet doInBackground(Void... params) {

            //받아올 기간을 설정한다.
            java.util.Calendar cal = java.util.Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long endTime = cal.getTimeInMillis();
            //1주일
            cal.add(java.util.Calendar.WEEK_OF_YEAR, -1);
            long startTime = cal.getTimeInMillis();

            java.text.DateFormat dateFormat = DateFormat.getDateInstance();
            Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
            Log.i(TAG, "Range End: " + dateFormat.format(endTime));

            //DataReadResult를 빌드한다. DataType을 바꿔 다른 정보를 받아 올 수 있음.
            //수면 정보는 ACTIVITY에 속해있다.
            //Todo ACTIVITY를 받아오는 플래그가 몇가지 있어서 테스트 요망.
            //Segment면 충분하긴하다.
            PendingResult<DataReadResult> pendingResult = Fitness.HistoryApi.readData(
                    client,
                    new DataReadRequest.Builder()
                            .read(DataType.TYPE_ACTIVITY_SEGMENT)
                            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                            .build());

            DataReadResult readDataResult = pendingResult.await();
            DataSet dataSet = readDataResult.getDataSet(DataType.TYPE_ACTIVITY_SEGMENT);
            return dataSet;
        }

        @Override
        protected void onPostExecute(DataSet dataReadResult) {
            super.onPostExecute(dataReadResult);
            dumpDataSet(dataReadResult);
        }

        //데이터셋을 출력.
        //Todo 액티비티 타입을 구분하고, 통합 기능 추가해야한다. 통합시 Awake에 대한 별도 처리가 필요할듯 하다.
        //Sleeping : 72, Light Sleep : 109, Deep Sleep : 110, REM Sellp : 111, Awake(during sleep) : 112
        //액티비티 타입 참고링크 https://developers.google.com/fit/rest/v1/reference/activity-types
        private void dumpDataSet(DataSet dataSet) {
            mOutputText.append("Data returned for Data type: " + dataSet.getDataType().getName() + "\n");
            DateFormat dateFormat = getTimeInstance();

            for (DataPoint dp : dataSet.getDataPoints()) {
                mOutputText.append("Data point:");
                mOutputText.append("\tType: " + dp.getDataType().getName() + "\n");
                mOutputText.append("\tStart: " + new Date(dp.getStartTime(TimeUnit.MILLISECONDS)) + "\n");
                mOutputText.append("\tEnd: " + new Date(dp.getEndTime(TimeUnit.MILLISECONDS)) + "\n");
                for(Field field : dp.getDataType().getFields()) {
                    mOutputText.append("\tField: " + field.getName() +
                            " Value: " + dp.getValue(field) + "\n");
                }
            }
        }
    }
}

