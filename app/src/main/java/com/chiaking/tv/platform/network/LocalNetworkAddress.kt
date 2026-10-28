package com.chiaking.tv.platform.network

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Prefer the active network so Ethernet and Wi-Fi TVs advertise the address phones can reach. */
internal fun localNetworkAddress(context: Context): InetAddress? {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val active = manager.activeNetwork?.let { manager.getLinkProperties(it) }
        ?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    return active ?: NetworkInterface.getNetworkInterfaces()?.toList()
        ?.filter { it.isUp && !it.isLoopback }
        ?.flatMap { it.inetAddresses.toList() }
        ?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
}
