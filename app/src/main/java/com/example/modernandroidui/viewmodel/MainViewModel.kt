//mainviewmodel

package com.example.modernandroidui.viewmodel

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernandroidui.R
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.EmployeeEntity
import com.example.modernandroidui.data.FaceMapEntity
import com.example.modernandroidui.luxand.LuxandTrackerManager
import com.example.modernandroidui.model.UiModel
import com.luxand.FSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.example.modernandroidui.luxand.FacesProcessor
import com.example.modernandroidui.session.SessionManager
import com.example.modernandroidui.util.NetworkUtil
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStreamReader

// Login UI State

data class LoginUiState(
    val phone: String = "",
    val otp: String = "",
    val pin: String = "",
    val otpSent: Boolean = false,
    val loading: Boolean = false,
    val loginSuccess: Boolean = false,
    val phoneError: Int? = null,
    val otpError: Int? = null,
    val pinError: Int? = null,
    val generalError: Int? = null,
    val userId: Int? = null,
    val employerId: Int? = null,
    val otpServer: String? = null,
    val token: String? = null,
    val loginMessage: String? = null,
    val showPinField: Boolean = false
)

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onPhoneChanged(phone: String) {
        _uiState.value = _uiState.value.copy(
            phone = phone.take(10).filter { it.isDigit() },
            phoneError = null,
            generalError = null
        )
    }

    fun onOtpChanged(otp: String) {
        _uiState.value = _uiState.value.copy(
            otp = otp.take(6).filter { it.isDigit() },
            otpError = null,
            generalError = null
        )
    }

    fun onPinChanged(pin: String) {
        _uiState.value = _uiState.value.copy(
            pin = pin.filter { it.isDigit() },
            pinError = null,
            generalError = null
        )
    }

    fun sendOtp(context: Context, onPinRequired: (() -> Unit)? = null) {
        val phone = _uiState.value.phone
        if (phone.length != 10) {
            _uiState.value = _uiState.value.copy(phoneError = R.string.error_invalid_phone)
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, generalError = null, loginMessage = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://app.nithrapeople.com/api/login")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val jsonBody = JSONObject()
                    jsonBody.put("phone_number", phone)
                    com.example.modernandroidui.session.SessionManager.savePhoneNumber(context, phone)
                    jsonBody.put("is_mirror", false)
                    conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
                    val response = if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                    val json = JSONObject(response)
                    json
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("success", false)
                        put("message", e.localizedMessage ?: "Network error")
                    }
                }
            }
            if (result.optBoolean("success")) {
                val data = result.optJSONObject("data")
                Log.d("AttendanceSyncUtil","recieved $data")
                if (data != null && data.has("employer_id")) {
                    SessionManager.saveEmployerId(context, data.optInt("employer_id"))
                }
                val isverified = data?.optBoolean("verified") ?: false
                if (isverified) {
                    // If verified, show PIN page
                    _uiState.value = _uiState.value.copy(loading = false, loginMessage = result.optString("message"), showPinField = true)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    otpSent = true,
                    loading = false,
                    otp = "",
                    otpError = null,
                    userId = data?.optInt("userId"),
                    employerId = data?.optInt("employer_id"),
                    otpServer = data?.optString("otp"),
                    loginMessage = result.optString("message"),
                    showPinField = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    generalError = null,
                    loginMessage = result.optString("message").ifBlank { "Invalid phone number or user not found." },
                    showPinField = false
                )
            }
        }
    }

    fun verifyOtp() {
        val otp = _uiState.value.otp
        val userId = _uiState.value.userId
        if (otp.length != 6) {
            _uiState.value = _uiState.value.copy(otpError = R.string.error_invalid_otp)
            return
        }
        if (userId == null) {
            _uiState.value = _uiState.value.copy(generalError = R.string.error_invalid_phone)
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, generalError = null, loginMessage = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://app.nithrapeople.com/api/verify-otp")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val jsonBody = JSONObject()
                    jsonBody.put("userId", userId)
                    jsonBody.put("otp", otp)
                    conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    json
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("success", false)
                        put("message", e.localizedMessage ?: "Network error")
                    }
                }
            }
            if (result.optBoolean("success")) {
                val data = result.optJSONObject("data")
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loginSuccess = true,
                    token = data?.optString("token"),
                    employerId = data?.optInt("employer_id"),
                    loginMessage = result.optString("message")
                )
            } else {
                // Patch: Show a meaningful error message if API message is blank or looks like a URL
                var errorMsg = result.optString("message")
                if (errorMsg.isBlank() || errorMsg.startsWith("http")) {
                    errorMsg = "Invalid OTP or server error. Please try again."
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loginSuccess = false,
                    loginMessage = errorMsg
                )
            }
        }
    }

    fun verifyPin(context: Context) {
        val pin = _uiState.value.pin
        val phone = _uiState.value.phone
        if (pin.isBlank()) {
            _uiState.value = _uiState.value.copy(pinError = R.string.error_invalid_pin)
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, generalError = null, loginMessage = null)
        viewModelScope.launch {
            val (success, message, token, userId) = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://app.nithrapeople.com/api/login-pin")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val jsonBody = JSONObject()
                    jsonBody.put("phone_number", phone)
                    jsonBody.put("pin", pin)
                    conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
                    val response = if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    }
                    val json = JSONObject(response)
                    val success = json.optBoolean("success", false)
                    val message = json.optString("message", "")
                    val data = json.optJSONObject("data")
                    val token = data?.optString("token")
                    val userId = data?.optInt("user_id")
                    Quadruple(success, message, token, userId)
                } catch (e: Exception) {
                    Quadruple(false, e.localizedMessage ?: "Unknown error", null, null)
                }
            }
            if (success && token != null && userId != null) {
                SessionManager.saveSession(context, token, userId)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loginSuccess = true,
                    loginMessage = message,
                    pin = "",
                    token = token
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loginSuccess = false,
                    pinError = null,
                    loginMessage = message.ifBlank { "Invalid PIN. Please try again." }
                )
            }
        }
    }
}

