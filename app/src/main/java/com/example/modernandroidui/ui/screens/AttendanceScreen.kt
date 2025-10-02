//attendance screen

package com.example.modernandroidui.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
//New generated AttendanceScreen


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.FaceMapEntity
import com.example.modernandroidui.luxand.LuxandTrackerManager
import com.example.modernandroidui.luxand.FacesProcessor
import com.luxand.FSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import com.example.modernandroidui.data.SettingsDataStore
import com.example.modernandroidui.data.BranchDataStore
import java.net.HttpURLConnection
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.example.modernandroidui.luxand.FacesView
import kotlin.math.acos
import kotlin.math.round
import kotlin.math.sqrt
import com.example.modernandroidui.util.AttendanceSyncUtil
import com.example.modernandroidui.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    luxandTrackerManager: LuxandTrackerManager,
    onMatch: (FaceMapEntity?) -> Unit,
    onBack: () -> Unit
) {

    val context = LocalContext.current
    // State to block attendance marking while sync is running
    var isSyncing by remember { mutableStateOf(false) }
    val settingsDataStore = remember { SettingsDataStore(context) }
    val branchDataStore = remember { BranchDataStore(context) }
    // Internet connection state
    var isConnected by remember { mutableStateOf(true) }
    // Observe geofencingEnabledFlow as State
    val geofencingEnabledState by settingsDataStore.geofencingEnabledFlow.collectAsState(initial = false)
    // --- Mirror Geo-Fencing API logic ---
    // 0 = normal, 1 = require geofencing enabled in settings
    val mirrorGeoFencing = remember { com.example.modernandroidui.MainActivity.getSavedMirrorGeoFencing(context) ?: 0 }

    // If mirrorGeoFencing == 1 and geofencing is not enabled, block attendance and show message
    if (mirrorGeoFencing == 1 && !geofencingEnabledState) {
        Box(Modifier
            .fillMaxSize()
            .padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You are restricted to enable geofencing!\nPlease enable geo-fencing from the app's Settings page.", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Back to Menu") }
            }
        }
        return
    }

    // Check internet connection only
    LaunchedEffect(Unit) {
        // Check real internet connectivity by pinging Google DNS
        isConnected = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
                val exitCode = process.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    // Location/geofence state
    var deviceLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var geofenceChecked by remember { mutableStateOf(false) }
    var geofenceAllowed by remember { mutableStateOf(true) }
    // Room DAOs
    val employeeDao = AppDatabase.getInstance(context).employeeDao()
    val attendanceLogDao = AppDatabase.getInstance(context).attendanceLogDao()
    var matchedEmployee by remember { mutableStateOf<FaceMapEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var faceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Removed sync progress, status, and result state for direct upload
    var trackerLoaded by remember { mutableStateOf(false) }
    var trackerError by remember { mutableStateOf<String?>(null) }
    var faceStatus by remember { mutableStateOf("no_face") } // "no_face", "not_matched", "matched"
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var animationText by remember { mutableStateOf("") }
    // Track last animation time per employeeId to prevent duplicate animation within 1 min
    val lastAnimationTimes = remember { mutableStateMapOf<String, Long>() }
    // Track blocked employee and time for 1 min message
    var blockedEmpId by remember { mutableStateOf<String?>(null) }
    var blockedUntil by remember { mutableStateOf<Long?>(null) }
    val facesViewRef = remember { arrayOfNulls<FacesView>(1) }

    // Persist last animation times using SharedPreferences
    val sharedPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
    // On first composition, load persisted times
    LaunchedEffect(Unit) {
        val all = sharedPrefs.all
        for ((key, value) in all) {
            if (key.startsWith("lastAnimTime_")) {
                val empId = key.removePrefix("lastAnimTime_")
                val time = (value as? Long) ?: (value as? Int)?.toLong() ?: continue
                lastAnimationTimes[empId] = time
            }
        }
    }

    // Compose-style permission launcher for seamless permission flow
    var permissionStep by remember { mutableStateOf(0) } // 0: none, 1: camera, 2: location, 3: ready
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                // Now check location
                if (!locationPermissionGranted) permissionStep = 2 else permissionStep = 3
            } else {
                permissionStep = 1
            }
        }
    )
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            locationPermissionGranted = granted
            if (granted) permissionStep = 3 else permissionStep = 2
        }
    )
    // Initial check
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionStep = 1
        } else if (!locationPermissionGranted) {
            permissionStep = 2
        } else {
            permissionStep = 3
        }
    }
    // Launchers for permission requests
    LaunchedEffect(permissionStep) {
        if (permissionStep == 1 && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (permissionStep == 2 && !locationPermissionGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Get device location on open and when screen resumes
    val lifecycleOwner2 = LocalLifecycleOwner.current
    DisposableEffect(locationPermissionGranted, lifecycleOwner2) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                deviceLatLng = loc.latitude to loc.longitude
            }
            override fun onStatusChanged(p0: String?, p1: Int, p2: android.os.Bundle?) {}
            override fun onProviderEnabled(p0: String) {}
            override fun onProviderDisabled(p0: String) {}
        }
        fun tryGetLocation() {
            try {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                var found = false
                for (provider in providers) {
                    if (lm.isProviderEnabled(provider)) {
                        val lastLoc = lm.getLastKnownLocation(provider)
                        if (lastLoc != null) {
                            deviceLatLng = lastLoc.latitude to lastLoc.longitude
                            found = true
                            break
                        }
                    }
                }
                if (!found) {
                    for (provider in providers) {
                        if (lm.isProviderEnabled(provider)) {
                            lm.requestSingleUpdate(provider, locationListener, Looper.getMainLooper())
                        }
                    }
                }
            } catch (e: Exception) {
                locationError = "Failed to get location: ${e.message}"
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && locationPermissionGranted) {
                tryGetLocation()
            }
        }
        lifecycleOwner2.lifecycle.addObserver(observer)
        if (locationPermissionGranted) {
            tryGetLocation()
        }
        onDispose {
            lifecycleOwner2.lifecycle.removeObserver(observer)
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Tracker is assumed to be initialized after sync/merge. No need to initialize here.
    if (permissionStep == 1) {
        // Waiting for camera permission
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required. Please grant to continue.")
        }
        return
    }
    if (permissionStep == 2) {
        // Waiting for location permission
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Location permission required. Please grant to continue.")
        }
        return
    }
    // Only proceed if permissionStep == 3
    if (locationError != null) {
        Box(Modifier
            .fillMaxSize()
            .padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(locationError!!)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack){ Text("Back to Menu") }
            }
        }
        return
    }
    if (deviceLatLng == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Enable device's location.")
                Spacer(modifier = Modifier.height(16.dp))
                val context = LocalContext.current
                Button(onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text("Turn On Location")
                }
            }
        }
        return
    }

    // Geofencing check before camera: only once per open
    LaunchedEffect(deviceLatLng) {
        if (!geofenceChecked && deviceLatLng != null) {
            geofenceChecked = true
            // Check geofencing enabled
            val geofencingEnabled = settingsDataStore.geofencingEnabledFlow.firstOrNull() ?: false
            if (geofencingEnabled) {
                val branchId = branchDataStore.selectedBranchIdFlow.firstOrNull()
                Log.d("AttendanceScreen", "Geofencing enabled, branchId: $branchId")
                // Check internet connection before API call
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetworkInfo
                val isNetConnected = activeNetwork != null && activeNetwork.isConnected
                if (!isNetConnected) {
                    geofenceAllowed = false
                    locationError = "No internet connection"
                    Log.d("AttendanceScreen", "No internet connection before branch detail fetch.")
                    return@LaunchedEffect
                }
                if (branchId != null) {
                    // Fetch branch detail from API
                    try {
                        val branchDetail = withContext(Dispatchers.IO) {
                            val urlStr = "https://web.nithrapeople.com/v1/api/branch-detail?id=$branchId"
                            Log.d("AttendanceScreen", "Fetching branch detail from: $urlStr")
                            val url = java.net.URL(urlStr)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.setRequestProperty("Accept", "application/json")
                            val token = com.example.modernandroidui.session.SessionManager.getToken(context)
                            Log.d("AttendanceScreen", "Using Bearer token: $token")
                            if (!token.isNullOrBlank()) {
                                conn.setRequestProperty("Authorization", "Bearer $token")
                            }
                            val code = conn.responseCode
                            Log.d("AttendanceScreen", "Branch detail API response code: $code")
                            val response = if (code in 200..299) {
                                conn.inputStream.bufferedReader().readText()
                            } else {
                                conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                            }
                            Log.d("AttendanceScreen", "Branch detail API response body: $response")
                            JSONObject(response)
                        }
                        if (branchDetail.optBoolean("success")) {
                            val data = branchDetail.getJSONObject("data")
                            val branchLat = data.getString("latitude").toDoubleOrNull()
                            val branchLng = data.getString("longitude").toDoubleOrNull()
                            Log.d("AttendanceScreen","Longitude ${data.getString("longitude")}")
                            Log.d("AttendanceScreen","latitude ${data.getString("latitude")}")
                            if (branchLat != null && branchLng != null) {
                                val deviceLoc = Location("device").apply {
                                    latitude = deviceLatLng!!.first
                                    longitude = deviceLatLng!!.second
                                }
                                val branchLoc = Location("branch").apply {
                                    latitude = branchLat
                                    longitude = branchLng
                                }
                                val distance = deviceLoc.distanceTo(branchLoc) // in meters
                                val allowedRadius = 200f // meters, adjust as needed
                                Log.d("AttendanceScreen", "Device location: ${deviceLatLng!!.first}, ${deviceLatLng!!.second}, Branch location: $branchLat, $branchLng, Distance: $distance")
                                if (distance > allowedRadius) {
                                    geofenceAllowed = false
                                    locationError = "You are not within allowed branch geofence radius. Distance: ${distance.toInt()}m"
                                } else {
                                    geofenceAllowed = true
                                }
                            } else {
                                geofenceAllowed = false
                                locationError = "Branch location not set."
                            }
                        } else {
                            geofenceAllowed = false
                            locationError = "Failed to fetch branch details."
                            Log.d("AttendanceScreen", "Branch detail API did not return success: $branchDetail")
                        }
                    } catch (e: Exception) {
                        geofenceAllowed = false
                        locationError = "Error fetching branch details: ${e.message}"
                        Log.e("AttendanceScreen", "Exception fetching branch details", e)
                    }
                } else {
                    geofenceAllowed = false
                    locationError = "No branch selected."
                    Log.d("AttendanceScreen", "No branch selected for geofencing.")
                }
            } else {
                geofenceAllowed = true
            }
        }
    }
    if (!geofenceAllowed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                locationError ?: "Not within geofence.",
                modifier = Modifier.padding(12.dp)
            )
        }
        return
    }

    // Camera lens facing state (front/back)
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    val previewViewRef = remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    // If geofencing is ON and no internet, block attendance marking
    val blockAttendance = geofencingEnabledState && !isConnected

    // Gesture detector for swipe up/down to switch camera
    val gestureModifier = Modifier.pointerInput(lensFacing) {
        detectVerticalDragGestures { change, dragAmount ->
            if (dragAmount > 50) {
                // Swipe down: switch to back camera
                if (lensFacing != CameraSelector.LENS_FACING_BACK) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                }
            } else if (dragAmount < -50) {
                // Swipe up: switch to front camera
                if (lensFacing != CameraSelector.LENS_FACING_FRONT) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                }
            }
        }
    }

    // Top app bar with back button
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Attendance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Menu"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // Removed sync button and progress/result UI for direct upload
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .then(gestureModifier)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = androidx.camera.view.PreviewView(ctx)
                    previewViewRef.value = previewView
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            AndroidView(
                factory = { ctx ->
                    FacesView(ctx, null).also { facesViewRef[0] = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay for face rectangle
            if (faceRect != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // ...existing code...
                }
            }

            // --- No internet warning message ---
            if (!isConnected) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Text(
                        "No internet connection",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }

            // --- Attendance status and animation ---
            if (blockAttendance) {
                // Geofencing ON and no internet: block attendance marking
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(16.dp)
                ) {
                    Text("Attendance marking is disabled without internet (geofencing required)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                // --- Existing status/animation logic ---
                if (showSuccessAnimation) {
                    val transition = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(showSuccessAnimation) {
                        if (showSuccessAnimation) {
                            transition.snapTo(0f)
                            transition.animateTo(
                                targetValue = 1f,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                            kotlinx.coroutines.delay(2200)
                            transition.animateTo(
                                targetValue = 0f,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                            showSuccessAnimation = false
                            matchedEmployee = null
                            faceStatus = "no_face"
                        }
                    }
                    val alpha = transition.value
                    val scale = 0.95f + 0.05f * transition.value
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f * alpha))
                            .zIndex(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(visible = alpha > 0.01f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        this.scaleX = scale
                                        this.scaleY = scale
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(96.dp)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    animationText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (matchedEmployee != null && faceStatus!= "no_face") {
                    // Do not show anything, animation will handle feedback
                } else if (error != null && faceStatus!= "no_face") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                else if (error == null && faceStatus!= "no_face")
                {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        when (faceStatus) {
                            "not_matched" -> Text("Face Not Matched", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                else
                {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        when (faceStatus) {
                            "no_face" -> {
                                val now = System.currentTimeMillis()
                                if (blockedEmpId != null && blockedUntil != null && now < blockedUntil!!) {
                                    Text("Try again after 1 minute.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                                } else {
                                    Text("No face found", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            // Removed sync progress bar and result dialog
        }
    }

    // Rebind camera when lensFacing changes
    // Animation effect: when matchedEmployee is set, show animation and block further marking
    LaunchedEffect(matchedEmployee, faceStatus, showSuccessAnimation, blockedEmpId, blockedUntil) {
        // Block new attendance marking while animation/sound is running or if cooldown is active
        val now = System.currentTimeMillis()
        if (showSuccessAnimation) return@LaunchedEffect
//        if (blockedEmpId != null && blockedUntil != null && now < blockedUntil!!) {
//            // Cooldown active: do not allow attendance marking or API call
//            matchedEmployee = null
//            faceStatus = "no_face"
//            return@LaunchedEffect
//        }
    if (isSyncing) return@LaunchedEffect
//    if (matchedEmployee != null && faceStatus == "matched") {
//            val empId = matchedEmployee!!.employeeId
//            Log.i("AttendanceScreen","I am matching against $empId")
//            val lastAnimTime  = lastAnimationTimes[empId] ?: 0L
//            val windowMillis = 70_000L // 70 seconds (server-safe 1 min gap)
//            if (now - lastAnimTime >= windowMillis) {
//                animationText = "Marked attendance for ${matchedEmployee!!.name} at " + java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(now))
//                showSuccessAnimation = true
//                lastAnimationTimes[empId] = now
//                sharedPrefs.edit().putLong("lastAnimTime_" + empId, now).apply()
//                blockedEmpId = null
//                blockedUntil = null
//                // Save face image to file for upload (no DB interaction)
//                var imageFile: java.io.File? = null
//                try {
//                    val fileName = "attendance_${empId}_${now}.jpg"
//                    val bitmap = previewBitmap
//                    if (bitmap != null) {
//                        val file = java.io.File(context.filesDir, fileName)
//                        val out = java.io.FileOutputStream(file)
//                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
//                        out.flush()
//                        out.close()
//                        imageFile = file
//                        Log.d("AttendanceScreen", "Saved image at ${file.absolutePath}, size=${file.length()} bytes")
//                    }
//                } catch (e: Exception) {
//                    Log.e("AttendanceScreen", "Failed to save attendance image for upload: ${e.message}")
//                }
//
//                // Play voice for attendance marked using raw resource
//                try {
//                    Log.d("AttendanceScreen", "Attempting to play attn_mark.mp3 for empId=$empId at $now")
//                    var localMediaPlayer: android.media.MediaPlayer? = null
//                    val mediaPlayer = android.media.MediaPlayer.create(context, com.example.modernandroidui.R.raw.attn_mark)
//                    localMediaPlayer = mediaPlayer
//
//                    mediaPlayer.start()
//                } catch (e: Exception) {
//                    Log.e("AttendanceScreen", "Failed to play attn_mark.mp3: ${e.message}")
//                }
//            } else {
//                blockedEmpId = empId
//                blockedUntil = lastAnimTime + windowMillis
//                matchedEmployee = null
//                faceStatus = "no_face"
//            }
//        }
    }

    LaunchedEffect(lensFacing, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = cameraProviderFuture.get()
        val previewView = previewViewRef.value ?: return@LaunchedEffect
        val facesFile = java.io.File(context.filesDir, "faces.dat")
        if (com.example.modernandroidui.luxand.FacesProcessor.shouldReload(facesFile)) {
            com.example.modernandroidui.luxand.FacesProcessor.load(facesFile)
            com.example.modernandroidui.luxand.FacesProcessor.updateLastLoaded(facesFile)
        }
        val targetSize = android.util.Size(1280, 720)
        val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setAllowedResolutionMode(androidx.camera.core.resolutionselector.ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
            .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(targetSize, androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val preview = androidx.camera.core.Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .setOutputImageRotationEnabled(true)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val faces = FacesProcessor.acceptV3(imageProxy)
                    val facesView = facesViewRef[0]
                    if (facesView != null) {
                        val activity = context as? android.app.Activity
                        if (facesView.facesTransform == null && activity != null) {
                            activity.runOnUiThread {
                                val source = imageProxy.imageInfo.sensorToBufferTransformMatrix
                                val target = previewView.outputTransform?.matrix
                                if (target != null) {
                                    val matrix = Matrix()
                                    source.invert(matrix)
                                    val values = FloatArray(9)
                                    source.getValues(values)
                                    val length = kotlin.math.sqrt(values[0] * values[0] + values[3] * values[3])
                                    val angle = kotlin.math.round(kotlin.math.acos(values[0] / length) / Math.PI * 180) % 180
                                    if (angle in 45.0..135.0) {
                                        matrix.postScale(2.0F / imageProxy.height, 2.0F / imageProxy.width)
                                    } else {
                                        matrix.postScale(2.0F / imageProxy.width, 2.0F / imageProxy.height)
                                    }
                                    matrix.postTranslate(-1.0F, -1.0F)
                                    matrix.postConcat(target)
                                    facesView.facesTransform = matrix
                                }
                            }
                        }
                        // setFaces can be called from any thread if FacesView is thread-safe, otherwise also wrap in runOnUiThread
                        activity?.runOnUiThread {
                            facesView.setFaces(faces)
                        }
                    }
                    val empList = employeeDao.getAll()
                    if (faces.isEmpty()) {
                        faceRect = null
                        faceStatus = "no_face"
                        matchedEmployee = null
                        blockedEmpId = null
                        blockedUntil = null
                    } else {
                        // Block new matches while animation/sound is running
                        if (showSuccessAnimation) return@launch
                        val face = faces[0]
                        faceRect = face.rect

                        if (com.example.modernandroidui.session.SessionManager.lightCondition == 1) {
                            val emp = empList.find { it.id.trim() == face.name.trim() }
                            Log.d("AttendanceScreen", "Comparing face.name='${face.name.trim()}' to DB IDs...")

                            if (!isSyncing && emp != null && emp.faceRegistered) {
                                faceStatus = "matched"
                                matchedEmployee = FaceMapEntity(
                                    faceId = face.id,
                                    employeeId = emp.id,
                                    name = emp.name,
                                    mobile = emp.mobile,
                                    branch = emp.branch
                                )

                                val now = System.currentTimeMillis()
                                val lastAnimTime = lastAnimationTimes[emp.id] ?: 0L
                                val windowMillis = 70_000L // 70 seconds (server-safe 1 min gap)
                                val cooldownActive = (System.currentTimeMillis() - lastAnimTime) < windowMillis
                                if (!cooldownActive) {

                                    animationText = "Marked attendance for ${matchedEmployee!!.name} at " + java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(now))
                                    showSuccessAnimation = true
                                    lastAnimationTimes[emp.id] = now
                                    sharedPrefs.edit().putLong("lastAnimTime_" + emp.id, now).apply()


                                    // Play voice for attendance marked using raw resource
                                    try {
                                        Log.d("AttendanceScreen", "Attempting to play attn_mark.mp3 for empId=${emp.id} at $now")
                                        var localMediaPlayer: android.media.MediaPlayer? = null
                                        val mediaPlayer = android.media.MediaPlayer.create(context, com.example.modernandroidui.R.raw.attn_mark)
                                        localMediaPlayer = mediaPlayer

                                        mediaPlayer.start()
                                    } catch (e: Exception) {
                                        Log.e("AttendanceScreen", "Failed to play attn_mark.mp3: ${e.message}")
                                    }

                                // Attendance log logic with rolling window duplicate prevention
//                                val now = System.currentTimeMillis()
//                                val windowMillis = 70_000L // 70 seconds (server-safe 1 min gap)
//                                val logs = attendanceLogDao.getLogsForEmployeeWithinWindow(emp.id, now - windowMillis)
//                                val shouldLog = logs.isEmpty()
//                                if (shouldLog) {
                                    // Save face image to file
                                    val bitmap = imageProxy.toBitmap()
                                    val rect = face.rect
                                    val cropped = if (bitmap != null && rect != null) {
                                        try {
                                            // Expand crop area to 2x face rect, centered, but not exceeding bitmap bounds
                                            val cx = (rect.left + rect.right) / 2
                                            val cy = (rect.top + rect.bottom) / 2
                                            val w2 = (rect.width() * 2).coerceAtMost(bitmap.width)
                                            val h2 = (rect.height() * 2).coerceAtMost(bitmap.height)
                                            val left = (cx - w2 / 2).coerceAtLeast(0)
                                            val top = (cy - h2 / 2).coerceAtLeast(0)
                                            val right = (cx + w2 / 2).coerceAtMost(bitmap.width)
                                            val bottom = (cy + h2 / 2).coerceAtMost(bitmap.height)
                                            val width = right - left
                                            val height = bottom - top
                                            if (width > 0 && height > 0) {
                                                Bitmap.createBitmap(bitmap, left, top, width, height)
                                            } else null
                                        } catch (e: Exception) { null }
                                    } else null
                                    var imagePath = ""
                                    if (cropped != null) {
                                        try {
                                            val fileName = "attendance_${emp.id}_${now}.jpg"
                                            val file = java.io.File(context.filesDir, fileName)
                                            val out = java.io.FileOutputStream(file)
                                            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            out.flush()
                                            out.close()
                                            imagePath = file.absolutePath
                                            Log.d("AttendanceScreen", "Saved image at $imagePath, size=${file.length()} bytes")
                                        } catch (e: Exception) {
                                            Log.e("AttendanceScreen", "Failed to save attendance image: "+e.message)
                                        }
                                    }

                                        // No internet, insert into DB for later sync


                                        val log = com.example.modernandroidui.data.AttendanceLogEntity(
                                            empId = emp.id,
                                            attendanceDatetime = now,
                                            mirrorImagePath = imagePath
                                        )
                                        withContext(Dispatchers.IO) {
                                            attendanceLogDao.insert(log)
                                        }
                                        Log.d(
                                            "AttendanceScreen",
                                            "Attendance log inserted for ${emp.id} at $now, image: $imagePath (offline mode)"
                                        )
                                        kotlinx.coroutines.delay(200) // Small delay to ensure DB write
                                        isSyncing = false
                                        com.example.modernandroidui.util.AttendanceSyncUtil.uploadAllLogsAndGetUrls(
                                            context
                                        )

//                                        try {
//
//                                        } finally {
//                                            isSyncing = false
//                                        }
                                    }
                                else{
                                    Log.d("AttendanceScreen", "blocked for: ${emp.id}")

                                    blockedEmpId = emp.id
                                    blockedUntil = now + windowMillis
                                    faceStatus = "no_face"
                                }
                                //}
                            } else {
                                faceStatus = "not_matched"
                                matchedEmployee = null
                            }
                            error = null
                        } else {
                            faceStatus = ""
                            matchedEmployee = null
                            error = com.example.modernandroidui.session.SessionManager.getText()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceScreen", "Analyzer exception: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e("AttendanceScreen", "Camera binding failed", exc)
            error = "Camera error: ${exc.localizedMessage}"
        }
    }

}

// Helper to convert ImageProxy to Bitmap
fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

// Helper to convert YUV_420_888 ImageProxy to RGB byte array for Luxand
fun yuvToRGB(image: ImageProxy): ByteArray {
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()
    val ySize = yBuffer.remaining()
    val nv21 = ByteArray(ySize + image.width * image.height / 2)
    var position = 0
    for (row in 0 until image.height) {
        yBuffer.get(nv21, position, image.width)
        position += image.width
        yBuffer.position(ySize.coerceAtMost(yBuffer.position() - image.width + yPlane.rowStride))
    }
    val chromaHeight = image.height / 2
    val chromaWidth = image.width / 2
    val vRowStride = vPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    val vLineBuffer = ByteArray(vRowStride)
    val uLineBuffer = ByteArray(uRowStride)
    for (row in 0 until chromaHeight) {
        vBuffer.get(vLineBuffer, 0, vRowStride.coerceAtMost(vBuffer.remaining()))
        uBuffer.get(uLineBuffer, 0, uRowStride.coerceAtMost(uBuffer.remaining()))
        var vLineBufferPosition = 0
        var uLineBufferPosition = 0
        for (col in 0 until chromaWidth) {
            nv21[position++] = vLineBuffer[vLineBufferPosition]
            nv21[position++] = uLineBuffer[uLineBufferPosition]
            vLineBufferPosition += vPixelStride
            uLineBufferPosition += uPixelStride
        }
    }
    val rgb = ByteArray(image.width * image.height * 3)
    var outIndex: Int
    val outStride = 3 * image.width
    var yIndex: Int
    var cIndex = image.width * image.height
    for (i in 0 until image.height / 2) {
        yIndex = 2 * i * image.width
        outIndex = 6 * i * image.width
        for (j in 0 until image.width / 2) {
            val u = toUnsigned(nv21[cIndex].toInt())
            val v = toUnsigned(nv21[cIndex + 1].toInt())
            val r = (91881 * v shr 16) - 179
            val g = ((22544 * u + 46793 * v) shr 16) - 135
            val b = (116129 * u shr 16) - 226
            fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex].toInt()), outIndex)
            fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + 1].toInt()), outIndex + 3)
            fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + image.width].toInt()), outIndex + outStride)
            fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + image.width + 1].toInt()), outIndex + outStride + 3)
            yIndex += 2
            cIndex += 2
            outIndex += 6
        }
    }
    // Log a few pixel values for debugging (ensure log always prints)
    try {
        val debugPixels = StringBuilder()
        val maxIdx = minOf(10, image.width * image.height)
        for (i in 0 until maxIdx) {
            val base = i * 3
            val r = rgb[base].toUByte().toString(16)
            val g = rgb[base + 1].toUByte().toString(16)
            val b = rgb[base + 2].toUByte().toString(16)
            debugPixels.append("[$i]=#$r$g$b ")
        }
        Log.d("AttendanceScreen", "Sample RGB pixel values: $debugPixels")
    } catch (e: Exception) {
        Log.e("AttendanceScreen", "Error logging RGB pixel values: ${e.message}")
    }
    return rgb
}

// Helper to convert ImageProxy to RGB byte array using Bitmap only (no YuvToRgbConverter)
// This is a fallback for environments where YuvToRgbConverter is not available
fun imageProxyToRgbBuffer(image: ImageProxy, context: Context): ByteArray {
    val width = image.width
    val height = image.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    val rgbBuffer = ByteArray(width * height * 3)
    var idx = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = decodedBitmap.getPixel(x, y)
            rgbBuffer[idx++] = ((pixel shr 16) and 0xFF).toByte() // R
            rgbBuffer[idx++] = ((pixel shr 8) and 0xFF).toByte()  // G
            rgbBuffer[idx++] = (pixel and 0xFF).toByte()         // B
        }
    }
    return rgbBuffer
}

fun toUnsigned(value: Int): Int = if (value < 0) value + 256 else value
fun fillRGBBytes(rgb: ByteArray, r: Int, g: Int, b: Int, y: Int, outIndex: Int) {
    val yVal = y - 16
    val rVal = (298 * yVal + 409 * r + 128) shr 8
    val gVal = (298 * yVal - 100 * g - 208 * r + 128) shr 8
    val bVal = (298 * yVal + 516 * b + 128) shr 8
    rgb[outIndex] = rVal.coerceIn(0, 255).toByte()
    rgb[outIndex + 1] = gVal.coerceIn(0, 255).toByte()
    rgb[outIndex + 2] = bVal.coerceIn(0, 255).toByte()
}

// Helper to transform a face rectangle using a matrix
fun transformRect(rect: android.graphics.Rect, matrix: Matrix): RectF {
    val rectF = RectF(rect)
    matrix.mapRect(rectF)
    return rectF
}

// Helper to calculate the transformation matrix for the preview
fun calculateFaceTransform(
    previewWidth: Float,
    previewHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    isMirrored: Boolean = true // front camera
): Matrix {
    val matrix = Matrix()
    // Mirror horizontally for front camera
    if (isMirrored) {
        matrix.postScale(-1f, 1f, previewWidth / 2f, previewHeight / 2f)
    }
    // Scale to fit canvas
    val scaleX = canvasWidth / previewWidth
    val scaleY = canvasHeight / previewHeight
    matrix.postScale(scaleX, scaleY)
    return matrix
}

// Utility function to check if face is centered and straight (strict)
fun isFaceCenteredAndStraight(
    faceRect: android.graphics.Rect?,
    previewWidth: Int,
    previewHeight: Int,
    pan: Float?,
    tilt: Float?,
    centerThreshold: Float = 0.15f, // 15% of width/height
    angleThreshold: Float = 10f // degrees
): Boolean {
    if (faceRect == null || pan == null || tilt == null) return false
    val faceCenterX = (faceRect.left + faceRect.right) / 2f
    val faceCenterY = (faceRect.top + faceRect.bottom) / 2f
    val centerX = previewWidth / 2f
    val centerY = previewHeight / 2f
    val dx = Math.abs(faceCenterX - centerX) / previewWidth
    val dy = Math.abs(faceCenterY - centerY) / previewHeight
    val isCentered = dx < centerThreshold && dy < centerThreshold
    val isStraight = Math.abs(pan) < angleThreshold && Math.abs(tilt) < angleThreshold
    return isCentered && isStraight
}

//@Composable
//fun TestStaticImageFaceDetection(context: Context) {
//    val result = remember { mutableStateOf("") }
//    LaunchedEffect(Unit) {
//        kotlinx.coroutines.withContext(Dispatchers.Default) {
//            try {
//                val assetManager = context.assets
//                val inputStream = assetManager.open("face.jpg")
//                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
//                inputStream.close()
//                val width = bitmap.width
//                val height = bitmap.height
//                val rgbBuffer = ByteArray(width * height * 3)
//                var idx = 0
//                for (y in 0 until height) {
//                    for (x in 0 until width) {
//                        val pixel = bitmap.getPixel(x, y)
//                        rgbBuffer[idx++] = ((pixel shr 16) and 0xFF).toByte() // R
//                        rgbBuffer[idx++] = ((pixel shr 8) and 0xFF).toByte()  // G
//                        rgbBuffer[idx++] = (pixel and 0xFF).toByte()         // B
//                    }
//                }
//                val hImage = FSDK.HImage()
//                val rgbImageMode = FSDK.FSDK_IMAGEMODE()
//                rgbImageMode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT
//                val loadResult = FSDK.LoadImageFromBuffer(hImage, rgbBuffer, width, height, width * 3, rgbImageMode)
//                val facePosition = FSDK.TFacePosition()
//                val detectResult = FSDK.DetectFace(hImage, facePosition)
//                val logMsg = "LoadImageFromBuffer: $loadResult, DetectFace: $detectResult, xc=${facePosition.xc}, yc=${facePosition.yc}, w=${facePosition.w}"
//                //android.util.Log.d("TestStaticImageFaceDetection", logMsg)
//                result.value = logMsg
//            } catch (e: Exception) {
//                //android.util.Log.e("TestStaticImageFaceDetection", "Error: ${e.message}")
//                result.value = "Error: ${e.message}"
//            }
//        }
//    }
//    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        Text(result.value)
//    }
//}
