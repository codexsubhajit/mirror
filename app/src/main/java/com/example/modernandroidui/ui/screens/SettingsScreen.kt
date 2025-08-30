package com.example.modernandroidui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modernandroidui.data.SettingsDataStore
import com.example.modernandroidui.data.BranchDataStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

data class Branch(val id: Int, val name: String)

class SettingsViewModel(private val context: android.content.Context) : ViewModel() {
    private val TAG = "SettingsViewModel"
    private val dataStore = SettingsDataStore(context)
    private val branchDataStore = BranchDataStore(context)
    var geofencingEnabled by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var branchList by mutableStateOf<List<Branch>>(emptyList())
        private set
    var selectedBranchId by mutableStateOf<Int?>(null)
        private set
    var selectedBranchName by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            dataStore.geofencingEnabledFlow.collectLatest { enabled ->
                android.util.Log.d(TAG, "[DataStore] geofencingEnabledFlow emitted: $enabled")
                geofencingEnabled = enabled
                if (!enabled) {
                    android.util.Log.d(TAG, "[DataStore] Geofencing turned OFF, clearing branch state")
                    branchList = emptyList()
                    selectedBranchId = null
                    selectedBranchName = null
                }
            }
        }
        viewModelScope.launch {
            branchDataStore.selectedBranchIdFlow.collectLatest { id ->
                android.util.Log.d(TAG, "[DataStore] selectedBranchIdFlow emitted: $id")
                selectedBranchId = id
            }
        }
    }

    fun updateGeofencingEnabled(enabled: Boolean) {
        android.util.Log.d(TAG, "[UI] updateGeofencingEnabled called with: $enabled")
        geofencingEnabled = enabled
        viewModelScope.launch {
            android.util.Log.d(TAG, "[DataStore] setGeofencingEnabled($enabled)")
            dataStore.setGeofencingEnabled(enabled)
        }
        if (enabled) {
            android.util.Log.d(TAG, "[UI] Geofencing turned ON, fetching branch list...")
            fetchBranchList()
        } else {
            android.util.Log.d(TAG, "[UI] Geofencing turned OFF, clearing branch state")
            branchList = emptyList()
            selectedBranchId = null
            selectedBranchName = null
        }
    }

    fun fetchBranchList() {
        android.util.Log.d(TAG, "[API] fetchBranchList called")
        viewModelScope.launch {
            isLoading = true
            try {
                val branches = withContext(Dispatchers.IO) {
                    val token = com.example.modernandroidui.session.SessionManager.getToken(context)
                    android.util.Log.d(TAG, "[API] Got token: $token")
                    val url = java.net.URL("https://web.nithrapeople.com/v1/api/branch-list")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/json")
                    val code = conn.responseCode
                    android.util.Log.d(TAG, "[API] Response code: $code")
                    val response = if (code in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    }
                    android.util.Log.d(TAG, "[API] Response: $response")
                    val json = JSONObject(response)
                    val dataArr = json.optJSONArray("data")
                    val branches = mutableListOf<Branch>()
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val obj = dataArr.getJSONObject(i)
                            branches.add(Branch(obj.getInt("id"), obj.getString("name")))
                        }
                    }
                    branches
                }
                android.util.Log.d(TAG, "[API] Parsed branches: ${branches.size}")
                branchList = branches
                // Set selectedBranchName if id is already selected
                selectedBranchName = branches.find { it.id == selectedBranchId }?.name
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[API] Error fetching branch list", e)
                branchList = emptyList()
            } finally {
                isLoading = false
                android.util.Log.d(TAG, "[API] fetchBranchList done, isLoading=false")
            }
        }
    }

    fun selectBranch(branch: Branch) {
        selectedBranchId = branch.id
        selectedBranchName = branch.name
        viewModelScope.launch {
            branchDataStore.setSelectedBranchId(branch.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context.applicationContext) as T
        }
    })
    // System bar colors are now set globally in the theme. No per-screen code needed.
    LaunchedEffect(viewModel.geofencingEnabled) {
        android.util.Log.d("SettingsScreen", "[UI] LaunchedEffect: geofencingEnabled = ${viewModel.geofencingEnabled}")
        if (viewModel.geofencingEnabled) {
            android.util.Log.d("SettingsScreen", "[UI] Geofencing enabled, calling fetchBranchList()")
            viewModel.fetchBranchList()
        } else {
            android.util.Log.d("SettingsScreen", "[UI] Geofencing disabled, not fetching branch list")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Button(onClick = onBack){
            Text("Back to Menu")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Geofencing", modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.geofencingEnabled,
                onCheckedChange = { viewModel.updateGeofencingEnabled(it) }
            )
        }
        if (viewModel.geofencingEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            if (viewModel.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.branchList.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val selectedName = viewModel.branchList.find { it.id == viewModel.selectedBranchId }?.name
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedName ?: "Select Branch",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Branch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        viewModel.branchList.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = {
                                    viewModel.selectBranch(branch)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