class MainViewModel : ViewModel() {

    fun syncAttendanceLogs(context: Context, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _showSyncLoader.value = true
            _syncStatusText.value = "Syncing attendance logs..."
            _syncStatus.value = "Syncing attendance logs..."
            // Real internet connectivity check (ping 8.8.8.8)
            val hasInternet = withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
                    val exitCode = process.waitFor()
                    exitCode == 0
                } catch (e: Exception) {
                    false
                }
            }
            if (!hasInternet) {
                _syncStatus.value = "No internet connection"
                _showSyncLoader.value = false
                onComplete(false, "No internet connection")
                return@launch
            }
            try {
                val attendanceResult = com.example.modernandroidui.util.AttendanceSyncUtil.uploadAllLogsAndGetUrls(
                    context,
                    onProgress = { progress ->
                        _syncProgress.value = progress
                        _syncStatusText.value = "Syncing attendance logs... (${(progress * 100).toInt()}%)"
                    }
                )
                if (attendanceResult.isNotEmpty()) {
                    Log.d("SyncAttendanceLogs", "Attendance sync result: $attendanceResult")
                }
                _syncStatus.value = "Attendance sync complete!"
                _showSyncLoader.value = false
                onComplete(true, "Attendance sync complete!")
            } catch (e: Exception) {
                Log.e("SyncAttendanceLogs", "Attendance sync failed: ${e.localizedMessage}", e)
                _syncStatus.value = "Attendance sync failed: ${e.localizedMessage}"
                _showSyncLoader.value = false
                onComplete(false, "Attendance sync failed: ${e.localizedMessage}")
            }
        }
    }
    // Call this from your Activity after ViewModel creation, passing context
    fun loadBranchAndDepartmentFromDb(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val employees = db.employeeDao().getAll()
            val branches = employees.map { it.branch }.filter { it.isNotBlank() }.distinct().sorted()
            val departments = employees.map { it.department }.filter { it.isNotBlank() }.distinct().sorted()
            _branchList.value = branches
            _departmentList.value = departments
        }
    }
    // Dynamic branch and department lists for UI filters
    private val _branchList = MutableStateFlow<List<String>>(emptyList())
    val branchList: StateFlow<List<String>> = _branchList.asStateFlow()
    private val _departmentList = MutableStateFlow<List<String>>(emptyList())
    val departmentList: StateFlow<List<String>> = _departmentList.asStateFlow()
    private val _uiState = mutableStateOf<List<UiModel>>(emptyList())
    val uiState: State<List<UiModel>> = _uiState

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress
    val _syncStatusText = MutableStateFlow("")
    val syncStatusText: StateFlow<String> = _syncStatusText

    private val _showSyncLoader = MutableStateFlow(false)
    val showSyncLoader: StateFlow<Boolean> = _showSyncLoader

    fun setShowSyncLoader(value: Boolean) {
        _showSyncLoader.value = value
    }

    private val _mergeLogs = MutableStateFlow("")
    val mergeLogs: StateFlow<String> = _mergeLogs

    fun syncNow(token: String?, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _syncStatus.value = "Syncing..."
            try {
                // TODO: Implement API calls, Luxand, and Room logic here
                // Simulate network delay
                delay(2000)
                // On success:
                _syncStatus.value = "Sync complete!"
                onComplete(true, "Sync complete!")
            } catch (e: Exception) {
                _syncStatus.value = "Sync failed: ${e.localizedMessage}"
                onComplete(false, "Sync failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun syncNowFull(context: Context, token: String?, luxandTrackerManager: LuxandTrackerManager, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _showSyncLoader.value = true
            _syncStatus.value = "Syncing..."
            _syncProgress.value = 0f // Start progress bar at 0% immediately
            if (token.isNullOrBlank()) {
                _showSyncLoader.value = false
                onComplete(false, "No session token")
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    if (NetworkUtil.isInternetAvailable(context)) {
                        // --- EMPLOYEE/FACE SYNC (existing logic) ---
                        // luxandTrackerManager.initialize()
                        val facesDatFile = File(context.filesDir, "faces.dat")
                        FacesProcessor.load(facesDatFile)
                        if (facesDatFile.exists()) {
                            facesDatFile.writeBytes(byteArrayOf())
                            Log.d("SyncNowFull", "faces.dat cleared before merging")
                        }
                        val db = AppDatabase.getInstance(context)
                        val employeeDao = db.employeeDao()
                        val faceMapDao = db.faceMapDao()
                        val countUrl =
                            URL("https://app.nithrapeople.com/api/mirrorjsondatacount")
                        val countConn = countUrl.openConnection() as HttpURLConnection
                        countConn.requestMethod = "GET"
                        countConn.setRequestProperty("Authorization", "Bearer $token")
                        val countResponse = countConn.inputStream.bufferedReader().readText()
                        val countJson = JSONObject(countResponse)
                        val totalCount = countJson.optInt("data", 0)
                        val pageSize = 100
                        var offset = 0
                        val allEmployees = mutableListOf<JSONObject>()
                        while (offset < totalCount) {
                            Log.e("ChaquopyMerge", "getsyncemployees offset $offset")
                            val syncUrl =
                                URL("https://app.nithrapeople.com/api/getsyncemployees")
                            val syncConn = syncUrl.openConnection() as HttpURLConnection
                            syncConn.requestMethod = "POST"
                            syncConn.setRequestProperty("Authorization", "Bearer $token")
                            syncConn.setRequestProperty("Content-Type", "application/json")
                            syncConn.doOutput = true
                            val body = JSONObject()
                            body.put("limit", pageSize)
                            body.put("offset", offset)
                            syncConn.outputStream.use { it.write(body.toString().toByteArray()) }
                            val syncResponse = syncConn.inputStream.bufferedReader().readText()
                            val syncJson = JSONObject(syncResponse)
                            //val employeeList =
                            //    syncJson.optJSONObject("data")?.optJSONArray("employee_list")
                            val employeeList = syncJson.optJSONArray("data")
                            Log.i("mainViewModel EMP list", employeeList as String)

                            var fetched = 0
                            if (employeeList != null) {
                                for (i in 0 until employeeList.length()) {
                                    allEmployees.add(employeeList.getJSONObject(i))
                                }
                                fetched = employeeList.length()
                            }
                            offset += fetched
                            val shownCount = minOf(offset, totalCount)
                            _syncProgress.value = shownCount.toFloat() / totalCount.toFloat()
                            _syncStatusText.value =
                                "Downloading employees ($shownCount/$totalCount)"
                        }
                        employeeDao.clearAll()
                        faceMapDao.clearAll()
                        val faceMapEntities = mutableListOf<FaceMapEntity>()
                        val employeeEntities = mutableListOf<EmployeeEntity>()
                        val tracker = luxandTrackerManager.tracker
                        val branchSet = mutableSetOf<String>()
                        val departmentSet = mutableSetOf<String>()
                        for (emp in allEmployees) {
                            Log.i("mainViewModel EMP", "$emp")
                            val id = emp.optInt("id").toString()
                            val name = emp.optString("name")
                            val mobile = emp.optString("mobile")
                            val branch = emp.optString("branch_name", "")
                            val department = emp.optString("department_name", "")
                            if (branch.isNotBlank()) branchSet.add(branch)
                            if (department.isNotBlank()) departmentSet.add(department)
                            val mirrorImage = emp.optString("mirror_image", null)
                            val jsonTrackData = emp.optJSONObject("json_trackedata")
                            var faceRegistered = false
                            var faceId: Long? = null
                            if (jsonTrackData != null) {
                                faceRegistered = true
//                                faceMapEntities.add(
//                                    FaceMapEntity(
//                                        faceId,
//                                        id,
//                                        name,
//                                        mobile,
//                                        branch
//                                    )
//                                )
                                //val faces = jsonTrackData.optJSONArray("faces")
//                                if (faces != null && faces.length() > 0) {
//                                    val faceObj = faces.getJSONObject(0)
//                                    val templateBase64 = faceObj.optString("template")
//                                    if (templateBase64.isNotBlank() && tracker != null) {
//                                        val templateBytes = android.util.Base64.decode(
//                                            templateBase64,
//                                            android.util.Base64.DEFAULT
//                                        )
//                                        faceRegistered = true
//                                        faceId = faceObj.optLong("face_id", -1)
//                                        if (faceId != -1L) {
//                                            faceMapEntities.add(
//                                                FaceMapEntity(
//                                                    faceId,
//                                                    id,
//                                                    name,
//                                                    mobile,
//                                                    branch
//                                                )
//                                            )
//                                        }
//                                    }
//                                }
                            }
                            Log.i(
                                "mainViewModel",
                                "$id: name -$name branch - $branch department - $department faceRegistered - $faceRegistered"
                            )

                            employeeEntities.add(
                                EmployeeEntity(
                                    id,
                                    name,
                                    null,
                                    mobile,
                                    branch,
                                    department,
                                    faceRegistered,
                                    mirrorImage
                                )
                            )
                        }
                        // Update StateFlows for UI
                        _branchList.value = branchSet.toList().sorted()
                        _departmentList.value = departmentSet.toList().sorted()
                        employeeDao.insertAll(employeeEntities)
                        faceMapDao.insertAll(faceMapEntities)
//                        luxandTrackerManager.saveTrackerMemory()
//                        Log.d(
//                            "SyncNowFull",
//                            "luxandTrackerManager saving dat"
//                        )
                        val jsonTrackDataFiles = mutableListOf<File>()
                        for (emp in allEmployees) {
                            val jsonTrackData = emp.optJSONObject("json_trackedata")
                            if (jsonTrackData != null) {
                                val fileName = "emp_${emp.optInt("id")}_trackdata.json"
                                val file = File(context.filesDir, fileName)
                                file.writeText(jsonTrackData.toString())
                                jsonTrackDataFiles.add(file)
                            }
                        }
                        mergeTrackerDataWithLogs(context, jsonTrackDataFiles) { success, message ->
                            _mergeLogs.value += "Chaquopy merge finished: $message\n"
                            val dir = context.filesDir
                            val deleted =
                                dir.listFiles { file -> file.name.matches(Regex("emp_.*_trackdata\\.json")) }
                                    ?.map { it.delete() }?.count { it } ?: 0
                            Log.d(
                                "SyncNowFull",
                                "Deleted $deleted emp_{{empID}}_trackdata.json files after merging"
                            )
                        }
                    }
                    else
                    {
                        _showSyncLoader.value = false
                        onComplete(false, "No session token")
                        return@withContext
                    }
                }



                _syncProgress.value = 1f
                _syncStatusText.value = "Sync complete!"
                _syncStatus.value = "Sync complete!"
                onComplete(true, "Sync complete!")
                delay(2000)
                _showSyncLoader.value = false
            } catch (e: Exception) {
                _showSyncLoader.value = false
                _syncStatus.value = "Sync failed: ${e.localizedMessage}"
                onComplete(false, "Sync failed: ${e.localizedMessage}")
            }
        }
    }

    fun mergeTrackerDataWithLogs(context: Context, jsonFiles: List<File>, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _mergeLogs.value = "[Chaquopy] Starting merge...\n"
                Log.d("ChaquopyMerge", "[Chaquopy] Starting merge...")
                if (!com.chaquo.python.Python.isStarted()) {
                    com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(context))
                }
                val py = com.chaquo.python.Python.getInstance()
                val pyModule = py.getModule("trackerMemoryTool")
                val TrackerData = pyModule.get("TrackerData")
                val trackerDataList = mutableListOf<com.chaquo.python.PyObject>()

                for (file in jsonFiles) {

                    if (file.length() == 0L) {
                        _mergeLogs.value += "Skipped empty file: ${file.name}\n"
                        Log.w("ChaquopyMerge", "Skipped empty file: ${file.name}")
                        continue
                    }
                    try {
                        _mergeLogs.value += "Loading: ${file.name}\n"
                        Log.d("ChaquopyMerge", "Loading: ${file.name}")
                        val trackerData = TrackerData?.callAttr("from_file", file.absolutePath)
                        if (trackerData != null) {
                            trackerDataList.add(trackerData)
                        } else {
                            _mergeLogs.value += "Failed to load: ${file.name}\n"
                            Log.e("ChaquopyMerge", "Failed to load: ${file.name}")
                        }

                    } catch (e: Exception) {
                        _mergeLogs.value += "Invalid file: ${file.name} (${e.localizedMessage})\n"
                        Log.e("ChaquopyMerge", "Invalid file: ${file.name} (${e.localizedMessage})")
                    }

                }
                _mergeLogs.value += "Merging ${trackerDataList.size} files...\n"
                Log.d("ChaquopyMerge", "Merging ${trackerDataList.size} files...")
                val mergedTrackerData = trackerDataList[0]
                var l_track = 0;
                _mergeLogs.value += "Merging with file 1 \n"
                for (i in 1 until trackerDataList.size) {
//                    if(l_track != 0)
//                    {
                    mergedTrackerData?.callAttr("merge", trackerDataList[i])
                    _mergeLogs.value += "Merged file ${i + 1}\n"
                    Log.d("ChaquopyMerge", "Merged file ${i + 1}")
//                    }
//
//                    l_track++
                }

                    val outFile = File(context.filesDir, "faces.dat")
                    mergedTrackerData?.callAttr("save_to_binary", outFile.absolutePath)
                    _mergeLogs.value += "Saved merged tracker to faces.dat\n"
                    Log.d("ChaquopyMerge", "Saved merged tracker to faces.dat")



                // Debug: Save merged tracker as JSON for inspection to external storage
