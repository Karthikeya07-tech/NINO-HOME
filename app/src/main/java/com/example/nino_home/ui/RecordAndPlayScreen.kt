package com.example.nino_home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.ActionFrame
import com.example.nino_home.BotCardInfo
import com.example.nino_home.BotService
import com.example.nino_home.InsertMode
import com.example.nino_home.MediaItem
import com.example.nino_home.MotorSelection
import com.example.nino_home.RecordPlayViewModel
import com.example.nino_home.ServoAction
import com.example.nino_home.formatDurationSeconds

private enum class RecordPlayPage {
    SelectDevice,
    Actions,
    Editor,
    AutoCreate,
    Guide,
}

private fun botIdentity(bot: BotService) = "${bot.host}:${bot.port}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAndPlayScreen(
    discoveredBots: List<BotService>,
    botCardInfo: Map<String, BotCardInfo>,
    onBack: () -> Unit,
    viewModel: RecordPlayViewModel = viewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    var selectedBotKey by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable {
        mutableStateOf(
            if (discoveredBots.size > 1) RecordPlayPage.SelectDevice else RecordPlayPage.Actions,
        )
    }
    var renameTarget by remember { mutableStateOf<ServoAction?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ServoAction?>(null) }
    var editorMenuExpanded by remember { mutableStateOf(false) }

    val selectedBot = discoveredBots.firstOrNull { botIdentity(it) == selectedBotKey }

    LaunchedEffect(discoveredBots) {
        val keys = discoveredBots.map(::botIdentity)
        when {
            discoveredBots.isEmpty() -> {
                selectedBotKey = null
                page = RecordPlayPage.SelectDevice
            }
            discoveredBots.size == 1 -> {
                selectedBotKey = keys.first()
                if (page == RecordPlayPage.SelectDevice) {
                    page = RecordPlayPage.Actions
                }
            }
            selectedBotKey == null || selectedBotKey !in keys -> {
                if (selectedBotKey != null) {
                    if (uiState.isRecording) viewModel.leaveRecordMode()
                    if (uiState.isPlaying) viewModel.stopPlay()
                }
                selectedBotKey = null
                page = RecordPlayPage.SelectDevice
            }
        }
    }

    LaunchedEffect(selectedBot) {
        viewModel.bindBot(selectedBot)
        viewModel.refreshActions()
    }

    fun goBackFromActions() {
        if (uiState.isRecording) viewModel.leaveRecordMode()
        if (uiState.isPlaying) viewModel.stopPlay()
        if (discoveredBots.size > 1) {
            selectedBotKey = null
            page = RecordPlayPage.SelectDevice
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (uiState.isRecording) {
                viewModel.leaveRecordMode()
            }
        }
    }

    BackHandler {
        when {
            page == RecordPlayPage.Guide -> {
                page = RecordPlayPage.Actions
            }
            page == RecordPlayPage.AutoCreate -> {
                page = RecordPlayPage.Editor
            }
            page == RecordPlayPage.Editor -> {
                if (uiState.isRecording) viewModel.leaveRecordMode()
                page = RecordPlayPage.Actions
                viewModel.refreshActions()
            }
            page == RecordPlayPage.Actions -> goBackFromActions()
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (page) {
                            RecordPlayPage.SelectDevice -> "Choose Device"
                            RecordPlayPage.Actions -> "Actions"
                            RecordPlayPage.Editor -> "Action Editor"
                            RecordPlayPage.AutoCreate -> "Auto action creation"
                            RecordPlayPage.Guide -> "Record & Play Guide"
                        },
                        color = colors.onPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (page) {
                                RecordPlayPage.Guide -> page = RecordPlayPage.Actions
                                RecordPlayPage.AutoCreate -> page = RecordPlayPage.Editor
                                RecordPlayPage.Editor -> {
                                    if (uiState.isRecording) viewModel.leaveRecordMode()
                                    page = RecordPlayPage.Actions
                                    viewModel.refreshActions()
                                }
                                RecordPlayPage.Actions -> goBackFromActions()
                                RecordPlayPage.SelectDevice -> onBack()
                            }
                        },
                    ) {
                        Text(
                            text = "<",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    if (page == RecordPlayPage.Actions) {
                        IconButton(onClick = { page = RecordPlayPage.Guide }) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = colors.onPrimary,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "?",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (page == RecordPlayPage.Editor) {
                        Box {
                            IconButton(onClick = { editorMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = colors.onPrimary,
                                )
                            }
                            DropdownMenu(
                                expanded = editorMenuExpanded,
                                onDismissRequest = { editorMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto action creation") },
                                    onClick = {
                                        editorMenuExpanded = false
                                        viewModel.startAutoCreate()
                                        page = RecordPlayPage.AutoCreate
                                    },
                                )
                            }
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
    ) { padding ->
        when (page) {
            RecordPlayPage.SelectDevice -> SelectDevicePage(
                contentPadding = padding,
                discoveredBots = discoveredBots,
                botCardInfo = botCardInfo,
                onSelect = { bot ->
                    selectedBotKey = botIdentity(bot)
                    page = RecordPlayPage.Actions
                },
            )

            RecordPlayPage.Actions -> ActionsListPage(
                contentPadding = padding,
                bot = selectedBot,
                botCardInfo = botCardInfo,
                canChangeDevice = discoveredBots.size > 1,
                actions = uiState.actions,
                isPlaying = uiState.isPlaying,
                statusMessage = uiState.statusMessage,
                error = uiState.error,
                onChangeDevice = { goBackFromActions() },
                onNewAction = {
                    viewModel.startNewAction()
                    page = RecordPlayPage.Editor
                },
                onPlay = { viewModel.playAction(it) },
                onStopPlay = { viewModel.stopPlay() },
                onEdit = {
                    viewModel.openActionForEdit(it)
                    page = RecordPlayPage.Editor
                },
                onRename = {
                    renameTarget = it
                    renameText = it.name
                },
                onDelete = { deleteTarget = it },
                onClearError = viewModel::clearError,
            )

            RecordPlayPage.Editor -> ActionEditorPage(
                contentPadding = padding,
                bot = selectedBot,
                actionName = uiState.actionName,
                motors = uiState.motors,
                frames = uiState.frames,
                selectedFrameIndex = uiState.selectedFrameIndex,
                liveServos = uiState.liveServos,
                isRecording = uiState.isRecording,
                isPlaying = uiState.isPlaying,
                isBusy = uiState.isBusy,
                statusMessage = uiState.statusMessage,
                error = uiState.error,
                selectedAudioId = uiState.selectedAudioId,
                selectedAudioName = uiState.selectedAudioName,
                selectedAudioDurationMs = uiState.selectedAudioDurationMs,
                mediaItems = uiState.mediaItems,
                onNameChange = viewModel::updateActionName,
                onMotorsChange = viewModel::setMotors,
                onSelectAudio = viewModel::selectAudio,
                onClearAudio = viewModel::clearAudio,
                onEnterRecord = viewModel::enterRecordMode,
                onLeaveRecord = viewModel::leaveRecordMode,
                onSelectFrame = viewModel::selectFrame,
                onHoldMsChange = viewModel::updateSelectedHoldMs,
                onAddFrame = { viewModel.addFrame(InsertMode.Append) },
                onInsertBefore = { viewModel.addFrame(InsertMode.Before) },
                onInsertAfter = { viewModel.addFrame(InsertMode.After) },
                onReplace = viewModel::replaceSelectedFrame,
                onDeleteFrame = viewModel::deleteSelectedFrame,
                onPreview = viewModel::previewSelectedFrame,
                onNeutral = viewModel::goNeutral,
                onPlay = viewModel::playCurrentEditorAction,
                onStopPlay = viewModel::stopPlay,
                onSave = {
                    viewModel.saveAction()
                    page = RecordPlayPage.Actions
                    viewModel.refreshActions()
                },
                onClearError = viewModel::clearError,
            )

            RecordPlayPage.AutoCreate -> AutoCreateActionPage(
                contentPadding = padding,
                durationSeconds = uiState.autoDurationSeconds,
                selectedAudioId = uiState.autoAudioId,
                selectedAudioName = uiState.autoAudioName,
                selectedAudioDurationMs = uiState.autoAudioDurationMs,
                mediaItems = uiState.mediaItems,
                isBusy = uiState.isBusy,
                statusMessage = uiState.statusMessage,
                error = uiState.error,
                onDurationChange = viewModel::updateAutoDurationSeconds,
                onSelectAudio = viewModel::selectAutoAudio,
                onClearAudio = viewModel::clearAutoAudio,
                onCreate = viewModel::createAutoAction,
                onClearError = viewModel::clearError,
            )

            RecordPlayPage.Guide -> RecordPlayGuidePage(
                contentPadding = padding,
            )
        }
    }

    uiState.pendingAutoAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.discardPendingAutoAction() },
            title = { Text("Add to Actions?") },
            text = {
                Column {
                    Text(
                        text = "Created “${pending.name}”.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${pending.frameCount} frames · ${formatDurationSeconds(pending.durationMs)}" +
                            if (pending.hasAudio) {
                                "\nAudio: ${pending.audioName ?: "Selected"} · ${formatDurationSeconds(pending.audioDurationMs)}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add this auto action to the Actions page?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmAddPendingAutoAction()
                        page = RecordPlayPage.Actions
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardPendingAutoAction() }) {
                    Text("Discard")
                }
            },
        )
    }

    renameTarget?.let { action ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename action") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(40) },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameAction(action.id, renameText)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { action ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete action?") },
            text = { Text("Remove “${action.name}”? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAction(action.id)
                        deleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SelectDevicePage(
    contentPadding: PaddingValues,
    discoveredBots: List<BotService>,
    botCardInfo: Map<String, BotCardInfo>,
    onSelect: (BotService) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = if (discoveredBots.isEmpty()) {
                "No devices online. Make sure a bot is on the same Wi-Fi, then come back."
            } else {
                "Choose which device to record and play on."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground.copy(alpha = 0.75f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        discoveredBots.forEach { bot ->
            val name = botCardInfo[botIdentity(bot)]?.deviceName ?: bot.serviceName
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(bot) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bot.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionsListPage(
    contentPadding: PaddingValues,
    bot: BotService?,
    botCardInfo: Map<String, BotCardInfo>,
    canChangeDevice: Boolean,
    actions: List<ServoAction>,
    isPlaying: Boolean,
    statusMessage: String?,
    error: String?,
    onChangeDevice: () -> Unit,
    onNewAction: () -> Unit,
    onPlay: (ServoAction) -> Unit,
    onStopPlay: () -> Unit,
    onEdit: (ServoAction) -> Unit,
    onRename: (ServoAction) -> Unit,
    onDelete: (ServoAction) -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deviceLabel = bot?.let { botCardInfo[botIdentity(it)]?.deviceName ?: it.serviceName }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (deviceLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = deviceLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (canChangeDevice) {
                    TextButton(onClick = onChangeDevice) {
                        Text("Change")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = "Record & Play motor actions on the bot.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground.copy(alpha = 0.75f),
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNewAction,
            enabled = bot != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create an action")
        }

        if (isPlaying) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStopPlay,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Play")
            }
        }

        statusMessage?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                modifier = Modifier.clickable(onClick = onClearError),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Saved actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (actions.isEmpty()) {
            Text(
                text = "No actions yet. Create one, move the head, and Add Frame.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.7f),
            )
        } else {
            actions.forEach { action ->
                ActionListCard(
                    action = action,
                    playEnabled = bot != null && !isPlaying,
                    onPlay = { onPlay(action) },
                    onEdit = { onEdit(action) },
                    onRename = { onRename(action) },
                    onDelete = { onDelete(action) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ActionListCard(
    action: ServoAction,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        border = BorderStroke(1.dp, Color.Black),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = action.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${action.frameCount} frames · ${formatDurationSeconds(action.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.7f),
            )
            if (action.hasAudio) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Audio: ${action.audioName ?: "Selected"} · ${formatDurationSeconds(action.audioDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallActionButton(
                    text = "Play",
                    enabled = playEnabled,
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                )
                SmallActionButton(
                    text = "Edit",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallActionButton(
                    text = "Rename",
                    onClick = onRename,
                    modifier = Modifier.weight(1f),
                    outlined = true,
                )
                SmallActionButton(
                    text = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    outlined = true,
                )
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(text, textAlign = TextAlign.Center)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionEditorPage(
    contentPadding: PaddingValues,
    bot: BotService?,
    actionName: String,
    motors: MotorSelection,
    frames: List<ActionFrame>,
    selectedFrameIndex: Int,
    liveServos: List<com.example.nino_home.LiveServo>,
    isRecording: Boolean,
    isPlaying: Boolean,
    isBusy: Boolean,
    statusMessage: String?,
    error: String?,
    selectedAudioId: String?,
    selectedAudioName: String?,
    selectedAudioDurationMs: Int?,
    mediaItems: List<MediaItem>,
    onNameChange: (String) -> Unit,
    onMotorsChange: (MotorSelection) -> Unit,
    onSelectAudio: (MediaItem) -> Unit,
    onClearAudio: () -> Unit,
    onEnterRecord: () -> Unit,
    onLeaveRecord: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onHoldMsChange: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onInsertBefore: () -> Unit,
    onInsertAfter: () -> Unit,
    onReplace: () -> Unit,
    onDeleteFrame: () -> Unit,
    onPreview: () -> Unit,
    onNeutral: () -> Unit,
    onPlay: () -> Unit,
    onStopPlay: () -> Unit,
    onSave: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onBackground,
        unfocusedTextColor = colors.onBackground,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        cursorColor = colors.primary,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onBackground,
    )
    val selectedFrame = frames.getOrNull(selectedFrameIndex)
    val tiltLive = liveServos.find { it.id == 1 }?.position
    val panLive = liveServos.find { it.id == 2 }?.position
    val actionDurationMs = frames.sumOf { it.holdMs }
    var showAudioPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = actionName,
            onValueChange = onNameChange,
            label = { Text("Action name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            trailingIcon = {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Motors",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotorChip("Lift", motors == MotorSelection.Tilt) { onMotorsChange(MotorSelection.Tilt) }
            MotorChip("Turn", motors == MotorSelection.Pan) { onMotorsChange(MotorSelection.Pan) }
            MotorChip("Both", motors == MotorSelection.Both) { onMotorsChange(MotorSelection.Both) }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Audio",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.Black),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (selectedAudioId == null) {
                    Text(
                        text = "No audio selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.65f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAudioPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Select audio from My Music")
                    }
                } else {
                    Text(
                        text = selectedAudioName ?: "Selected audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Audio duration: ${formatDurationSeconds(selectedAudioDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = { showAudioPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Change")
                        }
                        OutlinedButton(
                            onClick = onClearAudio,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Action duration: ${formatDurationSeconds(actionDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
                if (selectedAudioId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Audio duration: ${formatDurationSeconds(selectedAudioDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.Black),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Live positions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Lift (ID1): ${tiltLive ?: "—"}")
                Text("Turn (ID2): ${panLive ?: "—"}")
                Spacer(modifier = Modifier.height(10.dp))
                if (!isRecording) {
                    Button(
                        onClick = onEnterRecord,
                        enabled = bot != null && !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Text("Enter Record Mode")
                    }
                } else {
                    OutlinedButton(
                        onClick = onLeaveRecord,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Leave Record Mode")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNeutral,
                    enabled = bot != null && !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Neutral")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Frames (${frames.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (frames.isEmpty()) {
                Text(
                    text = "No frames yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            } else {
                frames.forEachIndexed { index, _ ->
                    val selected = index == selectedFrameIndex
                    Box(
                        modifier = Modifier
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) colors.primary else Color.Black,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                color = if (selected) colors.primary.copy(alpha = 0.12f) else Color.White,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectFrame(index) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "F$index",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = colors.onBackground,
                        )
                    }
                }
            }
        }

        if (selectedFrame != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.Black),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Selected F${selectedFrame.index}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    selectedFrame.pose.positions.toSortedMap().forEach { (id, pos) ->
                        val label = if (id == 1) "Lift" else if (id == 2) "Turn" else "ID$id"
                        Text("$label: $pos")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Hold (ms)", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HoldChip("-100", enabled = selectedFrame.holdMs >= 100) {
                            onHoldMsChange(selectedFrame.holdMs - 100)
                        }
                        Text(
                            text = "${selectedFrame.holdMs}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                        HoldChip("+100") {
                            onHoldMsChange(selectedFrame.holdMs + 100)
                        }
                        HoldChip("500") { onHoldMsChange(500) }
                        HoldChip("0") { onHoldMsChange(0) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Edit frames",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EditorButton("Add Frame", Modifier.weight(1f), enabled = isRecording || liveServos.isNotEmpty(), onClick = onAddFrame)
            EditorButton("Insert Before", Modifier.weight(1f), enabled = selectedFrame != null, onClick = onInsertBefore)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EditorButton("Insert After", Modifier.weight(1f), enabled = selectedFrame != null, onClick = onInsertAfter)
            EditorButton("Replace", Modifier.weight(1f), enabled = selectedFrame != null, onClick = onReplace)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EditorButton("Delete Frame", Modifier.weight(1f), enabled = selectedFrame != null, onClick = onDeleteFrame)
            EditorButton("Preview", Modifier.weight(1f), enabled = selectedFrame != null && bot != null, onClick = onPreview)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                modifier = Modifier.clickable(onClick = onClearError),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isPlaying) {
            OutlinedButton(
                onClick = onStopPlay,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Play")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onPlay,
            enabled = frames.isNotEmpty() && bot != null && !isBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play Action")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSave,
            enabled = frames.isNotEmpty() && actionName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text("Save Action")
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showAudioPicker) {
        AudioPickerDialog(
            mediaItems = mediaItems,
            selectedAudioId = selectedAudioId,
            onDismiss = { showAudioPicker = false },
            onSelect = { media ->
                onSelectAudio(media)
                showAudioPicker = false
            },
        )
    }
}

@Composable
private fun AutoCreateActionPage(
    contentPadding: PaddingValues,
    durationSeconds: String,
    selectedAudioId: String?,
    selectedAudioName: String?,
    selectedAudioDurationMs: Int?,
    mediaItems: List<MediaItem>,
    isBusy: Boolean,
    statusMessage: String?,
    error: String?,
    onDurationChange: (String) -> Unit,
    onSelectAudio: (MediaItem) -> Unit,
    onClearAudio: () -> Unit,
    onCreate: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onBackground,
        unfocusedTextColor = colors.onBackground,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        cursorColor = colors.primary,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onBackground,
    )
    var showAudioPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Generate a random head motion within safe angles, then choose whether to add it to Actions.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground.copy(alpha = 0.75f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Lift 510–550 · Turn 400–600 · motors start at neutral",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = durationSeconds,
            onValueChange = onDurationChange,
            label = { Text("Action duration (seconds)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Audio",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.Black),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (selectedAudioId == null) {
                    Text(
                        text = "No audio selected (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.65f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAudioPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Select audio from My Music")
                    }
                } else {
                    Text(
                        text = selectedAudioName ?: "Selected audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Audio duration: ${formatDurationSeconds(selectedAudioDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = { showAudioPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Change")
                        }
                        OutlinedButton(
                            onClick = onClearAudio,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                modifier = Modifier.clickable(onClick = onClearError),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onCreate,
            enabled = !isBusy && durationSeconds.toIntOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text(if (isBusy) "Creating…" else "Create action")
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showAudioPicker) {
        AudioPickerDialog(
            mediaItems = mediaItems,
            selectedAudioId = selectedAudioId,
            onDismiss = { showAudioPicker = false },
            onSelect = { media ->
                onSelectAudio(media)
                showAudioPicker = false
            },
        )
    }
}

@Composable
private fun AudioPickerDialog(
    mediaItems: List<MediaItem>,
    selectedAudioId: String?,
    onDismiss: () -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select audio") },
        text = {
            Column {
                Text(
                    text = "From My Music → Media",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.65f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (mediaItems.isEmpty()) {
                    Text(
                        text = "No media available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.7f),
                    )
                } else {
                    mediaItems.forEachIndexed { index, item ->
                        val selected = item.id == selectedAudioId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .background(
                                    if (selected) colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onBackground.copy(alpha = 0.6f),
                                )
                            }
                            Text(
                                text = formatDurationSeconds(item.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.onBackground,
                            )
                        }
                        if (index < mediaItems.lastIndex) {
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MotorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
        ),
    )
}

@Composable
private fun HoldChip(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EditorButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.secondary,
            disabledContentColor = colors.onSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RecordPlayGuidePage(
    contentPadding: PaddingValues,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        GuideSection(
            title = "What is Record & Play?",
            body = "Teach head motions on the bot by capturing motor poses as frames, joining them into a named action, then playing that action back.",
        )
        GuideSection(
            title = "Key ideas",
            body = "Frame = one snapshot of Lift (ID1) and/or Turn (ID2) plus hold time.\n" +
                "Action = ordered list of frames played in order.\n" +
                "Neutral = center pose 512 for both motors.\n" +
                "Actions are saved on the phone; the bot receives frames only when you Play.",
        )
        GuideSection(
            title = "How to open",
            body = "Home - Scenes → ⋮ (top right) → Record and Play.\n" +
                "Keep the bot on the same Wi-Fi as your phone.",
        )
        GuideSection(
            title = "Actions page buttons",
            body = "? (circle) — opens this guide.\n" +
                "Create an action — start a blank action in the editor.\n" +
                "Stop Play — stop an action that is currently playing.\n" +
                "Play (on a card) — run that saved action on the bot.\n" +
                "Edit — open the action in the editor.\n" +
                "Rename — change only the action name.\n" +
                "Delete — remove the action from phone storage.",
        )
        GuideSection(
            title = "Create an action (step by step)",
            body = "1. Tap Create an action and enter a name.\n" +
                "2. Choose motors: Lift, Turn, or Both.\n" +
                "3. Tap Enter Record Mode.\n" +
                "   Motors automatically move to Neutral (512), then torque turns off for hand teaching.\n" +
                "4. Move the head by hand, then tap Add Frame. Repeat.\n" +
                "5. Fix poses with Insert Before / Insert After / Replace / Delete Frame.\n" +
                "6. Adjust Hold (ms) on a selected frame if needed.\n" +
                "7. Optional: Preview a selected frame, or Neutral to return to center.\n" +
                "8. Tap Save Action.\n" +
                "9. From Actions (or My Music → Actions), tap Play.",
        )
        GuideSection(
            title = "Action Editor controls",
            body = "Action name — required before Save.\n" +
                "Lift / Turn / Both — which motors to capture and move.\n" +
                "Audio — optional clip from My Music → Media; shows audio duration next to action duration.\n" +
                "Enter Record Mode — go to neutral 512, then enable hand teach + live angles.\n" +
                "Leave Record Mode — stop teaching mode and restore normal torque behavior.\n" +
                "Neutral — send selected motors to 512 anytime.\n" +
                "Live positions — current Lift/Turn readout while recording.\n" +
                "Frame strip (F0, F1…) — tap to select a frame.\n" +
                "Hold -100 / +100 / 500 / 0 — edit hold time for the selected frame.\n" +
                "Add Frame — append current live pose.\n" +
                "Insert Before / Insert After — insert current live pose around the selected frame.\n" +
                "Replace — overwrite selected frame pose with live pose (keep hold time).\n" +
                "Delete Frame — remove selected frame and reindex.\n" +
                "Preview — move bot to the selected frame pose only.\n" +
                "Play Action — play current editor frames on the bot.\n" +
                "Stop Play — stop playback.\n" +
                "Save Action — store name + frames (+ optional audio) on the phone.",
        )
        GuideSection(
            title = "My Music → Actions",
            body = "Saved action names also appear under My Music → device → Actions.\n" +
                "Tap a name to play that action (and its linked audio, if any).\n" +
                "Tap media under My Music → Media to play that audio; if an action is linked to it, both play together.\n" +
                "Create and edit still happen in Record and Play.",
        )
        GuideSection(
            title = "Tips",
            body = "Always Leave Record Mode when finished.\n" +
                "Neutral and Enter Record Mode both use position 512.\n" +
                "Need at least one frame to Save or Play; two or more frames make a motion.\n" +
                "If Play fails, confirm phone and bot share the same Wi-Fi.",
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GuideSection(
    title: String,
    body: String,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.onBackground,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onBackground.copy(alpha = 0.85f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
    Spacer(modifier = Modifier.height(16.dp))
}
