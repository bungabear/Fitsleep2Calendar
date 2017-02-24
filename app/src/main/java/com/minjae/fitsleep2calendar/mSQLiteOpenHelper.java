package com.minjae.fitsleep2calendar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Created by Minjae on 2017-02-24.
 */

public class mSQLiteOpenHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Fit2sleepCalendar";


    public mSQLiteOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DBFitSleepSegmentTable.CREATE_TABLE_QUERY);
        db.execSQL(DBCalendarSleepEvent.CREATE_TABLE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//        db.execSQL(DBFitSleepSegmentTable.DELETE_TABLE_QUERY);
//        db.execSQL(DBCalendarSleepEvent.DELETE_TABLE_QUERY);
//        onCreate(db);
    }
}
