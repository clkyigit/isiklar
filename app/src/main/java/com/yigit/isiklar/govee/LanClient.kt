package com.yigit.isiklar.govee

import android.content.Context
import android.net.wifi.WifiManager
import com.yigit.isiklar.model.LightState
import com.yigit.isiklar.model.Rgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Govee yerel ağ (LAN) API'si — UDP üzerinden JSON.
 *  - Tarama: 239.255.255.250:4001'e multicast, cevaplar 4002'ye gelir
 *  - Komut: cihaz IP'sine 4003
 * Her cihazda Govee Home > Ayarlar > "LAN Control" açık olmalı.
 */
object LanClient {
    private const val MULTICAST_ADDR = "239.255.255.250"
    private const val SCAN_PORT = 4001
    private const val LISTEN_PORT = 4002
    private const val COMMAND_PORT = 4003

    private val listenMutex = Mutex()

    data class Found(val id: String, val sku: String, val ip: String)

    suspend fun scan(context: Context, timeoutMs: Long = 2500): List<Found> {
        val result = linkedMapOf<String, Found>()
        withListener(context) { socket ->
            val payload = msg("scan", JSONObject().put("account_topic", "reserve"))
            val group = InetAddress.getByName(MULTICAST_ADDR)
            repeat(2) { sendOn(socket, payload, group, SCAN_PORT) }
            receiveFor(socket, timeoutMs) { json, from ->
                val m = json.optJSONObject("msg")
                if (m != null && m.optString("cmd") == "scan") {
                    val d = m.optJSONObject("data")
                    if (d != null) {
                        val id = d.optString("device").uppercase()
                        if (id.isNotEmpty()) {
                            val ip = d.optString("ip").ifEmpty { from }
                            result[id] = Found(id, d.optString("sku"), ip)
                        }
                    }
                }
                false
            }
        }
        return result.values.toList()
    }

    /** Verilen IP'lerin durumunu sorar. Dönen harita: ip -> durum (cevap vermeyenler yok). */
    suspend fun status(context: Context, ips: Collection<String>, timeoutMs: Long = 1200): Map<String, LightState> {
        if (ips.isEmpty()) return emptyMap()
        val wanted = ips.toSet()
        val result = mutableMapOf<String, LightState>()
        withListener(context) { socket ->
            val payload = msg("devStatus", JSONObject())
            wanted.forEach { sendOn(socket, payload, InetAddress.getByName(it), COMMAND_PORT) }
            receiveFor(socket, timeoutMs) { json, from ->
                val m = json.optJSONObject("msg")
                if (m != null && m.optString("cmd") == "devStatus") {
                    val d = m.optJSONObject("data")
                    if (d != null) result[from] = parseStatus(d)
                }
                result.keys.containsAll(wanted)
            }
        }
        return result
    }

    suspend fun power(ip: String, on: Boolean) =
        send(ip, msg("turn", JSONObject().put("value", if (on) 1 else 0)))

    suspend fun brightness(ip: String, value: Int) =
        send(ip, msg("brightness", JSONObject().put("value", value.coerceIn(1, 100))))

    suspend fun color(ip: String, rgb: Rgb) =
        send(ip, msg("colorwc", JSONObject().put("color", rgbJson(rgb)).put("colorTemInKelvin", 0)))

    suspend fun kelvin(ip: String, kelvin: Int) =
        send(
            ip,
            msg(
                "colorwc",
                JSONObject().put("color", rgbJson(Rgb(255, 255, 255)))
                    .put("colorTemInKelvin", kelvin.coerceIn(2000, 9000)),
            ),
        )

    // ---------------------------------------------------------------------

    private fun parseStatus(d: JSONObject): LightState {
        val c = d.optJSONObject("color")
        val k = d.optInt("colorTemInKelvin", 0)
        return LightState(
            on = d.optInt("onOff", 0) == 1,
            brightness = d.optInt("brightness", 0).coerceIn(0, 100),
            color = if (k == 0 && c != null) Rgb(c.optInt("r"), c.optInt("g"), c.optInt("b")) else null,
            kelvin = if (k > 0) k else null,
        )
    }

    private suspend fun send(ip: String, payload: String) {
        withContext(Dispatchers.IO) {
            DatagramSocket().use { s -> sendOn(s, payload, InetAddress.getByName(ip), COMMAND_PORT) }
        }
    }

    /** 4002 portunu dinleyen soketi açar; aynı anda yalnızca bir tarama/sorgu çalışır. */
    private suspend fun withListener(context: Context, block: (DatagramSocket) -> Unit) {
        listenMutex.withLock {
            withContext(Dispatchers.IO) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val lock = wifi.createMulticastLock("isiklar")
                lock.setReferenceCounted(false)
                lock.acquire()
                try {
                    DatagramSocket(null).use { socket ->
                        socket.reuseAddress = true
                        socket.broadcast = true
                        socket.bind(InetSocketAddress(LISTEN_PORT))
                        block(socket)
                    }
                } finally {
                    if (lock.isHeld) lock.release()
                }
            }
        }
    }

    /** Süre dolana veya onMessage true dönene kadar paket okur. */
    private fun receiveFor(socket: DatagramSocket, timeoutMs: Long, onMessage: (JSONObject, String) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(4096)
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            socket.soTimeout = remaining.toInt().coerceAtLeast(1)
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                break
            }
            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
            val from = packet.address?.hostAddress ?: continue
            if (onMessage(json, from)) break
        }
    }

    private fun msg(cmd: String, data: JSONObject): String =
        JSONObject().put("msg", JSONObject().put("cmd", cmd).put("data", data)).toString()

    private fun rgbJson(c: Rgb) = JSONObject().put("r", c.r).put("g", c.g).put("b", c.b)

    private fun sendOn(socket: DatagramSocket, payload: String, addr: InetAddress, port: Int) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(bytes, bytes.size, addr, port))
    }
}
