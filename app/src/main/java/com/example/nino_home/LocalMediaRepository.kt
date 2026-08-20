package com.example.nino_home

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class LocalMediaRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<MediaItem> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val uri = o.optString("uri")
                if (id.isBlank() || uri.isBlank()) continue
                add(
                    MediaItem(
                        id = id,
                        name = o.optString("name", "Song"),
                        description = "From phone",
                        durationMs = o.optInt("durationMs", -1).takeIf { it > 0 },
                        kind = MediaKind.Local,
                        uri = uri,
                    ),
                )
            }
        }
    }

    fun upsert(item: MediaItem) {
        val items = load().toMutableList()
        val index = items.indexOfFirst { it.id == item.id || it.uri == item.uri }
        if (index >= 0) {
            items[index] = item
        } else {
            items.add(item)
        }
        save(items)
    }

    fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }

    private fun save(items: List<MediaItem>) {
        val arr = JSONArray()
        items.filter { it.kind == MediaKind.Local && !it.uri.isNullOrBlank() }.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("uri", item.uri)
                    .put("durationMs", item.durationMs ?: -1),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "nino_local_media"
        private const val KEY = "items"

        fun idForUri(uri: Uri): String = "local_${uri.toString().hashCode().toUInt()}"
    }
}
