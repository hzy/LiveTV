package dev.faraway.livetv

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

class LiveTVApplication : Application() {

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("LiveTV-multicast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d("LiveTVApplication", "Multicast lock acquired")
        } catch (e: Exception) {
            Log.w("LiveTVApplication", "Failed to acquire multicast lock", e)
        }
    }
}
