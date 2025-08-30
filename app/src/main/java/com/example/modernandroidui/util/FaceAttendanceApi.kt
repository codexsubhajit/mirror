package com.example.modernandroidui.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object FaceAttendanceApi {
    suspend fun sendFaceAttendance(
        token: String,
        empId: String,
        attendanceDatetime: String,
        mirrorImageUrl: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("employeeid", empId)
                put("attendance_datetime", attendanceDatetime)
                put("mirror_image", mirrorImageUrl)
            }
            Log.d("FaceAttendanceApi", "Sending JSON: $json")
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://web.nithrapeople.com/v1/api/face_attendance")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val success = response.isSuccessful
            Log.d("FaceAttendanceApi", "Response code: ${response.code}")
            Log.d("FaceAttendanceApi", "Response body: $responseBody")
            if (!success) {
                Log.e("FaceAttendanceApi", "Failed: ${response.code} ${response.message} $responseBody")
            }
            response.close()
            return@withContext success
        } catch (e: Exception) {
            Log.e("FaceAttendanceApi", "Exception: ${e.message}")
            return@withContext false
        }
    }
}
