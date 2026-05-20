package com.apix.app.data

import androidx.room.*

// p5 = CategoryDao
@Dao
interface p5 {

    @Query("SELECT * FROM t3 WHERE e = 0 ORDER BY d ASC")
    fun q1(): List<p3>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun i1(rows: List<p3>)

    @Query("DELETE FROM t3")
    fun d1()

    @Query("SELECT COUNT(*) FROM t3")
    fun n1(): Int
}