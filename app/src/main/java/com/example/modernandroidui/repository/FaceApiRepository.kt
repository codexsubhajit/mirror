package com.example.modernandroidui.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

object FaceApiRepository {
    suspend fun deleteFaceFromApi(empID: String, bearerToken: String): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://app.nithrapeople.com/api/face_delete"
        val json = JSONObject().apply {
            put("employeeid", empID)
            put("face_status", false)
        }


        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        response.isSuccessful
    }
}
