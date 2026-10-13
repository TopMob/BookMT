package com.TopMob.bookmt.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.TopMob.bookmt.data.local.BookMTDatabase
import java.io.File

/** Creates a standalone SQLite snapshot from one consistent Room read transaction, including WAL data. */
internal suspend fun BookMTDatabase.snapshot(destination: File) {
    withTransaction {
        val source = openHelper.readableDatabase
        SQLiteDatabase.openOrCreateDatabase(destination, null).use { target ->
            target.beginTransaction()
            try {
                val tables = listOf("books", "bookmarks", "room_master_table")
                tables.forEach { table ->
                    source.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                        check(it.moveToFirst()) { "Missing table: $table" }
                        target.execSQL(it.getString(0))
                    }
                    source.query("SELECT * FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) {
                            val row = ContentValues()
                            for (i in 0 until cursor.columnCount) {
                                val column = cursor.getColumnName(i)
                                when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> row.putNull(column)
                                    Cursor.FIELD_TYPE_INTEGER -> row.put(column, cursor.getLong(i))
                                    Cursor.FIELD_TYPE_FLOAT -> row.put(column, cursor.getDouble(i))
                                    Cursor.FIELD_TYPE_BLOB -> row.put(column, cursor.getBlob(i))
                                    else -> row.put(column, cursor.getString(i))
                                }
                            }
                            target.insertOrThrow(table, null, row)
                        }
                    }
                }
                source.query("SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL AND tbl_name IN ('books','bookmarks')").use {
                    while (it.moveToNext()) target.execSQL(it.getString(0))
                }
                target.version = source.version
                target.setTransactionSuccessful()
            } finally { target.endTransaction() }
        }
    }
}
