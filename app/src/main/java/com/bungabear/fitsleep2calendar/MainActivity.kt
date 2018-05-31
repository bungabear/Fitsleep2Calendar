package com.bungabear.fitsleep2calendar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference

const val REQUEST_OAUTH_REQUEST_CODE = 1000
const val LOG_TAG = "FitSleep2Calendar"

class MainActivity : AppCompatActivity() {

    private lateinit var adapter : ArrayAdapter<String>
    private lateinit var fitApiClient : GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        lv.adapter = adapter
        val fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
                .build()
        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    REQUEST_OAUTH_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions)
        } else {
            readData()
        }
    }

    fun getGoogleFitClient(): GoogleApiClient {

        return if (this::fitApiClient.isInitialized) {
            fitApiClient
        } else {
            GoogleApiClient.Builder(this)
                    .addApi(Fitness.HISTORY_API).addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
                    .addConnectionCallbacks(
                            object : GoogleApiClient.ConnectionCallbacks {
                                override fun onConnected(bundle: Bundle?) {
                                    Log.i(LOG_TAG, "Connected!!!")
                                }

                                override fun onConnectionSuspended(i: Int) {
                                    // If your connection to the sensor gets lost at some point,
                                    // you'll be able to determine the reason and react to it here.
                                    if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                        Log.i(LOG_TAG, "Connection lost.  Cause: Network Lost.")
                                    } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                        Log.i(LOG_TAG,
                                                "Connection lost.  Reason: Service Disconnected")
                                    }
                                }
                            }
                    )
                    .build()
        }
    }

    fun getAdapter() : ArrayAdapter<String>{
        return adapter
    }

    private fun readData(){
        val fitAsyncTask = FitAPIAsyncTask(WeakReference(this))
        fitAsyncTask.execute()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                readData()
            }
        }
    }
}
