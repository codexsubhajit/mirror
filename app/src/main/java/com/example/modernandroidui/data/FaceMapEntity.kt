package com.example.modernandroidui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_map")
data class FaceMapEntity(
    @PrimaryKey val faceId: Long,
    val employeeId: String,
    val name: String,
    val mobile: String,
    val branch: String
)
