package dev.faraway.livetv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.faraway.livetv.scanner.ChannelScanner
import dev.faraway.livetv.scanner.DiscoveredChannel
import dev.faraway.livetv.scanner.DiscoveredChannelStore
import dev.faraway.livetv.scanner.ScanPresets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel managing TV app state: current/target channel, UI visibility,
 * channel scanner. The unified [channels] list combines the bundled
 * [ChannelList] with channels discovered by [ChannelScanner]; discovered
 * channels are persisted via [DiscoveredChannelStore] and are assigned ids
 * starting at [DISCOVERED_ID_BASE] to avoid collisions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val PREFS_NAME = "livetv_prefs"
        const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        const val KEY_LAST_CATEGORY_INDEX = "last_category_index"
        const val DISCOVERED_ID_BASE = 100_000
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val discoveredStore = DiscoveredChannelStore(application)

    val categories = ChannelList.categories

    private val builtinChannels: List<Channel> = ChannelList.channels

    private val discoveredChannels = mutableStateListOf<DiscoveredChannel>().apply {
        addAll(discoveredStore.load())
    }

    // Cached union of builtin + discovered, rebuilt only when the discovered
    // list mutates. Backed by a State so Compose recomposes appropriately.
    private var combinedChannels by mutableStateOf<List<Channel>>(emptyList())

    val channels: List<Channel> get() = combinedChannels

    init {
        rebuildCombined()
    }

    private fun rebuildCombined() {
        combinedChannels = builtinChannels + discoveredChannels.mapIndexed { i, d ->
            d.toChannel(
                id = DISCOVERED_ID_BASE + i,
                number = "S%02d".format(i + 1),
            )
        }
    }

    private val initialChannelIndex: Int = run {
        val savedId = prefs.getInt(KEY_LAST_CHANNEL_ID, -1)
        channels.indexOfFirst { it.id == savedId }.takeIf { it >= 0 } ?: 0
    }

    var currentChannelIndex by mutableIntStateOf(initialChannelIndex)
        private set

    var targetChannelIndex by mutableIntStateOf(initialChannelIndex)
        private set

    var isChannelInfoVisible by mutableStateOf(false)
        private set

    var isChannelListOpen by mutableStateOf(false)
        private set

    var listFocusIndex by mutableIntStateOf(0)
        private set

    var activeCategoryIndex by mutableIntStateOf(
        prefs.getInt(KEY_LAST_CATEGORY_INDEX, 0).coerceIn(0, categories.size - 1)
    )
        private set

    // ============ Scanner state ============

    var isScannerOpen by mutableStateOf(false)
        private set

    var scanInProgress by mutableStateOf(false)
        private set

    var scannerPresetIndex by mutableIntStateOf(0)
        private set

    var scanChecked by mutableIntStateOf(0)
        private set

    var scanTotal by mutableIntStateOf(0)
        private set

    var scanCurrentTarget by mutableStateOf("")
        private set

    private val _scanFound = mutableStateListOf<DiscoveredChannel>()
    val scanFound: List<DiscoveredChannel> get() = _scanFound

    var scanError by mutableStateOf<String?>(null)
        private set

    private var scanJob: Job? = null

    private var hideJob: Job? = null
    private val autoHideDelayMs = 4000L

    val currentChannel: Channel
        get() = channels[currentChannelIndex.coerceIn(0, channels.lastIndex)]

    val targetChannel: Channel
        get() = channels[targetChannelIndex.coerceIn(0, channels.lastIndex)]

    val filteredChannels: List<Channel>
        get() {
            if (activeCategoryIndex == 0) return channels
            val cat = categories[activeCategoryIndex]
            return channels.filter { it.category == cat }
        }

    fun channelDown() {
        targetChannelIndex = (targetChannelIndex + 1) % channels.size
        showChannelInfo()
    }

    fun channelUp() {
        targetChannelIndex = (targetChannelIndex - 1 + channels.size) % channels.size
        showChannelInfo()
    }

    fun confirmSwitch(): String? {
        if (isChannelInfoVisible && targetChannelIndex != currentChannelIndex) {
            currentChannelIndex = targetChannelIndex
            persistCurrentChannel()
            hideJob?.cancel()
            hideJob = viewModelScope.launch {
                delay(800)
                isChannelInfoVisible = false
            }
            return currentChannel.url
        }
        return null
    }

    fun cancelSwitch() {
        targetChannelIndex = currentChannelIndex
        hideChannelInfo()
    }

    fun showCurrentChannelInfo() {
        if (!isChannelInfoVisible) showChannelInfo()
    }

    private fun showChannelInfo() {
        isChannelInfoVisible = true
        restartHideTimer()
    }

    private fun hideChannelInfo() {
        isChannelInfoVisible = false
        hideJob?.cancel()
    }

    private fun restartHideTimer() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(autoHideDelayMs)
            isChannelInfoVisible = false
            targetChannelIndex = currentChannelIndex
        }
    }

    // ============ Channel List Panel ============

    fun toggleChannelList() {
        isChannelListOpen = !isChannelListOpen
        if (isChannelListOpen) {
            val filtered = filteredChannels
            listFocusIndex = filtered.indexOfFirst { it.id == currentChannel.id }
                .coerceAtLeast(0)
            hideChannelInfo()
        }
    }

    fun closeChannelList() {
        isChannelListOpen = false
    }

    fun listUp() {
        listFocusIndex = (listFocusIndex - 1).coerceAtLeast(0)
    }

    fun listDown() {
        listFocusIndex = (listFocusIndex + 1).coerceAtMost(filteredChannels.size - 1)
    }

    fun switchCategory(direction: Int) {
        activeCategoryIndex = (activeCategoryIndex + direction + categories.size) % categories.size
        listFocusIndex = 0
        prefs.edit().putInt(KEY_LAST_CATEGORY_INDEX, activeCategoryIndex).apply()
    }

    fun selectFromList(): String {
        val selected = filteredChannels[listFocusIndex]
        val realIndex = channels.indexOfFirst { it.id == selected.id }
        currentChannelIndex = realIndex
        targetChannelIndex = realIndex
        isChannelListOpen = false
        persistCurrentChannel()
        return selected.url
    }

    private fun persistCurrentChannel() {
        prefs.edit().putInt(KEY_LAST_CHANNEL_ID, currentChannel.id).apply()
    }

    // ============ Scanner ============

    fun openScanner() {
        // Closing the channel list first keeps the UI predictable.
        isChannelListOpen = false
        hideChannelInfo()
        isScannerOpen = true
    }

    fun closeScanner() {
        if (scanInProgress) cancelScan()
        isScannerOpen = false
    }

    fun startScan(preset: ScanPresets.Preset) {
        if (scanInProgress) return
        scanError = null
        _scanFound.clear()
        scanChecked = 0
        scanTotal = 0
        scanCurrentTarget = ""
        scanInProgress = true

        val scanner = ChannelScanner(ports = preset.ports())
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { event ->
                    when (event) {
                        is ChannelScanner.ScanEvent.Progress -> {
                            scanChecked = event.checked
                            scanTotal = event.total
                            scanCurrentTarget = event.current
                        }
                        is ChannelScanner.ScanEvent.Found -> {
                            _scanFound += event.channel
                        }
                        is ChannelScanner.ScanEvent.Error -> {
                            scanError = event.message
                        }
                        is ChannelScanner.ScanEvent.Done -> {
                            commitScanResults(event.channels)
                        }
                    }
                }
            } finally {
                scanInProgress = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        scanInProgress = false
    }

    /** Cycles the highlighted preset in the scanner UI. No-op while scanning. */
    fun scannerCyclePreset(direction: Int) {
        if (scanInProgress) return
        val n = ScanPresets.Preset.values().size
        scannerPresetIndex = (scannerPresetIndex + direction + n) % n
    }

    /** OK key from the scanner UI: starts a scan or cancels the running one. */
    fun scannerActivate() {
        if (scanInProgress) {
            cancelScan()
        } else {
            startScan(ScanPresets.Preset.values()[scannerPresetIndex])
        }
    }

    fun clearDiscovered() {
        if (scanInProgress) return
        discoveredChannels.clear()
        discoveredStore.clear()
        rebuildCombined()
        rebindIndicesAfterMutation()
    }

    /**
     * Merges [results] into the persistent discovered list, deduping by url.
     * Existing entries keep their position so user-visible numbers (`S01`,
     * `S02`, ...) don't shuffle on every scan.
     */
    private fun commitScanResults(results: List<DiscoveredChannel>) {
        val byUrl = discoveredChannels.associateBy { it.url }.toMutableMap()
        for (r in results) byUrl[r.url] = r
        val merged = byUrl.values
            .sortedWith(compareBy({ ipToKey(it.ip) }, { it.port }))
        discoveredChannels.clear()
        discoveredChannels.addAll(merged)
        discoveredStore.save(merged)
        rebuildCombined()
        rebindIndicesAfterMutation()
    }

    private fun rebindIndicesAfterMutation() {
        // After mutating the channel list, current/target indices may be
        // pointing at stale slots. Re-resolve against the saved channel id.
        val savedId = prefs.getInt(KEY_LAST_CHANNEL_ID, -1)
        val newIdx = channels.indexOfFirst { it.id == savedId }.takeIf { it >= 0 } ?: 0
        currentChannelIndex = newIdx
        targetChannelIndex = newIdx
    }

    private fun ipToKey(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        var v = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return 0L
            v = (v shl 8) or n.toLong()
        }
        return v
    }
}
