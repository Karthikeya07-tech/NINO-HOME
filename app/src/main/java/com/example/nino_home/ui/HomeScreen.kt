package com.example.nino_home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.BotService
import com.example.nino_home.BotStatus
import com.example.nino_home.HomeViewModel
import kotlin.math.roundToInt

private enum class HomeTab(val title: String) {
    Music("My Music"),
    Create("Create New"),
    Configure("Configure"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val colors = MaterialTheme.colorScheme
    var selectedTab by remember { mutableStateOf(HomeTab.Create) }
    var showBotDetail by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.Create) {
            homeViewModel.startBotDiscovery()
        } else {
            homeViewModel.stopBotDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { homeViewModel.stopBotDiscovery() }
    }

    if (showBotDetail) {
        if (showAdvancedOptions) {
            AdvancedOptionsScreen(
                selectedBot = homeUiState.selectedBot,
                isUpdatingName = homeUiState.isUpdatingName,
                botStatus = homeUiState.botStatus,
                onRenameRequested = { newName ->
                    homeUiState.selectedBot?.let { homeViewModel.renameBot(it, newName) }
                },
                onBack = { showAdvancedOptions = false },
            )
            return
        }
        BotDetailScreen(
            selectedBot = homeUiState.selectedBot,
            isLoadingStatus = homeUiState.isLoadingStatus,
            isUpdatingName = homeUiState.isUpdatingName,
            botStatus = homeUiState.botStatus,
            statusError = homeUiState.statusError,
            onVolumeChange = { volume ->
                homeUiState.selectedBot?.let { homeViewModel.setVolume(it, volume) }
            },
            onRenameRequested = { newName ->
                homeUiState.selectedBot?.let { homeViewModel.renameBot(it, newName) }
            },
            onOpenAdvanced = { showAdvancedOptions = true },
            onBack = {
                showBotDetail = false
                showAdvancedOptions = false
                homeViewModel.clearBotSelection()
            },
        )
        return
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home - ${selectedTab.title}",
                        color = colors.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                ),
            )
        },
        bottomBar = {
            BottomMenu(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        when (selectedTab) {
            HomeTab.Music -> MusicPlaceholder(padding)
            HomeTab.Create -> CreateLanding(
                contentPadding = padding,
                onAddNewDevice = { selectedTab = HomeTab.Configure },
                discoveredBots = homeUiState.discoveredBots,
                isDiscoveringBots = homeUiState.isDiscoveringBots,
                discoveryError = homeUiState.discoveryError,
                onRefreshBots = { homeViewModel.startBotDiscovery() },
                onClearError = homeViewModel::clearDiscoveryError,
                onBotTapped = { bot ->
                    homeViewModel.fetchBotStatus(bot)
                    showBotDetail = true
                },
            )
            HomeTab.Configure -> ProvisionScreen(
                showTopBar = false,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun CreateLanding(
    contentPadding: PaddingValues,
    onAddNewDevice: () -> Unit,
    discoveredBots: List<BotService>,
    isDiscoveringBots: Boolean,
    discoveryError: String?,
    onRefreshBots: () -> Unit,
    onClearError: () -> Unit,
    onBotTapped: (BotService) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Please Setup your Device",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bots on Home Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
            )
            Button(
                onClick = onRefreshBots,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(if (isDiscoveringBots) "Scanning..." else "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (discoveredBots.isEmpty()) {
            Text(
                text = "No bot found yet. Keep bot and phone on same Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
        } else {
            discoveredBots.forEach { bot ->
                BotCard(
                    bot = bot,
                    onTap = { onBotTapped(bot) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (discoveryError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = discoveryError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearError),
            )
        }

        if (discoveredBots.isEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddNewDevice,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = "Add New Device",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ensure your Device is charged\nor\nswitched on",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BotCard(
    bot: BotService,
    onTap: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val localName = bot.hostName?.removeSuffix(".")
    val deviceTag = bot.txt["device"]
    val mdnsHost = localName ?: bot.host
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = bot.serviceName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bot.serviceType,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!localName.isNullOrBlank()) {
                Text(
                    text = "$localName",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "http://${bot.host}:${bot.port}/status",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!deviceTag.isNullOrBlank()) {
                Text(
                    text = "device=$deviceTag",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotDetailScreen(
    selectedBot: BotService?,
    isLoadingStatus: Boolean,
    isUpdatingName: Boolean,
    botStatus: BotStatus?,
    statusError: String?,
    onVolumeChange: (Int) -> Unit,
    onRenameRequested: (String) -> Unit,
    onOpenAdvanced: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val botTitle = selectedBot?.serviceName ?: botStatus?.deviceName ?: "Device"
    var isEditingName by remember(botStatus?.deviceName, selectedBot?.serviceName) { mutableStateOf(false) }
    var pendingName by remember(botStatus?.deviceName, selectedBot?.serviceName) {
        mutableStateOf(botStatus?.deviceName ?: selectedBot?.serviceName.orEmpty())
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = botTitle,
                        color = colors.onPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "<",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                isLoadingStatus -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Getting device status...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onBackground,
                        )
                    }
                }

                botStatus != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = BorderStroke(1.dp, Color.Black),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (isEditingName) {
                                Column {
                                    Text(
                                        text = "Device Name",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedTextField(
                                        value = pendingName,
                                        onValueChange = { pendingName = it },
                                        singleLine = true,
                                        enabled = !isUpdatingName,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            cursorColor = Color.Black,
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedBorderColor = Color.Black,
                                            unfocusedBorderColor = Color.Black,
                                        ),
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    enabled = !isUpdatingName,
                                                    onClick = {
                                                        isEditingName = false
                                                        onRenameRequested(pendingName)
                                                    },
                                                ) {
                                                    Text("✓")
                                                }
                                                IconButton(
                                                    enabled = !isUpdatingName,
                                                    onClick = {
                                                        isEditingName = false
                                                        pendingName = botStatus.deviceName
                                                    },
                                                ) {
                                                    Text("✕")
                                                }
                                            }
                                        },
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onLongPress = { onOpenAdvanced() },
                                                )
                                            },
                                    ) {
                                        DetailRow(label = "Device Name", value = botStatus.deviceName)
                                    }
                                    IconButton(
                                        onClick = {
                                            pendingName = botStatus.deviceName
                                            isEditingName = true
                                        },
                                    ) {
                                        Text("✎")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            DetailRow(label = "Connected Wi-Fi", value = botStatus.wifiSsid)
                            Spacer(modifier = Modifier.height(18.dp))
                            VolumeControl(
                                volume = botStatus.volume.coerceIn(0, 100),
                                onVolumeChange = onVolumeChange,
                            )
                        }
                    }
                }

                statusError != null -> {
                    Text(
                        text = statusError,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedOptionsScreen(
    selectedBot: BotService?,
    isUpdatingName: Boolean,
    botStatus: BotStatus?,
    onRenameRequested: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deviceName = botStatus?.deviceName ?: selectedBot?.serviceName ?: "Device"
    val firmware = botStatus?.firmware ?: "Unknown"
    val ipAddress = selectedBot?.host ?: "Unknown"

    var isEditingName by remember(botStatus?.deviceName) { mutableStateOf(false) }
    var pendingName by remember(botStatus?.deviceName) { mutableStateOf(deviceName) }
    var touchSensorOn by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Advanced Options",
                        color = colors.onPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "←",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Device Name :",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditingName) {
                OutlinedTextField(
                    value = pendingName,
                    onValueChange = { pendingName = it },
                    singleLine = true,
                    enabled = !isUpdatingName,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = Color.Black,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black,
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                enabled = !isUpdatingName,
                                onClick = {
                                    isEditingName = false
                                    onRenameRequested(pendingName)
                                },
                            ) {
                                Text("✓")
                            }
                            IconButton(
                                enabled = !isUpdatingName,
                                onClick = {
                                    isEditingName = false
                                    pendingName = deviceName
                                },
                            ) {
                                Text("✕")
                            }
                        }
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = colors.onBackground.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground,
                    )
                    IconButton(
                        onClick = {
                            pendingName = deviceName
                            isEditingName = true
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Text("✎", color = colors.onBackground)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "IP Address : $ipAddress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Firmware Version : $firmware",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Touch Sensor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                OnOffToggle(
                    isOn = touchSensorOn,
                    onToggle = { touchSensorOn = it },
                )
            }
        }
    }
}

@Composable
private fun OnOffToggle(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                border = BorderStroke(1.dp, colors.primary),
                shape = RoundedCornerShape(6.dp),
            ),
    ) {
        ToggleSegment(
            text = "OFF",
            selected = !isOn,
            onClick = { onToggle(false) },
        )
        ToggleSegment(
            text = "ON",
            selected = isOn,
            onClick = { onToggle(true) },
        )
    }
}

@Composable
private fun ToggleSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.primary,
        )
    }
}

