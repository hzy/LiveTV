package dev.faraway.livetv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory

/**
 * ExoPlayer-backed live TV player. Targets the HWC video overlay path on
 * Android TVs so 1080p streams are upscaled by the dedicated video scaler
 * instead of the framebuffer scaler. Uses [RtpDataSource] for `rtp://` and
 * [UdpDataSource] for `udp://` multicast.
 */
class ExoPlayerImpl(context: Context) {

    companion object {
        private const val TAG = "ExoPlayerImpl"
    }

    private val main = Handler(Looper.getMainLooper())
    private val player: ExoPlayer

    init {
        // Tighter live buffer to keep latency low on RTP IPTV.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 8000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1500,
            )
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "playback error", error)
                    }
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Log.d(TAG, "video size: ${videoSize.width}x${videoSize.height}")
                    }
                })
            }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        runOnMain { player.setVideoSurfaceView(surfaceView) }
    }

    fun detachSurface() {
        runOnMain { player.clearVideoSurface() }
    }

    fun play(url: String) {
        runOnMain {
            try {
                Log.d(TAG, "play $url")
                val item = MediaItem.fromUri(Uri.parse(url))
                val source = if (url.startsWith("rtp://", ignoreCase = true) ||
                    url.startsWith("udp://", ignoreCase = true)
                ) {
                    val factory = if (url.startsWith("rtp://", ignoreCase = true)) {
                        RtpDataSource.Factory()
                    } else {
                        UdpDataSource.Factory()
                    }
                    val extractors = DefaultExtractorsFactory()
                    ProgressiveMediaSource.Factory(factory, extractors).createMediaSource(item)
                } else {
                    null
                }
                if (source != null) {
                    player.setMediaSource(source)
                } else {
                    player.setMediaItem(item)
                }
                player.prepare()
                player.play()
            } catch (e: Exception) {
                Log.e(TAG, "failed to play $url", e)
            }
        }
    }

    fun stop() {
        runOnMain { player.stop() }
    }

    fun release() {
        runOnMain { player.release() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
