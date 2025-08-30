package com.example.modernandroidui.luxand

import android.content.Context
import com.luxand.FSDK
import com.luxand.FSDK.HTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LuxandInitializer {
    private const val LICENSE_KEY = "your_license_key" // TODO: Replace with your real key
    private const val FACE_DATA_FILE = "faces.dat"
    private var tracker: HTracker? = null

    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Activate Luxand SDK
            val activationResult = FSDK.ActivateLibrary(LICENSE_KEY)
            if (activationResult != FSDK.FSDKE_OK) return@withContext false

            // 2. Initialize Luxand SDK
            val initResult = FSDK.Initialize()
            if (initResult != FSDK.FSDKE_OK) return@withContext false

            // 3. Create or load tracker
            val trackerInstance = HTracker()
            val createResult = FSDK.CreateTracker(trackerInstance)
            if (createResult != FSDK.FSDKE_OK) return@withContext false
            tracker = trackerInstance

            val file = File(context.filesDir, FACE_DATA_FILE)
            if (file.exists()) {
                val bytes = file.readBytes()
                FSDK.LoadTrackerMemoryFromBuffer(trackerInstance, bytes)
            }
            // else: tracker is already empty
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getTracker(): HTracker? = tracker
}

// Note: You must add the Luxand SDK .aar/.jar to your project and import the FSDK class for this to work.
// This is a utility object for initialization only. Use it from your Application or first Activity.