@Composable
private fun VolumeControl(
    volume: Int,
    onVolumeChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val waveCount = when {
        volume <= 0 -> 0
        volume < 40 -> 1
        volume < 70 -> 2
        else -> 3
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Volume",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "$volume",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeakerIcon(
                waveCount = waveCount,
                tint = Color.Black,
                modifier = Modifier
                    .size(width = 40.dp, height = 28.dp)
                    .clickable { onVolumeChange(0) },
            )
            Spacer(modifier = Modifier.width(16.dp))
            WedgeVolumeBar(
                volume = volume,
                fillColor = colors.primary,
                trackColor = Color(0xFFE2E2E2),
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            )
        }
    }
}

@Composable
private fun SpeakerIcon(
    waveCount: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val s = size.height / 24f
        val cone = Path().apply {
            moveTo(3f * s, 9f * s)
            lineTo(3f * s, 15f * s)
            lineTo(7f * s, 15f * s)
            lineTo(12f * s, 20f * s)
            lineTo(12f * s, 4f * s)
            lineTo(7f * s, 9f * s)
            close()
        }
        drawPath(cone, tint)

        val centerX = 12f * s
        val centerY = 12f * s
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        for (i in 0 until waveCount) {
            val radius = (3.5f + i * 3.5f) * s
            drawArc(
                color = tint,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
    }
}

@Composable
private fun WedgeVolumeBar(
    volume: Int,
    fillColor: Color,
    trackColor: Color,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frac = (volume / 100f).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onVolumeChange((f * 100).roundToInt())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    onVolumeChange((f * 100).roundToInt())
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val track = Path().apply {
            moveTo(0f, h)
            lineTo(w, h)
            lineTo(w, 0f)
            close()
        }
        drawPath(track, trackColor)

        val fillX = w * frac
        if (fillX > 0f) {
            val fill = Path().apply {
                moveTo(0f, h)
                lineTo(fillX, h)
                lineTo(fillX, h - h * frac)
                close()
            }
            drawPath(fill, fillColor)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MusicPlaceholder(contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "My Music",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun BottomMenu(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.secondary)
            .height(76.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomMenuItem(
            label = HomeTab.Music.title,
            selected = selectedTab == HomeTab.Music,
            onClick = { onTabSelected(HomeTab.Music) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp),
            color = colors.onSecondary.copy(alpha = 0.45f),
        )
        BottomMenuItem(
            label = HomeTab.Create.title,
            selected = selectedTab == HomeTab.Create,
            onClick = { onTabSelected(HomeTab.Create) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp),
            color = colors.onSecondary.copy(alpha = 0.45f),
        )
        BottomMenuItem(
            label = HomeTab.Configure.title,
            selected = selectedTab == HomeTab.Configure,
            onClick = { onTabSelected(HomeTab.Configure) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onSecondary else colors.onSecondary.copy(alpha = 0.8f),
        )
    }
}
