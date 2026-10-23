package com.yigit.isiklar.data

import android.content.Context
import com.yigit.isiklar.model.LightDevice
import com.yigit.isiklar.model.LightState
import com.yigit.isiklar.model.Rgb
import com.yigit.isiklar.model.Scene
import com.yigit.isiklar.model.SceneAction
import com.yigit.isiklar.model.defaultScenes
import org.json.JSONArray
import org.json.JSONObject

/** Ayarlar, bilinen cihazlar ve sahneler — SharedPreferences içinde JSON olarak. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("isiklar", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API, value.trim()).apply()
        }

    fun loadDevices(): List<LightDevice> = readArray(KEY_DEVICES).mapNotNull { o ->
        val id = o.optString("id")
        if (id.isEmpty()) return@mapNotNull null
        LightDevice(
            id = id,
            sku = o.optString("sku"),
            name = o.optString("name").ifBlank { id },
            ip = o.optNullString("ip"),
            cloudId = o.optNullString("cloudId"),
            state = stateFrom(o.optJSONObject("state")),
            caps = o.optJSONArray("caps")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() },
            kelvinMin = o.optInt("kMin", 2000),
            kelvinMax = o.optInt("kMax", 9000),
        )
    }

    fun saveDevices(list: List<LightDevice>) {
        val arr = JSONArray()
        list.forEach { d ->
            val o = JSONObject()
                .put("id", d.id)
                .put("sku", d.sku)
                .put("name", d.name)
                .put("ip", d.ip ?: "")
                .put("cloudId", d.cloudId ?: "")
                .put("state", stateTo(d.state))
                .put("kMin", d.kelvinMin)
                .put("kMax", d.kelvinMax)
            d.caps?.let { caps -> o.put("caps", JSONArray(caps.toList())) }
            arr.put(o)
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    fun loadScenes(): List<Scene> {
        if (!prefs.contains(KEY_SCENES)) return defaultScenes()
        return readArray(KEY_SCENES).mapNotNull { o ->
            val id = o.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            val actions = mutableMapOf<String, SceneAction>()
            o.optJSONObject("actions")?.let { a ->
                val keys = a.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    a.optJSONObject(k)?.let { actions[k] = actionFrom(it) }
                }
            }
            var all = o.optJSONObject("all")?.let { actionFrom(it) }
            // Eski sürümde kaydedilmiş "Film" sahnesini ekran senkronuna yükselt
            if (id == "film" && all != null && o.optJSONObject("all")?.has("sync") != true) {
                all = all.copy(sync = true)
            }
            Scene(
                id = id,
                name = o.optString("name").ifBlank { "Sahne" },
                actions = actions,
                applyToAll = all,
            )
        }
    }

    fun saveScenes(list: List<Scene>) {
        val arr = JSONArray()
        list.forEach { s ->
            val actions = JSONObject()
            s.actions.forEach { (k, v) -> actions.put(k, actionTo(v)) }
            val o = JSONObject().put("id", s.id).put("name", s.name).put("actions", actions)
            s.applyToAll?.let { o.put("all", actionTo(it)) }
            arr.put(o)
        }
        prefs.edit().putString(KEY_SCENES, arr.toString()).apply()
    }

    // ---------------------------------------------------------------------

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.optNullString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

    private fun stateFrom(o: JSONObject?): LightState {
        if (o == null) return LightState()
        return LightState(
            on = if (o.has("on")) o.optBoolean("on") else null,
            brightness = if (o.has("brightness")) o.optInt("brightness") else null,
            color = if (o.has("color")) Rgb.fromInt(o.optInt("color")) else null,
            kelvin = if (o.has("kelvin")) o.optInt("kelvin") else null,
        )
    }

    private fun stateTo(s: LightState): JSONObject {
        val o = JSONObject()
        s.on?.let { o.put("on", it) }
        s.brightness?.let { o.put("brightness", it) }
        s.color?.let { o.put("color", it.toInt()) }
        s.kelvin?.let { o.put("kelvin", it) }
        return o
    }

    private fun actionFrom(o: JSONObject) = SceneAction(
        on = o.optBoolean("on", true),
        brightness = if (o.has("brightness")) o.optInt("brightness") else null,
        color = if (o.has("color")) Rgb.fromInt(o.optInt("color")) else null,
        kelvin = if (o.has("kelvin")) o.optInt("kelvin") else null,
        sync = o.optBoolean("sync", false),
    )

    private fun actionTo(a: SceneAction): JSONObject {
        val o = JSONObject().put("on", a.on).put("sync", a.sync)
        a.brightness?.let { o.put("brightness", it) }
        a.color?.let { o.put("color", it.toInt()) }
        a.kelvin?.let { o.put("kelvin", it) }
        return o
    }

    private companion object {
        const val KEY_API = "apiKey"
        const val KEY_DEVICES = "devices"
        const val KEY_SCENES = "scenes"
    }
}
