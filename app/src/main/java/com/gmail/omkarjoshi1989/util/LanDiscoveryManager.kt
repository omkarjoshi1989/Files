package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gmail.omkarjoshi1989.model.LanHostCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

object LanDiscoveryManager {

    suspend fun discoverNearbyHosts(context: Context): List<LanHostCandidate> = withContext(Dispatchers.IO) {
        if (!isOnWifi(context)) return@withContext emptyList()

        val local = getPrimaryIpv4Address() ?: return@withContext emptyList()
        val base = local.substringBeforeLast('.', "")
        if (base.isBlank()) return@withContext emptyList()

        coroutineScope {
            (1..254).map { last ->
                async {
                    val ip = "$base.$last"
                    if (ip == local) return@async null
                    if (!isSmbPortOpen(ip, 445, 140)) return@async null
                    val host = runCatching {
                        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } ?: ip
                    }.getOrDefault(ip)
                    LanHostCandidate(ipAddress = ip, hostName = host)
                }
            }.awaitAll().filterNotNull().sortedBy { it.ipAddress }
        }
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getPrimaryIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress ?: "" }
                .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
        }.getOrNull()
    }

    private fun isSmbPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

