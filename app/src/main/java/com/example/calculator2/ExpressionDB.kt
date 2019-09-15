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
import kotlin.math.max
import kotlin.math.min

@Suppress("UNUSED_VARIABLE", "PLATFORM_CLASS_MAPPED_TO_KOTLIN", "MemberVisibilityCanBePrivate")
class ExpressionDB(context: Context) {
    /**
     * The custom [SQLiteOpenHelper] we use to create, open, and/or manage our expression database.
     */
    private val mExpressionDBHelper: ExpressionDBHelper

    /**
     * The [SQLiteDatabase] which contains our expression history data.
     */
    private var mExpressionDB: SQLiteDatabase? = null  // Constant after initialization.

    // Expression indices between mMinAccessible and mMaxAccessible inclusive can be accessed.
    // We set these to more interesting values if a database access fails.
    // We punt on writes outside this range. We should never read outside this range.
    // If higher layers refer to an index outside this range, it will already be cached.
    // This also somewhat limits the size of the database, but only to an unreasonably
    // huge value.
    private var mMinAccessible = -10000000L
    private var mMaxAccessible = 10000000L

    /**
     * Minimum index value in DB.
     */
    private var mMinIndex: Long = 0
    /**
     * Maximum index value in DB.
     */
    private var mMaxIndex: Long = 0

    /**
     * A cursor that refers to the whole table, in reverse order.
     */
    private var mAllCursor: AbstractWindowedCursor? = null

    /**
     * Expression index corresponding to a zero absolute offset for [mAllCursor]. This is the
     * argument we passed to the query. We explicitly query only for entries that existed when
     * we started, to avoid interference from updates as we're running. It's unclear whether or
     * not this matters.
     */
    private var mAllCursorBase: Int = 0

    /**
     * Database has been opened, [mMinIndex] and [mMaxIndex] are correct, [mAllCursorBase] and
     * [mAllCursor] have been set.
     */
    private var mDBInitialized: Boolean = false

    /**
     * [mLock] protects [mExpressionDB], [mMinAccessible], and [mMaxAccessible], [mAllCursor],
     * [mAllCursorBase], [mMinIndex], [mMaxIndex], and [mDBInitialized]. We access [mExpressionDB]
     * without synchronization after it's known to be initialized. Used to wait for database
     * initialization. Needs to be an [Object] instead of an [Any] because or `wait` and `notifyAll`
     */
    private val mLock = Object()

    /**
     * Is database completely unusable?
     *
     * @return *true if [mMinAccessible] is greater than [mMaxAccessible]
     */
    @Suppress("ConstantConditionIf")
    private val isDBBad: Boolean
        get() {
            if (!CONTINUE_WITH_BAD_DB) {
                return false
            }
            synchronized(mLock) {
                return mMinAccessible > mMaxAccessible
            }
        }

    /**
     * This is set to *true* by our [displayDatabaseWarning] method so that only one database error
     * is logged. [displayDatabaseWarning] is called when one of our database accessing [AsyncTask]'s
     * detects an error in the database and decides the database is unusable.
     */
    private var databaseWarningIssued: Boolean = false

    /**
     * We track the number of outstanding writes to prevent `onSaveInstanceState` from completing
     * with in-flight database writes.
     */
    private var mIncompleteWrites = 0
    /**
     * Protects the [mIncompleteWrites] field. Needs to be an [Object] instead of an [Any] because
     * of `wait` and `notifyAll`
     */
    private val mWriteCountsLock = Object()

    /** Table contents */
    class ExpressionEntry : BaseColumns {
        companion object {
            /**
             * The name of our table in the database.
             */
            const val TABLE_NAME = "expressions"
            /**
             * The name of the column for expressions in the [TABLE_NAME] table (holds a BLOB)
             */
            const val COLUMN_NAME_EXPRESSION = "expression"
            /**
             * The name of the column for flags in the [TABLE_NAME] table (holds an INTEGER)
             */
            const val COLUMN_NAME_FLAGS = "flags"
            /**
             * Time stamp as returned by [System.currentTimeMillis] in the [TABLE_NAME]
             * table (holds an INTEGER)
             */
            const val COLUMN_NAME_TIMESTAMP = "timeStamp"
        }
    }

