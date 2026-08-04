package com.example.nino_home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ServoActionRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadActions(): List<ServoAction> {
        val raw = prefs.getString(KEY_ACTIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(array.getJSONObject(i).toServoAction())
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrElse { emptyList() }
    }

    fun saveActions(actions: List<ServoAction>) {
        val array = JSONArray()
        actions.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACTIONS, array.toString()).apply()
    }

    fun upsert(action: ServoAction) {
        val current = loadActions().toMutableList()
        val index = current.indexOfFirst { it.id == action.id }
        if (index >= 0) {
            current[index] = action
        } else {
            current.add(0, action)
        }
        saveActions(current)
    }

    fun delete(actionId: String) {
        saveActions(loadActions().filterNot { it.id == actionId })
    }

    fun rename(actionId: String, newName: String, updatedAt: String) {
        val updated = loadActions().map { action ->
            if (action.id == actionId) {
                action.copy(name = newName, updatedAt = updatedAt)
            } else {
                action
            }
        }
        saveActions(updated)
    }

    companion object {
        private const val PREFS_NAME = "nino_servo_actions"
        private const val KEY_ACTIONS = "actions"
    }
}
