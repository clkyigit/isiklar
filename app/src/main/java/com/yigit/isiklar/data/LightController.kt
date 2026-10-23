package com.yigit.isiklar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yigit.isiklar.govee.CloudClient
import com.yigit.isiklar.govee.LanClient
import com.yigit.isiklar.model.LightDevice
import com.yigit.isiklar.model.LightState
import com.yigit.isiklar.model.Rgb
import com.yigit.isiklar.model.Scene
import com.yigit.isiklar.model.SceneAction
import com.yigit.isiklar.widget.LightsWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID

/**
 * Tüm cihaz kontrolünün tek giriş noktası.
 * Önce yerel ağ (hızlı, internetsiz) denenir; cihaz yerelde cevap vermiyorsa bulut API'ye düşülür.
 */
class LightController private constructor(context: Context) {
    private val app = context.applicationContext
    val store = Store(app)

    private val _devices = MutableStateFlow(store.loadDevices())
    val devices: StateFlow<List<LightDevice>> = _devices.asStateFlow()

    private val _scenes = MutableStateFlow(store.loadScenes())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    @Volatile
    private var lanCheckedAt = 0L

    private sealed interface Cmd {
        data class Power(val on: Boolean) : Cmd
        data class Brightness(val value: Int) : Cmd
        data class Color(val rgb: Rgb) : Cmd
        data class Kelvin(val kelvin: Int) : Cmd
    }

    // ------------------------------------------------------------ keşif

