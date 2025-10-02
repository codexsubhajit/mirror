//main activity

package com.example.modernandroidui

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import android.app.Activity
import android.content.Intent

// ...existing imports...
import com.example.modernandroidui.ui.screens.AttendanceLogScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.modernandroidui.ui.theme.ModernAndroidUITheme
import com.example.modernandroidui.ui.screens.MainScreen
import com.example.modernandroidui.ui.screens.MainMenuScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.modernandroidui.ui.screens.EmployeeListScreen
import com.example.modernandroidui.data.EmployeeEntity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

//import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.modernandroidui.ui.screens.SettingsScreen
import com.example.modernandroidui.session.SessionManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.luxand.LuxandTrackerManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import com.example.modernandroidui.ui.screens.AttendanceScreen
import androidx.compose.runtime.collectAsState
import com.example.modernandroidui.ui.screens.RegistrationScreen
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay

// Global process-wide sync flag (resets when app process is killed)
object SyncSessionState {
    var autoSyncTriggered = androidx.compose.runtime.mutableStateOf(false)
}
// Track if user skipped update this session
object UpdateSessionState {
    var updateSkipped = false
}
//import com.example.modernandroidui.ui.screens.RegistrationScreenWrapper

//import com.example.modernandroidui.ui.screens.TestStaticImageFaceDetection

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_NAME = "mirror_prefs"
        private const val KEY_MIRROR_GEO_FENCING = "mirror_geo_fencing"

        /**
         * Fetches mirror geo-fencing status from API and saves it in SharedPreferences.
         * Returns the value (0 or 1), or null if failed.
         */
        suspend fun fetchMirrorGeoFencingStatus(context: Context): Int? {
            // Check internet connectivity (ping)
            val hasInternet = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
                    val exitCode = process.waitFor()
                    exitCode == 0
                } catch (e: Exception) {
                    false
                }
            }
            if (!hasInternet) return null
            return try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val url = java.net.URL("https://web.nithrapeople.com/v1/api/mirror-geo-fencing")
                    Log.d("MirrorGeoFencing", "Calling API: $url")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val token = com.example.modernandroidui.session.SessionManager.getToken(context)
                    if (!token.isNullOrBlank()) {
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        Log.d("MirrorGeoFencing", "Added Bearer token to header")
                    } else {
                        Log.d("MirrorGeoFencing", "No Bearer token found, request will be unauthenticated")
                    }
                    val code = conn.responseCode
                    Log.d("MirrorGeoFencing", "Response code: $code")
                    val nres = if (code in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    }
                    Log.d("MirrorGeoFencing", "Response body: $nres")
                    val jsonObject = org.json.JSONObject(nres)
                    if (jsonObject.optBoolean("success", false)) {
                        val data = jsonObject.optJSONObject("data")
                        val mirrorGeoFencing = data?.optInt("mirror_geo_fencing", 0) ?: 0
                        Log.d("MirrorGeoFencing", "mirror_geo_fencing value: $mirrorGeoFencing")
                        // Save to SharedPreferences
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putInt(KEY_MIRROR_GEO_FENCING, mirrorGeoFencing).apply()
                        mirrorGeoFencing
                    } else {
                        Log.d("MirrorGeoFencing", "API success=false or missing data")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("MirrorGeoFencing", "Exception: ${e.localizedMessage}", e)
                null
            }
        }

        /**
         * Gets the last saved mirror geo-fencing value (0 or 1), or null if not set.
         */
        fun getSavedMirrorGeoFencing(context: Context): Int? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return if (prefs.contains(KEY_MIRROR_GEO_FENCING)) prefs.getInt(KEY_MIRROR_GEO_FENCING, 0) else null
        }

        @JvmStatic
        suspend fun verifyPinApi(context: android.content.Context, pin: String): Pair<Boolean, String> {
            val token = com.example.modernandroidui.session.SessionManager.getToken(context)
            return verifyPinApiInternal(token, pin)
        }

        private suspend fun verifyPinApiInternal(token: String?, pin: String): Pair<Boolean, String> {
            Log.d("PIN_API", "verifyPinApi called with token: $token, pin: $pin")
            if (token.isNullOrBlank()) {
                Log.e("PIN_API", "No token found")
                return false to "No token"
            }
            // Real internet connectivity check (ping 8.8.8.8)
            val hasInternet = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
                    val exitCode = process.waitFor()
                    exitCode == 0
                } catch (e: Exception) {
                    false
                }
            }
            if (!hasInternet) {
                Log.e("PIN_API", "No internet connection (ping failed)")
                return false to "No internet connection"
            }
            return try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val url = java.net.URL("https://web.nithrapeople.com/v1/api/checkpin")
                    Log.d("PIN_API", "URL created")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    Log.d("PIN_API", "Connection opened")
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val jsonBody = org.json.JSONObject()
                    jsonBody.put("pin", pin)
                    val bodyString = jsonBody.toString()
                    Log.d("PIN_API", "Request body: $bodyString")
                    conn.outputStream.use { it.write(bodyString.toByteArray()) }
                    Log.d("PIN_API", "Request sent")
                    val code = conn.responseCode
                    Log.d("PIN_API", "Response code: $code")
                    val response = if (code in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    }
                    Log.d("PIN_API", "Response body: $response")
                    val json = org.json.JSONObject(response)
                    val success = json.optBoolean("success", false)
                    val message = json.optString("message", "")
                    Log.d("PIN_API", "Parsed success: $success, message: $message")
                    success to message
                }
            } catch (e: Exception) {
                Log.e("PIN_API", "Exception: ${e.localizedMessage}", e)
                false to (e.localizedMessage ?: e.message ?: "Unknown error")
            }
        }
    }
    private val IN_APP_UPDATE_REQUEST_CODE = 1234
    override fun onStart() {
        super.onStart()
        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        if (UpdateSessionState.updateSkipped) return
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Update Available")
                    .setMessage("A new version of Mirror is available. Would you like to update now?")
                    .setPositiveButton("Update") { _, _ ->
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            this,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        UpdateSessionState.updateSkipped = true
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IN_APP_UPDATE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Update canceled. Please update the app from Play Store.", Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "App closed from background")
        val appContext = applicationContext
        SessionManager.setSyncDone(appContext, false)
        SyncSessionState.autoSyncTriggered.value = false

        UpdateSessionState.updateSkipped = false
    }
    private var luxandTrackerManager: LuxandTrackerManager? = null


    // --- PIN API Call ---
//    companion object {
//
//    }


    @SuppressLint("StateFlowValueCalledInComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("stoken_F", "FSDK Activating")

        SessionManager.setAppContext(this)
        luxandTrackerManager = LuxandTrackerManager(applicationContext)
//        var luxandReady by mutableStateOf(false)
//        lifecycleScope.launch {
//            luxandReady = luxandTrackerManager?.initialize() == true
//        }
        setContent {
            val navController = rememberNavController()
            val appContext = applicationContext
            // Insert dummy employees into SQLite on first launch
            LaunchedEffect(Unit) {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(appContext)
                    val dao = db.employeeDao()
                    if (dao.getAll().isEmpty()) {
                        dao.insertAll(
                            listOf(
                                EmployeeEntity("1", "Alice Smith", null, "1234567890", "HQ", "HR", true),
                                EmployeeEntity("2", "Bob Lee", null, "9876543210", "Branch A", "IT", false),
                                EmployeeEntity("3", "Carol Jones", null, "5551234567", "Branch B", "Finance", true)
                            )
                        )
                    }
                }
            }

            // --- PIN Dialog State ---
            var showPinDialog by remember { mutableStateOf(false) }
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf<String?>(null) }
            var pinLoading by remember { mutableStateOf(false) }

            // --- Determine start destination based on session ---
            val startDestination = remember {
                if (SessionManager.getToken(appContext).isNullOrBlank()) "login" else "mainMenu"
            }

            // --- Track if auto-sync has been triggered for this login/session (persistent) ---
            // Use process-wide state so it resets after app is killed, but persists during navigation
            val autoSyncTriggered = SyncSessionState.autoSyncTriggered
            // On first launch after process death, check persistent flag and reset process state
//            LaunchedEffect(Unit) {
//                if (!SessionManager.isSyncDone(appContext)) {
//                    autoSyncTriggered.value = false
//                }
//            }

            ModernAndroidUITheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // PIN Dialog Composable (OTP-style keyboard, no KeyboardOptions)
//                    if (showPinDialog) {
//                        androidx.compose.material3.AlertDialog(
//                            onDismissRequest = {
//                                if (!pinLoading) {
//                                    showPinDialog = false
//                                    pinInput = ""
//                                    pinError = null
//                                }
//                            },
//                            title = { Text("Enter 4-digit PIN") },
//                            text = {
//                                androidx.compose.foundation.layout.Column {
//                                    androidx.compose.material3.OutlinedTextField(
//                                        value = pinInput,
//                                        keyboardOptions = KeyboardOptions(
//                                            keyboardType = KeyboardType.NumberPassword,
//                                            imeAction = ImeAction.Done
//                                        ),
//                                        onValueChange = {
//                                            if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it
//                                        },
//                                        label = { Text("PIN") },
//                                        singleLine = true,
//                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
//                                        // No keyboardOptions, let system default (same as OTP)
//                                    )
//                                    if (pinError != null) {
//                                        Text(pinError!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
//                                    }
//                                }
//                            },
//                            confirmButton = {
//                                Button(
//                                    onClick = {
//                                        if (pinInput.length != 4) {
//                                            pinError = "PIN must be 4 digits"
//                                            return@Button
//                                        }
//                                        pinLoading = true
//                                        pinError = null
//                                        // Call API to verify PIN
//                                        lifecycleScope.launch {
//                                            try {
//                                                val sessionToken = SessionManager.getToken(appContext)
//                                                val (success, message) = verifyPinApi(sessionToken, pinInput)
//                                                if (success) {
//                                                    showPinDialog = false
//                                                    pinInput = ""
//                                                    pinError = null
//                                                    navController.navigate("employeeList")
//                                                } else {
//                                                    pinError = message.ifBlank { "Invalid PIN. Please try again." }
//                                                }
//                                            } catch (e: Exception) {
//                                                pinError = "Error verifying PIN."
//                                            } finally {
//                                                pinLoading = false
//                                            }
//                                        }
//                                    },
//                                    enabled = !pinLoading
//                                ) {
//                                    if (pinLoading) {
//                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.height(20.dp))
//                                    } else {
//                                        Text("Submit")
//                                    }
//                                }
//                            },
//                            dismissButton = {
//                                Button(onClick = {
//                                    if (!pinLoading) {
//                                        showPinDialog = false
//                                        pinInput = ""
//                                        pinError = null
//                                    }
//                                }, enabled = !pinLoading) {
//                                    Text("Cancel")
//                                }
//                            }
//                        )
//                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("mainMenu") {
                            val mainViewModel = viewModel<com.example.modernandroidui.viewmodel.MainViewModel>()
                            val syncProgress by mainViewModel.syncProgress.collectAsState()
                            val syncStatusText by mainViewModel.syncStatusText.collectAsState()
                            val showSyncLoader by mainViewModel.showSyncLoader.collectAsState()
                            val appContext = LocalContext.current.applicationContext
                            val autoSyncTriggered = SyncSessionState.autoSyncTriggered
                            // Trigger sync automatically after login if not already done
                            LaunchedEffect(Unit) {
                                Log.d("SyncNowFull", "autoSyncTriggered ${autoSyncTriggered.value} issync ${SessionManager.isSyncDone(appContext)}")
                                if (!autoSyncTriggered.value ) {
                                    delay(300)
                                    //&& !SessionManager.isSyncDone(appContext)
                                    autoSyncTriggered.value = true
                                    mainViewModel.syncNowFull(
                                        context = appContext,
                                        token = SessionManager.getToken(appContext),
                                        luxandTrackerManager = luxandTrackerManager!!
                                    ) { success, message ->
                                        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            SessionManager.setSyncDone(appContext, true)
                                        }
                                    }
                                }
                            }
                            MainMenuScreen(
                                onAttendanceClick = { navController.navigate("attendance") },
                                onEmployeeClick = {
                                    navController.navigate("employeeList")
                                },
                                onSettingsClick = { navController.navigate("settings") },
                                onAttendanceLogClick = { navController.navigate("attendanceLog") },
                                onLogoutClick = {
                                    SessionManager.clearSession(appContext)
                                    SessionManager.setSyncDone(appContext, false)
                                    autoSyncTriggered.value = false
                                    navController.navigate("login") {
                                        popUpTo("mainMenu") { inclusive = true }
                                    }
                                },
                                onSyncNowClick = {
                                    lifecycleScope.launch {
                                        val sessionToken = SessionManager.getToken(appContext)
                                        mainViewModel.syncNowFull(
                                            context = appContext,
                                            token = sessionToken,
                                            luxandTrackerManager = luxandTrackerManager!!
                                        ) { success, message ->
                                            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                                            if (success) {
                                                SessionManager.setSyncDone(appContext, true)
                                                autoSyncTriggered.value = true
                                            }
                                        }
                                    }
                                },
                                syncProgress = syncProgress,
                                syncStatusText = syncStatusText,
                                showSyncLoader = showSyncLoader,
                                mainViewModel = mainViewModel,
                                autoSyncTriggered = autoSyncTriggered
                            )
                        }
                        composable("attendanceLog") {
                            AttendanceLogScreen(onBack = { navController.popBackStack() })
                        }
                        composable("employeeList") {
                            val mainViewModel = viewModel<com.example.modernandroidui.viewmodel.MainViewModel>()
                            val context = LocalContext.current
                            LaunchedEffect(Unit) {
                                mainViewModel.loadBranchAndDepartmentFromDb(context)
                            }
                            val branches by mainViewModel.branchList.collectAsState()
                            val departments by mainViewModel.departmentList.collectAsState()
                            EmployeeListScreen(
                                branches = branches,
                                departments = departments,
                                onRegisterFace = { employee, reload ->
                                    navController.navigate("register/${employee.id}")
                                },
                                navController = navController,
                                onBackToMenu = {
                                    navController.navigate("mainMenu") {
                                        popUpTo("employeeList") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("register/{employeeId}") { backStackEntry ->
                            val employeeId = backStackEntry.arguments?.getString("employeeId")
                            val context = LocalContext.current
                            var employee by remember { mutableStateOf<EmployeeEntity?>(null) }
                            LaunchedEffect(employeeId) {
                                if (employeeId != null) {
                                    val db = AppDatabase.getInstance(context)
                                    val emp = db.employeeDao().getById(employeeId)
                                    employee = emp
                                }
                            }
                            if (employee != null) {
                                RegistrationScreen(
                                    employee = employee!!,
                                    luxandTrackerManager = luxandTrackerManager!!,
                                    onRegistrationSuccess = {
                                        navController.navigate("employeeList") {
                                            popUpTo("register/{employeeId}") { inclusive = true }
                                        }
                                    },
                                    onRegistrationError = {
                                        navController.popBackStack()
                                    },
                                    onBack = {
                                        navController.navigate("employeeList") {
                                            popUpTo("register/{employeeId}") { inclusive = true }
                                        }
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                                    androidx.compose.material3.Text("Loading employee data...")
                                }
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("attendance") {
                            AttendanceScreen(
                                luxandTrackerManager = luxandTrackerManager!!,
                                onMatch = { },
                                onBack = {
                                    navController.navigate("mainMenu") {
                                        popUpTo("attendance") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            val loginViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.modernandroidui.viewmodel.LoginViewModel>()
                            val mainViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.modernandroidui.viewmodel.MainViewModel>()
                            com.example.modernandroidui.ui.screens.LoginScreen(
                                onLoginSuccess = {
                                    val token = loginViewModel.uiState.value.token
                                    val userId = loginViewModel.uiState.value.userId
                                    val employerId = loginViewModel.uiState.value.employerId // Add this to LoginViewModel and LoginScreen if not present
                                    SessionManager.saveSession(appContext, token, userId)
                                    SessionManager.setSyncDone(appContext, false)
                                    autoSyncTriggered.value = false
                                    navController.navigate("mainMenu") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                viewModel = loginViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
//        lifecycleScope.launch {
//            luxandTrackerManager?.saveTrackerMemory()
//        }
    }
}
