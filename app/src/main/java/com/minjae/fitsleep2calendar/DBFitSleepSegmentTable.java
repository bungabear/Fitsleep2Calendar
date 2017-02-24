package com.minjae.fitsleep2calendar;

import android.provider.BaseColumns;

/**
 * Created by Minjae on 2017-02-24.
 *
 * Google Fit의 Activity Segment와 같은 형태로 작성되는 DB이다.
 *
 */
public final class DBFitSleepSegmentTable implements BaseColumns {

        public static final String TABLE_NAME = "fitsleep";

        // Segment의 시간항목과 동일하다.
        // DATETIME, NOT NULL
        // 시작시간, 종료시간끼리 겹치지 않으므로 unique key로 설정한다.
        public static final String START_TIME = "start";
        public static final String END_TIME = "end";

        // Segment의 액티비티 번호를 그대로 담는다.
        // INT, NOT NULL
        public static final String ACATIVITY_NAME = "activity";

        // 상위항목인 Calendar Table의 id를 가리킨다.
        // Segment값을 합산하여 Calendar record를 작성하고 그 ID를 받아온다.
        // 수면데이터를 목록형으로 보여줄때, Join의 형태로 Calendar record하위 항목을 찾기 위해서 등록한다.
        // INT
        public static final String CARENDAR_EVENT_ID = "carendar_event_id";

        public static final String CREATE_TABLE_QUERY =
                "CREATE TABLE" + TABLE_NAME + '('
                        + _ID + "INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + START_TIME + "DATETIME NOT NULL UNIQUE,"
                        + END_TIME + "DATETIME NOT NULL UNIQUE,"
                        + ACATIVITY_NAME + "INTEGER NOT NULL,"
                        + CARENDAR_EVENT_ID + "TEXT)";

        public static final String DELETE_TABLE_QUERY = "DROP TABLE IF EXISTS" + TABLE_NAME;
}



