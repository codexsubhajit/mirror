package com.example.modernandroidui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import java.io.File

object S3Uploader {
    private const val KEY = "DO00TXJG9L4MDP3TPW8L"
    private const val SECRET = "xf4OSgvZq2SCOpNUl8re8AhfnbyPQ45Kd9pdPdEKM/c"
    private const val ENDPOINT = "https://fra1.digitaloceanspaces.com"
    private const val BUCKET = "salarydocument"
    private const val BASE_URL = "https://salarydocument.fra1.digitaloceanspaces.com/employeeattendance/"

    fun isInternetAvailable(context: Context): Boolean {
        // Now, check actual internet access by pinging 8.8.8.8
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun uploadImage(context: Context, file: File, uniqueFileName: String, onResult: (Boolean, String?) -> Unit) {
        val credentials = BasicAWSCredentials(KEY, SECRET)
        val s3Client = AmazonS3Client(credentials)
        s3Client.setEndpoint(ENDPOINT)
        val transferUtility = TransferUtility.builder()
            .context(context.applicationContext)
            .s3Client(s3Client)
            .build()
        val observer = transferUtility.upload(
            BUCKET,
            "employeeattendance/$uniqueFileName",
            file,
            CannedAccessControlList.PublicRead
        )
        // Guard to ensure onResult is only called once
        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    if (called.compareAndSet(false, true)) {
                        val url = BASE_URL + uniqueFileName
                        onResult(true, url)
                    }
                } else if (state == TransferState.FAILED || state == TransferState.CANCELED) {
                    if (called.compareAndSet(false, true)) {
                        onResult(false, null)
                    }
                }
            }
            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                Log.e("S3Uploader", "Upload error", ex)
                if (called.compareAndSet(false, true)) {
                    onResult(false, null)
                }
            }
        })
    }
}
