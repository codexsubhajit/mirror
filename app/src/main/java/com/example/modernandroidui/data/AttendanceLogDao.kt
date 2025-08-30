package com.example.modernandroidui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.room.Delete



@Dao
interface AttendanceLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AttendanceLogEntity)

    @Query("SELECT * FROM attendance_logs WHERE empId = :empId ORDER BY attendanceDatetime DESC LIMIT 1")
    suspend fun getLastLogForEmployee(empId: String): AttendanceLogEntity?

    @Query("SELECT * FROM attendance_logs ORDER BY attendanceDatetime DESC")
    suspend fun getAllLogs(): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs WHERE empId = :empId AND attendanceDatetime > :since")
    suspend fun getLogsForEmployeeWithinWindow(empId: String, since: Long): List<AttendanceLogEntity>

    @Delete
    suspend fun deleteLogs(logs: List<AttendanceLogEntity>)
}
