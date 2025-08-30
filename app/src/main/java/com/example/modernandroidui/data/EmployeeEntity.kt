package com.example.modernandroidui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photoRes: Int?,
    val mobile: String,
    val branch: String,
    val department: String,
    val faceRegistered: Boolean,
    val mirror_image: String? = null
)
