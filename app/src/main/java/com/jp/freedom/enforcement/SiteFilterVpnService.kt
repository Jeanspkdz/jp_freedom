package com.jp.freedom.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.ServiceInfo
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.AccessProfile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import kotlin.concurrent.thread

class SiteFilterVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val repository by lazy { FocusRepository(applicationContext) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (VpnService.prepare(this) != null) {
            stopSelf(startId)
            return Service.START_NOT_STICKY
        }
        if (tunnel != null) return Service.START_STICKY

        return try {
            val upstreamDns = currentUpstreamDns(this)
            val builder = Builder()
                .setSession("JP Freedom")
                .setMtu(1500)
                .addAddress(DNS_TUNNEL_ADDRESS, 32)
                .addDnsServer(DNS_SERVER_ADDRESS)
                .addRoute(DNS_SERVER_ADDRESS, 32)

            val excludedPackages = FocusAccessibilityService.essentialPackages(this) + setOf(
                packageName,
                CHATGPT_PACKAGE,
                GOOGLE_SEARCH_PACKAGE,
            )
            excludedPackages.forEach { app ->
                runCatching { builder.addDisallowedApplication(app) }
            }

            tunnel = builder.establish() ?: error("Android no creó la interfaz del filtro web.")
            startAsForeground()
            setRunning(this, true)
            PolicyAlarmScheduler.broadcastRecheck(this)
            worker = thread(name = "jp-freedom-dns") { processTunnel(upstreamDns) }
            Service.START_STICKY
        } catch (_: Exception) {
            setRunning(this, false)
            stopSelf(startId)
            Service.START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        setRunning(this, false)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        setRunning(this, false)
        runCatching { tunnel?.close() }
        tunnel = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Filtro web de JP Freedom", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Filtro web activo")
            .setContentText("Los dominios se filtran localmente durante los bloques.")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun processTunnel(upstreamDns: InetAddress) {
        val descriptor = tunnel ?: return
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteArray(32 * 1024)
        while (tunnel != null && !Thread.currentThread().isInterrupted) {
            val length = runCatching { input.read(packet) }.getOrElse { break }
            if (length <= 0) continue
            val dnsQuery = extractDnsQuery(packet, length) ?: continue
            val response = if (isAllowedDomain(dnsQuery.domain)) {
                forwardDns(dnsQuery.payload, upstreamDns)
            } else {
                blockedDnsResponse(dnsQuery.payload)
            } ?: continue
            val responsePacket = buildDnsResponsePacket(packet, length, response) ?: continue
            runCatching { output.write(responsePacket) }
        }
    }

    private fun isAllowedDomain(domain: String): Boolean {
        val state = FocusPolicy.evaluate(LocalDateTime.now(), repository.allBlocks(), repository.settings())
        return SiteDomainPolicy.isAllowed(domain, state)
    }

    private fun forwardDns(query: ByteArray, upstreamDns: InetAddress): ByteArray? {
        val socket = DatagramSocket()
        return try {
            if (!protect(socket)) return null
            socket.soTimeout = DNS_TIMEOUT_MS
            socket.send(DatagramPacket(query, query.size, upstreamDns, DNS_PORT))
            val buffer = ByteArray(MAX_DNS_PACKET_BYTES)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
    }

    private data class DnsQuery(val domain: String, val payload: ByteArray)

    companion object {
        private const val PREFERENCES_NAME = "site-filter"
        private const val KEY_RUNNING = "running"
        private const val DNS_TUNNEL_ADDRESS = "10.77.0.1"
        private const val DNS_SERVER_ADDRESS = "10.77.0.2"
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 2_000
        private const val MAX_DNS_PACKET_BYTES = 4_096
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "site-filter"
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"

        fun isRunning(context: Context): Boolean = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, false)

        fun startIfAuthorized(context: Context) {
            if (VpnService.prepare(context) != null || isRunning(context)) return
            val intent = Intent(context, SiteFilterVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun requestPermission(context: Context) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent == null) {
                startIfAuthorized(context)
            } else {
                context.startActivity(prepareIntent)
            }
        }

        private fun extractDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
            if (length < IPV4_HEADER_BYTES + UDP_HEADER_BYTES + DNS_HEADER_BYTES) return null
            val first = packet[0].toInt() and 0xff
            if (first ushr 4 != 4 || packet[9].toInt() and 0xff != UDP_PROTOCOL) return null
            val ipHeaderLength = (first and 0x0f) * 4
            if (ipHeaderLength < IPV4_HEADER_BYTES || length < ipHeaderLength + UDP_HEADER_BYTES) return null
            val destinationPort = readUnsignedShort(packet, ipHeaderLength + 2)
            if (destinationPort != DNS_PORT) return null
            val udpLength = readUnsignedShort(packet, ipHeaderLength + 4)
            val dnsOffset = ipHeaderLength + UDP_HEADER_BYTES
            if (udpLength < UDP_HEADER_BYTES + DNS_HEADER_BYTES || dnsOffset + udpLength - UDP_HEADER_BYTES > length) return null
            val dns = packet.copyOfRange(dnsOffset, dnsOffset + udpLength - UDP_HEADER_BYTES)
            if (dns.size < DNS_HEADER_BYTES || readUnsignedShort(dns, 4) != 1) return null

            val labels = mutableListOf<String>()
            var cursor = DNS_HEADER_BYTES
            while (cursor < dns.size) {
                val labelLength = dns[cursor++].toInt() and 0xff
                if (labelLength == 0) break
                if (labelLength > 63 || cursor + labelLength > dns.size) return null
                labels += dns.copyOfRange(cursor, cursor + labelLength).toString(Charsets.US_ASCII)
                cursor += labelLength
            }
            if (labels.isEmpty() || cursor + 4 > dns.size) return null
            return DnsQuery(labels.joinToString(".").lowercase(), dns)
        }

