package com.yigit.isiklar.model

import kotlin.math.ln
import kotlin.math.pow

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toInt(): Int = (r shl 16) or (g shl 8) or b

    companion object {
        fun fromInt(v: Int) = Rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }
}

data class LightState(
    val on: Boolean? = null,
    val brightness: Int? = null,
    val color: Rgb? = null,
    val kelvin: Int? = null,
)

data class LightDevice(
    /** MAC benzeri cihaz kimliği, büyük harf (LAN ve bulutu eşleştirmek için) */
    val id: String,
    val sku: String,
    val name: String,
    /** Yerel ağdaki IP (son taramadan) */
    val ip: String? = null,
    /** Bulut API'nin verdiği orijinal cihaz kimliği; null ise bulutta yok */
    val cloudId: String? = null,
    /** Son kontrolde yerel ağdan cevap verdi mi (kaydedilmez) */
    val lanReachable: Boolean = false,
    val state: LightState = LightState(),
)

data class SceneAction(
    val on: Boolean,
    val brightness: Int? = null,
    val color: Rgb? = null,
    val kelvin: Int? = null,
)

data class Scene(
    val id: String,
    val name: String,
    /** Cihaza özel ayarlar (cihaz id -> ayar) */
    val actions: Map<String, SceneAction> = emptyMap(),
    /** Listede olmayan tüm cihazlara uygulanacak ayar */
    val applyToAll: SceneAction? = null,
)

fun defaultScenes(): List<Scene> = listOf(
    Scene("okuma", "Okuma", applyToAll = SceneAction(on = true, brightness = 100, kelvin = 4500)),
    Scene("aksam", "Akşam", applyToAll = SceneAction(on = true, brightness = 35, kelvin = 2700)),
    Scene("film", "Film", applyToAll = SceneAction(on = true, brightness = 15, color = Rgb(40, 60, 255))),
)

/** Renk sıcaklığını yaklaşık RGB'ye çevirir (yalnızca ekranda göstermek için). */
fun kelvinToRgb(kelvin: Int): Rgb {
    val t = kelvin.coerceIn(1000, 40000) / 100.0
    val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
    val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
    val b = when {
        t >= 66 -> 255.0
        t <= 19 -> 0.0
        else -> 138.5177312231 * ln(t - 10) - 305.0447927307
    }
    return Rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
}
