package com.apix.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apix.app.security.g4
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

// p1 = AppDatabase
@Database(
    entities  = [p2::class, p3::class],
    version   = 1,
    exportSchema = false
)
abstract class p1 : RoomDatabase() {

    abstract fun r1(): p4  // channelDao
    abstract fun r2(): p5  // categoryDao

    companion object {
        @Volatile private var X: p1? = null

        fun get(ctx: Context): p1 =
            X ?: synchronized(this) {
                X ?: bld(ctx).also { X = it }
            }

        private fun bld(ctx: Context): p1 {
            val pass = SQLiteDatabase.getBytes(
                g4.kg().toCharArray()
            )
            return Room.databaseBuilder(
                ctx.applicationContext,
                p1::class.java,
                "x.db"          // اسم الملف مموه
            )
            .openHelperFactory(SupportFactory(pass))
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}