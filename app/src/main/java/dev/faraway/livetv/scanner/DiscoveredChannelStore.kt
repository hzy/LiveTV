package dev.faraway.livetv.scanner

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists [DiscoveredChannel] results into SharedPreferences as JSON. The
 * channel scanner is intentionally additive: results from older scans are
 * preserved unless [clear] is called.
 */
class DiscoveredChannelStore(context: Context) {

    private companion object {
        const val TAG = "DiscoveredChannelStore"
        const val PREFS_NAME = "livetv_scanner"
        const val KEY_DISCOVERED = "discovered_channels"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<DiscoveredChannel> {
        val raw = prefs.getString(KEY_DISCOVERED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<DiscoveredChannel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += DiscoveredChannel(
                    ip = o.getString("ip"),
                    port = o.getInt("port"),
                    sdtName = o.optString("sdtName", ""),
                    sdtProvider = o.optString("sdtProvider", ""),
                    builtinName = o.optString("builtinName").takeIf { it.isNotEmpty() },
                    builtinCategory = o.optString("builtinCategory").takeIf { it.isNotEmpty() },
                )
            }
            out
        } catch (e: Throwable) {
            Log.w(TAG, "failed to parse stored channels", e)
            emptyList()
        }
    }

    fun save(channels: List<DiscoveredChannel>) {
        val arr = JSONArray()
        for (c in channels) {
            arr.put(JSONObject().apply {
                put("ip", c.ip)
                put("port", c.port)
                put("sdtName", c.sdtName)
                put("sdtProvider", c.sdtProvider)
                put("builtinName", c.builtinName ?: "")
                put("builtinCategory", c.builtinCategory ?: "")
            })
        }
        prefs.edit().putString(KEY_DISCOVERED, arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DISCOVERED).apply()
    }
}
