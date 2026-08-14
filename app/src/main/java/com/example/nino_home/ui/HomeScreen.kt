package com.example.nino_home.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.BotCardInfo
import com.example.nino_home.BotService
import com.example.nino_home.R
import com.example.nino_home.BotStatus
import com.example.nino_home.HomeViewModel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val CAMERA_STREAM_PATH = "/stream"
private const val CAMERA_STREAM_ROTATION_DEGREES = 90f

private enum class HomeTab(val title: String) {
    Music("My Music"),
    Create("Create New"),
    Configure("Configure"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val colors = MaterialTheme.colorScheme
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Create) }
    var showBotDetail by rememberSaveable { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var showDeviceCamera by rememberSaveable { mutableStateOf(false) }
    var showPlayZone by rememberSaveable { mutableStateOf(false) }
    var showVisualsScreen by rememberSaveable { mutableStateOf(false) }
    var showMusicPlayer by rememberSaveable { mutableStateOf(false) }
    var showRecordAndPlay by rememberSaveable { mutableStateOf(false) }
    var showScenesMenu by remember { mutableStateOf(false) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.Create || selectedTab == HomeTab.Music) {
            homeViewModel.startBotDiscovery()
        } else {
            homeViewModel.stopBotDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { homeViewModel.stopBotDiscovery() }
    }

    if (showRecordAndPlay) {
        val recordBot = homeUiState.selectedBot ?: homeUiState.discoveredBots.firstOrNull()
        RecordAndPlayScreen(
            bot = recordBot,
            onBack = { showRecordAndPlay = false },
        )
        return
    }

    if (showVisualsScreen) {
        VisualsScreen(
            onBack = { showVisualsScreen = false },
        )
        return
    }

    if (showMusicPlayer) {
        NowPlayingScreen(
            selectedBot = homeUiState.selectedBot,
            botStatus = homeUiState.botStatus,
            onVolumeChange = { volume ->
                homeUiState.selectedBot?.let { homeViewModel.setVolume(it, volume) }
            },
            onBack = {
                showMusicPlayer = false
                homeViewModel.clearBotSelection()
            },
            onGoHome = {
                showMusicPlayer = false
                homeViewModel.clearBotSelection()
                selectedTab = HomeTab.Create
            },
        )
        return
    }

    if (showBotDetail) {
        if (showPlayZone) {
            VisualsScreen(
                onBack = { showPlayZone = false },
            )
            return
        }
        if (showDeviceCamera) {
            DeviceCameraScreen(
                selectedBot = homeUiState.selectedBot,
                onBack = { showDeviceCamera = false },
            )
            return
        }
        if (showAdvancedOptions) {
            AdvancedOptionsScreen(
                selectedBot = homeUiState.selectedBot,
                isUpdatingName = homeUiState.isUpdatingName,
                botStatus = homeUiState.botStatus,
                onRenameRequested = { newName ->
                    homeUiState.selectedBot?.let { homeViewModel.renameBot(it, newName) }
                },
                onFaceTrackToggle = { enabled ->
                    homeUiState.selectedBot?.let { homeViewModel.setFaceTrack(it, enabled) }
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
            onOpenCamera = { showDeviceCamera = true },
            onOpenPlayZone = { showPlayZone = true },
            onBack = {
                showBotDetail = false
                showAdvancedOptions = false
                showDeviceCamera = false
                showPlayZone = false
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
                    val title = when {
                        selectedTab == HomeTab.Create && homeUiState.discoveredBots.isNotEmpty() ->
                            "Home - Scenes"
                        selectedTab == HomeTab.Music && homeUiState.discoveredBots.isNotEmpty() ->
                            "Home - My Music"
                        else -> "Home - ${selectedTab.title}"
                    }
                    Text(
                        text = title,
                        color = colors.onPrimary,
                    )
                },
                actions = {
                    if (selectedTab == HomeTab.Create) {
                        IconButton(onClick = { showScenesMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = colors.onPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = showScenesMenu,
                            onDismissRequest = { showScenesMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Record and Play") },
                                onClick = {
                                    showScenesMenu = false
                                    showRecordAndPlay = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                    actionIconContentColor = colors.onPrimary,
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
            HomeTab.Music -> MusicLanding(
                contentPadding = padding,
                onAddNewDevice = { selectedTab = HomeTab.Configure },
                discoveredBots = homeUiState.discoveredBots,
                botCardInfo = homeUiState.botCardInfo,
                isDiscoveringBots = homeUiState.isDiscoveringBots,
                discoveryError = homeUiState.discoveryError,
                onRefreshBots = { homeViewModel.refreshBotDiscovery() },
                onClearError = homeViewModel::clearDiscoveryError,
                onBotTapped = { bot ->
                    homeViewModel.fetchBotStatus(bot)
                    showMusicPlayer = true
                },
                onVolumeChange = { bot, volume ->
                    homeViewModel.setVolume(bot, volume)
                },
                onOpenVisuals = { showVisualsScreen = true },
            )
            HomeTab.Create -> CreateLanding(
                contentPadding = padding,
                onAddNewDevice = { selectedTab = HomeTab.Configure },
                discoveredBots = homeUiState.discoveredBots,
                botCardInfo = homeUiState.botCardInfo,
                isDiscoveringBots = homeUiState.isDiscoveringBots,
                discoveryError = homeUiState.discoveryError,
                onRefreshBots = { homeViewModel.refreshBotDiscovery() },
                onClearError = homeViewModel::clearDiscoveryError,
                onBotTapped = { bot ->
                    homeViewModel.fetchBotStatus(bot)
                    showBotDetail = true
                },
                onVolumeChange = { bot, volume ->
                    homeViewModel.setVolume(bot, volume)
                },
                onOpenVisuals = { showVisualsScreen = true },
            )
            HomeTab.Configure -> ProvisionScreen(
                showTopBar = false,
                contentPadding = padding,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicLanding(
    contentPadding: PaddingValues,
    onAddNewDevice: () -> Unit,
    discoveredBots: List<BotService>,
    botCardInfo: Map<String, BotCardInfo>,
    isDiscoveringBots: Boolean,
    discoveryError: String?,
    onRefreshBots: () -> Unit,
    onClearError: () -> Unit,
    onBotTapped: (BotService) -> Unit,
    onVolumeChange: (BotService, Int) -> Unit,
    onOpenVisuals: () -> Unit,
) {
    // Same layout as home scenes: device cards when found, setup empty state otherwise.
    CreateLanding(
        contentPadding = contentPadding,
        onAddNewDevice = onAddNewDevice,
        discoveredBots = discoveredBots,
        botCardInfo = botCardInfo,
        isDiscoveringBots = isDiscoveringBots,
        discoveryError = discoveryError,
        onRefreshBots = onRefreshBots,
        onClearError = onClearError,
        onBotTapped = onBotTapped,
        onVolumeChange = onVolumeChange,
        onOpenVisuals = onOpenVisuals,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLanding(
    contentPadding: PaddingValues,
    onAddNewDevice: () -> Unit,
    discoveredBots: List<BotService>,
    botCardInfo: Map<String, BotCardInfo>,
    isDiscoveringBots: Boolean,
    discoveryError: String?,
    onRefreshBots: () -> Unit,
    onClearError: () -> Unit,
    onBotTapped: (BotService) -> Unit,
    onVolumeChange: (BotService, Int) -> Unit,
    onOpenVisuals: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isDiscoveringBots,
        onRefresh = onRefreshBots,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (discoveredBots.isEmpty()) {
            SetupEmptyState(
                isDiscoveringBots = isDiscoveringBots,
                discoveryError = discoveryError,
                onAddNewDevice = onAddNewDevice,
                onClearError = onClearError,
            )
        } else {
            DeviceScenesList(
                discoveredBots = discoveredBots,
                botCardInfo = botCardInfo,
                discoveryError = discoveryError,
                onClearError = onClearError,
                onBotTapped = onBotTapped,
                onVolumeChange = onVolumeChange,
                onOpenVisuals = onOpenVisuals,
            )
        }
    }
}

@Composable
private fun SetupEmptyState(
    isDiscoveringBots: Boolean,
    discoveryError: String?,
    onAddNewDevice: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
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

        if (isDiscoveringBots) {
            Text(
                text = "Scanning home network…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (discoveryError != null) {
            Text(
                text = discoveryError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearError),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Image(
            painter = painterResource(R.drawable.no_device),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(260.dp)
                .padding(vertical = 8.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.weight(1f))

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

@Composable
private fun DeviceScenesList(
    discoveredBots: List<BotService>,
    botCardInfo: Map<String, BotCardInfo>,
    discoveryError: String?,
    onClearError: () -> Unit,
    onBotTapped: (BotService) -> Unit,
    onVolumeChange: (BotService, Int) -> Unit,
    onOpenVisuals: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (discoveryError != null) {
            Text(
                text = discoveryError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(onClick = onClearError),
            )
        }

        discoveredBots.forEach { bot ->
            val cardKey = "${bot.host}:${bot.port}"
            DeviceSceneCard(
                bot = bot,
                cardInfo = botCardInfo[cardKey],
                onTap = { onBotTapped(bot) },
                onVolumeChange = { volume -> onVolumeChange(bot, volume) },
                onOpenVisuals = onOpenVisuals,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DeviceSceneCard(
    bot: BotService,
    cardInfo: BotCardInfo?,
    onTap: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onOpenVisuals: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deviceLabel = cardInfo?.deviceName ?: bot.serviceName
    var volume by remember(bot.host, cardInfo?.volume) {
        mutableStateOf(cardInfo?.volume ?: 50)
    }

    LaunchedEffect(cardInfo?.volume) {
        cardInfo?.volume?.let { volume = it }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.38f)
                    .background(Color(0xFFE8E8E8))
                    .clickable(onClick = onTap),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.side_image),
                    contentDescription = deviceLabel,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deviceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onTap),
                        maxLines = 1,
                    )
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SceneControlButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SceneControlButton(
                        icon = Icons.Filled.Pause,
                        contentDescription = "Pause",
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SceneControlButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeakerIcon(
                        waveCount = if (volume <= 0) 0 else if (volume < 40) 1 else if (volume < 70) 2 else 3,
                        tint = Color.White,
                        modifier = Modifier
                            .size(width = 28.dp, height = 22.dp)
                            .clickable { onVolumeChange(0) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    WedgeVolumeBar(
                        volume = volume,
                        fillColor = colors.primary,
                        trackColor = Color(0xFF3A3A3A),
                        onVolumeChange = {
                            volume = it
                            onVolumeChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VisualsCircleButton(onClick = onOpenVisuals)
                }
            }
        }
    }
}

@Composable
private fun VisualsCircleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF3A3A3A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.side_image),
            contentDescription = "Open visuals",
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun SceneControlButton(
    icon: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3A3A3A)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
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
    onOpenCamera: () -> Unit,
    onOpenPlayZone: () -> Unit,
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
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val actionButtonModifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                val actionButtonColors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                )
                                Button(
                                    onClick = onOpenCamera,
                                    modifier = actionButtonModifier,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = actionButtonColors,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = "Device Camera",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                    )
                                }
                                Button(
                                    onClick = onOpenPlayZone,
                                    modifier = actionButtonModifier,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = actionButtonColors,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = "Play Zone",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                    )
                                }
                            }
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
private fun VisualsScreen(
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val visualOptions = listOf(
        "Happy",
        "Excited",
        "Thinking",
        "Idea",
        "Hello",
        "Wink",
    )

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Visuals",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visualOptions.forEach { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { },
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    border = BorderStroke(1.dp, Color.Black),
                ) {
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 20.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCameraScreen(
    selectedBot: BotService?,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val streamUrl = selectedBot?.let { "http://${it.host}:${it.port}$CAMERA_STREAM_PATH" }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    FullScreenSystemUiEffect(enabled = isFullScreen)

    BackHandler {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = if (isFullScreen) Color.Black else colors.background,
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Device Camera",
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
            }
        },
    ) { padding ->
        if (streamUrl == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Camera is unavailable. Select a device first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.primary,
                )
            }
            return@Scaffold
        }

        val webView = remember(streamUrl) {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setSupportZoom(false)
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                              const styleId = 'nino-stream-fullscreen-style';
                              let style = document.getElementById(styleId);
                              if (!style) {
                                style = document.createElement('style');
                                style.id = styleId;
                                document.head.appendChild(style);
                              }
                              style.innerHTML = `
                                html, body {
                                  margin: 0;
                                  padding: 0;
                                  width: 100%;
                                  height: 100%;
                                  overflow: hidden;
                                  background: #fff;
                                  touch-action: none !important;
                                }
                                body {
                                  display: flex;
                                  align-items: center;
                                  justify-content: center;
                                }
                                video, img, canvas, iframe {
                                  width: 100% !important;
                                  height: 100% !important;
                                  max-width: 100% !important;
                                  max-height: 100% !important;
                                  object-fit: contain !important;
                                  object-position: center center !important;
                                  background: #fff !important;
                                  transform: none !important;
                                }
                              `;
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
                loadUrl(streamUrl)
            }
        }

        DisposableEffect(webView) {
            onDispose {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }

        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.8f)
                        .aspectRatio(
                            ratio = 16f / 9f,
                            matchHeightConstraintsFirst = true,
                        )
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Black,
                            shape = RoundedCornerShape(6.dp),
                        ),
                ) {
                    CameraStreamView(
                        webView = webView,
                        modifier = Modifier.fillMaxSize(),
                        interactionEnabled = false,
                        rotationDegrees = CAMERA_STREAM_ROTATION_DEGREES,
                    )
                    IconButton(
                        onClick = { isFullScreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FullscreenExit,
                            contentDescription = "Exit full screen",
                            tint = Color.Black,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Live stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        border = BorderStroke(1.dp, Color.Black),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                        ),
                    ) {
                        CameraStreamView(
                            webView = webView,
                            modifier = Modifier.fillMaxSize(),
                            interactionEnabled = true,
                            rotationDegrees = CAMERA_STREAM_ROTATION_DEGREES,
                        )
                    }
                    IconButton(
                        onClick = { isFullScreen = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "Enter full screen",
                            tint = Color.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenSystemUiEffect(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(enabled, context) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val previousOrientation = activity.requestedOrientation
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity.requestedOrientation = previousOrientation
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun CameraStreamView(
    webView: WebView,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    rotationDegrees: Float = 0f,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val quarterTurn = (abs(rotationDegrees) % 180f) > 0.1f
    val fitScale = if (quarterTurn && viewSize.width > 0 && viewSize.height > 0) {
        min(
            viewSize.width.toFloat() / viewSize.height.toFloat(),
            viewSize.height.toFloat() / viewSize.width.toFloat(),
        )
    } else {
        1f
    }

    webView.setOnTouchListener { _, event ->
        if (interactionEnabled) {
            false
        } else {
            event.actionMasked != MotionEvent.ACTION_OUTSIDE
        }
    }

    AndroidView(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .graphicsLayer {
                rotationZ = rotationDegrees
                scaleX = fitScale
                scaleY = fitScale
            },
        factory = { webView },
        update = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedOptionsScreen(
    selectedBot: BotService?,
    isUpdatingName: Boolean,
    botStatus: BotStatus?,
    onRenameRequested: (String) -> Unit,
    onFaceTrackToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deviceName = botStatus?.deviceName ?: selectedBot?.serviceName ?: "Device"
    val firmware = botStatus?.firmware ?: "Unknown"
    val ipAddress = selectedBot?.host ?: "Unknown"
    val serviceType = selectedBot?.serviceType ?: "Unknown"
    val mdnsHost = selectedBot?.hostName?.removeSuffix(".") ?: "Unknown"
    val statusUrl = selectedBot?.let { "http://${it.host}:${it.port}/status" } ?: "Unknown"
    val deviceTag = selectedBot?.txt?.get("device") ?: "Unknown"

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
                text = "Service Type : $serviceType",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "mDNS Host : $mdnsHost",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Status URL : $statusUrl",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Device Tag : $deviceTag",
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

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Face Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                OnOffToggle(
                    isOn = botStatus?.faceTrackEnabled ?: false,
                    onToggle = onFaceTrackToggle,
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
            icon = "♪",
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
            icon = "▶",
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
            icon = "⚙",
            selected = selectedTab == HomeTab.Configure,
            onClick = { onTabSelected(HomeTab.Configure) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomMenuItem(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconAndTextColor = Color.White
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                color = iconAndTextColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = iconAndTextColor,
            )
        }
    }
}
