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
) {
    val durationMs: Int get() = frames.sumOf { it.holdMs }
    val frameCount: Int get() = frames.size
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
    return ServoAction(
        id = optString("id", UUID.randomUUID().toString()),
        name = optString("name", "Untitled"),
        createdAt = optString("created_at", ""),
        updatedAt = optString("updated_at", ""),
        motors = motors.ifEmpty { listOf(1, 2) },
        frames = frames,
    )
}

fun List<ActionFrame>.reindexed(): List<ActionFrame> =
    mapIndexed { index, frame -> frame.copy(index = index) }

fun newActionId(): String = "act_${System.currentTimeMillis()}"
