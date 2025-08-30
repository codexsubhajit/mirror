
package com.example.modernandroidui.ui.screens

import kotlinx.coroutines.withContext
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import okhttp3.MediaType.Companion.toMediaType
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.EmployeeEntity
import com.example.modernandroidui.luxand.LuxandTrackerManager
import com.example.modernandroidui.luxand.FacesProcessor
import com.example.modernandroidui.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.Executors
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.luxand.FacesView
//import com.example.modernandroidui.luxand.LuxandTrackerManager.Companion.FACE_DATA_FILE
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay


@Composable
fun RegistrationAnimatedOverlay(
    visible: Boolean,
    showSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val bgColor = MaterialTheme.colorScheme.primary
    // Loader animation state for in-progress
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val dotAnim = remember { Animatable(0f) }
    LaunchedEffect(visible, showSuccess) {
        if (visible && !showSuccess) {
            scale.snapTo(0.7f)
            alpha.snapTo(0f)
            dotAnim.snapTo(0f)
            scale.animateTo(1.1f, animationSpec = tween(350))
            scale.animateTo(1f, animationSpec = tween(250))
            alpha.animateTo(1f, animationSpec = tween(250))
            // Animate loader dots
            while (visible && !showSuccess) {
                dotAnim.animateTo(1f, animationSpec = tween(900))
                dotAnim.snapTo(0f)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!showSuccess) {
            // Loader dots and text for registration in progress
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    for (i in 0..2) {
                        val dotAlpha = if (dotAnim.value > i * 0.33f) 1f else 0.3f
                        Box(
                            Modifier
                                .size(22.dp)
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value,
                                    alpha = alpha.value * dotAlpha
                                )
                                .background(Color.White, shape = MaterialTheme.shapes.small)
                        )
                        if (i < 2) Spacer(Modifier.width(12.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Registration in process",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer(alpha = alpha.value)
                )
            }
        } else {
            // Only show the static success icon and text, no animation or confetti
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.Center)
            ) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.55f)
                    lineTo(size.width * 0.45f, size.height * 0.8f)
                    lineTo(size.width * 0.82f, size.height * 0.25f)
                }
                // Main green circle
                drawCircle(
                    color = bgColor,
                    radius = size.minDimension / 2,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2),
                    alpha = 0.98f
                )
                // Checkmark
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "Registration Complete!",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            )
        }
    }
}

