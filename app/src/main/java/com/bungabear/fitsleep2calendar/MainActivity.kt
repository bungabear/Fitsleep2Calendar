package com.bungabear.fitsleep2calendar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference


const val REQUEST_OAUTH_REQUEST_CODE = 1000
const val LOG_TAG = "FitSleep2Calendar"

class MainActivity : AppCompatActivity() {

    private lateinit var adapter : ArrayAdapter<String>
    private lateinit var fitAsyncTask : FitAPIAsyncTask
    private lateinit var preference : SharedPreferences
    private val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preference = PreferenceManager.getDefaultSharedPreferences(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        lv.adapter = adapter

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            requestFitnessPermission()
        } else {
            doAfterOauth()
        }
    }

    fun getAdapter() : ArrayAdapter<String>{
        return adapter
    }

    override fun onBackPressed() {
        if(this::fitAsyncTask.isInitialized){
            if(fitAsyncTask.status == AsyncTask.Status.RUNNING){
                fitAsyncTask.cancel(true)
                return
            }
        }
        super.onBackPressed()
    }

    private fun doAfterOauth(){
        if(preference.getString("email", null) == null ){
            val preferenceEditor = preference.edit()
            val email = GoogleSignIn.getLastSignedInAccount(this)!!.email
            preferenceEditor.putString("email", email)
            preferenceEditor.apply()
        }
        requestCalendarPermission({readFitData()})
    }

    private fun readFitData(){
        fitAsyncTask = FitAPIAsyncTask(WeakReference(this))
        fitAsyncTask.execute()
    }

    private fun requestFitnessPermission(){
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .addExtension(fitnessOptions)
                .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(mGoogleSignInClient.signInIntent, REQUEST_OAUTH_REQUEST_CODE)
    }

    private fun requestCalendarPermission(successCallback : ((String?)->Unit?)){
        // TODO Permission denied action
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken) {

                    }
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if(report.areAllPermissionsGranted()){
                            successCallback.invoke(null)
                        }
                        else {
                            Log.d(LOG_TAG,"Permission denied below")
                            report.deniedPermissionResponses.forEach { Log.d(LOG_TAG, it.permissionName) }
                        }
                    }
                }).check()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                doAfterOauth()
            }
        }
    }
}
