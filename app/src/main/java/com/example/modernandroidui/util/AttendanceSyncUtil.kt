package com.example.modernandroidui.util

import android.content.Context
import android.util.Log
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.AttendanceLogEntity
import com.example.modernandroidui.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File

object AttendanceSyncUtil {
    // Mutex to prevent concurrent syncs
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun uploadSingleAttendance(context: Context, empId: String, timestamp: Long, imageUrl: String) {
        val token = SessionManager.getToken(context)
        if (token.isNullOrBlank()) {
            Log.e("AttendanceSyncUtil", "No token for single attendance upload!")
            return
        }
        val file = File(imageUrl)
        var url: String? = ""
        val result = mutableListOf<Pair<AttendanceLogEntity, String?>>()


        if (file.exists()) {
            val uniqueFileName = file.name // already unique in your logic
            val uploadResult = kotlinx.coroutines.suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                com.example.modernandroidui.util.S3Uploader.uploadImage(context, file, uniqueFileName) { success, uploadedUrl ->
                cont.resume(Pair(success, uploadedUrl), null)
                }
            }
            url = if (uploadResult.first) uploadResult.second else null
            Log.d("AttendanceSyncUtil", "Single Uploaded URL: $url")

        }
        val formattedDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))
//        val payload = mapOf(
//            "employeeid" to empId,
//            "attendance_datetime" to formattedDate,
//            "mirror_image" to url
//        )
//        val outgoingJson = com.google.gson.Gson().toJson(payload)
//        Log.d("AttendanceSyncUtil", "Single attendance outgoing JSON: $outgoingJson")
        try {
//            val apiSuccess = FaceAttendanceApi.sendFaceAttendance(
//                token = token,
//                empId = empId,
//                attendanceDatetime = formattedDate,
//                mirrorImageUrl = url
//            )
            val employerId = SessionManager.getEmployerId(context) // or getEmployerId if you have it

            val dataMap = mapOf(
                "employee_id" to (empId as Any),
                "recordTime" to (formattedDate as Any),
                "employer_id" to (employerId as Any),
                "target_image" to (url as Any),
                "type" to (3 as Any)
            )
            val outgoingJson2 = com.google.gson.Gson().toJson(dataMap)
            Log.d("AttendanceSyncUtil", "Single attendance outgoing JSON: $outgoingJson2")
            Log.d("AttendanceScreen", "starting upload $outgoingJson2")

            val payload = listOf(dataMap)

            val (success, response) = CommonAttendanceApi.sendBatchAttendance("Bearer $token", payload)

           // After successful upload to API, remove uploaded logs from SQLite
            if (success) {
                Log.d("AttendanceSyncUtil", "Single face attendance API for empId=$empId response=$response")
            }
        } catch (e: Exception) {
            Log.e("AttendanceSyncUtil", "Exception in single face attendance API: ${e.message}", e)
        }
    }
    suspend fun uploadAllLogsAndGetUrls(
        context: Context,
        onProgress: ((Float) -> Unit)? = null
    ): List<Pair<AttendanceLogEntity, String?>> {
        return syncMutex.withLock {
            val db = AppDatabase.getInstance(context)
            val logs = db.attendanceLogDao().getAllLogs()
            val result = mutableListOf<Pair<AttendanceLogEntity, String?>>()
            val token = SessionManager.getToken(context)
            if (!S3Uploader.isInternetAvailable(context)) {
                Log.e("AttendanceSyncUtil", "No internet connection.")
                return@withLock logs.map { it to null }
            }
            for ((index, log) in logs.withIndex()) {
                val localPath = log.mirrorImagePath
                val file = File(localPath)
                if (file.exists()) {
                    val uniqueFileName = file.name // already unique in your logic
                    val pair: Pair<AttendanceLogEntity, String?> = withContext(Dispatchers.IO) {
                        val uploadResult = kotlinx.coroutines.suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                            S3Uploader.uploadImage(context, file, uniqueFileName) { success, uploadedUrl ->
                                cont.resume(Pair(success, uploadedUrl), null)
                            }
                        }
                        val url = if (uploadResult.first) uploadResult.second else null
                        if (url != null) {
                            Log.d("AttendanceSyncUtil", "Uploaded image for empId=${log.empId}: $url")
                            // Send to face attendance API
                            val formattedDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(log.attendanceDatetime))
                            if (!token.isNullOrBlank()) {
                                try {
                                    val empIdInt = log.empId.toIntOrNull() ?: log.empId // fallback to string if not int
                                } catch (e: Exception) {
                                    Log.e("AttendanceSyncUtil", "Exception in face attendance API: ${e.message}", e)
                                }
                            } else {
                                Log.e("AttendanceSyncUtil", "No token for face attendance API call!")
                            }
                        } else {
                            Log.e("AttendanceSyncUtil", "Failed to upload image for empId=${log.empId}")
                        }
                        log to url
                    }
                    result.add(pair)
                } else {
                    result.add(log to null)
                }
                // Update progress after each log (show 0% at start, 100% at end)
                val progress = if (logs.isNotEmpty()) (index + 1).toFloat() / logs.size else 1f
                onProgress?.invoke(progress)
            }
            // Build batch payload for API
            val employerId = SessionManager.getEmployerId(context) // or getEmployerId if you have it
            val batchPayload = result.filter { it.second != null }
                .map { (log, url) ->
                    val recordTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(log.attendanceDatetime))
                    Log.d("AttendanceSyncUtil", "employee_id ${log.empId} recordTime=$recordTime")
                    mapOf(
                        "employee_id" to ((log.empId.toIntOrNull() ?: log.empId) as Any),
                        "recordTime" to (recordTime as Any),
                        "employer_id" to (employerId as Any),
                        "target_image" to (url as Any),
                        "type" to (3 as Any)
                    )
                }
            var batchSuccess = false
            if (batchPayload.isNotEmpty() && !token.isNullOrBlank()) {
                Log.d("AttendanceSyncUtil", "payload sending $batchPayload")

                val (success, response) = CommonAttendanceApi.sendBatchAttendance("Bearer $token", batchPayload)
                Log.d("AttendanceSyncUtil", "Batch attendance API response: success=$success, body=$response")
                batchSuccess = success
            }
            // After successful upload to API, remove uploaded logs from SQLite
            if (batchSuccess) {
                val successfullyUploadedLogs = result.filter { it.second != null }.map { it.first }
                if (successfullyUploadedLogs.isNotEmpty()) {
                    db.attendanceLogDao().deleteLogs(successfullyUploadedLogs)
                    Log.d("AttendanceSyncUtil", "Deleted ${successfullyUploadedLogs.size} uploaded logs from SQLite after API upload.")
                }
            }
            // Only return non-empty result if there was something to sync
            if (result.isEmpty() || result.all { it.second == null }) {
                return@withLock emptyList()
            }
            // Ensure progress is 100% at the end (in case of rounding)
            onProgress?.invoke(1f)
            return@withLock result
        }
    }
}