@Composable
fun RegistrationScreen(
    employee: EmployeeEntity,
    luxandTrackerManager: LuxandTrackerManager,
    onRegistrationSuccess: () -> Unit,
    onRegistrationError: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var registrationInProgress by remember { mutableStateOf(false) }
    var registrationSuccess by remember { mutableStateOf(false) }
    var showRegistrationAnimation by remember { mutableStateOf(false) }
    var faceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var countdownStartTime by remember { mutableStateOf<Long?>(null) }
    var countdownFaceId by remember { mutableStateOf<String?>(null) }
    var countdownActive by remember { mutableStateOf(false) }
    val db = remember { AppDatabase.getInstance(context) }
    val employeeDao = remember { db.employeeDao() }

    // Camera lens facing state (front/back)
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val facesViewRef = remember { arrayOfNulls<FacesView>(1) }
    // Top bar with back button
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { onBack() }) {
            Text("Back to Employee List")
        }
    }
    val previewViewRef = remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )
    val permissionRequested = remember { mutableStateOf(false) }

    // Request camera permission as soon as the screen opens if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested.value) {
            permissionRequested.value = true
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required. Please grant permission in settings.")
        }
        return
    }

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

    // Rebind camera when lensFacing changes
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
                    Log.d("RegistrationScreen", "Faces detected: ${faces.size}")

                    // --- FacesView integration: set transform and faces ---
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

                    // --- Unified condition check hierarchy and status message ---
                    if (faces.isEmpty()) {
                        countdownStartTime = null
                        countdownFaceId = null
                        countdownActive = false
                        statusMessage = "Face not found"
                        registrationInProgress = false
                    } else if (SessionManager.lightCondition != 1) {
                        countdownStartTime = null
                        countdownFaceId = null
                        countdownActive = false
                        statusMessage = SessionManager.getText()
                        registrationInProgress = false
                    } else {
                        val face = faces[0]
                        faceRect = face.rect
                        val faceName = face.name.trim()
                        val allEmployees = employeeDao.getAll()
                        val alreadyRegistered = allEmployees.any { it.id.trim() == faceName && it.faceRegistered }
                        if (alreadyRegistered) {
                            countdownStartTime = null
                            countdownFaceId = null
                            countdownActive = false
                            statusMessage = "This face is already registered as $faceName."
                            registrationInProgress = false
                            registrationSuccess = false
                        } else {
                            // Start or continue countdown
                            val now = System.currentTimeMillis()
                            if (!countdownActive || countdownFaceId != faceName) {
                                countdownStartTime = now
                                countdownFaceId = faceName
                                countdownActive = true
                            }
                            val elapsed = now - (countdownStartTime ?: now)
                            if (countdownActive && countdownFaceId == faceName && elapsed < 3000) {
                                statusMessage = null
                                registrationInProgress = false
                            } else if (countdownActive && countdownFaceId == faceName && elapsed >= 3000) {
                                // All conditions held for 3 seconds, proceed to register
                                countdownActive = false
                                countdownStartTime = null
                                countdownFaceId = null
                                if (!registrationInProgress && !registrationSuccess) {
                                    statusMessage = null
                                    registrationInProgress = true
                                    //showRegistrationAnimation = true
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        kotlinx.coroutines.delay(1000)
                                    }
                                    val empID = employee.id.trim()
                                    face.unlock()
                                    face.name = empID
                                    face.lock()
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        kotlinx.coroutines.delay(500)
                                    }
                                    //registrationSuccess = true
                                    //registrationInProgress = false
                                    //statusMessage = "Registration successful!"
                                    val tempJsonFile = java.io.File(context.filesDir, "faces_extracted.json")
                                    if (tempJsonFile.exists()) {
                                        tempJsonFile.delete()
                                        Log.d("RegistrationScreen", "Deleted old faces_extracted.json before extraction")
                                    }
                                    val file = File(context.filesDir, "faces.dat")
                                    FacesProcessor.save(file)
                                    if (!com.chaquo.python.Python.isStarted()) {
                                        com.chaquo.python.Python.start(
                                            com.chaquo.python.android.AndroidPlatform(context)
                                        )
                                    }
                                    val py = com.chaquo.python.Python.getInstance()
                                    val pyModule = py.getModule("trackerMemoryTool")
                                    val TrackerData = pyModule.get("TrackerData")
                                    val trackerData = TrackerData?.callAttr("from_binary", file.absolutePath)
                                    val extractByNameResult = trackerData?.callAttr("extract_profile_by_name", empID)
                                    Log.d("RegistrationScreen", "extract_profile_by_name('$empID') result: $extractByNameResult")
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        kotlinx.coroutines.delay(2000)
                                    }
                                    trackerData?.callAttr("save_to_json", tempJsonFile.absolutePath)
                                    val jsonString = tempJsonFile.readText()
                                    Log.d("RegistrationScreen", "trackerMemoryTool Chaquopy save_to_json output length: ${jsonString.length}")
                                    // 3. Capture JPG image of detected face (must be on main thread)
                                    val faceJpgBase64: String? = try {
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            val imageBitmap = imageProxy.toBitmap()
                                            if (imageBitmap == null) {
                                                Log.e("RegistrationScreen", "imageProxy.toBitmap() is null!")
                                                null
                                            } else {
                                                val rect = faceRect
                                                if (rect == null) {
                                                    Log.e("RegistrationScreen", "faceRect is null when capturing face image!")
                                                    null
                                                } else {
                                                    // Expand crop area: 2x face rect, centered, not exceeding image bounds
                                                    val centerX = rect.centerX()
                                                    val centerY = rect.centerY()
                                                    val cropWidth = (rect.width() * 2).coerceAtMost(imageBitmap.width)
                                                    val cropHeight = (rect.height() * 2).coerceAtMost(imageBitmap.height)
                                                    val left = (centerX - cropWidth / 2).coerceAtLeast(0)
                                                    val top = (centerY - cropHeight / 2).coerceAtLeast(0)
                                                    val right = (left + cropWidth).coerceAtMost(imageBitmap.width)
                                                    val bottom = (top + cropHeight).coerceAtMost(imageBitmap.height)
                                                    val finalWidth = right - left
                                                    val finalHeight = bottom - top
                                                    if (finalWidth <= 0 || finalHeight <= 0) {
                                                        Log.e("RegistrationScreen", "Invalid expanded crop size: width=$finalWidth, height=$finalHeight")
                                                        null
                                                    } else {
                                                        val cropped = Bitmap.createBitmap(
                                                            imageBitmap,
                                                            left,
                                                            top,
                                                            finalWidth,
                                                            finalHeight
                                                        )
                                                        val outputStream = java.io.ByteArrayOutputStream()
                                                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                                                        val jpgBytes = outputStream.toByteArray()
                                                        Log.d("RegistrationScreen", "Cropped face image (expanded) size: ${jpgBytes.size}")
                                                        if (jpgBytes.isEmpty()) {
                                                            Log.e("RegistrationScreen", "Cropped face image is empty!")
                                                            null
                                                        } else {
                                                            val base64 = android.util.Base64.encodeToString(jpgBytes, android.util.Base64.NO_WRAP)
                                                            Log.d("RegistrationScreen", "faceJpgBase64 length: ${base64.length}")
                                                            base64
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("RegistrationScreen", "Failed to capture face image: ${e.message}")
                                        null
                                    }
                                    // 4. Upload to API
                                    if (jsonString != null && faceJpgBase64 != null) {
                                        try {
                                            val apiUrl = "https://web.nithrapeople.com/v1/api/face_register"
                                            val body = okhttp3.MultipartBody.Builder()
                                                .setType(okhttp3.MultipartBody.FORM)
                                                .addFormDataPart("employeeid", empID)
                                                .addFormDataPart("facetemplate", empID)
                                                .addFormDataPart(
                                                    "mirror_image",
                                                    "face.jpg",
                                                    okhttp3.RequestBody.create(
                                                        "image/jpeg".toMediaType(),
                                                        android.util.Base64.decode(faceJpgBase64, android.util.Base64.NO_WRAP)
                                                    )
                                                )
                                                .addFormDataPart("json_trackedata", jsonString)
                                                .build()
                                            val client = okhttp3.OkHttpClient()
                                            val bearerToken = SessionManager.getToken(context)
                                            val request = okhttp3.Request.Builder()
                                                .url(apiUrl)
                                                .addHeader("Authorization", "Bearer $bearerToken")
                                                .addHeader("Accept", "application/json")
                                                .post(body)
                                                .build()
                                            val response = client.newCall(request).execute()
                                            val responseBody = response.body?.string()
                                            Log.d("RegistrationScreen", "Face registration API response: $responseBody")
                                            if (response.isSuccessful) {
                                                registrationSuccess = true
                                                Log.d("RegistrationScreen", "Face registration API success")
                                                // Save mirror_image as "face.jpg" (or use a unique name if available)
                                                // Save mirror image with unique name (employeeId + timestamp)
                                                val mirrorImageName = "face_${employee.id}_${System.currentTimeMillis()}.jpg"
                                                val mirrorImageFile = File(context.filesDir, mirrorImageName)
                                                try {
                                                    val jpgBytes = android.util.Base64.decode(faceJpgBase64, android.util.Base64.NO_WRAP)
                                                    mirrorImageFile.writeBytes(jpgBytes)
                                                } catch (e: Exception) {
                                                    Log.e("RegistrationScreen", "Failed to save mirror image: ${e.message}")
                                                }
                                                // Store full path in mirror_image
                                                val updatedEmployee = employee.copy(faceRegistered = true, mirror_image = mirrorImageFile.absolutePath)
                                                employeeDao.update(updatedEmployee)
                                                withContext(Dispatchers.Main) {
                                                    registrationInProgress = false
                                                    showRegistrationAnimation = true
                                                    delay(3000) // Show animation for 1.2s
                                                    showRegistrationAnimation = false
                                                    onRegistrationSuccess()
                                                    registrationSuccess = false
                                                }
                                            } else {
                                                statusMessage = "Registration failed: ${response.code} ${response.message}"
                                                registrationInProgress = false
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Registration failed: ${e.message}"
                                            registrationInProgress = false
                                            Log.e("RegistrationScreen", "API error: ${e.message}")
                                        }
                                    } else {
                                        statusMessage = "Failed to extract face data or image."
                                        registrationInProgress = false
                                    }

                                    //onRegistrationSuccess()
                                }
                            } else {
                                // Face changed or condition broken during countdown
                                countdownActive = false
                                countdownStartTime = null
                                countdownFaceId = null
                                statusMessage = "Hold still for 3 seconds with the same face, good light, and face straight/close."
                                registrationInProgress = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RegistrationScreen", "Analyzer exception: ${e.message}", e)
                    statusMessage = "Error: ${e.message}"
                    registrationInProgress = false
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
            Log.e("RegistrationScreen", "Camera binding failed", exc)
            statusMessage = "Camera error: ${exc.localizedMessage}"
        }
    }

    Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
        AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx)
                previewViewRef.value = previewView
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        // FacesView overlay for face rectangles
        AndroidView(
            factory = { ctx ->
                FacesView(ctx, null).also { facesViewRef[0] = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Draw green rectangle overlay if face is detected (draw after AndroidView for correct layering)
        if (faceRect != null) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
//                drawRect(
//                    color = androidx.compose.ui.graphics.Color.Green,
//                    topLeft = androidx.compose.ui.geometry.Offset(faceRect!!.left.toFloat(), faceRect!!.top.toFloat()),
//                    size = androidx.compose.ui.geometry.Size(faceRect!!.width().toFloat(), faceRect!!.height().toFloat()),
//                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
//                )
            }
        }

        // Countdown overlay (show 3, 2, 1) - always on top of camera, even when registering
        if (countdownActive && countdownStartTime != null && countdownFaceId != null) {
            var now = System.currentTimeMillis()
            val elapsed = (now - countdownStartTime!!).coerceAtLeast(0)
            val secondsLeft = (3 - (elapsed / 1000)).coerceAtLeast(0)
            if (secondsLeft > 0) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp
                    ) {
                        Box(
                            Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = secondsLeft.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        // Unified registration animation overlay for both in-progress and success
        RegistrationAnimatedOverlay(
            visible = registrationInProgress || showRegistrationAnimation,
            showSuccess = showRegistrationAnimation,
            modifier = Modifier.zIndex(2f)
        )
        if (statusMessage != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    statusMessage!!,
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        if (registrationSuccess) {
            // Hide animation after registration is done
            LaunchedEffect(registrationSuccess) {
//                if (registrationSuccess) {
//                    showRegistrationAnimation = false
//                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                //Text("Registration successful!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

fun isFaceCenteredAndStraight(
    faceRect: android.graphics.Rect?,
    previewWidth: Int,
    previewHeight: Int,
    pan: Float?,
    tilt: Float?
): Boolean {
    if (faceRect == null) return false
    val maxPan = 35 // degrees (even more relaxed)
    val maxTilt = 35 // degrees
    val centerTolerance = 0.40f // 40% of width/height
    val faceCenterX = faceRect.centerX()
    val faceCenterY = faceRect.centerY()
    val centerX = previewWidth / 2
    val centerY = previewHeight / 2
    val dx = Math.abs(faceCenterX - centerX)
    val dy = Math.abs(faceCenterY - centerY)
    val allowedDx = (previewWidth * centerTolerance).toInt()
    val allowedDy = (previewHeight * centerTolerance).toInt()
    val isCentered = dx <= allowedDx && dy <= allowedDy
    val isStraight = (pan == null || pan == 0f || Math.abs(pan) <= maxPan) && (tilt == null || tilt == 0f || Math.abs(tilt) <= maxTilt)
    Log.d("RegistrationScreen", "isFaceCenteredAndStraight: dx=$dx/$allowedDx dy=$dy/$allowedDy pan=$pan tilt=$tilt isCentered=$isCentered isStraight=$isStraight")
    return isCentered && isStraight
}
