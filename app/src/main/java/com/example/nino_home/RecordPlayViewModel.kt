package com.example.nino_home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class MotorSelection {
    Tilt,
    Pan,
    Both,
}

data class RecordPlayUiState(
    val actions: List<ServoAction> = emptyList(),
    val editingActionId: String? = null,
    val actionName: String = "New Action",
    val motors: MotorSelection = MotorSelection.Both,
    val frames: List<ActionFrame> = emptyList(),
    val selectedFrameIndex: Int = -1,
    val liveServos: List<LiveServo> = emptyList(),
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isBusy: Boolean = false,
    val defaultHoldMs: Int = 500,
    val statusMessage: String? = null,
    val error: String? = null,
)

class RecordPlayViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val POSITION_POLL_MS = 200L
        const val MOTOR_TILT = 1
        const val MOTOR_PAN = 2
        const val NEUTRAL_POSITION = 512
    }

    private val repository = ServoActionRepository(application)
    private val _uiState = MutableStateFlow(RecordPlayUiState(actions = repository.loadActions()))
    val uiState: StateFlow<RecordPlayUiState> = _uiState.asStateFlow()

    private var bot: BotService? = null
    private var pollJob: Job? = null

    fun bindBot(bot: BotService?) {
        this.bot = bot
        if (bot == null) {
            stopPolling()
            _uiState.update {
                it.copy(error = "No device connected. Open Record and Play from Home while a device is on Wi-Fi.")
            }
        } else {
            _uiState.update { it.copy(error = null) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = null) }

    fun refreshActions() {
        _uiState.update { it.copy(actions = repository.loadActions()) }
    }

    fun startNewAction() {
        _uiState.update {
            it.copy(
                editingActionId = null,
                actionName = "New Action",
                motors = MotorSelection.Both,
                frames = emptyList(),
                selectedFrameIndex = -1,
                statusMessage = "Move the head, then tap Add Frame.",
                error = null,
            )
        }
    }

    fun openActionForEdit(action: ServoAction) {
        _uiState.update {
            it.copy(
                editingActionId = action.id,
                actionName = action.name,
                motors = motorsFromIds(action.motors),
                frames = action.frames.reindexed(),
                selectedFrameIndex = if (action.frames.isNotEmpty()) 0 else -1,
                statusMessage = "Editing “${action.name}”",
                error = null,
            )
        }
    }

    fun updateActionName(name: String) {
        _uiState.update { it.copy(actionName = name.take(40)) }
    }

    fun setMotors(selection: MotorSelection) {
        _uiState.update { it.copy(motors = selection) }
    }

    fun selectFrame(index: Int) {
        _uiState.update { state ->
            state.copy(selectedFrameIndex = index.coerceIn(-1, state.frames.lastIndex))
        }
    }

    fun updateSelectedHoldMs(holdMs: Int) {
        val clamped = holdMs.coerceIn(0, 10_000)
        _uiState.update { state ->
            val index = state.selectedFrameIndex
            if (index !in state.frames.indices) return@update state
            val updated = state.frames.toMutableList()
            updated[index] = updated[index].copy(holdMs = clamped)
            state.copy(frames = updated)
        }
    }

    fun enterRecordMode() {
        val target = bot ?: return missingBot()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, error = null) }
            val ids = selectedMotorIds()
            val result = runCatching {
                // Move to neutral first while torque is still available, then enter hand-teach mode.
                ids.forEach { id ->
                    postGoal(target, id = id, position = NEUTRAL_POSITION, speed = 22)
                }
                delay(700)
                postRecord(target, action = "start", ids = ids, torqueOff = true)
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isRecording = true,
                            statusMessage = "Record mode on — motors at neutral ($NEUTRAL_POSITION). Move head and Add Frame.",
                        )
                    }
                    startPolling(target)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            error = err.message ?: "Failed to enter record mode",
                        )
                    }
                },
            )
        }
    }

    fun leaveRecordMode() {
        val target = bot
        stopPolling()
        if (target == null) {
            _uiState.update { it.copy(isRecording = false) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { postRecord(target, action = "stop") }
            _uiState.update {
                it.copy(
                    isRecording = false,
                    statusMessage = "Record mode stopped.",
                )
            }
        }
    }

    fun addFrame(insertMode: InsertMode = InsertMode.Append) {
        val pose = captureLivePose()
        if (pose == null) {
            _uiState.update {
                it.copy(error = "No live positions yet. Enter record mode and wait for motor readout.")
            }
            return
        }
        val hold = if (_uiState.value.frames.isEmpty()) 0 else _uiState.value.defaultHoldMs
        val newFrame = ActionFrame(index = 0, holdMs = hold, pose = pose)
        _uiState.update { state ->
            val frames = state.frames.toMutableList()
            val selected = state.selectedFrameIndex
            when (insertMode) {
                InsertMode.Append -> frames.add(newFrame)
                InsertMode.Before -> {
                    val at = if (selected in frames.indices) selected else 0
                    frames.add(at, newFrame)
                }
                InsertMode.After -> {
                    val at = if (selected in frames.indices) selected + 1 else frames.size
                    frames.add(at, newFrame)
                }
            }
            val reindexed = frames.reindexed()
            val newSelected = when (insertMode) {
                InsertMode.Append -> reindexed.lastIndex
                InsertMode.Before -> if (selected in state.frames.indices) selected else 0
                InsertMode.After -> if (selected in state.frames.indices) selected + 1 else reindexed.lastIndex
            }
            state.copy(
                frames = reindexed,
                selectedFrameIndex = newSelected,
                statusMessage = "Frame added (${reindexed.size} total)",
                error = null,
            )
        }
    }

    fun replaceSelectedFrame() {
        val pose = captureLivePose() ?: run {
            _uiState.update { it.copy(error = "No live positions to capture") }
            return
        }
        _uiState.update { state ->
            val index = state.selectedFrameIndex
            if (index !in state.frames.indices) {
                return@update state.copy(error = "Select a frame to replace")
            }
            val updated = state.frames.toMutableList()
            updated[index] = updated[index].copy(pose = pose)
            state.copy(
                frames = updated,
                statusMessage = "Frame $index replaced",
                error = null,
            )
        }
    }

    fun deleteSelectedFrame() {
        _uiState.update { state ->
            val index = state.selectedFrameIndex
            if (index !in state.frames.indices) {
                return@update state.copy(error = "Select a frame to delete")
            }
            val updated = state.frames.toMutableList().also { it.removeAt(index) }.reindexed()
            val newSelected = when {
                updated.isEmpty() -> -1
                index >= updated.size -> updated.lastIndex
                else -> index
            }
            state.copy(
                frames = updated,
                selectedFrameIndex = newSelected,
                statusMessage = "Frame deleted",
                error = null,
            )
        }
    }

    fun saveAction() {
        val state = _uiState.value
        val name = state.actionName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "Action name cannot be empty") }
            return
        }
        if (state.frames.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one frame before saving") }
            return
        }
        val now = Instant.now().toString()
        val action = ServoAction(
            id = state.editingActionId ?: newActionId(),
            name = name,
            createdAt = state.editingActionId
                ?.let { id -> state.actions.find { it.id == id }?.createdAt }
                ?: now,
            updatedAt = now,
            motors = selectedMotorIds(),
            frames = state.frames.reindexed(),
        )
        repository.upsert(action)
        if (state.isRecording) {
            leaveRecordMode()
        }
        _uiState.update {
            it.copy(
                actions = repository.loadActions(),
                editingActionId = action.id,
                statusMessage = "Saved “${action.name}”",
                error = null,
            )
        }
    }

    fun renameAction(actionId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return
        }
        repository.rename(actionId, trimmed.take(40), Instant.now().toString())
        _uiState.update {
            it.copy(
                actions = repository.loadActions(),
                actionName = if (it.editingActionId == actionId) trimmed.take(40) else it.actionName,
                statusMessage = "Renamed to “${trimmed.take(40)}”",
            )
        }
    }

    fun deleteAction(actionId: String) {
        repository.delete(actionId)
        _uiState.update {
            it.copy(
                actions = repository.loadActions(),
                statusMessage = "Action deleted",
            )
        }
    }

    fun playAction(action: ServoAction) {
        val target = bot ?: return missingBot()
        if (action.frames.isEmpty()) {
            _uiState.update { it.copy(error = "Action has no frames") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, isPlaying = true, error = null, statusMessage = "Playing “${action.name}”…") }
            val result = runCatching { postPlay(target, action) }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isPlaying = false,
                            statusMessage = "Finished “${action.name}”",
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isPlaying = false,
                            error = err.message ?: "Play failed",
                        )
                    }
                },
            )
        }
    }

    fun playCurrentEditorAction() {
        val state = _uiState.value
        if (state.frames.isEmpty()) {
            _uiState.update { it.copy(error = "Add frames before playing") }
            return
        }
        val draft = ServoAction(
            id = state.editingActionId ?: "draft",
            name = state.actionName.ifBlank { "Draft" },
            createdAt = "",
            updatedAt = "",
            motors = selectedMotorIds(),
            frames = state.frames.reindexed(),
        )
        playAction(draft)
    }

    fun stopPlay() {
        val target = bot ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { postPlayStop(target) }
            _uiState.update { it.copy(isPlaying = false, isBusy = false, statusMessage = "Play stopped") }
        }
    }

    fun goNeutral() {
        val target = bot ?: return missingBot()
        val ids = selectedMotorIds().ifEmpty { listOf(MOTOR_TILT, MOTOR_PAN) }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, error = null) }
            val result = runCatching {
                ids.forEach { id ->
                    postGoal(target, id = id, position = NEUTRAL_POSITION, speed = 22)
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Motors moved to neutral ($NEUTRAL_POSITION)",
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            error = err.message ?: "Failed to go neutral",
                        )
                    }
                },
            )
        }
    }

    fun previewSelectedFrame() {
        val target = bot ?: return missingBot()
        val state = _uiState.value
        val index = state.selectedFrameIndex
        if (index !in state.frames.indices) {
            _uiState.update { it.copy(error = "Select a frame to preview") }
            return
        }
        val frame = state.frames[index]
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, error = null) }
            val result = runCatching {
                frame.pose.positions.forEach { (id, position) ->
                    postGoal(target, id = id, position = position, speed = 22)
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isBusy = false, statusMessage = "Previewed frame $index")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isBusy = false, error = err.message ?: "Preview failed")
                    }
                },
            )
        }
    }

    override fun onCleared() {
        stopPolling()
        val target = bot
        if (_uiState.value.isRecording && target != null) {
            // Best-effort leave record mode
            runCatching {
                // Can't easily block here; fire and forget on IO would need scope.
            }
        }
        super.onCleared()
    }

    private fun missingBot() {
        _uiState.update {
            it.copy(error = "No device selected. Keep the bot on the same Wi-Fi.")
        }
    }

    private fun selectedMotorIds(): List<Int> = when (_uiState.value.motors) {
        MotorSelection.Tilt -> listOf(MOTOR_TILT)
        MotorSelection.Pan -> listOf(MOTOR_PAN)
        MotorSelection.Both -> listOf(MOTOR_TILT, MOTOR_PAN)
    }

    private fun motorsFromIds(ids: List<Int>): MotorSelection = when {
        ids.toSet() == setOf(MOTOR_TILT) -> MotorSelection.Tilt
        ids.toSet() == setOf(MOTOR_PAN) -> MotorSelection.Pan
        else -> MotorSelection.Both
    }

    private fun captureLivePose(): ServoPose? {
        val ids = selectedMotorIds()
        val live = _uiState.value.liveServos
        if (live.isEmpty()) return null
        val positions = mutableMapOf<Int, Int>()
        ids.forEach { id ->
            val servo = live.find { it.id == id } ?: return null
            positions[id] = servo.position.coerceIn(0, 1023)
        }
        return ServoPose(positions)
    }

    private fun startPolling(bot: BotService) {
        stopPolling()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { fetchPositions(bot) }
                    .onSuccess { snapshot ->
                        _uiState.update {
                            it.copy(
                                liveServos = snapshot.servos,
                                error = if (!snapshot.ready && it.error == null) {
                                    "Servos not ready"
                                } else {
                                    it.error
                                },
                            )
                        }
                    }
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun fetchPositions(bot: BotService): ServoPositionSnapshot {
        val url = URL("http://${bot.host}:${bot.port}/servo/position")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Position request failed ($code)")
            val payload = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            val servosJson = json.optJSONArray("servos") ?: JSONArray()
            val servos = buildList {
                for (i in 0 until servosJson.length()) {
                    val s = servosJson.getJSONObject(i)
                    add(
                        LiveServo(
                            id = s.optInt("id"),
                            position = s.optInt("position", 512),
                            torque = s.optBoolean("torque", false),
                        ),
                    )
                }
            }
            ServoPositionSnapshot(
                ok = json.optBoolean("ok", true),
                ready = json.optBoolean("ready", true),
                mode = json.optString("mode", "idle"),
                servos = servos,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun postRecord(
        bot: BotService,
        action: String,
        ids: List<Int> = emptyList(),
        torqueOff: Boolean = true,
    ) {
        val url = URL("http://${bot.host}:${bot.port}/servo/record")
        val body = JSONObject().put("action", action).apply {
            if (action == "start") {
                put("ids", JSONArray(ids))
                put("torque_off", torqueOff)
            }
        }
        postJson(url, body)
    }

    private fun postGoal(bot: BotService, id: Int, position: Int, speed: Int) {
        val url = URL("http://${bot.host}:${bot.port}/servo/goal")
        val body = JSONObject()
            .put("id", id)
            .put("position", position)
            .put("speed", speed)
        postJson(url, body)
    }

    private fun postPlay(bot: BotService, action: ServoAction) {
        val url = URL("http://${bot.host}:${bot.port}/servo/play")
        val framesJson = JSONArray()
        action.frames.reindexed().forEach { frame ->
            val p = JSONObject()
            frame.pose.positions.forEach { (id, pos) ->
                p.put(id.toString(), pos)
            }
            framesJson.put(
                JSONObject()
                    .put("hold_ms", frame.holdMs)
                    .put("p", p),
            )
        }
        val body = JSONObject()
            .put("name", action.name)
            .put("speed", 22)
            .put("frames", framesJson)
        postJson(url, body, readTimeoutMs = 60_000)
    }

    private fun postPlayStop(bot: BotService) {
        val url = URL("http://${bot.host}:${bot.port}/servo/play/stop")
        postJson(url, JSONObject())
    }

    private fun postJson(url: URL, body: JSONObject, readTimeoutMs: Int = 5000) {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching {
                    (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                throw IOException("Request failed ($code)${errBody?.let { ": $it" } ?: ""}")
            }
        } finally {
            conn.disconnect()
        }
    }
}

enum class InsertMode {
    Append,
    Before,
    After,
}
