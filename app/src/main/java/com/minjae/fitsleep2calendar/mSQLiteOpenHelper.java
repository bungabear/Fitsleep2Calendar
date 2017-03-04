package com.minjae.fitsleep2calendar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Created by Minjae on 2017-02-24.
 * 캘린더의 이벤트를 저장할 DB를 관리.
 */

public class mSQLiteOpenHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Fit2sleepCalendar";

    // TODO DB를 연단위로 끊어 사용하는게 성능에 좋다고 생각하는데, DB가 바뀌는 구간의 경우 데이터 조회가 번거로워진다..
    public mSQLiteOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DBCalendarSleepEvent.CREATE_TABLE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//        db.execSQL(DBFitSleepSegmentTable.DELETE_TABLE_QUERY);
//        db.execSQL(DBCalendarSleepEvent.DELETE_TABLE_QUERY);
//        onCreate(db);
    }
}
