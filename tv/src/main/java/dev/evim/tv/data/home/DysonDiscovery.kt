package dev.evim.tv.data.home

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Dyson cihazları ev ağında kendilerini mDNS ile "_dyson_mqtt._tcp" olarak duyurur; servis adında seri no geçer.
 * Bu duyurular dinlenip çözülerek cihazların IP'si bulunur. Bekleyen çağrı: arka plan iş parçacığından.
 */
class DysonDiscovery(private val context: Context) {

    /** Seri no → IP. [serials] verilirse hepsi bulununca erken döner. */
    fun scan(serials: Set<String> = emptySet(), durationMs: Long = 6_000): Map<String, String> {
        val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = ConcurrentHashMap<String, String>()
        val pending = java.util.concurrent.LinkedBlockingQueue<NsdServiceInfo>()
        val done = CountDownLatch(1)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { pending.add(info) }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { done.countDown() }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            val end = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < end && done.count > 0) {
                val info = pending.poll(200, TimeUnit.MILLISECONDS) ?: continue
                // Eski Android sürümleri aynı anda tek çözümlemeye izin veriyor; sırayla yapılır
                resolve(nsd, info)?.let { (name, ip) ->
                    val serial = SERIAL.find(name.uppercase())?.value ?: return@let
                    found[serial] = ip
                }
                if (serials.isNotEmpty() && found.keys.containsAll(serials)) break
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return found
    }

    @Suppress("DEPRECATION")
    private fun resolve(nsd: NsdManager, info: NsdServiceInfo): Pair<String, String>? {
        var result: Pair<String, String>? = null
        val latch = CountDownLatch(1)
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                resolved.host?.hostAddress?.let { result = resolved.serviceName to it }
                latch.countDown()
            }

            override fun onResolveFailed(failed: NsdServiceInfo, code: Int) { latch.countDown() }
        })
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    private companion object {
        const val SERVICE = "_dyson_mqtt._tcp."
        val SERIAL = Regex("[0-9A-Z]{3}-[A-Z]{2}-[0-9A-Z]{8,}")
    }
}
