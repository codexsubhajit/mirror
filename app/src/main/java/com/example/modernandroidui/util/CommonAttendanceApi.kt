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
        val client = OkHttpClient()
        val url = "https://app.nithrapeople.com/api/mark-attendance"
        val mediaType = "application/json".toMediaTypeOrNull()
        val batchSize = 8
        val responses = mutableListOf<String>()
        // Defensive copy to guarantee order
        val attendanceList = attendancePayloads.toList()
        try {
            var allSuccess = true
            for (i in attendanceList.indices step batchSize) {
                val batch = attendanceList.subList(i, kotlin.math.min(i + batchSize, attendanceList.size))
                val jsonArray = JSONArray()
                batch.forEachIndexed { idx, item ->
                    val obj = org.json.JSONObject(item)
                    Log.d("CommonAttendanceApi", "Adding to batch idx=$idx, employee_id=${item["employee_id"]}")
                    jsonArray.put(obj)
                }
                Log.d("CommonAttendanceApi", "Batch upload $jsonArray")

                val body = RequestBody.create(mediaType, jsonArray.toString())
                val request = Request.Builder()
                    .url(url)
                    .method("POST", body)
                    .addHeader("Authorization", bearerToken)
                    .addHeader("Content-Type", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val success = response.isSuccessful
                Log.d("CommonAttendanceApi", "Batch ${i / batchSize + 1} Response code: ${response.code}")
                Log.d("CommonAttendanceApi", "Batch ${i / batchSize + 1} Response body: $responseBody")
                Log.d("AttendanceScreen", "Batch ${i / batchSize + 1} Response body: $responseBody")
                responses.add(responseBody ?: "")
                if (!success) allSuccess = false
                response.close()
            }
            return@withContext Pair(allSuccess, responses.joinToString("\n"))
        } catch (e: Exception) {
            Log.e("CommonAttendanceApi", "Exception: ${e.message}", e)
            Log.d("AttendanceScreen", "Response body: ${e.message}")
            return@withContext Pair(false, e.message)
        }
    }
}
