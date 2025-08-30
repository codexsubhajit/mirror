package com.example.modernandroidui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.data.AppDatabase
import com.example.modernandroidui.data.AttendanceLogEntity
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var logs by remember { mutableStateOf<List<AttendanceLogEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        logs = db.attendanceLogDao().getAllLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No attendance logs found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Emp ID: ${log.empId}", style = MaterialTheme.typography.titleMedium)
                            Text("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(log.attendanceDatetime))}", style = MaterialTheme.typography.bodySmall)
                            if (log.mirrorImagePath.isNotBlank()) {
                                Text("Image: ${log.mirrorImagePath}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
