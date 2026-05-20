package com.apix.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// p2 = ChannelEntity
@Entity(tableName = "t2")
data class p2(
    @PrimaryKey val a: String,   // id
    val b: String,               // name
    val c: String,               // categoryId
    val d: String?,              // logoUrl
    val e: String,               // streamJson
    val f: Int,                  // sortOrder
    val g: Boolean,              // isHidden
    val h: Long                  // updatedAt
)