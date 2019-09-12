/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// We make some strong assumptions about the databases we manipulate.
// We maintain a single table containing expressions, their indices in the sequence of
// expressions, and some data associated with each expression.
// All indices are used, except for a small gap around zero.  New rows are added
// either just below the current minimum (negative) index, or just above the current
// maximum index. Currently no rows are deleted unless we clear the whole table.

// TODO: Especially if we notice serious performance issues on rotation in the history
// view, we may need to use a CursorLoader or some other scheme to preserve the database
// across rotations.
// TODO: We may want to switch to a scheme in which all expressions saved in the database have
// a positive index, and a flag indicates whether the expression is displayed as part of
// the history or not. That would avoid potential thrashing between CursorWindows when accessing
// with a negative index. It would also make it easy to sort expressions in dependency order,
// which helps with avoiding deep recursion during evaluation. But it makes the history UI
// implementation more complicated. It should be possible to make this change without a
// database version bump.

// This ensures strong thread-safety, i.e. each call looks atomic to other threads. We need some
// such property, since expressions may be read by one thread while the main thread is updating
// another expression.

package com.example.calculator2

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.AsyncTask
import android.provider.BaseColumns
import android.util.Log

// Note to markgray: use (foo as java.lang.Object).wait() and notifyAll?
@Suppress("UNUSED_VARIABLE", "PLATFORM_CLASS_MAPPED_TO_KOTLIN", "MemberVisibilityCanBePrivate")
class ExpressionDB(context: Context) {
    private val CONTINUE_WITH_BAD_DB = false

    private val mExpressionDBHelper: ExpressionDBHelper

    private var mExpressionDB: SQLiteDatabase? = null  // Constant after initialization.

    // Expression indices between mMinAccessible and mMaxAccessible inclusive can be accessed.
    // We set these to more interesting values if a database access fails.
    // We punt on writes outside this range. We should never read outside this range.
    // If higher layers refer to an index outside this range, it will already be cached.
    // This also somewhat limits the size of the database, but only to an unreasonably
    // huge value.
    private var mMinAccessible = -10000000L
    private var mMaxAccessible = 10000000L

    // Minimum index value in DB.
    private var mMinIndex: Long = 0
    // Maximum index value in DB.
    private var mMaxIndex: Long = 0

    // A cursor that refers to the whole table, in reverse order.
    private var mAllCursor: AbstractWindowedCursor? = null

    // Expression index corresponding to a zero absolute offset for mAllCursor.
    // This is the argument we passed to the query.
    // We explicitly query only for entries that existed when we started, to avoid
    // interference from updates as we're running. It's unclear whether or not this matters.
    private var mAllCursorBase: Int = 0

    // Database has been opened, mMinIndex and mMaxIndex are correct, mAllCursorBase and
    // mAllCursor have been set.
    private var mDBInitialized: Boolean = false

    // mLock protects mExpressionDB, mMinAccessible, and mMaxAccessible, mAllCursor,
    // mAllCursorBase, mMinIndex, mMaxIndex, and mDBInitialized. We access mExpressionDB without
    // synchronization after it's known to be initialized.  Used to wait for database
    // initialization.
    private val mLock = Any()

    // Is database completely unusable?
    private val isDBBad: Boolean
        get() {
            if (!CONTINUE_WITH_BAD_DB) {
                return false
            }
            synchronized(mLock) {
                return mMinAccessible > mMaxAccessible
            }
        }

    private var databaseWarningIssued: Boolean = false

    // We track the number of outstanding writes to prevent onSaveInstanceState from
    // completing with in-flight database writes.

    private var mIncompleteWrites = 0
    private val mWriteCountsLock = Any()  // Protects the preceding field.

    /* Table contents */
    class ExpressionEntry : BaseColumns {
        companion object {
            val TABLE_NAME = "expressions"
            val COLUMN_NAME_EXPRESSION = "expression"
            val COLUMN_NAME_FLAGS = "flags"
            // Time stamp as returned by currentTimeMillis().
            val COLUMN_NAME_TIMESTAMP = "timeStamp"
        }
    }

