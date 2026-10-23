package com.yigit.isiklar.govee

import com.yigit.isiklar.model.LightState
import com.yigit.isiklar.model.Rgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Govee bulut API'si (openapi.api.govee.com, v1 router).
 * Anahtar: Govee Home > Profil > Ayarlar > "Apply for API Key".
 */
object CloudClient {
    private const val BASE = "https://openapi.api.govee.com/router/api/v1"

    data class CloudDevice(val id: String, val rawId: String, val sku: String, val name: String)

    class CloudException(message: String) : Exception(message)

    suspend fun devices(apiKey: String): List<CloudDevice> {
        val res = request("GET", "/user/devices", apiKey, null)
        val arr = res.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<CloudDevice>()
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            if (!hasInstance(d.optJSONArray("capabilities"), "powerSwitch")) continue
            val raw = d.optString("device")
            if (raw.isEmpty()) continue
            val sku = d.optString("sku")
            list += CloudDevice(raw.uppercase(), raw, sku, d.optString("deviceName").ifBlank { "Govee $sku" })
        }
        return list
    }

    suspend fun state(apiKey: String, sku: String, deviceId: String): LightState {
        val body = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("payload", JSONObject().put("sku", sku).put("device", deviceId))
        val res = request("POST", "/device/state", apiKey, body)
        val caps = res.optJSONObject("payload")?.optJSONArray("capabilities") ?: return LightState()
        var on: Boolean? = null
        var brightness: Int? = null
        var color: Rgb? = null
        var kelvin: Int? = null
        for (i in 0 until caps.length()) {
            val c = caps.optJSONObject(i) ?: continue
            val n = (c.optJSONObject("state")?.opt("value") as? Number)?.toInt() ?: continue
            when (c.optString("instance")) {
                "powerSwitch" -> on = n == 1
                "brightness" -> brightness = n
                "colorRgb" -> color = Rgb.fromInt(n)
                "colorTemperatureK" -> if (n > 0) kelvin = n
            }
        }
        return LightState(on, brightness, if (kelvin != null) null else color, kelvin)
    }

    suspend fun power(apiKey: String, sku: String, deviceId: String, on: Boolean) =
        control(apiKey, sku, deviceId, "devices.capabilities.on_off", "powerSwitch", if (on) 1 else 0)

    suspend fun brightness(apiKey: String, sku: String, deviceId: String, value: Int) =
        control(apiKey, sku, deviceId, "devices.capabilities.range", "brightness", value.coerceIn(1, 100))

    suspend fun color(apiKey: String, sku: String, deviceId: String, rgb: Rgb) =
        control(apiKey, sku, deviceId, "devices.capabilities.color_setting", "colorRgb", rgb.toInt())

    suspend fun kelvin(apiKey: String, sku: String, deviceId: String, kelvin: Int) =
        control(
            apiKey, sku, deviceId, "devices.capabilities.color_setting", "colorTemperatureK",
            kelvin.coerceIn(2000, 9000),
        )

    // ---------------------------------------------------------------------

    private suspend fun control(
        apiKey: String, sku: String, deviceId: String,
        type: String, instance: String, value: Int,
    ) {
        val capability = JSONObject().put("type", type).put("instance", instance).put("value", value)
        val body = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("payload", JSONObject().put("sku", sku).put("device", deviceId).put("capability", capability))
        request("POST", "/device/control", apiKey, body)
    }

    private fun hasInstance(caps: JSONArray?, instance: String): Boolean {
        if (caps == null) return false
        for (i in 0 until caps.length()) {
            if (caps.optJSONObject(i)?.optString("instance") == instance) return true
        }
        return false
    }

    private suspend fun request(method: String, path: String, apiKey: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(BASE + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Govee-API-Key", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            try {
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (code == 401 || code == 403) throw CloudException("Govee API anahtarı geçersiz")
                if (code == 429) throw CloudException("Govee istek sınırı aşıldı, biraz bekle")
                if (code !in 200..299) throw CloudException("Govee bulut hatası ($code)")
                val apiCode = json.optInt("code", 200)
                if (apiCode != 200) {
                    val m = json.optString("message").ifBlank { json.optString("msg") }.ifBlank { "Govee bulut hatası" }
                    throw CloudException("$m ($apiCode)")
                }
                json
            } finally {
                conn.disconnect()
            }
        }
}
