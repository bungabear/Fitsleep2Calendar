package com.bungabear.fitsleep2calendar;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks, View.OnClickListener {

    static final String TAG = "Fitsleep2Calendar";
    private ListView listView;
    public Snackbar snackbar;
    private Button mGetFitData, mPutCalendarEvent;
    private CustomListViewAdapter mListViewAdapter;
    private ProgressDialog mProgress;
    private static SharedPreferences sharedPreferences;
    private static SharedPreferences.Editor preferencesEditor;

    // For GoogleFit
    private GoogleApiClient googleApiClient = null;

    // For Google Calendar
    private GoogleAccountCredential mCredential;
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};

    // For AutoSync
    private static final String AUTOSYNC_ACTION = "com.bungabear.fitsleep2calendar.F2CAutoSync";
    private TextView autoSyncStateView;
    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize credentials, client and objects.
        sharedPreferences = getPreferences(MODE_PRIVATE);
        preferencesEditor = sharedPreferences.edit();
        googleApiClient = FitAPITask.buildGoogleFitClient(getApplicationContext());
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        initView();


    }

    // Init. Views
    private void initView() {
        listView = (ListView)findViewById(R.id.listivew);
        mListViewAdapter = new CustomListViewAdapter(this);
        listView.setAdapter(mListViewAdapter);
        snackbar = Snackbar.make(listView, "", Snackbar.LENGTH_SHORT);
        mProgress = new ProgressDialog(this);


        mGetFitData = (Button)findViewById(R.id.get_fitdata);
        mGetFitData.setText("Fit에서 데이터가져오기");
        mGetFitData.setOnClickListener(this);

        mPutCalendarEvent = (Button)findViewById(R.id.sync_calendar);
        mPutCalendarEvent.setText("캘린더로 보내기");
        mPutCalendarEvent.setTag(getString(R.string.call_api));
        mPutCalendarEvent.setOnClickListener(this);

        autoSyncStateView = (TextView)findViewById(R.id.syncText);
        if(sharedPreferences.getBoolean("AutoSync",false)){
            int hour = sharedPreferences.getInt("AutoSyncHour",0), minute = sharedPreferences.getInt("AutoSyncMinute",0);
            String time = String.format("%02d:%02d",hour,minute);
            autoSyncStateView.setText("Sync automatically at " + time + " everyday.");
        } else {
            autoSyncStateView.setText("AutoSync is Disabled");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId()){
            // AutoSync Menu
            case R.id.item0:
                final Context context = this;
                final AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                Intent serviceIntent = new Intent(AUTOSYNC_ACTION);
                serviceIntent.putExtra("accountName", sharedPreferences.getString("accountName",""));
                final PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, serviceIntent, 0);

                TimePickerDialog timePickerDialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        // Check Google Fit and Calendar Account setting.
                        if ((sharedPreferences.getString(PREF_ACCOUNT_NAME, null) != null )|| googleApiClient == null) {

                            // Todo make select dialog to choose auto sync time.
                            Calendar calendar = Calendar.getInstance();
                            int year = calendar.get(Calendar.YEAR);
                            int month = calendar.get(Calendar.MONTH);   // 0~11
                            int day = calendar.get(Calendar.DAY_OF_MONTH);
                            if(calendar.get(Calendar.HOUR_OF_DAY) > hourOfDay){
                                day++;
                            }
                            calendar.set(year,month,day,hourOfDay,minute);
                            alarmManager.setInexactRepeating(AlarmManager.RTC,
                                    calendar.getTimeInMillis(),
                                    AlarmManager.INTERVAL_DAY, pendingIntent);
//                            alarmManager.set(AlarmManager.RTC,
//                                SystemClock.currentThreadTimeMillis()+5000, pendingIntent);
                            String time = String.format("%02d:%02d",hourOfDay,minute);
                            autoSyncStateView.setText("Sync automatically at " + time + " everyday.");
                            Toast.makeText(context, "" + new DateTime(calendar.getTimeInMillis()).toString().substring(0,16).replace('T', ' ') + "부터 매일 동기화가 시작됩니다.", Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "setAlarm : " + new DateTime(calendar.getTimeInMillis()).toString());
                            preferencesEditor.putBoolean("AutoSync", true);
                            preferencesEditor.putInt("AutoSyncHour", hourOfDay);
                            preferencesEditor.putInt("AutoSyncMinute",minute);
                            preferencesEditor.apply();
                        } else {
                            Toast.makeText(context, "설정이 되지 않았습니다. \n수동 동기화를 한번 해주세요", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, 0, 0, false);
                timePickerDialog.setCancelable(false);
                timePickerDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // Cancel Alarm by Finding Intent Action
                        alarmManager.cancel(pendingIntent);
                        Toast.makeText(context, "자동동기화를 해제하였습니다.", Toast.LENGTH_SHORT).show();
                        autoSyncStateView.setText("AutoSync is Disabled");
                        preferencesEditor.putBoolean("AutoSync", false);
                        preferencesEditor.apply();
                    }
                });
                timePickerDialog.show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */


    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            snackbar.setText(R.string.no_network).show();
        } else {
            new CalendarAPITask(mCredential, this, mProgress, snackbar).execute(mListViewAdapter.getEventList());
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
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
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

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */

    // 처음 사용자게에 캘린더 계정 선택 및 권한부여할때 필요한 부분이다.
    // Todo 인증 절차를 파악하고 분기문을 재구성해야한다.

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    snackbar.setText(
                            R.string.google_play_is_not_found).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

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

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.sync_calendar:
                mPutCalendarEvent.setEnabled(false);
                if(mListViewAdapter.getCount() != 0){
                    getResultsFromApi();
                } else {
                    snackbar.setText("유효한 Fit데이터를 먼저 불러와주세요").show();
                }
                mPutCalendarEvent.setEnabled(true);
                break;
            case R.id.get_fitdata:
                mGetFitData.setEnabled(false);

                String syncDays = ((EditText)findViewById(R.id.sync_days)).getText().toString();
                if(!syncDays.equals("")){
                    new FitAPITask(googleApiClient, mProgress, mListViewAdapter).execute(syncDays);
                } else {
                    snackbar.setText("동기화할 일 수를 입력해 주세요").show();
                }

                mGetFitData.setEnabled(true);
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
        Log.d(TAG, "onStart: GoogleFit Client Connected");
    }

    @Override
    protected void onStop() {
        super.onStop();
        googleApiClient.disconnect();
        Log.d(TAG, "onStop: GoogleFit Client Disconnected");
    }
}
