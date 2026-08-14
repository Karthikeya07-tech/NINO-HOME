package com.example.nino_home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.BotService
import com.example.nino_home.BotStatus
import com.example.nino_home.MediaItem
import com.example.nino_home.MediaLibrary
import com.example.nino_home.R
import com.example.nino_home.RecordPlayViewModel
import com.example.nino_home.ServoAction
import com.example.nino_home.ServoActionRepository
import com.example.nino_home.formatDurationSeconds
import kotlin.math.roundToInt

private enum class PlayerTab(val title: String) {
    Media("Media"),
    Actions("Actions"),
    Manage("Manage"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    selectedBot: BotService?,
    botStatus: BotStatus?,
    onVolumeChange: (Int) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val actionRepository = remember { ServoActionRepository(context) }
    val recordPlayViewModel: RecordPlayViewModel = viewModel()
    val recordPlayUi by recordPlayViewModel.uiState.collectAsState()
    // null = show the Now Playing player (entry state from bot tap)
    var selectedTab by remember { mutableStateOf<PlayerTab?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember(botStatus?.volume) {
        mutableStateOf((botStatus?.volume ?: 50).coerceIn(0, 100))
    }
    val deviceName = botStatus?.deviceName ?: selectedBot?.serviceName ?: "Device"

    LaunchedEffect(botStatus?.volume) {
        botStatus?.volume?.takeIf { it in 0..100 }?.let { volume = it }
    }

    LaunchedEffect(selectedBot) {
        recordPlayViewModel.bindBot(selectedBot)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == PlayerTab.Actions) {
            recordPlayViewModel.refreshActions()
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color(0xFFF2F2F2),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Now Playing",
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
                actions = {
                    IconButton(onClick = onGoHome) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home",
                            tint = colors.onPrimary,
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = colors.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                ),
            )
        },
        bottomBar = {
            PlayerBottomMenu(
                selectedTab = selectedTab ?: PlayerTab.Media,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                null -> PlayerMainPanel(
                    isPlaying = isPlaying,
                    volume = volume,
                    onVolumeChange = {
                        volume = it
                        onVolumeChange(it)
                    },
                    onTogglePlay = { isPlaying = !isPlaying },
                    modifier = Modifier.weight(1f),
                )
                PlayerTab.Media -> LocalContentPanel(
                    deviceName = deviceName,
                    mediaItems = MediaLibrary.all(),
                    onPlayMedia = { item ->
                        isPlaying = true
                        recordPlayViewModel.playMedia(item)
                        selectedTab = null
                    },
                    modifier = Modifier.weight(1f),
                )
                PlayerTab.Actions -> SavedActionsPanel(
                    actions = recordPlayUi.actions.ifEmpty { actionRepository.loadActions() },
                    onActionTap = { action ->
                        recordPlayViewModel.playAction(action)
                    },
                    modifier = Modifier.weight(1f),
                )
                PlayerTab.Manage -> PlaceholderPanel(
                    title = "Manage",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LocalContentPanel(
    deviceName: String,
    mediaItems: List<MediaItem>,
    onPlayMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Local Content",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))

        if (mediaItems.isEmpty()) {
            Text(
                text = "No media yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            mediaItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onPlayMedia(item) })
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8E8E8)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.side_image),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                        )
                        Text(
                            text = "${item.description} · ${formatDurationSeconds(item.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground.copy(alpha = 0.55f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${item.name}",
                        tint = colors.primary,
                    )
                }
                HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun PlayerMainPanel(
    isPlaying: Boolean,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFE8E8E8))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.side_image),
                contentDescription = "Nino",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentScale = ContentScale.Fit,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFBDBDBD)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isPlaying) 0.12f else 0f)
                        .height(4.dp)
                        .background(colors.primary),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = if (isPlaying) 24.dp else 0.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0:00", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                Text("0:00", style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF4A4A4A))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerSpeakerIcon(
                waveCount = when {
                    volume <= 0 -> 0
                    volume < 40 -> 1
                    volume < 70 -> 2
                    else -> 3
                },
                modifier = Modifier
                    .size(width = 28.dp, height = 22.dp)
                    .clickable { onVolumeChange(0) },
            )
            Spacer(modifier = Modifier.width(12.dp))
            PlayerVolumeBar(
                volume = volume,
                fillColor = colors.primary,
                trackColor = Color(0xFF3A3A3A),
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
        }
    }
}

@Composable
private fun SavedActionsPanel(
    actions: List<ServoAction>,
    onActionTap: (ServoAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Text(
            text = "Saved motor actions from Record and Play",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))

        if (actions.isEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No saved actions yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.65f),
            )
        } else {
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActionTap(action) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = action.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${action.name}",
                        tint = colors.primary,
                    )
                }
                HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun PlaceholderPanel(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlayerBottomMenu(
    selectedTab: PlayerTab,
    onTabSelected: (PlayerTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .height(76.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerTab.entries.forEachIndexed { index, tab ->
            if (index > 0) {
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp),
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = when (tab) {
                        PlayerTab.Media -> "♪"
                        PlayerTab.Actions -> "▶"
                        PlayerTab.Manage -> "⚙"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = tab.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (selectedTab == tab) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.dp)
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSpeakerIcon(
    waveCount: Int,
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
        drawPath(cone, Color.White)
        val centerX = 12f * s
        val centerY = 12f * s
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        for (i in 0 until waveCount) {
            val radius = (3.5f + i * 3.5f) * s
            drawArc(
                color = Color.White,
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
private fun PlayerVolumeBar(
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
            val wedge = Path().apply {
                moveTo(0f, h)
                lineTo(fillX, h)
                lineTo(fillX, h - (h * (fillX / w)))
                close()
            }
            drawPath(wedge, fillColor)
        }
    }
}