    /* Data to be written to or read from a row in the table */
    class RowData constructor(val mExpression: ByteArray, val mFlags: Int, var mTimeStamp: Long  // 0 ==> this and next field to be filled in when written.
    ) {
        private fun degreeModeFromFlags(flags: Int): Boolean {
            return flags and DEGREE_MODE != 0
        }

        private fun longTimeoutFromFlags(flags: Int): Boolean {
            return flags and LONG_TIMEOUT != 0
        }

        /**
         * More client-friendly constructor that hides implementation ugliness.
         * utcOffset here is uncompressed, in milliseconds.
         * A zero timestamp will cause it to be automatically filled in.
         */
        constructor(expr: ByteArray, degreeMode: Boolean, longTimeout: Boolean, timeStamp: Long) : this(expr, flagsFromDegreeAndTimeout(degreeMode, longTimeout), timeStamp) {}

        fun degreeMode(): Boolean {
            return degreeModeFromFlags(mFlags)
        }

        fun longTimeout(): Boolean {
            return longTimeoutFromFlags(mFlags)
        }

        /**
         * Return a ContentValues object representing the current data.
         */
        fun toContentValues(): ContentValues {
            val cvs = ContentValues()
            cvs.put(ExpressionEntry.COLUMN_NAME_EXPRESSION, mExpression)
            cvs.put(ExpressionEntry.COLUMN_NAME_FLAGS, mFlags)
            if (mTimeStamp == 0L) {
                mTimeStamp = System.currentTimeMillis()
            }
            cvs.put(ExpressionEntry.COLUMN_NAME_TIMESTAMP, mTimeStamp)
            return cvs
        }

        companion object {
            private val DEGREE_MODE = 2
            private val LONG_TIMEOUT = 1
            private fun flagsFromDegreeAndTimeout(DegreeMode: Boolean?, LongTimeout: Boolean?): Int {
                return (if (DegreeMode!!) DEGREE_MODE else 0) or if (LongTimeout!!) LONG_TIMEOUT else 0
            }

            @Suppress("unused")
            private const val MILLIS_IN_15_MINS = 15 * 60 * 1000
        }
    }