    /** Data to be written to or read from a row in the table */
    class RowData constructor(
            /**
             * The byte encoded [CalculatorExpr] expression to be stored in the row.
             */
            val mExpression: ByteArray,
            /**
             * Contains the flags in effect for the expression: [DEGREE_MODE] and [LONG_TIMEOUT]
             */
            val mFlags: Int,
            /**
             * Time stamp as returned by [System.currentTimeMillis] for the expression.
             */
            var mTimeStamp: Long  // 0 ==> this and next(?) field to be filled in when written.
    ) {

        /**
         * Returns *true* if the [DEGREE_MODE] bit in our parameter [flags] is set.
         *
         * @param flags the [Int] flags whose [DEGREE_MODE] bit we want to test.
         * @return *true* if the [DEGREE_MODE] bit in [flags] is set.
         */
        private fun degreeModeFromFlags(flags: Int): Boolean {
            return flags and DEGREE_MODE != 0
        }

        /**
         * Returns *true* if the [LONG_TIMEOUT] bit in our parameter [flags] is set.
         *
         * @param flags the [Int] flags whose [LONG_TIMEOUT] bit we want to test.
         * @return *true* if the [LONG_TIMEOUT] bit in [flags] is set.
         */
        private fun longTimeoutFromFlags(flags: Int): Boolean {
            return flags and LONG_TIMEOUT != 0
        }

        /**
         * More client-friendly constructor that hides implementation ugliness. utcOffset here is
         * uncompressed, in milliseconds. A zero timestamp will cause it to be automatically filled
         * in. We just call our three parameter constructor with the `flags` argument set to the
         * value returned by our [flagsFromDegreeAndTimeout] method when it's given our parameters
         * [degreeMode] and [longTimeout].
         *
         * @param expr The byte encoded [CalculatorExpr] expression to be stored in the row.
         * @param degreeMode The state of the degree mode flag that is in effect for the expression.
         * @param longTimeout The state of the long timeout flag that is in effect for the expression.
         * @param timeStamp Time stamp as returned by [System.currentTimeMillis] for the expression.
         */
        constructor(
                expr: ByteArray,
                degreeMode: Boolean,
                longTimeout: Boolean,
                timeStamp: Long
        ) : this(expr, flagsFromDegreeAndTimeout(degreeMode, longTimeout), timeStamp)

        /**
         * Returns *true* if the degree mode flag in our [mFlags] field is set.
         *
         * @return *true* if the degree mode flag in our [mFlags] field is set.
         */
        fun degreeMode(): Boolean {
            return degreeModeFromFlags(mFlags)
        }

        /**
         * Returns *true* if the long timeout flag in our [mFlags] field is set.
         *
         * @return *true* if the long timeout flag in our [mFlags] field is set.
         */
        fun longTimeout(): Boolean {
            return longTimeoutFromFlags(mFlags)
        }

        /**
         * Return a [ContentValues] object representing the current data. First we initialize our
         * `val cvs` with a new instance of [ContentValues]. Then we call its `put` method to add
         * [mExpression] to it under the key [ExpressionEntry.COLUMN_NAME_EXPRESSION], and then to
         * add [mFlags] under the key [ExpressionEntry.COLUMN_NAME_FLAGS]. If our [mTimeStamp] field
         * is 0L we initialize it with the current time in milliseconds, then add [mTimeStamp] to
         * `cvs` under the key [ExpressionEntry.COLUMN_NAME_TIMESTAMP]. Finally we return `cvs` to
         * the caller.
         *
         * @return a [ContentValues] object representing the current data.
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

        /**
         * Our static constants and methods.
         */
        companion object {
            /**
             * The bit in our [mFlags] field which holds the degree mode state.
             */
            private const val DEGREE_MODE = 2
            /**
             * The bit in our [mFlags] field which holds the long timeout state.
             */
            private const val LONG_TIMEOUT = 1

            /**
             * Stores the boolean state of its parameters [degreeMode] and [longTimeout] as bit
             * settings in an [Int] and returns that [Int]. We use a bit wise *or* to combine the
             * [DEGREE_MODE] bit if [degreeMode] is *true* and the [LONG_TIMEOUT] bit if [longTimeout]
             * is *true*, leaving the respective bits zero if their parameters are false, and return
             * the resulting [Int] to the caller.
             *
             * @param degreeMode the degree mode state to be stored.
             * @param longTimeout the long timeout state to be stored.
             * @return an [Int] with the [DEGREE_MODE] bit set if [degreeMode] is *true* and with the
             * [LONG_TIMEOUT] bit set if [longTimeout] is *true*.
             */
            private fun flagsFromDegreeAndTimeout(degreeMode: Boolean?, longTimeout: Boolean?): Int {
                return (if (degreeMode!!) DEGREE_MODE else 0) or if (longTimeout!!) LONG_TIMEOUT else 0
            }

            /**
             * The number of milliseconds in 15 minutes. UNUSED
             */
            @Suppress("unused")
            private const val MILLIS_IN_15_MINS = 15 * 60 * 1000
        }
    }

