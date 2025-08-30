package com.example.modernandroidui.repository

import android.content.Context
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.EmployeeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmployeeRepository(context: Context) {
    private val employeeDao = AppDatabase.getInstance(context).employeeDao()

    suspend fun getAllEmployees(): List<EmployeeEntity> = withContext(Dispatchers.IO) {
        employeeDao.getAll()
    }

    suspend fun searchEmployees(query: String): List<EmployeeEntity> = withContext(Dispatchers.IO) {
        employeeDao.searchByName(query)
    }

    suspend fun filterByBranch(branch: String): List<EmployeeEntity> = withContext(Dispatchers.IO) {
        employeeDao.filterByBranch(branch)
    }

    suspend fun filterByDepartment(department: String): List<EmployeeEntity> = withContext(Dispatchers.IO) {
        employeeDao.filterByDepartment(department)
    }

    suspend fun filterByFaceStatus(status: Boolean): List<EmployeeEntity> = withContext(Dispatchers.IO) {
        employeeDao.filterByFaceStatus(status)
    }

    suspend fun insertEmployee(employee: EmployeeEntity) = withContext(Dispatchers.IO) {
        employeeDao.insert(employee)
    }

    suspend fun updateEmployee(employee: EmployeeEntity) = withContext(Dispatchers.IO) {
        employeeDao.update(employee)
    }

    suspend fun deleteEmployee(employee: EmployeeEntity) = withContext(Dispatchers.IO) {
        employeeDao.delete(employee)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        employeeDao.clearAll()
    }
}
