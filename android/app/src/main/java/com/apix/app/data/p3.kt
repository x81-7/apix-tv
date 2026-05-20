package com.apix.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// p3 = CategoryEntity
@Entity(tableName = "t3")
data class p3(
    @PrimaryKey val a: String,   // id
    val b: String,               // name
    val c: String?,              // iconUrl
    val d: Int,                  // sortOrder
    val e: Boolean               // isHidden
)