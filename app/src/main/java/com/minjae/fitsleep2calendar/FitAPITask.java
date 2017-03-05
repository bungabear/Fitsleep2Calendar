package com.minjae.fitsleep2calendar;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Minjae on 2017-02-27.
 * Google Fit에서 데이터를 받아오는 스레드.
 */

class FitAPITask extends AsyncTask<Object, Object, List<Event>> {

    private static final String TAG = "F2C-FitAPITask";
    static private MainActivity activity;
    private GoogleApiClient mClient;
    private CustomListViewAdapter mListData;
    private ProgressDialog mProgress;
    FitAPITask(GoogleApiClient client, ProgressDialog progress, CustomListViewAdapter outputText) {
        mClient = client;
        mProgress =  progress;
        mListData = outputText;
        Log.d(TAG, "FitAPITask: created");
    }

    @Override
    protected List<Event> doInBackground(Object... params) {

        //받아올 기간을 설정한다.
        java.util.Calendar cal = java.util.Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        // Todo 기간 설정부분 만들기
        cal.add(Calendar.WEEK_OF_MONTH, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = DateFormat.getDateInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));

        //DataReadResult를 빌드한다. DataType을 바꿔 다른 정보를 받아 올 수 있음.
        //수면 정보는 ACTIVITY에 속해있다.
        // Aggregate형태와 Segment형태가 서로 받아오는 방식과 결과물이 다르다.
        // Segment는 시간별로 차곡차곡 나오는 반면, Aggrate는 인접한 액티비티를 묶어서 보여주는데,
        // 수면의 경우 깊은잠, 얕은잠 등이 따로 묶여 시간이 겹치고 가독성이 낮다.

        //Segment방식
        DataReadRequest dataReadRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

//            //Aggregate방식
//            DataReadRequest dataReadRequest = new DataReadRequest.Builder()
//                    // The data request can specify multiple data types to return, effectively
//                    // combining multiple data queries into one call.
//                    // In this example, it's very unlikely that the request is for several hundred
//                    // datapoints each consisting of a few steps and a timestamp.  The more likely
//                    // scenario is wanting to see how many steps were walked per day, for 7 days.
//                    .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
//                    // Analogous to a "Group By" in SQL, defines how data should be aggregated.
//                    // bucketByTime allows for a time span, whereas bucketBySession would allow
//                    // bucketing by "sessions", which would need to be defined in code.
//                    .bucketByTime(1, TimeUnit.DAYS)
//                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
//                    .build();


