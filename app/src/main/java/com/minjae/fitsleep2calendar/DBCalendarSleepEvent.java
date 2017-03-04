package com.minjae.fitsleep2calendar;

import android.provider.BaseColumns;

/**
 * Created by Minjae on 2017-02-24.
 *
 * 캘린더 동기화용으로, 캘린더 Event항목과 같은 형태로 작성되는 테이블이다.
 *
 */

public final class DBCalendarSleepEvent implements BaseColumns {
    public static final String TABLE_NAME = "calendarsleep";

    // Event의 시간항목과 동일하다.
    // DATETIME, NOT NULL
    // 시작시간, 종료시간끼리 겹치지 않으므로 unique key로 설정한다.
    public static final String START_TIME = "start";
    public static final String END_TIME = "end";

    // Event의 Start와 End에 있는 Timezone과 동일하다.
    // Event생성시에 필요하지만 DB에 굳이 넣어야 하는지 모호하다.
    // TEXT, NOT NULL
    public static final String TIMEZONE = "timezone";

    // Calendar의 Event를 저장한다. Event가 Calendar에 등록되어 EventID가 부여되어야 값을 넣을 수 있다..
    // TEXT
    public static final String EVENT_ID = "eventID";

    // 동기화에서 제외되는 항목 여부를 저장한다.
    // Boolean
    // Default = false
    public static final String IS_EXCLUDE = "is_exclude";

    public static final String CREATE_TABLE_QUERY =
            "CREATE TABLE" + TABLE_NAME + '('
                    + _ID + "INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + START_TIME + "DATETIME NOT NULL UNIQUE,"
                    + END_TIME + "DATETIME NOT NULL UNIQUE,"
                    + TIMEZONE + "TEXT,"
                    + EVENT_ID + "TEXT,"
                    + IS_EXCLUDE + "BOOLEAN DEFAULT FALSE)";

    public static final String DELETE_TABLE_QUERY = "DROP TABLE IF EXISTS" + TABLE_NAME;
}

