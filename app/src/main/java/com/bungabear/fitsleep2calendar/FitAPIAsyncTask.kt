package com.bungabear.fitsleep2calendar

import android.content.Context
import android.os.AsyncTask
import android.view.View
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class FitAPIAsyncTask (private val contextRef : WeakReference<MainActivity>) : AsyncTask<Long, Int, ArrayList<SleepEvent>>() {

    //    private val LOG_TAG = "fs2c"
    private val progressRef = WeakReference(contextRef.get()!!.progressLayout)
    private val adapter = contextRef.get()!!.getAdapter()
    private var events  = ArrayList<SleepEvent>()
    private lateinit var fitApiClient : GoogleApiClient

    override fun onPreExecute() {
        showProgressBar()
    }

    override fun doInBackground(vararg params: Long?): ArrayList<SleepEvent> {

        val cal = Calendar.getInstance()
        val now = Date()
        cal.time = now
        val endTime = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -1)
        val startTime = cal.timeInMillis

        val dataReadRequest = DataReadRequest.Builder()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()
        fitApiClient = getGoogleFitClient()
        fitApiClient.connect()
        val result = Fitness.HistoryApi.readData(fitApiClient,dataReadRequest).await()
        fitApiClient.disconnect()
        events = parseDataPoints(result.dataSets)
        events = bindSleepEvent(events)

        // TODO Drop first and last element that split event by search time

        return events
    }

    override fun onPostExecute(result: ArrayList<SleepEvent>) {
        result.forEach { adapter.add("${it.getStartAsString()} ~ ${it.getEndAsString()}") }
        adapter.notifyDataSetChanged()
        hideProgressBar()
        val calendarAsyncTask = CalendarAsyncTask(contextRef)
        calendarAsyncTask.execute()
    }

    override fun onCancelled() {
        hideProgressBar()
    }

    private fun showProgressBar(){
        val progressLayout =  progressRef.get()
        if(progressLayout != null) {
            progressLayout.visibility = View.VISIBLE
            progressLayout.progress.animate()
        }
    }

    private fun hideProgressBar(){
        val progressLayout =  progressRef.get()
        if(progressLayout != null) {
            progressLayout.visibility = View.GONE
        }
    }

    private fun parseDataPoints(dss : List<DataSet>) : ArrayList<SleepEvent>{
        val list = ArrayList<SleepEvent>()
        dss.forEach {
            ds -> ds.dataPoints.forEach {
            dp->
            val activityNum : Int = dp.getValue(dp.dataType.fields[0]).asInt()
            if(activityNum == 72 || activityNum in 109..112){
                val start = dp.getStartTime(TimeUnit.MILLISECONDS)
                val end = dp.getEndTime(TimeUnit.MILLISECONDS)
                val fields : ArrayList<String> = ArrayList()
                for (field in dp.dataType.fields) {
                    fields.add(field.name + " : " + dp.getValue(field))
                }
                var fieldStr = ""
                fields.forEach { fieldStr += it + "\n" }
                list.add(SleepEvent(start, end))
            }
        }
        }
        return list
    }

    private fun bindSleepEvent(events : ArrayList<SleepEvent>) : ArrayList<SleepEvent>{
        val bindThreshold = 1000*60*10
        val bindEvents = ArrayList<SleepEvent>()

        events.forEach { event->
            if(bindEvents.count() == 0){
                bindEvents.add(event)
            }
            else {
                if(event.start - bindEvents.last().end <= bindThreshold){
                    bindEvents.last().end = event.end
                }
                else {
                    bindEvents.add(event)
                }
            }
        }
        return bindEvents
    }

    private fun getGoogleFitClient(): GoogleApiClient {
            return GoogleApiClient.Builder(contextRef.get() as Context)
                    .addApi(Fitness.HISTORY_API).addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
                    // TODO Connection error feedback
//                    .addConnectionCallbacks(
//                            object : GoogleApiClient.ConnectionCallbacks {
//                                override fun onConnected(bundle: Bundle?) {
//                                      Log.i(LOG_TAG, "Connected!!!")
//                                }
//
//                                override fun onConnectionSuspended(i: Int) {
//
//                                    // If your connection to the sensor gets lost at some point,
//                                    // you'll be able to determine the reason and react to it here.
//                                    if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
//                                        Log.i(LOG_TAG, "Connection lost.  Cause: Network Lost.")
//                                    } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
//                                        Log.i(LOG_TAG,
//                                                "Connection lost.  Reason: Service Disconnected")
//                                    }
//                                }
//                            }
//                    )
                    .build()
    }
}