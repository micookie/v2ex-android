package com.czbix.v2ex.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;

import com.google.common.base.Preconditions;

public class TopicDao extends DaoBase {
    private static final String TABLE_NAME = "topic";

    private static final String KEY_TOPIC_ID = "topic_id";
    @Deprecated
    private static final String KEY_LAST_READ = "last_read";
    private static final String KEY_LAST_READ_TIME = "last_read_time";
    private static final String KEY_LAST_READ_REPLY = "last_read_reply";

    private static final String SQL_GET_REPLY_BY_TOPIC_ID = SQLiteQueryBuilder.buildQueryString(false,
            TABLE_NAME, new String[]{KEY_LAST_READ_REPLY}, KEY_TOPIC_ID + " = ?", null, null, null, null);

    static void createTable(SQLiteDatabase db) {
        Preconditions.checkState(db.inTransaction());

        String sql = "CREATE TABLE " + TABLE_NAME + "(" +
                KEY_TOPIC_ID + " INTEGER PRIMARY KEY," +
                KEY_LAST_READ_REPLY + " INTEGER NOT NULL," +
                KEY_LAST_READ_TIME + " INTEGER NOT NULL" +
                ")";
        db.execSQL(sql);
    }

    @SuppressWarnings("deprecation")
    static void upgradeTableZero2One(SQLiteDatabase db) {
        Preconditions.checkState(db.inTransaction());
        final String newTableName = TABLE_NAME + "_new";

        String sql = "CREATE TABLE " + newTableName + "(" +
                KEY_TOPIC_ID + " INTEGER PRIMARY KEY," +
                KEY_LAST_READ_REPLY + " INTEGER NOT NULL," +
                KEY_LAST_READ_TIME + " INTEGER NOT NULL" +
                ")";
        db.execSQL(sql);

        // convert data
        sql = String.format("INSERT INTO %s (%s, %s, %s) %s", newTableName, KEY_TOPIC_ID,
                KEY_LAST_READ_REPLY, KEY_LAST_READ_TIME, SQLiteQueryBuilder.buildQueryString(false,
                        TABLE_NAME, new String[]{KEY_TOPIC_ID, KEY_LAST_READ, "0"}, null, null, null,
                        null, null));
        db.execSQL(sql);

        // delete old table
        sql = "DROP TABLE " + TABLE_NAME;
        db.execSQL(sql);

        sql = String.format("ALTER TABLE %s RENAME TO %s", newTableName, TABLE_NAME);
        db.execSQL(sql);
    }

    public static int getLastReadReply(final int topicId) {
        return execute(new SqlOperation<Integer>() {
            @Override
            public Integer execute(SQLiteDatabase db) {
                Cursor cursor = null;
                try {
                    cursor = db.rawQuery(SQL_GET_REPLY_BY_TOPIC_ID, new String[]{Integer.toString(topicId)});
                    if (!cursor.moveToFirst()) {
                        return -1;
                    }

                    return cursor.getInt(0);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }

    public static void setLastReadReply(final int topicId, final int num) {
        Preconditions.checkArgument(num > 0);

        execute(new SqlOperation<Void>() {
            @Override
            public Void execute(SQLiteDatabase db) {
                final ContentValues values = new ContentValues(2);
                values.put(KEY_TOPIC_ID, topicId);
                values.put(KEY_LAST_READ_REPLY, num);
                values.put(KEY_LAST_READ_TIME, System.currentTimeMillis());

                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

                return null;
            }
        }, true);
    }
}