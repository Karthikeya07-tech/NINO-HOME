package com.example.nino_home.ui

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.DiscoveredDevice
import com.example.nino_home.ProvisionViewModel
import com.example.nino_home.R
import com.example.nino_home.ble.ProvGattUuids
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisionScreen(
    viewModel: ProvisionViewModel = viewModel(),
    showTopBar: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            viewModel.refreshCurrentWifiSsid(force = true)
        }
    }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    fun ensurePermissions(): Boolean {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        permissionLauncher.launch(missing.toTypedArray())
        return false
    }

    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val layoutDirection = LocalLayoutDirection.current
    var hasEverConnected by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val configureAudioPlayer = remember {
        MediaPlayer.create(context, R.raw.go_app)?.apply {
            setOnCompletionListener { seekTo(0) }
        }
    }

    DisposableEffect(configureAudioPlayer) {
        onDispose { configureAudioPlayer?.release() }
    }

    fun playConfigureAudio() {
        val player = configureAudioPlayer ?: return
        if (player.isPlaying) {
            player.seekTo(0)
        } else {
            player.start()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentWifiSsid(force = false)
    }

    suspend fun showTransientStatus(message: String) {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
    }

    LaunchedEffect(uiState.isScanning) {
        if (uiState.isScanning) {
            showTransientStatus("Scanning for ${ProvGattUuids.ADVERTISED_NAME}...")
        }
    }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            hasEverConnected = true
            showTransientStatus("Device connected")
        } else if (hasEverConnected && !uiState.isProvisioning) {
            showTransientStatus("Device disconnected")
        }
    }

    LaunchedEffect(uiState.isProvisioning) {
        if (uiState.isProvisioning) {
            showTransientStatus("Provisioning started...")
        }
    }

    LaunchedEffect(uiState.lastStatus) {
        val status = uiState.lastStatus ?: return@LaunchedEffect
        val message = when (status.state) {
            1 -> "Connecting to Wi-Fi..."
            2 -> "Connected to Wi-Fi"
            3 -> "Wi-Fi connection failed"
            else -> "Provisioning status updated"
        }
        showTransientStatus(message)
    }

    LaunchedEffect(uiState.robotIp) {
        val ip = uiState.robotIp ?: return@LaunchedEffect
        showTransientStatus("Provisioning complete: $ip")
    }

    LaunchedEffect(uiState.error) {
        val err = uiState.error ?: return@LaunchedEffect
        showTransientStatus(err)
        delay(2200)
        viewModel.clearError()
    }

    val screenContent: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.configure_device_note),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                    IconButton(onClick = { playConfigureAudio() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_speaker),
                            contentDescription = stringResource(R.string.play_configure_audio),
                            tint = Color.Black,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ensurePermissions()) viewModel.startScan()
                    },
                    enabled = !uiState.isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.secondary,
                        disabledContentColor = colors.onSecondary,
                    ),
                ) {
                    Text(stringResource(R.string.scan))
                }
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = colors.primary,
                        trackColor = colors.onBackground,
                    )
                }
                if (uiState.isConnected) {
                    TextButton(onClick = { viewModel.disconnect() }) {
                        Text(
                            stringResource(R.string.disconnect),
                            color = colors.primary,
                        )
                    }
                }
            }

            if (uiState.discoveredDevices.isNotEmpty()) {
                Text(stringResource(R.string.devices_found), style = MaterialTheme.typography.titleSmall)
                uiState.discoveredDevices.forEach { device ->
                    DeviceRow(
                        device = device,
                        selected = uiState.selectedDevice?.address == device.address,
                        onClick = {
                            if (ensurePermissions()) viewModel.selectDevice(device)
                        },
                    )
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onBackground,
                unfocusedTextColor = colors.onBackground,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onBackground,
                cursorColor = colors.primary,
            )

            OutlinedTextField(
                value = uiState.ssid,
                onValueChange = viewModel::updateSsid,
                label = { Text(stringResource(R.string.wifi_ssid)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = { Text(stringResource(R.string.wifi_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Text(if (isPasswordVisible) "🙈" else "👁")
                    }
                },
                colors = fieldColors,
            )

            Button(
                onClick = {
                    if (ensurePermissions()) viewModel.provision()
                },
                enabled = uiState.isConnected && !uiState.isProvisioning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.secondary,
                    disabledContentColor = colors.onSecondary,
                ),
            ) {
                Text(
                    if (uiState.isProvisioning) {
                        stringResource(R.string.provisioning)
                    } else {
                        stringResource(R.string.provision_wifi)
                    },
                )
            }

            if (uiState.credentialsSent) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.credentials_sent_message),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            uiState.robotIp?.let { ip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.success_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.robot_url, ip))
                        Text(
                            stringResource(R.string.same_wifi_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.provision_title),
                            color = colors.onPrimary,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.primary,
                        titleContentColor = colors.onPrimary,
                        navigationIconContentColor = colors.onPrimary,
                        actionIconContentColor = colors.onPrimary,
                    ),
                )
            }
        },
    ) { padding ->
        val mergedPadding = PaddingValues(
            start = contentPadding.calculateLeftPadding(layoutDirection) +
                padding.calculateLeftPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + padding.calculateTopPadding(),
            end = contentPadding.calculateRightPadding(layoutDirection) +
                padding.calculateRightPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + padding.calculateBottomPadding(),
        )
        screenContent(mergedPadding)
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.secondary else colors.surface,
            contentColor = if (selected) colors.onSecondary else colors.onSurface,
        ),
        border = if (selected) {
            BorderStroke(2.dp, colors.primary)
        } else {
            BorderStroke(1.dp, colors.outline)
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(device.name ?: ProvGattUuids.ADVERTISED_NAME)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Text(
                    stringResource(R.string.connected_label),
                    color = colors.primary,
                )
            }
        }
    }
}