    /**
     * Our custom [SQLiteOpenHelper].
     */
    private inner class ExpressionDBHelper(context: Context)
        : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        /**
         * Called when the database is created for the first time. This is where the creation of
         * tables and the initial population of the tables should happen. We call the `execSQL`
         * method of [db] to execute the SQL statement in [SQL_CREATE_ENTRIES] (which creates the
         * table [ExpressionEntry.TABLE_NAME] with the columns [BaseColumns._ID] as the integer
         * primary key, [ExpressionEntry.COLUMN_NAME_EXPRESSION] as the BLOB which holds the byte
         * encoded expression, [ExpressionEntry.COLUMN_NAME_FLAGS] as the integer holding our flags,
         * and [ExpressionEntry.COLUMN_NAME_TIMESTAMP] as the integer holding our timestamp). Then
         * we call its `execSQL` method to execute the SQL statement in [SQL_CREATE_TIMESTAMP_INDEX]
         * (which creates an INDEX with the index name "timestamp_index" on the table
         * [ExpressionEntry.TABLE_NAME] for the column [ExpressionEntry.COLUMN_NAME_TIMESTAMP]).
         *
         * @param db The database.
         */
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CREATE_ENTRIES)
            db.execSQL(SQL_CREATE_TIMESTAMP_INDEX)
        }

        /**
         * Called when the database needs to be upgraded. The implementation should use this method
         * to drop tables, add tables, or do anything else it needs to upgrade to the new schema
         * version. We just delete the old database by calling the `execSQL` method of [db] to
         * execute the SQL statement in [SQL_DROP_TIMESTAMP_INDEX] (which drops the index with the
         * name "timestamp_index" if it exists), and then call its `execSQL` method to execute the
         * SQL statement in [SQL_DROP_TABLE] (which drops the [ExpressionEntry.TABLE_NAME] table if
         * it exists). Then we call our override of the [onCreate] method to create a new empty table
         * and timestamp index.
         *
         * @param db The database.
         * @param oldVersion The old database version.
         * @param newVersion The new database version.
         */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // For now just throw away history on database version upgrade/downgrade.
            db.execSQL(SQL_DROP_TIMESTAMP_INDEX)
            db.execSQL(SQL_DROP_TABLE)
            onCreate(db)
        }

        /**
         * Called when the database needs to be downgraded. We just call our override of the
         * [onUpgrade] method.
         *
         * @param db The database.
         * @param oldVersion The old database version.
         * @param newVersion The new database version.
         */
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onUpgrade(db, oldVersion, newVersion)
        }
    }

    /**
     * The init block for the `ExpressionDB` constructor. We initialize our field `mExpressionDBHelper`
     * with a new instance of `ExpressionDBHelper`, initialize our `val initializer` with a new
     * instance of `AsyncInitializer` (the custom `AsyncTask` which initializes the database in the
     * background), and call the `executeOnExecutor` method of `initializer` to have it execute on
     * the `AsyncTask.SERIAL_EXECUTOR` (our UI uses the same executor so every operation on the
     * database is done in order) with our field `mExpressionDBHelper` as the `ExpressionDBHelper`
     * from which it acquires the writable `SQLiteDatabase` that it needs to initialize.
     */
    init {
        mExpressionDBHelper = ExpressionDBHelper(context)
        val initializer = AsyncInitializer()
        // All calls that create background database accesses are made from the UI thread, and
        // use a SERIAL_EXECUTOR. Thus they execute in order.
        initializer.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mExpressionDBHelper)
    }

    /**
     * Is the index in the accessible range of the database? If our debug flag [CONTINUE_WITH_BAD_DB]
     * is *false* we just return *true*. It is always *false* so this method always returns *true*
     * without checking! I have no idea whether this is intentional or not. If [CONTINUE_WITH_BAD_DB]
     * was *true* a block which is *synchronized* on our [mLock] field would return *true* only if
     * the *in* function determines that [index] is in the range [mMinAccessible] to [mMaxAccessible].
     *
     * @param index the index into the database we want to check for validity.
     * @return *true* if [index] is in the range [mMinAccessible] to [mMaxAccessible].
     */
    private fun inAccessibleRange(index: Long): Boolean {
        @Suppress("ConstantConditionIf")
        if (!CONTINUE_WITH_BAD_DB) {
            return true
        }
        synchronized(mLock) {
            return index in mMinAccessible..mMaxAccessible
        }
    }

    /**
     * This is called when an error occurs while accessing the database. The [CONTINUE_WITH_BAD_DB]
     * debugging flag is always *false* so this method just logs "Database access failed" and throws
     * a [RuntimeException] "Database access failed". If the [CONTINUE_WITH_BAD_DB] is *true* though
     * we would call our [displayDatabaseWarning] method to log the message "Calculator restarting
     * due to database error" and set the [databaseWarningIssued] flag to *true* so only one warning
     * will be logged, then in a block *synchronized* on our [mLock] field we would set our fields
     * [mMinAccessible] to 1L and [mMaxAccessible] to -1L.
     */
    private fun badDBset() {
        @Suppress("ConstantConditionIf")
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

        /**
         * We override this method to perform a computation on a background thread. The
         * [ExpressionDBHelper] parameter [helper] is the parameter passed to [execute]
         * by the caller of this task. We initialize our `val db` to the [SQLiteDatabase]
         * that our parameter [helper] opens or creates ([helper] is our custom [SQLiteOpenHelper]).
         * Then wrapped in a *try* block intended to catch [SQLiteException] we synchronize a
         * block on our field [mLock] wherein we:
         *
         *  - Set our field [mExpressionDB] to `db`
         *  - Call the `rawQuery` method of `db` to run the SQL in [SQL_GET_MIN] which selects the
         *  minimum [BaseColumns._ID] row in the table named [ExpressionEntry.TABLE_NAME] and returns
         *  a `Cursor` `minResult` which is positioned before this row which we then *use* to try to
         *  move to the first row in the cursor and if that fails we set our field [mMinIndex] to our
         *  constant [MAXIMUM_MIN_INDEX] (it is an empty database), and if it succeeds we set [mMinIndex]
         *  to the minimum of the [Long] in column 0 of the cursor and our constant [MAXIMUM_MIN_INDEX].
         *  - We next call the `rawQuery` method of `db` to run the SQL in [SQL_GET_MAX] which selects
         *  the maximum [BaseColumns._ID] row in the table named [ExpressionEntry.TABLE_NAME] and
         *  returns a `Cursor` `maxResult` which is positioned before this row which we then *use* to
         *  try to move to the first row in the cursor and if that fails we set our field [mMaxIndex]
         *  to 0L (it is an empty database), and if it succeeds we set [mMaxIndex] to the maximum of
         *  of the [Long] in column 0 of the cursor and 0L.
         *  - If [mMaxIndex] is now greater than [Integer.MAX_VALUE] we throw an [AssertionError]
         *  "Expression index absurdly large".
         *  - Otherwise we set our field [mAllCursorBase] to the [Int] value of [mMaxIndex].
         *  - If [mMaxIndex] is not equal to 0L or [mMinIndex] is not equal to [MAXIMUM_MIN_INDEX]
         *  there is data in the database so we initialize our `val args` to an array containing
         *  the string value of [mAllCursorBase] and the string value of [mMinIndex].
         *  - We then initialize our field [mAllCursor] with the `Cursor` returned by the `rawQuery`
         *  method of `db` when it executes the SQL command in [SQL_GET_ALL] using `args` as the
         *  selection arguments (the command selects all of the entries in the table with the name
         *  [ExpressionEntry.TABLE_NAME] where the [BaseColumns._ID] column is less than or equal
         *  to [mAllCursorBase] and greater than or equal to [mMinIndex] ordered by the column
         *  [BaseColumns._ID] in descending order).
         *  - If we are not able to move to the first row in [mAllCursor] we call our [badDBset]
         *  to register the fact that we have a bad database and then return *null* to the caller.
         *  - Otherwise we set [mDBInitialized] to *true* (the database is initialized) and wake up
         *  any threads waiting on [mLock] before exiting the *synchronized* block.
         *
         * Then we just return `db` to the caller (an open and initialized [SQLiteDatabase] ready
         * for use).
         *
         * @param helper The [ExpressionDBHelper] (our custom [SQLiteOpenHelper]) which we use to
         * open or create our [SQLiteDatabase].
         * @return Our opened (or just created if it did not already exist) [SQLiteDatabase].
         */
        @SuppressLint("Recycle") // Our mAllCursor is never to be closed
        override fun doInBackground(vararg helper: ExpressionDBHelper): SQLiteDatabase? {
            try {
                val db = helper[0].writableDatabase
                synchronized(mLock) {
                    mExpressionDB = db
                    db.rawQuery(SQL_GET_MIN, null).use { minResult ->
                        mMinIndex = if (!minResult.moveToFirst()) {
                            // Empty database.
                            MAXIMUM_MIN_INDEX
                        } else {
                            min(minResult.getLong(0), MAXIMUM_MIN_INDEX)
                        }
                    }
                    db.rawQuery(SQL_GET_MAX, null).use { maxResult ->
                        mMaxIndex = if (!maxResult.moveToFirst()) {
                            // Empty database.
                            0L
                        } else {
                            max(maxResult.getLong(0), 0L)
                        }
                    }
                    if (mMaxIndex > Integer.MAX_VALUE) {
                        throw AssertionError("Expression index absurdly large")
                    }
                    mAllCursorBase = mMaxIndex.toInt()
                    if (mMaxIndex != 0L || mMinIndex != MAXIMUM_MIN_INDEX) {
                        // Set up a cursor for reading the entire database.
                        val args = arrayOf(
                                mAllCursorBase.toLong().toString(),
                                mMinIndex.toString()
                        )
                        mAllCursor = db.rawQuery(SQL_GET_ALL, args) as AbstractWindowedCursor
                        if (!mAllCursor!!.moveToFirst()) {
                            badDBset()
                            return null
                        }
                    }
                    mDBInitialized = true
                    // We notify here, since there are unlikely cases in which the UI thread
                    // may be blocked on us, preventing onPostExecute from running.
                    mLock.notifyAll()
                }
                return db
            } catch (e: SQLiteException) {
                Log.e("Calculator", "Database initialization failed.\n", e)
                synchronized(mLock) {
                    badDBset()
                    mLock.notifyAll()
                }
                return null
            }

        }

        /**
         * Runs on the UI thread after [doInBackground]. [result] is the [SQLiteDatabase] returned
         * by [doInBackground]. If [result] is *null* we call our method [displayDatabaseWarning] to
         * log the fact that there was a database error, otherwise we do nothing.
         *
         * @param result The [SQLiteDatabase] opened or created by [doInBackground].
         */
        override fun onPostExecute(result: SQLiteDatabase?) {
            if (result == null) {
                displayDatabaseWarning()
            } // else doInBackground already set expressionDB.
        }

        // On cancellation we do nothing, so we do not override onCanceled
    }

    /**
     * Display a warning message that a database access failed. We do this only once.
     * TODO: Replace with a real UI message.
     */
    internal fun displayDatabaseWarning() {
        if (!databaseWarningIssued) {
            Log.e("Calculator", "Calculator restarting due to database error")
            databaseWarningIssued = true
        }
    }

    /**
     * Wait until the database and [mAllCursor], etc. have been initialized. In a block synchronized
     * on our field [mLock] we initialize our `var caught` to *false* (it is a flag which we set to
     * *true* if we are interrupted while waiting in order to deal with it after the database is
     * initialized). Then we loop while [mDBInitialized] is *false* (the database has not yet been
     * initialized) and [isDBBad] is *false (the database has not been declared "bad"). In this loop,
     * wrapped in a *try* block intended to catch [InterruptedException] in order to set `caught` to
     * *true*, we call the `wait` method of [mLock] which releases the lock and waits until another
     * thread invokes the `notify` or `notifyAll` method of [mLock]. When [mDBInitialized] or [isDBBad]
     * is set to *true* we exit the *while* loop and if `caught` has been set to *true* because our
     * `wait` was interrupted we call the `interrupt` method of the current thread to interrupt this
     * thread, otherwise we just exit the *synchronized* block and return.
     */
    private fun waitForDBInitialized() {
        synchronized(mLock) {
            // InterruptedExceptions are inconvenient here. Defer.
            var caught = false
            while (!mDBInitialized && !isDBBad) {
                try {
                    mLock.wait()
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
     * Erase the entire database in the background. Assumes no other accesses to the database
     * are currently in progress. These tasks must be executed on a serial executor to avoid
     * reordering writes.
     */
    @SuppressLint("StaticFieldLeak")
    private inner class AsyncEraser : AsyncTask<Void, Void, Void>() {
        /**
         * We override this method to erase the database on a background thread. First we call the
         * `execSQL` method of our [SQLiteDatabase] field [mExpressionDB] to execute the SQL command
         * in [SQL_DROP_TIMESTAMP_INDEX] (which drops the "timestamp_index" index if it exists), then
         * we call it to execute the SQL command in [SQL_DROP_TABLE] (which drops the table named
         * [ExpressionEntry.TABLE_NAME] if it exists). Then wrapped in a *try* block intended to
         * catch and log ant [Exception] ("Database VACUUM failed") we call the `execSQL` method of
         * [mExpressionDB] to execute the SQL command "VACUUM" which rebuilds the database packing
         * it into the minimum amount of space. After exiting the *try* block we then again call the
         * `execSQL` method of [mExpressionDB] to execute the SQL command in [SQL_CREATE_ENTRIES]
         * (which creates the table [ExpressionEntry.TABLE_NAME] with the columns [BaseColumns._ID]
         * as the integer primary key, [ExpressionEntry.COLUMN_NAME_EXPRESSION] as the BLOB which
         * holds the byte encoded expression, [ExpressionEntry.COLUMN_NAME_FLAGS] as the integer
         * holding our flags, and [ExpressionEntry.COLUMN_NAME_TIMESTAMP] as the integer holding our
         * timestamp), and then call it to execute the command in [SQL_CREATE_TIMESTAMP_INDEX] ((which
         * creates an INDEX with the index name "timestamp_index" on the table [ExpressionEntry.TABLE_NAME]
         * for the column [ExpressionEntry.COLUMN_NAME_TIMESTAMP]). Finally we return *null* to the
         * caller.
         *
         * @param nothings The parameters of the task -- there are none.
         * @return A *null* result always.
         */
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

        /**
         * Runs on the UI thread after [doInBackground]. The [nothing] result is the *null* value
         * returned by [doInBackground]. In a block *synchronized* on our field [mLock] we set our
         * field [mMinAccessible] to -10000000L, our field [mMaxAccessible] to 10000000L, our field
         * [mMinIndex] to the constant [MAXIMUM_MIN_INDEX] (-10), our field [mAllCursorBase] to 0,
         * our field [mMaxIndex] to the [Long] value of [mAllCursorBase] (0L), our [mDBInitialized]
         * field to *true*, and then call the `notifyAll` method of [mLock] to wake up all threads
         * that are waiting for [mLock].
         *
         * @param nothing Always *null* result of the operation computed by [doInBackground].
         */
        override fun onPostExecute(nothing: Void) {
            synchronized(mLock) {
                // Reinitialize everything to an empty and fully functional database.
                mMinAccessible = -10000000L
                mMaxAccessible = 10000000L
                mMinIndex = MAXIMUM_MIN_INDEX
                mAllCursorBase = 0
                mMaxIndex = mAllCursorBase.toLong()
                mDBInitialized = true
                mLock.notifyAll()
            }
        }

        // On cancellation we do nothing -- so we do not implement onCanceled
    }

    /**
     * Erase ALL database entries. This is currently only safe if expressions that may refer to them
     * are also erased. Should only be called when concurrent references to the database are impossible.
     * First we call our [waitForDBInitialized] method to wait until the database has been initialized,
     * then in a block *synchronized* on [mLock] we set our field [mDBInitialized] to *false*. We then
     * initialize our `val eraser` to a new instance of [AsyncEraser] and call its `executeOnExecutor`
     * method to have it start running its `doInBackground` method on the [AsyncTask.SERIAL_EXECUTOR]
     * executor (this is an `Executor` that executes tasks one at a time in serial order and this
     * serialization is global to this process).
     *
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

    /**
     * This is called from the `doInBackground` method of the [AsyncWriter] async task when it completes
     * the insertion of a row into the database in order for us to decrement our [mIncompleteWrites]
     * field (which records the number of in-flight writes outstanding). In a block *synchronized* on
     * our field [mWriteCountsLock] we decrement our field [mIncompleteWrites] and if has gone down
     * to 0 we call the `notifyAll` method of [mWriteCountsLock] to wake up all threads that are
     * waiting for [mWriteCountsLock] before we exit the *synchronized* block and release the lock
     * [mWriteCountsLock].
     */
    private fun writeCompleted() {
        synchronized(mWriteCountsLock) {
            if (--mIncompleteWrites == 0) {
                mWriteCountsLock.notifyAll()
            }
        }
    }

    /**
     * This is called from our [addRow] method just before it starts a background insertion of a
     * row by an [AsyncWriter] in order for us to increment our [mIncompleteWrites] field (which
     * records the number of in-flight writes outstanding). In a block *synchronized* on our field
     * [mWriteCountsLock] we just increment our field [mIncompleteWrites].
     */
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
                    mWriteCountsLock.wait()
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
            return when {
                result == -1L -> index
                result != index -> throw AssertionError("Expected row id $index, got $result")
                else -> 0L
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
        return RowData(badExpr.toBytes(), degreeMode = false, longTimeout = false, timeStamp = 0)
    }

    /**
     * Retrieve the row with the given index using a direct query.
     * Such a row must exist.
     * We assume that the database has been initialized, and the argument has been range checked.
     */
    private fun rowDirectGet(index: Long): RowData {
        var result: RowData
        val args = arrayOf(index.toString())
        mExpressionDB!!.rawQuery(SQL_GET_ROW, args).use { resultC ->
            return if (!resultC.moveToFirst()) {
                badDBset()
                makeBadRow()
            } else {
                result = RowData(resultC.getBlob(1), resultC.getInt(2) /* flags */,
                        resultC.getLong(3) /* timestamp */)
                result
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

        private const val SQL_CREATE_ENTRIES = (
                "CREATE TABLE " + ExpressionEntry.TABLE_NAME + " ("
                        + BaseColumns._ID + " INTEGER PRIMARY KEY,"
                        + ExpressionEntry.COLUMN_NAME_EXPRESSION + " BLOB,"
                        + ExpressionEntry.COLUMN_NAME_FLAGS + " INTEGER,"
                        + ExpressionEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)")
        private const val SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + ExpressionEntry.TABLE_NAME
        private const val SQL_GET_MIN = ("SELECT MIN(" + BaseColumns._ID
                + ") FROM " + ExpressionEntry.TABLE_NAME)
        private const val SQL_GET_MAX = ("SELECT MAX(" + BaseColumns._ID
                + ") FROM " + ExpressionEntry.TABLE_NAME)
        private const val SQL_GET_ROW = ("SELECT * FROM " + ExpressionEntry.TABLE_NAME
                + " WHERE " + BaseColumns._ID + " = ?")
        private const val SQL_GET_ALL = ("SELECT * FROM " + ExpressionEntry.TABLE_NAME
                + " WHERE " + BaseColumns._ID + " <= ? AND " +
                BaseColumns._ID + " >= ?" + " ORDER BY " + BaseColumns._ID + " DESC ")
        // We may eventually need an index by timestamp. We don't use it yet.
        private const val SQL_CREATE_TIMESTAMP_INDEX = (
                "CREATE INDEX timestamp_index ON " + ExpressionEntry.TABLE_NAME + "("
                        + ExpressionEntry.COLUMN_NAME_TIMESTAMP + ")")
        private const val SQL_DROP_TIMESTAMP_INDEX = "DROP INDEX IF EXISTS timestamp_index"

        // Never allocate new negative indices (row ids) >= MAXIMUM_MIN_INDEX.
        const val MAXIMUM_MIN_INDEX: Long = -10

        // Gap between negative and positive row ids in the database.
        // Expressions with index [MAXIMUM_MIN_INDEX .. 0] are not stored.
        private const val GAP = -MAXIMUM_MIN_INDEX + 1

        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "Expressions.db"
        const val CONTINUE_WITH_BAD_DB = false
    }

}
