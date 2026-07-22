package com.undy.puttlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "putts")
data class Putt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val distanceFeet: Double,
    val made: Boolean,
    val timestampMillis: Long
)
