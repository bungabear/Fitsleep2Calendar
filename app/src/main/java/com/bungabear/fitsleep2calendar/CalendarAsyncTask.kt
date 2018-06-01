package com.bungabear.fitsleep2calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.provider.CalendarContract.Calendars
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.lang.ref.WeakReference

private const val PROJECTION_ID_INDEX = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX = 1
private const val PROJECTION_DISPLAY_NAME_INDEX = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX = 3

class CalendarAsyncTask constructor(private val contextRef : WeakReference<MainActivity>) : AsyncTask<Void, Void, Void>(){

    private val progressRef = WeakReference(contextRef.get()!!.progressLayout)
    private val adapter = contextRef.get()!!.getAdapter()
    private val preference = PreferenceManager.getDefaultSharedPreferences(contextRef.get())
    private val eventProjection = arrayOf(
            Calendars._ID, // 0
            Calendars.ACCOUNT_NAME, // 1
            Calendars.CALENDAR_DISPLAY_NAME, // 2
            Calendars.OWNER_ACCOUNT                  // 3
    )

    val contentResolver = contextRef.get()!!.contentResolver

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

    override fun onPreExecute() {
        showProgressBar()
    }

    override fun doInBackground(vararg p0: Void?): Void? {
        val email = preference.getString("email", GoogleSignIn.getLastSignedInAccount(contextRef.get())!!.email)
//        Log.d(LOG_TAG, email)
        val uri = Calendars.CONTENT_URI
        val selection = "(${Calendars.CALENDAR_DISPLAY_NAME} = ?) AND (${Calendars.ACCOUNT_NAME} = ?)"
        val selectionArgs = arrayOf("Sleep", email)
        if (ActivityCompat.checkSelfPermission(contextRef.get() as Context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG, "No calendar permission")
            return null
        }
        val cur = contentResolver.query(uri, eventProjection, selection, selectionArgs, null)
        while (cur.moveToNext()) {
            val calID = cur.getLong(PROJECTION_ID_INDEX)
            val displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
            val accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
            val ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
            Log.d(LOG_TAG, "$calID $displayName $accountName $ownerName")
            // TODO if Sleep calendar not found, add it
        }
        cur.close()
        return null
    }

    override fun onPostExecute(result: Void?) {
        adapter.notifyDataSetChanged()
        hideProgressBar()
    }

    override fun onCancelled() {
        hideProgressBar()
    }
}