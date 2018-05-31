package com.bungabear.fitsleep2calendar

import android.os.AsyncTask
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResult
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class FitAPIAsyncTask (private val parentRef : WeakReference<MainActivity>) : AsyncTask<Long, Int, DataReadResult>() {

//    private val LOG_TAG = "fs2c"
    private val progressRef: WeakReference<ProgressBar> = WeakReference(parentRef.get()!!.progress)
    private val adapter : ArrayAdapter<String>? = parentRef.get()!!.getAdapter()

    override fun onPreExecute() {
        val progressBar =  progressRef.get()
        if(progressBar != null){
            progressBar.visibility = View.VISIBLE
            progressBar.animate()
        }
    }

    override fun doInBackground(vararg params: Long?): DataReadResult {

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
        return result
    }

    override fun onCancelled() {
        val progressBar =  progressRef.get()
        if(progressBar != null) {
            progressBar.visibility = View.GONE
        }
    }

    override fun onPostExecute(result: DataReadResult) {
        result.dataSets.forEach {
            ds -> ds.dataPoints.forEach {
                dp-> parseDataPoint(dp)
            }
        }
        adapter!!.notifyDataSetChanged()
        val progressBar =  progressRef.get()
        if(progressBar != null) {
            progressBar.visibility = View.GONE
        }
    }

    private fun parseDataPoint(dp : DataPoint){
        val activityNum : Int = dp.getValue(dp.dataType.fields[0]).asInt()
        if(activityNum == 72 || activityNum in 109..112){
            val dateFormat = DateFormat.getTimeInstance()
            //val type = dp.dataType.name
            val start = dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS))
            val end = dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS))
            val fields : ArrayList<String> = ArrayList()
            for (field in dp.dataType.fields) {
                fields.add(field.name + " : " + dp.getValue(field))
            }
            var fieldStr = ""
            fields.forEach { fieldStr += it + "\n" }
            adapter!!.add("$start ~ $end $fieldStr")
        }
    }

}