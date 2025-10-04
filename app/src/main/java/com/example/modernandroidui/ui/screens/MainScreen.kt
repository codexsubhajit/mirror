package com.example.modernandroidui.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.modernandroidui.viewmodel.MainViewModel
import com.example.modernandroidui.model.UiModel
import com.example.modernandroidui.ui.components.CustomCard
import com.example.modernandroidui.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.viewmodel.LoginViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onPinRequired: () -> Unit,
    viewModel: LoginViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val otpFocusRequester = remember { FocusRequester() }
    val pinFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasInternet = com.example.modernandroidui.util.NetworkUtil.isInternetAvailable(context)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!hasInternet) {
            // No internet UI
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = com.example.modernandroidui.R.drawable.ic_network_off),
                        contentDescription = "No Internet",
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "No internet connection",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Please check your connection to login.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Normal login UI
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.login_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = uiState.phone,
                            onValueChange = { viewModel.onPhoneChanged(it) },
                            label = { Text(stringResource(R.string.phone_label)) },
                            placeholder = { Text(stringResource(R.string.phone_placeholder)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            isError = uiState.phoneError != null,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                uiState.phoneError?.let {
                                    Text(
                                        text = stringResource(it),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(
                            visible = uiState.otpSent && !uiState.showPinField,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = uiState.otp,
                                    onValueChange = { viewModel.onOtpChanged(it) },
                                    label = { Text(stringResource(R.string.otp_label)) },
                                    placeholder = { Text(stringResource(R.string.otp_placeholder)) },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true,
                                    isError = uiState.otpError != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(otpFocusRequester),
                                    visualTransformation = PasswordVisualTransformation(),
                                    supportingText = {
                                        uiState.otpError?.let {
                                            Text(
                                                text = stringResource(it),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = uiState.pin,
                                    onValueChange = { viewModel.onPinChanged(it) },
                                    label = { Text("PIN") },
                                    placeholder = { Text("Enter 4-digit PIN") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    isError = uiState.pinError != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    supportingText = {
                                        uiState.pinError?.let {
                                            Text(
                                                text = stringResource(it),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = uiState.rePin,
                                    onValueChange = { viewModel.onRePinChanged(it) },
                                    label = { Text("Re-enter PIN") },
                                    placeholder = { Text("Re-enter 4-digit PIN") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true,
                                    isError = uiState.rePinError != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    supportingText = {
                                        uiState.rePinError?.let {
                                            Text(
                                                text = stringResource(it),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = uiState.showPinField,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = uiState.pin,
                                onValueChange = { viewModel.onPinChanged(it) },
                                label = { Text("PIN") },
                                placeholder = { Text("Enter 4-digit PIN") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                isError = uiState.pinError != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(pinFocusRequester),
                                visualTransformation = PasswordVisualTransformation(),
                                supportingText = {
                                    uiState.pinError?.let {
                                        Text(
                                            text = stringResource(it),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        AnimatedContent(
                            targetState = if (uiState.showPinField) "pin" else if (uiState.otpSent) "otp" else "send",
                            transitionSpec = { fadeIn() with fadeOut() }
                        ) { state ->
                            when (state) {
                                "send" -> Column(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.sendOtp(context, onPinRequired)
                                        },
                                        enabled = !uiState.loading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (uiState.loading) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(stringResource(R.string.send_otp))
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.resetPin(context)
                                        },
                                        enabled = !uiState.loading,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text("Reset PIN")
                                    }
                                }
                                "otp" -> Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.verifyOtp(context)
                                    },
                                    enabled = !uiState.loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.loading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(stringResource(R.string.verify_otp))
                                    }
                                }
                                "pin" -> Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.verifyPin(context)
                                    },
                                    enabled = !uiState.loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.loading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Verify PIN")
                                    }
                                }
                            }
                        }
                        uiState.generalError?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(it),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        uiState.loginMessage?.let { msg ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = msg,
                                color = if (uiState.loginSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.loginSuccess) {
        LaunchedEffect(Unit) {
            onLoginSuccess()
        }
    }
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    // ...other params...
) {
    val syncProgress by mainViewModel.syncProgress.collectAsState()
    val syncStatusText by mainViewModel.syncStatusText.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            // Top row: Mark Attendance & Employee Management
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { /* TODO: Navigate to Attendance */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.menu_attendance))
                }
                Button(
                    onClick = { /* TODO: Navigate to Employee Management */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.menu_employee))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Second row: Settings & Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { /* TODO: Navigate to Settings */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.menu_settings))
                }
                Button(
                    onClick = { /* TODO: Trigger Sync */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sync")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // Logout button at the bottom
            Button(
                onClick = { /* TODO: Logout */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(stringResource(R.string.menu_logout))
            }
        }
        // Loader/progress bar always visible at the bottom during syncing
        if (syncProgress > 0f && syncProgress < 1f) {
            Box(
                Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f))
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(progress = syncProgress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Syncing: ${(syncProgress * 100).toInt()}% $syncStatusText",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}