    /** Yerel ağı tarar, bulut listesini çeker, durumları günceller. Gösterilecek uyarıyı döner. */
    suspend fun refresh(): String? {
        val warnings = mutableListOf<String>()
        val local = onLocalNetwork()

        val found = if (local) {
            try {
                LanClient.scan(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnings += "Yerel ağ taraması başarısız: ${e.message}"
                emptyList()
            }
        } else {
            emptyList()
        }

        val key = store.apiKey
        var cloud: List<CloudClient.CloudDevice>? = null
        if (key.isNotBlank()) {
            cloud = try {
                CloudClient.devices(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnings += "Bulut: ${e.message}"
                null
            }
        }

        val old = _devices.value.associateBy { it.id }
        val foundMap = found.associateBy { it.id }
        val cloudMap = cloud?.associateBy { it.id }
        val ids = LinkedHashSet<String>()
        ids.addAll(old.keys)
        found.forEach { ids.add(it.id) }
        cloud?.forEach { ids.add(it.id) }

        val merged = ids.map { id ->
            val prev = old[id]
            val f = foundMap[id]
            val c = cloudMap?.get(id)
            val sku = c?.sku ?: f?.sku?.takeIf { it.isNotBlank() } ?: prev?.sku ?: ""
            LightDevice(
                id = id,
                sku = sku,
                name = c?.name ?: prev?.name ?: "Govee $sku",
                ip = f?.ip ?: prev?.ip,
                // Bulut listesi alınamadıysa eski bilgiyi koru
                cloudId = if (cloudMap != null) c?.rawId else prev?.cloudId,
                lanReachable = f != null,
                state = prev?.state ?: LightState(),
            )
        }

        val lanIps = merged.filter { it.lanReachable }.mapNotNull { it.ip }
        val lanStates = safeStatus(lanIps, 1200)

        val withState = merged.map { d ->
            val s = d.ip?.let { lanStates[it] }
            val cid = d.cloudId
            when {
                s != null -> d.copy(state = s)
                !d.lanReachable && cid != null && key.isNotBlank() -> {
                    val cs = try {
                        CloudClient.state(key, d.sku, cid)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (cs != null) d.copy(state = cs) else d
                }
                else -> d
            }
        }

        _devices.value = withState
        store.saveDevices(withState)
        lanCheckedAt = System.currentTimeMillis()

        if (withState.isEmpty()) {
            warnings += if (key.isBlank()) {
                "Cihaz bulunamadı. Govee Home'da LAN Control'ü aç veya Ayarlar'dan API anahtarı gir."
            } else {
                "Cihaz bulunamadı."
            }
        } else if (!local && key.isBlank()) {
            warnings += "Wi‑Fi'de değilsin; evden uzakta kontrol için Ayarlar'dan API anahtarı gir."
        }
        return warnings.joinToString("\n").ifBlank { null }
    }

    /** Hangi cihazın yerelde erişilebilir olduğunu hızlıca yeniler (widget ve sahneler için). */
    suspend fun ensureFresh(maxAgeMs: Long = 30_000) {
        if (System.currentTimeMillis() - lanCheckedAt < maxAgeMs) return
        if (!onLocalNetwork()) {
            mutate { list -> list.map { it.copy(lanReachable = false) } }
            lanCheckedAt = System.currentTimeMillis()
            return
        }
        val ips = _devices.value.mapNotNull { it.ip }
        val states = safeStatus(ips, 900)
        val missing = _devices.value.any { d -> d.ip?.let { states[it] } == null }
        val found: Map<String, LanClient.Found> = if (missing) {
            try {
                LanClient.scan(app, 1500).associateBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        mutate { list ->
            list.map { d ->
                val s = d.ip?.let { states[it] }
                val f = found[d.id]
                when {
                    s != null -> d.copy(lanReachable = true, state = s)
                    f != null -> d.copy(ip = f.ip, lanReachable = true)
                    else -> d.copy(lanReachable = false)
                }
            }
        }
        lanCheckedAt = System.currentTimeMillis()
    }

    // ------------------------------------------------------------ kontrol

    suspend fun setPower(id: String, on: Boolean) = execute(id, Cmd.Power(on))
    suspend fun setBrightness(id: String, value: Int) = execute(id, Cmd.Brightness(value))
    suspend fun setColor(id: String, rgb: Rgb) = execute(id, Cmd.Color(rgb))
    suspend fun setKelvin(id: String, kelvin: Int) = execute(id, Cmd.Kelvin(kelvin))

    suspend fun allPower(on: Boolean) {
        ensureFresh()
        val list = _devices.value
        if (list.isEmpty()) throw IOException("Henüz cihaz yok — uygulamayı açıp tara")
        val errors = mutableListOf<String>()
        for (d in list) {
            try {
                execute(d.id, Cmd.Power(on))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += e.message ?: d.name
            }
        }
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("\n"))
    }

    suspend fun applyScene(scene: Scene) {
        ensureFresh()
        val errors = mutableListOf<String>()
        for (d in _devices.value) {
            val action = scene.actions[d.id] ?: scene.applyToAll ?: continue
            try {
                applyAction(d.id, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += e.message ?: d.name
            }
        }
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("\n"))
    }

    private suspend fun applyAction(id: String, a: SceneAction) {
        if (!a.on) {
            execute(id, Cmd.Power(false))
            return
        }
        execute(id, Cmd.Power(true))
        delay(150)
        val b = a.brightness
        if (b != null) {
            execute(id, Cmd.Brightness(b))
            delay(150)
        }
        val k = a.kelvin
        val c = a.color
        if (k != null) {
            execute(id, Cmd.Kelvin(k))
        } else if (c != null) {
            execute(id, Cmd.Color(c))
        }
    }

    // ------------------------------------------------------------ sahneler & ayarlar

    fun saveCurrentAsScene(name: String) {
        val actions = _devices.value.associate { d ->
            d.id to SceneAction(
                on = d.state.on ?: false,
                brightness = d.state.brightness,
                color = if (d.state.kelvin == null) d.state.color else null,
                kelvin = d.state.kelvin,
            )
        }
        val scene = Scene(UUID.randomUUID().toString(), name.trim().ifBlank { "Sahne" }, actions)
        setScenes(_scenes.value + scene)
    }

    fun deleteScene(id: String) = setScenes(_scenes.value.filterNot { it.id == id })

    fun setApiKey(key: String) {
        store.apiKey = key
    }

    // ------------------------------------------------------------ iç işler

    private suspend fun execute(id: String, cmd: Cmd) {
        val d = _devices.value.firstOrNull { it.id == id } ?: return
        val before = d.state
        updateState(id) { applyCmd(it, cmd) }
        try {
            send(d, cmd)
        } catch (e: Exception) {
            updateState(id) { before }
            throw e
        }
    }

    private suspend fun send(d: LightDevice, cmd: Cmd) {
        val ip = d.ip
        if (d.lanReachable && ip != null) {
            when (cmd) {
                is Cmd.Power -> LanClient.power(ip, cmd.on)
                is Cmd.Brightness -> LanClient.brightness(ip, cmd.value)
                is Cmd.Color -> LanClient.color(ip, cmd.rgb)
                is Cmd.Kelvin -> LanClient.kelvin(ip, cmd.kelvin)
            }
            return
        }
        val key = store.apiKey
        val cid = d.cloudId
        if (key.isBlank() || cid == null) {
            throw IOException("${d.name}: yerel ağda cevap vermiyor ve bulut yedeği yok")
        }
        when (cmd) {
            is Cmd.Power -> CloudClient.power(key, d.sku, cid, cmd.on)
            is Cmd.Brightness -> CloudClient.brightness(key, d.sku, cid, cmd.value)
            is Cmd.Color -> CloudClient.color(key, d.sku, cid, cmd.rgb)
            is Cmd.Kelvin -> CloudClient.kelvin(key, d.sku, cid, cmd.kelvin)
        }
    }

    private fun applyCmd(s: LightState, cmd: Cmd): LightState = when (cmd) {
        is Cmd.Power -> s.copy(on = cmd.on)
        is Cmd.Brightness -> s.copy(brightness = cmd.value)
        is Cmd.Color -> s.copy(color = cmd.rgb, kelvin = null)
        is Cmd.Kelvin -> s.copy(kelvin = cmd.kelvin, color = null)
    }

    private fun updateState(id: String, f: (LightState) -> LightState) {
        mutate { list -> list.map { if (it.id == id) it.copy(state = f(it.state)) else it } }
    }

    private fun mutate(transform: (List<LightDevice>) -> List<LightDevice>) {
        _devices.update(transform)
        store.saveDevices(_devices.value)
    }

    private fun setScenes(list: List<Scene>) {
        _scenes.value = list
        store.saveScenes(list)
        LightsWidget.updateAll(app)
    }

    private suspend fun safeStatus(ips: List<String>, timeoutMs: Long): Map<String, LightState> {
        if (ips.isEmpty()) return emptyMap()
        return try {
            LanClient.status(app, ips, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun onLocalNetwork(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    companion object {
        @Volatile
        private var instance: LightController? = null

        fun get(context: Context): LightController =
            instance ?: synchronized(this) {
                instance ?: LightController(context).also { instance = it }
            }
    }
}
