package dev.faraway.livetv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel managing TV app state: current/target channel, UI visibility, etc.
 */
class MainViewModel : ViewModel() {

    // Channel data
    val channels = ChannelList.channels
    val categories = ChannelList.categories

    // Current playing channel index
    var currentChannelIndex by mutableIntStateOf(0)
        private set

    // Target channel index (while browsing with up/down)
    var targetChannelIndex by mutableIntStateOf(0)
        private set

    // Whether the channel-switch overlay is shown
    var isChannelInfoVisible by mutableStateOf(false)
        private set

    // Whether the channel list panel is open
    var isChannelListOpen by mutableStateOf(false)
        private set

    // Focused index in channel list
    var listFocusIndex by mutableIntStateOf(0)
        private set

    // Active category filter index
    var activeCategoryIndex by mutableIntStateOf(0)
        private set

    // Auto-hide timer job
    private var hideJob: Job? = null

    // Timeout duration for auto-hide (ms)
    private val autoHideDelayMs = 4000L

    // Current channel
    val currentChannel: Channel
        get() = channels[currentChannelIndex]

    // Target channel
    val targetChannel: Channel
        get() = channels[targetChannelIndex]

    // Filtered channels based on active category
    val filteredChannels: List<Channel>
        get() {
            if (activeCategoryIndex == 0) return channels
            val cat = categories[activeCategoryIndex]
            return channels.filter { it.category == cat }
        }

    /**
     * Navigate to next channel (Down key).
     */
    fun channelDown() {
        targetChannelIndex = (targetChannelIndex + 1) % channels.size
        showChannelInfo()
    }

    /**
     * Navigate to previous channel (Up key).
     */
    fun channelUp() {
        targetChannelIndex = (targetChannelIndex - 1 + channels.size) % channels.size
        showChannelInfo()
    }

    /**
     * Confirm channel switch (OK/Enter key).
     * Returns the new channel URL if actually switching, null otherwise.
     */
    fun confirmSwitch(): String? {
        if (isChannelInfoVisible && targetChannelIndex != currentChannelIndex) {
            currentChannelIndex = targetChannelIndex
            // Delay hiding so user can see the switch confirmed
            hideJob?.cancel()
            hideJob = viewModelScope.launch {
                delay(800)
                isChannelInfoVisible = false
            }
            return currentChannel.url
        }
        return null
    }

    /**
     * Cancel channel browsing (Back key).
     */
    fun cancelSwitch() {
        targetChannelIndex = currentChannelIndex
        hideChannelInfo()
    }

    /**
     * Show current channel info (when pressing OK with no pending switch).
     */
    fun showCurrentChannelInfo() {
        if (!isChannelInfoVisible) {
            showChannelInfo()
        }
    }

    /**
     * Show channel info overlay and start auto-hide timer.
     */
    private fun showChannelInfo() {
        isChannelInfoVisible = true
        restartHideTimer()
    }

    /**
     * Hide channel info overlay.
     */
    private fun hideChannelInfo() {
        isChannelInfoVisible = false
        hideJob?.cancel()
    }

    /**
     * Restart the auto-hide timer.
     */
    private fun restartHideTimer() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(autoHideDelayMs)
            isChannelInfoVisible = false
            targetChannelIndex = currentChannelIndex
        }
    }

    // ============ Channel List Panel ============

    /**
     * Toggle the channel list panel.
     */
    fun toggleChannelList() {
        isChannelListOpen = !isChannelListOpen
        if (isChannelListOpen) {
            // Focus current channel in list
            val filtered = filteredChannels
            listFocusIndex = filtered.indexOfFirst { it.id == currentChannel.id }
                .coerceAtLeast(0)
            // Hide channel info when list opens
            hideChannelInfo()
        }
    }

    /**
     * Close the channel list panel.
     */
    fun closeChannelList() {
        isChannelListOpen = false
    }

    /**
     * Move focus up in channel list.
     */
    fun listUp() {
        listFocusIndex = (listFocusIndex - 1).coerceAtLeast(0)
    }

    /**
     * Move focus down in channel list.
     */
    fun listDown() {
        listFocusIndex = (listFocusIndex + 1).coerceAtMost(filteredChannels.size - 1)
    }

    /**
     * Switch category (Left/Right in list mode).
     */
    fun switchCategory(direction: Int) {
        activeCategoryIndex = (activeCategoryIndex + direction + categories.size) % categories.size
        listFocusIndex = 0
    }

    /**
     * Select channel from list.
     * Returns the channel URL to play.
     */
    fun selectFromList(): String {
        val selected = filteredChannels[listFocusIndex]
        val realIndex = channels.indexOfFirst { it.id == selected.id }
        currentChannelIndex = realIndex
        targetChannelIndex = realIndex
        isChannelListOpen = false
        return selected.url
    }
}
