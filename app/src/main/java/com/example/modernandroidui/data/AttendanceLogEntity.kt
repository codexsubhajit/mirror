package com.example.modernandroidui.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empId: String,
    val attendanceDatetime: Long, // Store as epoch millis
    val mirrorImagePath: String // Local file path to compressed image
)
