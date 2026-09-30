package com.example.lumeocrtest

import android.content.Context
import com.example.lumeocrtest.research.Evaluation
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

data class SavedResearch(val query: String, val savedAt: Long, val result: String)

/** Histórico privado do aparelho; imagens e texto bruto de OCR nunca são salvos aqui. */
object ResearchHistory {
    private const val PREFS = "lume_history"
    private const val ITEMS = "researches"
    private const val DRAFT = "draft"
    private const val ENABLED = "enabled"
    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        val edit = prefs(context).edit().putBoolean(ENABLED, enabled)
        if (!enabled) edit.remove(DRAFT)
        edit.apply()
    }
    fun draft(context: Context): String = if (enabled(context)) prefs(context).getString(DRAFT, "") ?: "" else ""
    fun saveDraft(context: Context, text: String) {
        if (!enabled(context)) return
        prefs(context).edit().putString(DRAFT, text.take(4_000)).apply()
    }

    fun list(context: Context): List<SavedResearch> = runCatching {
        val array = JSONArray(prefs(context).getString(ITEMS, "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map {
            SavedResearch(it.getString("query"), it.getLong("savedAt"), it.getString("result"))
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, query: String, evaluation: Evaluation) {
        if (!enabled(context) || evaluation.partes.all { it.status == "erro" }) return
        val current = list(context).filterNot { it.query == query }.take(11)
        val array = JSONArray()
        (listOf(SavedResearch(query.take(700), System.currentTimeMillis(), gson.toJson(evaluation))) + current)
            .forEach { array.put(JSONObject().put("query", it.query).put("savedAt", it.savedAt).put("result", it.result)) }
        prefs(context).edit().putString(ITEMS, array.toString()).apply()
    }

    fun restore(saved: SavedResearch): Evaluation? = runCatching { gson.fromJson(saved.result, Evaluation::class.java) }.getOrNull()
    fun clear(context: Context) { prefs(context).edit().remove(ITEMS).remove(DRAFT).apply() }
}
