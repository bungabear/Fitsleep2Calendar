package com.bungabear.fitsleep2calendar

import android.os.AsyncTask
import android.view.View
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

class FitAPIAsyncTask (private val parentRef : WeakReference<MainActivity>) : AsyncTask<Long, Int, ArrayList<SleepEvent>>() {

    //    private val LOG_TAG = "fs2c"
    private val progressRef = WeakReference(parentRef.get()!!.progressLayout)
    private val adapter = parentRef.get()!!.getAdapter()
    private var events  = ArrayList<SleepEvent>()

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

        val client = parentRef.get()!!.getGoogleFitClient()
        client.connect()
        val result = Fitness.HistoryApi.readData(client,dataReadRequest).await()
        client.disconnect()
        events = parseDataPoints(result.dataSets)
        events = bindSleepEvent(events)

        // TODO Drop first element for first and last split sleep event

        return events
    }

    override fun onPostExecute(result: ArrayList<SleepEvent>) {
        result.forEach { adapter.add("${it.getStartAsString()} ~ ${it.getEndAsString()}") }
        adapter.notifyDataSetChanged()
        hideProgressBar()
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
}