//                val externalJsonOutFile = File(context.filesDir, "faces_merged.json")
//                mergedTrackerData?.callAttr("save_to_json", externalJsonOutFile.absolutePath)
//                _mergeLogs.value += "Saved merged tracker to external storage: faces_merged.json\n"
//                Log.d("ChaquopyMerge", "Saved merged tracker to external storage: faces_merged.json")

                // After saving merged tracker as JSON, log all face names in the merged JSON
//                try {
//                    if (externalJsonOutFile.exists()) {
//                        val jsonString = externalJsonOutFile.readText()
//                        val mergedJson = org.json.JSONObject(jsonString)
//                        val faceNames = mutableSetOf<String>()
//                        val profiles = mergedJson.optJSONObject("profiles")
//                        if (profiles != null) {
//                            val keys = profiles.keys()
//                            while (keys.hasNext()) {
//                                val k = keys.next()
//                                val v = profiles.optString(k)
//                                if (!v.isNullOrBlank()) faceNames.add(v)
//                            }
//                        }
//                        val faces = mergedJson.optJSONArray("faces")
//                        if (faces != null) {
//                            for (i in 0 until faces.length()) {
//                                val face = faces.optJSONObject(i)
//                                val name = face?.optString("name")
//                                    ?: profiles?.optString(face?.opt("id")?.toString() ?: "")
//                                if (!name.isNullOrBlank()) faceNames.add(name)
//                            }
//                        }
//                        _mergeLogs.value += "Merged JSON face names: " + faceNames.sorted().joinToString(", ") + "\n"
//                        Log.d("ChaquopyMerge", "Merged JSON face names: ${faceNames.sorted().joinToString(", ")}")
//                    }
//                } catch (e: Exception) {
//                    _mergeLogs.value += "Error reading merged JSON for face names: ${e.localizedMessage}\n"
//                    Log.e("ChaquopyMerge", "Error reading merged JSON for face names: ${e.localizedMessage}")
//                }
            } catch (e: Exception) {
                _mergeLogs.value += "Error: ${e.localizedMessage}\n"
                onComplete(false, e.localizedMessage ?: "Unknown error")
                Log.d("ChaquopyMerge", e.localizedMessage ?: "Unknown error")
                val facesDatFile = File(context.filesDir, "faces.dat")
                if (facesDatFile.exists()) {
                    facesDatFile.writeBytes(byteArrayOf())
                    Log.e("ChaquopyMerge", "faces.dat cleared before merging")
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
