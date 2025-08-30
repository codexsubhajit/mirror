package com.example.modernandroidui.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray

object CommonAttendanceApi {
    suspend fun sendBatchAttendance(
        bearerToken: String,
        attendancePayloads: List<Map<String, Any>>
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val jsonArray = JSONArray()
            attendancePayloads.forEach { item ->
                val obj = org.json.JSONObject(item)
                jsonArray.put(obj)
            }
            val mediaType = "application/json".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, jsonArray.toString())
            val request = Request.Builder()
                .url("https://web.nithrapeople.com/v1/api/common-api-attendance-store")
                .method("POST", body)
                .addHeader("Authorization", bearerToken)
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val success = response.isSuccessful
            Log.d("CommonAttendanceApi", "Response code: ${response.code}")
            Log.d("CommonAttendanceApi", "Response body: $responseBody")

            Log.d("AttendanceScreen", "Response body: $responseBody")

            response.close()
            return@withContext Pair(success, responseBody)
        } catch (e: Exception) {
            Log.e("CommonAttendanceApi", "Exception: ${e.message}", e)
            Log.d("AttendanceScreen", "Response body: ${e.message}")
            return@withContext Pair(false, e.message)
        }
    }
}
