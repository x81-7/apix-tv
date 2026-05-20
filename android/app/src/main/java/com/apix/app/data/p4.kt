package com.apix.app.data

import androidx.room.*

// p4 = ChannelDao
@Dao
interface p4 {

    @Query("SELECT * FROM t2 WHERE g = 0 ORDER BY f ASC")
    fun q1(): List<p2>

    @Query("SELECT * FROM t2 WHERE c = :x AND g = 0 ORDER BY f ASC")
    fun q2(x: String): List<p2>

    @Query("SELECT * FROM t2 WHERE b LIKE '%' || :x || '%' AND g = 0")
    fun q3(x: String): List<p2>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun i1(rows: List<p2>)

    @Query("DELETE FROM t2")
    fun d1()

    @Query("SELECT COUNT(*) FROM t2")
    fun n1(): Int
}