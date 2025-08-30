package com.example.modernandroidui.data

import androidx.room.*

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EmployeeEntity?
    @Query("SELECT * FROM employees")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE name LIKE '%' || :query || '%' ")
    suspend fun searchByName(query: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE branch = :branch")
    suspend fun filterByBranch(branch: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE department = :department")
    suspend fun filterByDepartment(department: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE faceRegistered = :status")
    suspend fun filterByFaceStatus(status: Boolean): List<EmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(employee: EmployeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<EmployeeEntity>)

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Delete
    suspend fun delete(employee: EmployeeEntity)

    @Query("DELETE FROM employees")
    suspend fun clearAll()
}
