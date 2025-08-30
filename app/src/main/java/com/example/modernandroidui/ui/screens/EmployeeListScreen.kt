package com.example.modernandroidui.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modernandroidui.viewmodel.EmployeeListViewModel
import com.example.modernandroidui.data.EmployeeEntity
import coil.compose.AsyncImage
import androidx.navigation.NavController

@Composable
fun EmployeeListScreen(
    branches: List<String>,
    departments: List<String>,
    modifier: Modifier = Modifier,
    viewModel: EmployeeListViewModel = viewModel(),
    onRegisterFace: (EmployeeEntity, () -> Unit) -> Unit, // <-- pass reload callback
    navController: NavController? = null, // <-- add navController as optional param
    onBackToMenu: () -> Unit // <-- new callback for back button
) {
    val employees by viewModel.employees.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf<String?>(null) }
    var selectedDepartment by remember { mutableStateOf<String?>(null) }

    var faceStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.loadEmployees() }

    val listState = rememberLazyListState()
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        // Show a warning if branches or departments are empty
        if (branches.isEmpty() || departments.isEmpty()) {
            Text(
                text = "Branch/Department list is empty. Please sync data from main menu.",
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        // Any UI for displaying sync logs after syncing has been removed as per request.
        // Top Bar with Back Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onBackToMenu() }) {
                Text("Back to Menu")
            }
        }
        Spacer(Modifier.height(12.dp))
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.searchEmployees(it)
            },
            label = { Text("Search by name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        // Filters Row
        // Ensure unique, sorted values for dropdowns
        val branchOptions = remember(branches) { branches.filter { it.isNotBlank() }.toSet().toList().sorted() }
        val departmentOptions = remember(departments) { departments.filter { it.isNotBlank() }.toSet().toList().sorted() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            DropdownMenuBox(
                label = "Branch",
                options = branchOptions,
                selected = selectedBranch,
                onSelected = {
                    selectedBranch = it
                    viewModel.filterEmployees(selectedBranch, selectedDepartment, faceStatus)
                },
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 180.dp)
            )
            Spacer(Modifier.width(8.dp))
            DropdownMenuBox(
                label = "Department",
                options = departmentOptions,
                selected = selectedDepartment,
                onSelected = {
                    selectedDepartment = it
                    viewModel.filterEmployees(selectedBranch, selectedDepartment, faceStatus)
                },
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 160.dp)
            )
            Spacer(Modifier.width(8.dp))
            DropdownMenuBox(
                label = "Face Status",
                options = listOf("Registered", "Not Registered"),
                selected = faceStatus,
                onSelected = {
                    faceStatus = it
                    viewModel.filterEmployees(selectedBranch, selectedDepartment, faceStatus)
                },
                modifier = Modifier
                    .widthIn(min = 100.dp, max = 140.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        // Employee List
        val context = LocalContext.current
        var showDeleteDialog by remember { mutableStateOf<EmployeeEntity?>(null) }
        var deleting by remember { mutableStateOf(false) }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(employees) { employee ->
                EmployeeCard(
                    employee = employee,
                    onRegisterFace = {
                        // Pass a reload callback to update UI after registration
                        onRegisterFace(it) {
                            // Caution: reload only after successful registration
                            viewModel.loadEmployees()
                        }
                    },
                    onDeleteFace = { showDeleteDialog = it }
                )
            }
        }
        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteDialog = null },
                title = { Text("Delete Face Data") },
                text = {
                    Column {
                        Text("Are you sure you want to delete the face data for ${showDeleteDialog!!.name}?")
                        if (deleting) {
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            deleting = true
                            val emp = showDeleteDialog!!
                            val token = com.example.modernandroidui.session.SessionManager.getToken(context) ?: ""
                            viewModel.deleteFace(emp, token) { success ->
                                deleting = false
                                if (!success) {
                                    android.widget.Toast.makeText(context, "Failed to delete face from server", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showDeleteDialog = null
                            }
                        },
                        enabled = !deleting
                    ) { Text("Delete") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDeleteDialog = null },
                        enabled = !deleting
                    ) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun DropdownMenuBox(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selected ?: label,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelected(null); expanded = false })
            options.forEach {
                DropdownMenuItem(text = { Text(it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = { onSelected(it); expanded = false })
            }
        }
    }
}

@Composable
fun EmployeeCard(
    employee: EmployeeEntity,
    onRegisterFace: (EmployeeEntity) -> Unit,
    onDeleteFace: (EmployeeEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            val context = LocalContext.current
            if (employee.faceRegistered && !employee.mirror_image.isNullOrBlank()) {
                val file = try { java.io.File(employee.mirror_image) } catch (_: Exception) { null }
                if (file != null && file.exists()) {
                    // Show local image
                    AsyncImage(
                        model = file,
                        contentDescription = employee.name,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback to remote URL
                    val imageUrl = "https://salarydocument.fra1.digitaloceanspaces.com/${employee.mirror_image}"
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = employee.name,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            } else if (employee.photoRes != null) {
                Image(
                    painter = painterResource(id = employee.photoRes),
                    contentDescription = employee.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = employee.name,
                    modifier = Modifier.size(56.dp),
                    tint = Color.Gray
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(employee.name, style = MaterialTheme.typography.titleMedium)
                Text(employee.mobile, style = MaterialTheme.typography.bodyMedium)
                Text("${employee.branch} | ${employee.department}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (employee.faceRegistered) "Face Registered" else "Not Registered",
                    color = if (employee.faceRegistered) Color(0xFF388E3C) else Color(0xFFD32F2F),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!employee.faceRegistered) {
                IconButton(onClick = {
                    android.util.Log.d("EmployeeListScreen", "Plus icon clicked for employee: ${employee.id} - ${employee.name}")
                    try {
                        onRegisterFace(employee)
                    } catch (e: Exception) {
                        android.util.Log.e("EmployeeListScreen", "Error in onRegisterFace: ${e.localizedMessage}", e)
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Register Face")
                }
            } else {
                IconButton(onClick = { onDeleteFace(employee) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Face")
                }
            }
        }
    }
}