    private inner class ExpressionDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CREATE_ENTRIES)
            db.execSQL(SQL_CREATE_TIMESTAMP_INDEX)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // For now just throw away history on database version upgrade/downgrade.
            db.execSQL(SQL_DROP_TIMESTAMP_INDEX)
            db.execSQL(SQL_DROP_TABLE)
            onCreate(db)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onUpgrade(db, oldVersion, newVersion)
        }
    }

    init {
        mExpressionDBHelper = ExpressionDBHelper(context)
        val initializer = AsyncInitializer()
        // All calls that create background database accesses are made from the UI thread, and
        // use a SERIAL_EXECUTOR. Thus they execute in order.
        initializer.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mExpressionDBHelper)
    }

    // Is the index in the accessible range of the database?
    private fun inAccessibleRange(index: Long): Boolean {
        if (!CONTINUE_WITH_BAD_DB) {
            return true
        }
        synchronized(mLock) {
            return index >= mMinAccessible && index <= mMaxAccessible
        }
    }


    private fun badDBset() {
        if (!CONTINUE_WITH_BAD_DB) {
            Log.e("Calculator", "Database access failed")
            throw RuntimeException("Database access failed")
        }
        displayDatabaseWarning()
        synchronized(mLock) {
            mMinAccessible = 1L
            mMaxAccessible = -1L
        }
    }

    /**
     * Initialize the database in the background.
     */
    @SuppressLint("StaticFieldLeak")
    private inner class AsyncInitializer : AsyncTask<ExpressionDBHelper, Void, SQLiteDatabase>() {
        @SuppressLint("Recycle")
        override fun doInBackground(vararg helper: ExpressionDBHelper): SQLiteDatabase? {
            try {
                val db = helper[0].writableDatabase
                synchronized(mLock) {
                    mExpressionDB = db
                    db.rawQuery(SQL_GET_MIN, null).use { minResult ->
                        if (!minResult.moveToFirst()) {
                            // Empty database.
                            mMinIndex = MAXIMUM_MIN_INDEX
                        } else {
                            mMinIndex = Math.min(minResult.getLong(0), MAXIMUM_MIN_INDEX)
                        }
                    }
                    db.rawQuery(SQL_GET_MAX, null).use { maxResult ->
                        if (!maxResult.moveToFirst()) {
                            // Empty database.
                            mMaxIndex = 0L
                        } else {
                            mMaxIndex = Math.max(maxResult.getLong(0), 0L)
                        }
                    }
                    if (mMaxIndex > Integer.MAX_VALUE) {
                        throw AssertionError("Expression index absurdly large")
                    }
                    mAllCursorBase = mMaxIndex.toInt()
                    if (mMaxIndex != 0L || mMinIndex != MAXIMUM_MIN_INDEX) {
                        // Set up a cursor for reading the entire database.
                        val args = arrayOf(java.lang.Long.toString(mAllCursorBase.toLong()), java.lang.Long.toString(mMinIndex))
                        mAllCursor = db.rawQuery(SQL_GET_ALL, args) as AbstractWindowedCursor
                        if (!mAllCursor!!.moveToFirst()) {
                            badDBset()
                            return null
                        }
                    }
                    mDBInitialized = true
                    // We notify here, since there are unlikely cases in which the UI thread
                    // may be blocked on us, preventing onPostExecute from running.
                    (mLock as Object).notifyAll()
                }
                return db
            } catch (e: SQLiteException) {
                Log.e("Calculator", "Database initialization failed.\n", e)
                synchronized(mLock) {
                    badDBset()
                    (mLock as Object).notifyAll()
                }
                return null
            }

        }

        override fun onPostExecute(result: SQLiteDatabase?) {
            if (result == null) {
                displayDatabaseWarning()
            } // else doInBackground already set expressionDB.
        }
        // On cancellation we do nothing;
    }

    /**
     * Display a warning message that a database access failed.
     * Do this only once. TODO: Replace with a real UI message.
     */
    internal fun displayDatabaseWarning() {
        if (!databaseWarningIssued) {
            Log.e("Calculator", "Calculator restarting due to database error")
            databaseWarningIssued = true
        }
    }

    /**
     * Wait until the database and mAllCursor, etc. have been initialized.
     */
    private fun waitForDBInitialized() {
        synchronized(mLock) {
            // InterruptedExceptions are inconvenient here. Defer.
            var caught = false
            while (!mDBInitialized && !isDBBad) {
                try {
                    (mLock as Object).wait()
                } catch (e: InterruptedException) {
                    caught = true
                }

            }
            if (caught) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Erase the entire database. Assumes no other accesses to the database are
     * currently in progress
     * These tasks must be executed on a serial executor to avoid reordering writes.
     */
    @SuppressLint("StaticFieldLeak")
    private inner class AsyncEraser : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg nothings: Void): Void? {
            mExpressionDB!!.execSQL(SQL_DROP_TIMESTAMP_INDEX)
            mExpressionDB!!.execSQL(SQL_DROP_TABLE)
            try {
                mExpressionDB!!.execSQL("VACUUM")
            } catch (e: Exception) {
                Log.v("Calculator", "Database VACUUM failed\n", e)
                // Should only happen with concurrent execution, which should be impossible.
            }

            mExpressionDB!!.execSQL(SQL_CREATE_ENTRIES)
            mExpressionDB!!.execSQL(SQL_CREATE_TIMESTAMP_INDEX)
            return null
        }

        override fun onPostExecute(nothing: Void) {
            synchronized(mLock) {
                // Reinitialize everything to an empty and fully functional database.
                mMinAccessible = -10000000L
                mMaxAccessible = 10000000L
                mMinIndex = MAXIMUM_MIN_INDEX
                mAllCursorBase = 0
                mMaxIndex = mAllCursorBase.toLong()
                mDBInitialized = true
                (mLock as Object).notifyAll()
            }
        }
        // On cancellation we do nothing;
    }

    /**
     * Erase ALL database entries.
     * This is currently only safe if expressions that may refer to them are also erased.
     * Should only be called when concurrent references to the database are impossible.
     * TODO: Look at ways to more selectively clear the database.
     */
    fun eraseAll() {
        waitForDBInitialized()
        synchronized(mLock) {
            mDBInitialized = false
        }
        val eraser = AsyncEraser()
        eraser.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
    }

    private fun writeCompleted() {
        synchronized(mWriteCountsLock) {
            if (--mIncompleteWrites == 0) {
                (mWriteCountsLock as Object).notifyAll()
            }
        }
    }

    private fun writeStarted() {
        synchronized(mWriteCountsLock) {
            ++mIncompleteWrites
        }
    }

    /**
     * Wait for in-flight writes to complete.
     * This is not safe to call from one of our background tasks, since the writing
     * tasks may be waiting for the same underlying thread that we're using, resulting
     * in deadlock.
     */
    fun waitForWrites() {
        synchronized(mWriteCountsLock) {
            var caught = false
            while (mIncompleteWrites != 0) {
                try {
                    (mWriteCountsLock as Object).wait()
                } catch (e: InterruptedException) {
                    caught = true
                }

            }
            if (caught) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Insert the given row in the database without blocking the UI thread.
     * These tasks must be executed on a serial executor to avoid reordering writes.
     */
    @SuppressLint("StaticFieldLeak")
    private inner class AsyncWriter : AsyncTask<ContentValues, Void, Long>() {
        override fun doInBackground(vararg cvs: ContentValues): Long? {
            val index = cvs[0].getAsLong(BaseColumns._ID)!!
            val result = mExpressionDB!!.insert(ExpressionEntry.TABLE_NAME, null, cvs[0])
            writeCompleted()
            // Return 0 on success, row id on failure.
            return if (result == -1L) {
                index
            } else if (result != index) {
                throw AssertionError("Expected row id $index, got $result")
            } else {
                0L
            }
        }

        override fun onPostExecute(result: Long?) {
            if (result != 0L) {
                synchronized(mLock) {
                    if (result!! > 0L) {
                        mMaxAccessible = result - 1
                    } else {
                        mMinAccessible = result + 1
                    }
                }
                displayDatabaseWarning()
            }
        }
        // On cancellation we do nothing;
    }

    /**
     * Add a row with index outside existing range.
     * The returned index will be just larger than any existing index unless negative_index is true.
     * In that case it will be smaller than any existing index and smaller than MAXIMUM_MIN_INDEX.
     * This ensures that prior additions have completed, but does not wait for this insertion
     * to complete.
     */
    fun addRow(negativeIndex: Boolean, data: RowData): Long {

        @Suppress("UNUSED_VARIABLE")
        val result: Long
        val newIndex: Long
        waitForDBInitialized()
        synchronized(mLock) {
            if (negativeIndex) {
                newIndex = mMinIndex - 1
                mMinIndex = newIndex
            } else {
                newIndex = mMaxIndex + 1
                mMaxIndex = newIndex
            }
            if (!inAccessibleRange(newIndex)) {
                // Just drop it, but go ahead and return a new index to use for the cache.
                // So long as reads of previously written expressions continue to work,
                // we should be fine. When the application is restarted, history will revert
                // to just include values between mMinAccessible and mMaxAccessible.
                return newIndex
            }
            writeStarted()
            val cvs = data.toContentValues()
            cvs.put(BaseColumns._ID, newIndex)
            val awriter = AsyncWriter()
            // Ensure that writes are executed in order.
            awriter.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, cvs)
        }
        return newIndex
    }

    /**
     * Generate a fake database row that's good enough to hopefully prevent crashes,
     * but bad enough to avoid confusion with real data. In particular, the result
     * will fail to evaluate.
     */
    internal fun makeBadRow(): RowData {
        val badExpr = CalculatorExpr()
        badExpr.add(R.id.lparen)
        badExpr.add(R.id.rparen)
        return RowData(badExpr.toBytes(), false, false, 0)
    }

    /**
     * Retrieve the row with the given index using a direct query.
     * Such a row must exist.
     * We assume that the database has been initialized, and the argument has been range checked.
     */
    private fun rowDirectGet(index: Long): RowData {
        var result: RowData
        val args = arrayOf(java.lang.Long.toString(index))
        mExpressionDB!!.rawQuery(SQL_GET_ROW, args).use { resultC ->
            if (!resultC.moveToFirst()) {
                badDBset()
                return makeBadRow()
            } else {
                result = RowData(resultC.getBlob(1), resultC.getInt(2) /* flags */,
                        resultC.getLong(3) /* timestamp */)
                return result
            }
        }

    }

    /**
     * Retrieve the row at the given offset from mAllCursorBase.
     * Note the argument is NOT an expression index!
     * We assume that the database has been initialized, and the argument has been range checked.
     */
    private fun rowFromCursorGet(offset: Int): RowData {

        val result: RowData
        synchronized(mLock) {
            if (!mAllCursor!!.moveToPosition(offset)) {
                Log.e("Calculator", "Failed to move cursor to position $offset")
                badDBset()
                return makeBadRow()
            }
            return RowData(mAllCursor!!.getBlob(1), mAllCursor!!.getInt(2) /* flags */,
                    mAllCursor!!.getLong(3) /* timestamp */)
        }
    }

    /**
     * Retrieve the database row at the given index.
     * We currently assume that we never read data that we added since we initialized the database.
     * This makes sense, since we cache it anyway. And we should always cache recently added data.
     */
    fun rowGet(index: Long): RowData {
        waitForDBInitialized()
        if (!inAccessibleRange(index)) {
            // Even if something went wrong opening or writing the database, we should
            // not see such read requests, unless they correspond to a persistently
            // saved index, and we can't retrieve that expression.
            displayDatabaseWarning()
            return makeBadRow()
        }
        var position = mAllCursorBase - index.toInt()
        // We currently assume that the only gap between expression indices is the one around 0.
        if (index < 0) {
            position -= GAP.toInt()
        }
        if (position < 0) {
            throw AssertionError("Database access out of range, index = " + index
                    + " rel. nextPos. = " + position)
        }
        if (index < 0) {
            // Avoid using mAllCursor to read data that's far away from the current position,
            // since we're likely to have to return to the current position.
            // This is a heuristic; we don't worry about doing the "wrong" thing in the race case.
            val endPosition: Int
            synchronized(mLock) {
                val window = mAllCursor!!.window
                endPosition = window.startPosition + window.numRows
            }
            if (position >= endPosition) {
                return rowDirectGet(index)
            }
        }
        // In the positive index case, it's probably OK to cross a cursor boundary, since
        // we're much more likely to stay in the new window.
        return rowFromCursorGet(position)
    }

    fun minIndexGet(): Long {
        waitForDBInitialized()
        synchronized(mLock) {
            return mMinIndex
        }
    }

    fun maxIndexGet(): Long {
        waitForDBInitialized()
        synchronized(mLock) {
            return mMaxIndex
        }
    }

    fun close() {
        mExpressionDBHelper.close()
    }

    companion object {

        private val SQL_CREATE_ENTRIES = (
                "CREATE TABLE " + ExpressionEntry.TABLE_NAME + " ("
                        + BaseColumns._ID + " INTEGER PRIMARY KEY,"
                        + ExpressionEntry.COLUMN_NAME_EXPRESSION + " BLOB,"
                        + ExpressionEntry.COLUMN_NAME_FLAGS + " INTEGER,"
                        + ExpressionEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)")
        private val SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + ExpressionEntry.TABLE_NAME
        private val SQL_GET_MIN = ("SELECT MIN(" + BaseColumns._ID
                + ") FROM " + ExpressionEntry.TABLE_NAME)
        private val SQL_GET_MAX = ("SELECT MAX(" + BaseColumns._ID
                + ") FROM " + ExpressionEntry.TABLE_NAME)
        private val SQL_GET_ROW = ("SELECT * FROM " + ExpressionEntry.TABLE_NAME
                + " WHERE " + BaseColumns._ID + " = ?")
        private val SQL_GET_ALL = ("SELECT * FROM " + ExpressionEntry.TABLE_NAME
                + " WHERE " + BaseColumns._ID + " <= ? AND " +
                BaseColumns._ID + " >= ?" + " ORDER BY " + BaseColumns._ID + " DESC ")
        // We may eventually need an index by timestamp. We don't use it yet.
        private val SQL_CREATE_TIMESTAMP_INDEX = (
                "CREATE INDEX timestamp_index ON " + ExpressionEntry.TABLE_NAME + "("
                        + ExpressionEntry.COLUMN_NAME_TIMESTAMP + ")")
        private val SQL_DROP_TIMESTAMP_INDEX = "DROP INDEX IF EXISTS timestamp_index"

        // Never allocate new negative indices (row ids) >= MAXIMUM_MIN_INDEX.
        val MAXIMUM_MIN_INDEX: Long = -10

        // Gap between negative and positive row ids in the database.
        // Expressions with index [MAXIMUM_MIN_INDEX .. 0] are not stored.
        private val GAP = -MAXIMUM_MIN_INDEX + 1

        // If you change the database schema, you must increment the database version.
        val DATABASE_VERSION = 1
        val DATABASE_NAME = "Expressions.db"
    }

}
