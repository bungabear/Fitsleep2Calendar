package com.bungabear.fitsleep2calendar;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Minjae on 2017-03-12.
 */

public class AutoSync extends BroadcastReceiver {

    private GoogleApiClient client;
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};
    static final String TAG = "F2C-AutoSync";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String name = intent.getAction();
        if(name.equals("com.bungabear.fitsleep2calendar.F2CAutoSync")){
            client = FitAPITask.buildGoogleFitClient(context);
            String accountName = intent.getStringExtra("accountName");
            if(!accountName.equals("")){
                final GoogleAccountCredential mCredential = GoogleAccountCredential.usingOAuth2(
                        context, Arrays.asList(SCOPES))
                        .setBackOff(new ExponentialBackOff());
                mCredential.setSelectedAccountName(accountName);

                client.connect();
                Log.d(TAG, "onReceive: GoogleFit Client Connected");

                new FitAPITask(client, new FitAPITask.AsyncResponse(){

                    @Override
                    public void processFinish(List<Event> eventList) {
                        Log.d(TAG, "onReceive: event count " + eventList.size());
                        if(eventList.size() > 0) {
                            new CalendarAPITask(mCredential, context).execute(eventList);
                            client.disconnect();
                            Log.d(TAG, "onReceive: GoogleFit Client Disconnected");
                            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                            NotificationCompat.Builder notification = new NotificationCompat.Builder(context);
                            notification.setSmallIcon(R.mipmap.ic_launcher);
                            notification.setTicker("FitSleep2Calendar에서 동기화수행");
                            notification.setContentTitle("FitSleep2Calendar 자동 동기화");
                            notification.setContentText(eventList.size() + "개의 이벤트가 동기화 되었습니다.");
                            nm.notify(1,notification.build());
                        }
                    }
                }).execute("1");
            } else {
                Log.d(TAG, "onReceive: No Account Data in SharedPreferences");
            }
            
        }
    }
}