        private fun blockedDnsResponse(query: ByteArray): ByteArray = query.copyOf().apply {
            val requestFlags = readUnsignedShort(query, 2)
            writeUnsignedShort(this, 2, (requestFlags and 0x0100) or 0x8083)
            writeUnsignedShort(this, 6, 0)
            writeUnsignedShort(this, 8, 0)
            writeUnsignedShort(this, 10, 0)
        }

        private fun buildDnsResponsePacket(original: ByteArray, originalLength: Int, dns: ByteArray): ByteArray? {
            val first = original[0].toInt() and 0xff
            val headerLength = (first and 0x0f) * 4
            if (headerLength < IPV4_HEADER_BYTES || originalLength < headerLength + UDP_HEADER_BYTES) return null
            val originalUdpLength = readUnsignedShort(original, headerLength + 4)
            if (headerLength + originalUdpLength > originalLength) return null
            val udpLength = UDP_HEADER_BYTES + dns.size
            val totalLength = IPV4_HEADER_BYTES + udpLength
            if (totalLength > 65_535) return null
            val response = ByteArray(totalLength)

            response[0] = 0x45
            writeUnsignedShort(response, 2, totalLength)
            response[8] = 64
            response[9] = UDP_PROTOCOL.toByte()
            copyAddress(original, 16, response, 12)
            copyAddress(original, 12, response, 16)
            writeUnsignedShort(response, 10, checksum(response, 0, IPV4_HEADER_BYTES))

            val responseUdpOffset = IPV4_HEADER_BYTES
            writeUnsignedShort(response, responseUdpOffset, DNS_PORT)
            writeUnsignedShort(response, responseUdpOffset + 2, readUnsignedShort(original, headerLength))
            writeUnsignedShort(response, responseUdpOffset + 4, udpLength)
            dns.copyInto(response, responseUdpOffset + UDP_HEADER_BYTES)
            return response
        }

        private fun copyAddress(source: ByteArray, sourceOffset: Int, target: ByteArray, targetOffset: Int) {
            source.copyInto(target, targetOffset, sourceOffset, sourceOffset + 4)
        }

        private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var cursor = offset
            val end = offset + length
            while (cursor + 1 < end) {
                sum += readUnsignedShort(bytes, cursor).toLong()
                cursor += 2
            }
            if (cursor < end) sum += (bytes[cursor].toInt() and 0xff).toLong() shl 8
            while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
            return sum.inv().toInt() and 0xffff
        }

        private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

        private fun writeUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }

        private fun currentUpstreamDns(context: Context): InetAddress {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val dns = connectivity.activeNetwork?.let(connectivity::getLinkProperties)?.dnsServers
                ?.firstOrNull { it is Inet4Address }
            return dns ?: InetAddress.getByName(FALLBACK_DNS)
        }

        private const val IPV4_HEADER_BYTES = 20
        private const val UDP_HEADER_BYTES = 8
        private const val DNS_HEADER_BYTES = 12
        private const val UDP_PROTOCOL = 17
    }
}
