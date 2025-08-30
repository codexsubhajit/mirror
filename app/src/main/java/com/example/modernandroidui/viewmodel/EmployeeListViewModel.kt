package com.example.modernandroidui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernandroidui.data.EmployeeEntity
import com.example.modernandroidui.repository.EmployeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EmployeeListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmployeeRepository(application)
    private val _employees = MutableStateFlow<List<EmployeeEntity>>(emptyList())
    val employees: StateFlow<List<EmployeeEntity>> = _employees

    var searchQuery: String = ""
    var selectedBranch: String? = null
    var selectedDepartment: String? = null
    var faceStatus: String? = null

    fun loadEmployees() {
        viewModelScope.launch {
            _employees.value = repository.getAllEmployees()
        }
    }

    fun searchEmployees(query: String) {
        searchQuery = query
        viewModelScope.launch {
            _employees.value = if (query.isBlank()) repository.getAllEmployees() else repository.searchEmployees(query)
        }
    }

    fun filterEmployees(branch: String?, department: String?, faceStatus: String?) {
        selectedBranch = branch
        selectedDepartment = department
        this.faceStatus = faceStatus
        viewModelScope.launch {
            var result = repository.getAllEmployees()
            branch?.let { b -> result = result.filter { emp -> emp.branch == b } }
            department?.let { d -> result = result.filter { emp -> emp.department == d } }
            faceStatus?.let { status ->
                result = result.filter { emp ->
                    (status == "Registered" && emp.faceRegistered) || (status == "Not Registered" && !emp.faceRegistered)
                }
            }
            _employees.value = result
        }
    }

    fun registerFace(employee: EmployeeEntity) {
        viewModelScope.launch {
            repository.updateEmployee(employee.copy(faceRegistered = true))
            loadEmployees()
        }
    }

    fun deleteFace(employee: EmployeeEntity, bearerToken: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            // 1. Delete from API
            val apiSuccess = try {
                com.example.modernandroidui.repository.FaceApiRepository.deleteFaceFromApi(employee.id, bearerToken)
            } catch (e: Exception) {
                false
            }
            // 2. Update local DB if API succeeded
            if (apiSuccess) {
                repository.updateEmployee(employee.copy(faceRegistered = false))
                loadEmployees()
            }
            onResult(apiSuccess)
        }
    }
}
