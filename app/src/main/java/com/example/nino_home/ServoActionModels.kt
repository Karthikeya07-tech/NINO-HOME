package com.example.nino_home

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ServoPose(
    val positions: Map<Int, Int>,
) {
    fun positionFor(id: Int): Int? = positions[id]
}

data class ActionFrame(
    val index: Int,
    val holdMs: Int,
    val pose: ServoPose,
)

data class ServoAction(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val motors: List<Int>,
    val frames: List<ActionFrame>,
    /** Optional media id from [MediaLibrary] / My Music → Media. */
    val audioId: String? = null,
    val audioName: String? = null,
    val audioDurationMs: Int? = null,
) {
    val durationMs: Int get() = frames.sumOf { it.holdMs }
    val frameCount: Int get() = frames.size
    val hasAudio: Boolean get() = !audioId.isNullOrBlank()
}

data class LiveServo(
    val id: Int,
    val position: Int,
    val torque: Boolean,
)

data class ServoPositionSnapshot(
    val ok: Boolean,
    val ready: Boolean,
    val mode: String,
    val servos: List<LiveServo>,
)

fun ServoAction.toJson(): JSONObject {
    val framesJson = JSONArray()
    frames.forEach { frame ->
        val p = JSONObject()
        frame.pose.positions.forEach { (id, pos) ->
            p.put(id.toString(), pos)
        }
        framesJson.put(
            JSONObject()
                .put("index", frame.index)
                .put("hold_ms", frame.holdMs)
                .put("p", p),
        )
    }
    return JSONObject()
        .put("id", id)
        .put("name", name)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("motors", JSONArray(motors))
        .put("frames", framesJson)
        .apply {
            if (!audioId.isNullOrBlank()) {
                put("audio_id", audioId)
                put("audio_name", audioName ?: "")
                if (audioDurationMs != null) put("audio_duration_ms", audioDurationMs)
            }
        }
}

fun JSONObject.toServoAction(): ServoAction {
    val motorsJson = optJSONArray("motors") ?: JSONArray()
    val motors = buildList {
        for (i in 0 until motorsJson.length()) {
            add(motorsJson.getInt(i))
        }
    }
    val framesJson = optJSONArray("frames") ?: JSONArray()
    val frames = buildList {
        for (i in 0 until framesJson.length()) {
            val f = framesJson.getJSONObject(i)
            val pObj = f.optJSONObject("p") ?: JSONObject()
            val positions = mutableMapOf<Int, Int>()
            pObj.keys().forEach { key ->
                positions[key.toInt()] = pObj.getInt(key)
            }
            add(
                ActionFrame(
                    index = f.optInt("index", i),
                    holdMs = f.optInt("hold_ms", 500),
                    pose = ServoPose(positions),
                ),
            )
        }
    }.sortedBy { it.index }
    val audioId = optString("audio_id", "").ifBlank { null }
    return ServoAction(
        id = optString("id", UUID.randomUUID().toString()),
        name = optString("name", "Untitled"),
        createdAt = optString("created_at", ""),
        updatedAt = optString("updated_at", ""),
        motors = motors.ifEmpty { listOf(1, 2) },
        frames = frames,
        audioId = audioId,
        audioName = optString("audio_name", "").ifBlank { null },
        audioDurationMs = if (has("audio_duration_ms")) optInt("audio_duration_ms") else null,
    )
}

fun List<ActionFrame>.reindexed(): List<ActionFrame> =
    mapIndexed { index, frame -> frame.copy(index = index) }

fun newActionId(): String = "act_${System.currentTimeMillis()}"
