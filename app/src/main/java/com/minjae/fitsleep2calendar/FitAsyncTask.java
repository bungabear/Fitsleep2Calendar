package com.minjae.fitsleep2calendar;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.api.client.util.DateTime;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Minjae on 2017-02-27.
 */

//Google Fit에서 데이터를 받아오는 스레드.
public class FitAsyncTask extends AsyncTask<Object, Object, List<String>> {

    static final String TAG = "F2C-FitAsyncTask";
    private GoogleApiClient mClient;
    private TextView mOutputText;
    private ProgressDialog mProgress;
    public FitAsyncTask(GoogleApiClient client, ProgressDialog progress, TextView outputText) {
        mClient = client;
        mProgress =  progress;
        mOutputText = outputText;
    }

    @Override
    protected List<String> doInBackground(Object... params) {

        //받아올 기간을 설정한다.
        java.util.Calendar cal = java.util.Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        //1주일
        cal.add(java.util.Calendar.YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = DateFormat.getDateInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));

        //DataReadResult를 빌드한다. DataType을 바꿔 다른 정보를 받아 올 수 있음.
        //수면 정보는 ACTIVITY에 속해있다.
        // Aggregate형태와 Segment형태가 서로 받아오는 방식과 결과물이 다르다.
        // Segment는 시간별로 차곡차곡 나오는 반면, Aggrate는 인접한 액티비티를 묶어서 보여주는데,
        // 수면의 경우 깊은잠, 얕은잠 등이 따로 묶여 시간이 겹치고 가독성이 낮다.
        // Todo DB를 이용하여 수면 데이터를 저장하고 Calendar Event용 데이터도 DB에 저장해 비교 동기화해야한다.

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
        List<String>data = new ArrayList<>();
        // 데이터가 Bucket으로 오는경우. Aggregate로 조회했을경우이다.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    data =  dumpDataSet(dataSet);
                }
            }
            // 데이터가 DataSet으로 오는경우. Segment로 조회했을 경우이다.
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                data = dumpDataSet(dataSet);
            }
        }
        return data;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgress.setMessage("Connecting To Google Fit");
        mProgress.show();
    }

    @Override
    protected void onPostExecute(List<String> dataReadResult) {
        super.onPostExecute(dataReadResult);
        for(String str : dataReadResult){
            mOutputText.append(str.replace('T', ' '));
        }
        mProgress.hide();
    }

    // Todo Awake로 수면기록을 나누어 넣을지 사용자에게 물어보아야한다.
    // 액티비티 타입 참고링크 https://developers.google.com/fit/rest/v1/reference/activity-types
    // DataSet안에는 DataPoint들이 들어있음. DataPoint는 type, start, end,가 기본으로 있고, Field항목으로 추가요소가 들어있다.
    // Field에는 액티비티 번호, Aggregateg형태에서는 결합된 액티비티 갯수 등이 나온다.
    private List<String> dumpDataSet(DataSet dataSet) {
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

            DataPoint postDp = dataSet.getDataPoints().get(i+1);
            int postActivity = Integer.parseInt(postDp.getValue(postDp.getDataType().getFields().get(0)).toString());


            // 액티비티가 수면인지 확인해 넣어준다.
            boolean isSleep = (activity == 72 || (activity >= 109 && activity <= 112));
            boolean isNextSleep = (nextActivity == 72 || (nextActivity >= 109 && nextActivity <= 112));
            boolean isPostSleep = (postActivity == 72 || (postActivity >= 109 && postActivity <= 112));

            if(isSleep){
                Log.d(TAG, "dumpDataSet: " + startTime.substring(0,10) + " " + activity + " " + startTime.substring(11,16) + " ~ " + endTime.substring(0,10) + " " + endTime.substring(11,16));
                String postEndTime = new DateTime(postDp.getEndTime(TimeUnit.MILLISECONDS)).toString();
                String nextStartTime = new DateTime(nextDp.getEndTime(TimeUnit.MILLISECONDS)).toString();
                boolean isContinueFromPost = startTime.equals(postEndTime);
                boolean isContinueToNext = startTime.equals(nextStartTime);

                // 전,후 세그먼트에 따라 분기.
                if(isPostSleep & isNextSleep){
                    if(isContinueFromPost & isContinueToNext){
                        // 수면주기의 중간
                    } else if(isContinueFromPost & !isContinueToNext){
                        // 수면주기의 끝
                        data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                    } else if(!isContinueFromPost & isContinueToNext){
                        // 수면주기의 시작
                        data.add(String.format("start : %s \n", startTime.substring(5,16)));
                    } else {
                        // 독립적인 수면
                        data.add(String.format("start : %s \n", startTime.substring(5,16)));
                        data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                    }
                } else if(isPostSleep & !isNextSleep){
                    if(isContinueFromPost) {
                        // 수면주기의 끝
                        data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                    } else {
                        // 독립 수면
                        data.add(String.format("start : %s \n", startTime.substring(5,16)));
                        data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                    }
                    // 뒷 세그먼트가 수면이 아니므로 새로운 수면을 찾아 이동시킴.

                } else if(!isPostSleep & isNextSleep){
                    if(isContinueToNext){
                        // 수면의 시작
                        data.add(String.format("start : %s \n", startTime.substring(5,16)));
                    } else {
                        // 독립 수면
                        data.add(String.format("start : %s \n", startTime.substring(5,16)));
                        data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                    }
                } else {
                    //독립 수면
                    data.add(String.format("start : %s \n", startTime.substring(5,16)));
                    data.add(String.format("end : %s \n\n", endTime.substring(5,16)));
                }
            } else {

            }



                /*
                *   전/후 활동과 비교
                *       전/후 둘다 수면
                *           전/후 시간 비교
                *               전/후 연속
                *                   -수면의 중간, PASS
                *               전만 연속
                *                   -수면의 끝
                *               후만 연속
                *                   -수면의 시작
                *               전/후 불연속
                *                   -독립 수면
                *       전만 수면
                *           전/후 시간비교
                *               전과 연속
                *                   -수면의 끝
                *                   후가 연속/불연속
                *                       -후를 하나 건너뜀.
                *               전과 불연속
                *                   -독립 수면
                *                   후가 연속/불연속
                   *                    -후를 하나 건너뜀
                *
                *       후만 수면
                *           후와 시간비교
                *               후와 연속
                *                   -수면의 시작
                *               후와 불연속
                *                   -독립 수면
                *       둘다 수면아님
                *           - 독립적인 수면
                *
                *
                *      여기서, 전/후 수면을 비교해야하는데, 목록의 끝인경우.
                *       현재 세그먼트가 수면이 아니면 놔두면 되지만,
                *       수면이면 추가로 값을 불러와 수면의 시작/끝까지만 추적? 1일 새벽(전날 저녁)~ 4일 저녁(다음날 오전)이 포함됨.
                *       양쪽으로 1일치씩만 불러오고 불연속을 배제하여 수면주기를 분석.
                *
                *
                *
                 */

//               mOutputText.append(String.format("end : %s \n\n", nextStartTime.substring(5,16)));
        }
        return data;
    }
}