        DataReadResult dataReadResult =
                Fitness.HistoryApi.readData(mClient, dataReadRequest).await(1, TimeUnit.MINUTES);
        List<Event>eventList = new ArrayList<>();
        // 데이터가 Bucket으로 오는경우. Aggregate로 조회했을경우이다.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    eventList =  bindSleepDataset(dataSet);
                }
            }
            // 데이터가 DataSet으로 오는경우. Segment로 조회했을 경우이다.
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                eventList = bindSleepDataset(dataSet);
            }
        }
        return eventList;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgress.setMessage("Connecting To Google Fit");
        mProgress.show();
    }

    @Override
    protected void onPostExecute(List<Event> eventList) {
        super.onPostExecute(eventList);

        activity.setFitAPIResult(eventList);

        // 커스텀 리스트뷰에 반영
        for(int i = 0 ; i < eventList.size() ; i++){
            mListData.addItem(String.valueOf(i), eventList.get(i).getStart().getDateTime().toString(), eventList.get(i).getEnd().getDateTime().toString(), false , null);
        }
        mListData.notifyDataSetChanged();
        mProgress.hide();
    }

    // Todo Awake로 수면기록을 나누어 넣을지 사용자에게 물어보아야한다.
    // 액티비티 타입 참고링크 https://developers.google.com/fit/rest/v1/reference/activity-types
    // DataSet안에는 DataPoint들이 들어있음. DataPoint는 type, start, end,가 기본으로 있고, Field항목으로 추가요소가 들어있다.
    // Field에는 액티비티 번호, Aggregateg형태에서는 결합된 액티비티 갯수 등이 나온다.
    private List<Event> bindSleepDataset(DataSet dataSet) {
        List<String> data = new ArrayList<>();
        for (int i = 1 ; i < dataSet.getDataPoints().size()-1; i++) {

//                시간을 시:분으로 간략화하는 파싱.
//                Date tmpDate = new Date(dp.getStartTime(TimeUnit.MILLISECONDS));
//                String startTime = new SimpleDateFormat("H:mm").format(tmpDate);

            DataPoint dp = dataSet.getDataPoints().get(i);
            int activity = Integer.parseInt(dp.getValue(dp.getDataType().getFields().get(0)).toString());
            String startTime =  new DateTime(dp.getStartTime(TimeUnit.MILLISECONDS)).toString();
            String endTime =  new DateTime(dp.getEndTime(TimeUnit.MILLISECONDS)).toString();

            DataPoint nextDp = dataSet.getDataPoints().get(i+1);
            int nextActivity = Integer.parseInt(nextDp.getValue(nextDp.getDataType().getFields().get(0)).toString());

            DataPoint postDp = dataSet.getDataPoints().get(i-1);
            int postActivity = Integer.parseInt(postDp.getValue(postDp.getDataType().getFields().get(0)).toString());


            // 액티비티가 수면인지 확인해 넣어준다.
            boolean isSleep = (activity == 72 || (activity >= 109 && activity <= 112));
            boolean isNextSleep = (nextActivity == 72 || (nextActivity >= 109 && nextActivity <= 112));
            boolean isPostSleep = (postActivity == 72 || (postActivity >= 109 && postActivity <= 112));

            if(isSleep){
//                Log.d(TAG, "bindSleepActivity: " + startTime.substring(0,10) + " " + activity + " " + startTime.substring(11,16) + " ~ " + endTime.substring(0,10) + " " + endTime.substring(11,16));
                String postEndTime = new DateTime(postDp.getEndTime(TimeUnit.MILLISECONDS)).toString();
                String nextStartTime = new DateTime(nextDp.getStartTime(TimeUnit.MILLISECONDS)).toString();
                boolean isContinueFromPost = startTime.equals(postEndTime);
                boolean isContinueToNext = endTime.equals(nextStartTime);

                // 전,후 세그먼트에 따라 분기.
                if(isPostSleep & isNextSleep){
                    if(isContinueFromPost & isContinueToNext){
                        // 수면주기의 중간
                    } else if(isContinueFromPost & !isContinueToNext){
                        // 수면주기의 끝
                        data.add(String.format("%s : end", endTime));
                    } else if(!isContinueFromPost & isContinueToNext){
                        // 수면주기의 시작
                        data.add(String.format("%s : start", startTime));
                    } else {
                        // 독립적인 수면
                        data.add(String.format("%s : start", startTime));
                        data.add(String.format("%s : end", endTime));
                    }
                } else if(isPostSleep & !isNextSleep){
                    if(isContinueFromPost) {
                        // 수면주기의 끝
                        data.add(String.format("%s : end", endTime));
                    } else {
                        // 독립 수면
                        data.add(String.format("%s : start", startTime));
                        data.add(String.format("%s : end", endTime));
                    }
                    // 뒷 세그먼트가 수면이 아니므로 새로운 수면을 찾아 이동시킴.

                } else if(!isPostSleep & isNextSleep){
                    if(isContinueToNext){
                        // 수면의 시작
                        data.add(String.format("%s : start", startTime));
                    } else {
                        // 독립 수면
                        data.add(String.format("%s : start", startTime));
                        data.add(String.format("%s : end", endTime));
                    }
                } else {
                    //독립 수면
                    data.add(String.format("%s : start", startTime));
                    data.add(String.format("%s : end", endTime));
                }
            }
        }
        // 계산한 데이터를 이벤트 리스트로 저장한다.
        boolean first = true;
        List<Event> eventList = new ArrayList<>();
        for(int i = 0 ; i < data.size()-1 ; i++){
            //첫 값이 End면 삭제
            if(first && data.get(i).substring(32).equals("end")){
                first = false;
                data.remove(0);
            }
            eventList.add(CalendarAPITask.makeEvent("수면", null, data.get(i).substring(0,29), data.get(i+1).substring(0,29), "Asia/Seoul"));
            i++;
        }
        // Todo 캘린더 API를 호출하는곳을 바꿔주어야 할듯하다.
        new CalendarAPITask(activity.mCredential, activity, mProgress, activity.snackbar).execute(eventList);

        return eventList;
    }

    static GoogleApiClient buildGoogleFitClient(final Context mContext, MainActivity mActivity) {
        Log.i(TAG, mContext.getString(R.string.building_google_fit_clinet));
        activity = mActivity;
        GoogleApiClient client = new GoogleApiClient.Builder(mContext)
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
                .enableAutoManage(mActivity, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        Snackbar.make(((Activity)mContext).getWindow().getDecorView().findViewById(R.id.listivew), "Exception while connecting to Google Play services: " + result.getErrorMessage(), Snackbar.LENGTH_SHORT).show();
                    }
                })
                .build();
        return client;
    }


}
