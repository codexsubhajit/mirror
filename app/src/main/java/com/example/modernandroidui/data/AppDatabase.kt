package com.example.modernandroidui.data

import android.content.Context
import androidx.room.Database
import com.example.modernandroidui.data.AttendanceLogEntity
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EmployeeEntity::class, FaceMapEntity::class, AttendanceLogEntity::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun faceMapDao(): FaceMapDao
    abstract fun attendanceLogDao(): AttendanceLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mirror_ai_db"
            )
            .fallbackToDestructiveMigration() // Allow destructive migration for dev
            .build().also { INSTANCE = it }
        }
    }
}
