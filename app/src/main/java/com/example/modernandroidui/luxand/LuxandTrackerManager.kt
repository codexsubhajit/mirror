package com.example.modernandroidui.luxand

import android.app.Application
import android.content.Context
import android.util.Log
import com.luxand.FSDK
import com.luxand.FSDK.HTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LuxandTrackerManager(private val context: Context) {
    companion object {
        private const val TAG = "LuxandTrackerManager"
        private const val LICENSE_KEY = "MK+N5q+SIfht9Z+j4w4AJQEtaRhEpdITWAqSsJkaMur14PSCPQHcCpQJmUG9L5/MqzXdUV+5c/nN93OHYMPJa9FCdWqUXVd3/AS8geg72msTJZfCX6DFUhs+1rIB6jQzKR3NUM/W+VdVU/HV1cvkuR1mEfLKjhwEk6rP1mOAsy8=" // Replace with your real key
        private const val FACE_DATA_FILE = "faces.dat"
    }

    var tracker: HTracker? = null
        private set

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        // 1. Activate and initialize Luxand SDK
        val activateResult = FSDK.ActivateLibrary(LICENSE_KEY)
        Log.i(TAG, "FSDK Activating")
        if (activateResult != FSDK.FSDKE_OK) {
            Log.e(TAG, "Luxand activation failed: $activateResult")
            return@withContext false
        }
        val initResult = FSDK.Initialize()
        if (initResult != FSDK.FSDKE_OK) {
            Log.e(TAG, "Luxand initialization failed: $initResult")
            return@withContext false
        }
        // 2. Create tracker instance
        val trackerInstance = HTracker()
        val createResult = FSDK.CreateTracker(trackerInstance)
        if (createResult != FSDK.FSDKE_OK) {
            Log.e(TAG, "Tracker creation failed: $createResult")
            return@withContext false
        }
        tracker = trackerInstance
        // 3. Load or create tracker memory
        val file = File(context.filesDir, FACE_DATA_FILE)
        if (file.exists()) {
            Log.i(TAG, "faces.dat found, size: ${file.length()} bytes")
            FacesProcessor.load(file)
                FSDK.SetTrackerMultipleParameters(
                    trackerInstance,
                    "FaceDetection2PatchSize=256;" +
                            "TrimOutOfScreenFaces=true;"+
                            "TrimFacesWithUncertainFacialFeatures=true;"+
                            "Threshold=0.8;" +
                            "MemoryLimit=30000;" +
                            "Threshold2=0.9"+
                            "DetectFacialFeatures=true;" +
                            "HandleArbitraryRotations=true;" +
                            "DetermineFaceRotationAngle=true;" +
                            "DetectExpression=true;" +
                            "DetectAngles=true;"+
                            "DetectLiveness=true",
                    intArrayOf(0)
                )
            val loadResult = FSDK.LoadTrackerMemoryFromFile(trackerInstance, file.absolutePath)
            Log.i(TAG, "LoadTrackerMemoryFromFile result: $loadResult")
            // Try to count faces in tracker (if possible)
            val faceCountStrArr = arrayOf("")
            val countResult = FSDK.GetTrackerParameter(trackerInstance, "RecognizedFaceCount", faceCountStrArr, 256)
            val faceCount = faceCountStrArr[0].toLongOrNull() ?: -1L
            Log.i(TAG, "RecognizedFaceCount: $faceCount, result: $countResult")
            // Log all face names in tracker (workaround: enumerate known face IDs from FaceMapEntity)
            try {
                val db = com.example.modernandroidui.data.AppDatabase.getInstance(context)
                val faceMapDao = db.faceMapDao()
                val faceMaps = faceMapDao.getAllFaceMapsSync() // You need to implement this method as a suspend or blocking call
//                if (faceMaps.isNotEmpty()) {
//                    for (faceMap in faceMaps) {
//                        val namesArray = arrayOf("")
//                        val getNameResult = FSDK.GetAllNames(trackerInstance, faceMap.faceId, namesArray, 256)
//                        Log.i(TAG, "Tracker faceId=${faceMap.faceId}, name='${namesArray[0]}' (getNameResult=$getNameResult)")
//                    }
//                } else {
//                    Log.i(TAG, "No face IDs found in FaceMapEntity table for tracker enumeration.")
//                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enumerating face IDs from FaceMapEntity: ${e.message}")
            }
            if (loadResult == FSDK.FSDKE_OK) {
                Log.i(TAG, "Loaded tracker memory from faces.dat")
            } else {
                Log.e(TAG, "Failed to load faces.dat, creating new tracker memory. Error: $loadResult")
                return@withContext false
            }
        } else {
            Log.e(TAG, "faces.dat not found, created new tracker memory.")
        }
        true
    }

    suspend fun saveTrackerMemory() = withContext(Dispatchers.IO) {
        tracker?.let {
            val file = File(context.filesDir, FACE_DATA_FILE)
            val saveResult = FSDK.SaveTrackerMemoryToFile(it, file.absolutePath)
            if (saveResult == FSDK.FSDKE_OK) {
                Log.i(TAG, "Tracker memory saved to faces.dat")
            } else {
                Log.e(TAG, "Failed to save tracker memory: $saveResult")
            }
        }
    }
}

// Usage in Application or Activity:
// val manager = LuxandTrackerManager(context)
// lifecycleScope.launch { manager.initialize() }
// override fun onPause() { lifecycleScope.launch { manager.saveTrackerMemory() } }
