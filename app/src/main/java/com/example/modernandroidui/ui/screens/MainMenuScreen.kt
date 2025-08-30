package com.example.modernandroidui.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.R

import com.example.modernandroidui.viewmodel.MainViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.example.modernandroidui.util.AttendanceSyncUtil
import com.example.modernandroidui.util.NetworkUtil
import kotlinx.coroutines.launch

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Add to MainViewModel (suggested, not in this file) ---
// val _branchList = MutableStateFlow<List<String>>(emptyList())
// val branchList: StateFlow<List<String>> = _branchList
// val _departmentList = MutableStateFlow<List<String>>(emptyList())
// val departmentList: StateFlow<List<String>> = _departmentList

// In your sync logic (AttendanceSyncUtil or repository), after parsing employees:
// val branches = employees.map { it.optString("branch_name", "") }.filter { it.isNotBlank() }.distinct().sorted()
// val departments = employees.map { it.optString("department_name", "") }.filter { it.isNotBlank() }.distinct().sorted()
// mainViewModel._branchList.value = branches
// mainViewModel._departmentList.value = departments

@Composable
fun MergeLogsView(mainViewModel: MainViewModel) {
    val mergeLogs by mainViewModel.mergeLogs.collectAsState()
    if (mergeLogs.isNotBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            Text(
                text = mergeLogs,
                color = Color.Green,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun MainMenuScreen(
    onAttendanceClick: () -> Unit,
    onEmployeeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAttendanceLogClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    syncProgress: Float = 0f,
    syncStatusText: String = "",
    showSyncLoader: Boolean = false,
    mainViewModel: MainViewModel, // <-- add this param
    autoSyncTriggered: androidx.compose.runtime.MutableState<Boolean>
) {
    // Use a process/session-wide flag to ensure auto-sync only happens on first login or after app is killed
    val syncInProgressState = mainViewModel.showSyncLoader.collectAsState()
    val syncProgressState = mainViewModel.syncProgress.collectAsState()
    val syncStatusTextState = mainViewModel.syncStatusText.collectAsState()
    val branchListState = mainViewModel.branchList.collectAsState()
    val departmentListState = mainViewModel.departmentList.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncResult = remember { androidx.compose.runtime.mutableStateOf<List<Pair<com.example.modernandroidui.data.AttendanceLogEntity, String?>>?>(null) }
    val hasInternet = remember { androidx.compose.runtime.mutableStateOf(NetworkUtil.isInternetAvailable(context)) }
    // Listen for connectivity changes (optional: for real-time updates, use a BroadcastReceiver or ConnectivityManager)
    LaunchedEffect(Unit) {
        hasInternet.value = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
                val exitCode = process.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    // Fetch mirror geo-fencing status from API and save in session when menu opens (if internet)
    val geoFencingChecked = remember { mutableStateOf(false) }
    LaunchedEffect(hasInternet.value) {
        if (hasInternet.value && !geoFencingChecked.value) {
            geoFencingChecked.value = true
            com.example.modernandroidui.MainActivity.fetchMirrorGeoFencingStatus(context)
        }
    }

    // Always sync attendance logs when menu page opens (on first composition)
    LaunchedEffect(Unit) {
        mainViewModel.syncAttendanceLogs(context) { success, message ->
            android.util.Log.d("MainMenuScreen", "Attendance sync result: $message")
        }
    }
    var showPinDialogFor by remember { mutableStateOf<String?>(null) } // "employee" or "settings" or null
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) } // null or error message
    var pinVerifying by remember { mutableStateOf(false) }
    var pinValidatedAction by remember { mutableStateOf<String?>(null) } // null or "employee" or "settings"

    var showLogoutDialog by remember { mutableStateOf(false) }

    // System bar colors are now set globally in the theme. No per-screen code needed.
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Confirm Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }) { Text("Logout") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            // Top row: Mark Attendance & Employees
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    24.dp,
                    Alignment.CenterHorizontally
                )
            ) {
                MenuIconButton(
                    icon = Icons.Filled.Face,
                    label = stringResource(id = R.string.menu_attendance),
                    onClick = onAttendanceClick,
                    modifier = Modifier.weight(1f)
                )
                MenuIconButton(
                    icon = Icons.Filled.Person,
                    label = stringResource(id = R.string.menu_employee),
                    onClick = if (hasInternet.value) { { showPinDialogFor = "employee" } } else { { } },
                    modifier = Modifier.weight(1f),
                    enabled = hasInternet.value
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            // Second row: Settings, Attendance Log & Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    24.dp,
                    Alignment.CenterHorizontally
                )
            ) {
                MenuIconButton(
                    icon = Icons.Filled.Menu, // Use a suitable icon for log
                    label = "Offline Logs",
                    onClick = onAttendanceLogClick,
                    modifier = Modifier.weight(1f)
                )
                MenuIconButton(
                    icon = Icons.Filled.Settings,
                    label = stringResource(id = R.string.menu_settings),
                    onClick = if (hasInternet.value) { { showPinDialogFor = "settings" } } else { { } },
                    modifier = Modifier.weight(1f),
                    enabled = hasInternet.value
                )
        // PIN Dialog (shared for Employee and Settings)
        if (showPinDialogFor != null) {
            AlertDialog(
                onDismissRequest = {
                    showPinDialogFor = null
                    pinInput = ""
                    pinError = null
                    pinVerifying = false
                },
                title = { Text("Enter 4-digit PIN") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    pinInput = it
                                    pinError = null
                                }
                            },
                            label = { Text("PIN") },
                            isError = pinError != null,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                        )
                        if (pinVerifying) {
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (pinError != null) {
                            Text(pinError ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput.length != 4) {
                            pinError = "PIN must be 4 digits."
                            return@TextButton
                        }
                        pinVerifying = true
                        pinError = null
                        // Use API for PIN check
                        coroutineScope.launch {
                            try {
                                val (success, message) = com.example.modernandroidui.MainActivity.verifyPinApi(context, pinInput)
                                if (success) {
                                    pinValidatedAction = showPinDialogFor
                                    showPinDialogFor = null
                                    pinInput = ""
                                    pinError = null
                                    pinVerifying = false
                                } else {
                                    pinError = message.ifBlank { "Invalid PIN. Please try again." }
                                    pinVerifying = false
                                }
                            } catch (e: Exception) {
                                pinError = "Error verifying PIN."
                                pinVerifying = false
                            }
                        }
                    }, enabled = !pinVerifying) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showPinDialogFor = null
                        pinInput = ""
                        pinError = null
                        pinVerifying = false
                    }, enabled = !pinVerifying) {
                        Text("Cancel")
                    }
                }
            )
        }

        // After PIN validated, trigger navigation and reset state
        LaunchedEffect(pinValidatedAction) {
            if (pinValidatedAction == "employee") {
                onEmployeeClick()
                pinValidatedAction = null
            } else if (pinValidatedAction == "settings") {
                onSettingsClick()
                pinValidatedAction = null
            }
        }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    24.dp,
                    Alignment.CenterHorizontally
                )
            ) {
                MenuIconButton(
                    icon = Icons.Filled.Refresh,
                    label = "Sync Now",
                    onClick = if (hasInternet.value) onSyncNowClick else { { } },
                    modifier = Modifier.weight(1f),
                    enabled = hasInternet.value
                )
                MenuIconButton(
                    icon = Icons.Filled.ExitToApp,
                    label = stringResource(id = R.string.menu_logout),
                    onClick = if (hasInternet.value) { { showLogoutDialog = true } } else { { } },
                    modifier = Modifier.weight(1f),
                    enabled = hasInternet.value
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            // MergeLogsView removed: no logs will be shown after syncing
            // Add extra space above logout to avoid overlap with loader
            if (syncInProgressState.value) {
                Spacer(modifier = Modifier.height(72.dp)) // Reserve space for progress bar
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Show sync result dialog if available and not empty
            if (syncResult.value != null && syncResult.value!!.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { syncResult.value = null },
                    title = { Text("Sync Result") },
                    text = {
                        Column {
                            syncResult.value!!.forEach { (log, url) ->
                                Text("Emp: ${log.empId}, Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(log.attendanceDatetime))}\nURL: ${url ?: "Failed/Offline"}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { syncResult.value = null }) { Text("OK") }
                    }
                )
            }
        }
        // Loader/progress bar always visible at the bottom during syncing
        if (syncInProgressState.value) {
            Box(
                Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f))
                        .navigationBarsPadding()
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress bar with contrasting color
                        LinearProgressIndicator(
                            progress = syncProgressState.value,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Syncing: ${(syncProgressState.value * 100).toInt()}% ${syncStatusTextState.value}",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        // Show No Internet connection message at the bottom if offline
        if (!hasInternet.value) {
            Box(
                Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(Color.Red.copy(alpha = 0.95f))
                        .padding(vertical = 16.dp, horizontal = 16.dp)
                ) {
                    Text(
                        "No Internet connection",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun MenuIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(64.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else Color.Gray
        )
    }
}
