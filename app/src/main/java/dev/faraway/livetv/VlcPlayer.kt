package dev.faraway.livetv

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * VLC player wrapper that handles RTP multicast playback with HDR support.
 * Configured for 4K HDR passthrough on Android 14+ TV.
 */
class VlcPlayer(context: Context) {

    companion object {
        private const val TAG = "VlcPlayer"
    }

    private val libVLC: LibVLC
    private val mediaPlayer: MediaPlayer
    private var surfaceAttached = false

    init {
        // VLC options optimized for TV + 4K HDR + RTP multicast
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",

            // Network / RTP tuning
            "--network-caching=2000",
            "--live-caching=2000",
            "--clock-jitter=0",
            "--clock-synchro=0",

            // Hardware decoding for 4K HDR
            "--codec=mediacodec_ndk,mediacodec_jni,none",
            "--mediacodec-dr",

            // Audio
            "--aout=audiotrack",

            // Deinterlace off for progressive 4K
            "--deinterlace=0",

            // Performance
            "--avcodec-fast",
            "--avcodec-threads=0",
        )

        libVLC = LibVLC(context.applicationContext, options)
        mediaPlayer = MediaPlayer(libVLC)

        Log.d(TAG, "VLC initialized successfully")
    }

    /**
     * Attach VLC output to a SurfaceView. Must be called when surface is valid.
     */
    fun attachSurface(surfaceView: SurfaceView) {
        try {
            val vout = mediaPlayer.vlcVout
            if (!vout.areViewsAttached()) {
                vout.setVideoSurface(surfaceView.holder.surface, surfaceView.holder)
                val w = surfaceView.width
                val h = surfaceView.height
                if (w > 0 && h > 0) {
                    vout.setWindowSize(w, h)
                }
                vout.attachViews()
                surfaceAttached = true
                Log.d(TAG, "Surface attached: ${w}x${h}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface", e)
        }
    }

    /**
     * Detach from the surface.
     */
    fun detachSurface() {
        try {
            if (surfaceAttached) {
                mediaPlayer.vlcVout.detachViews()
                surfaceAttached = false
                Log.d(TAG, "Surface detached")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detach surface", e)
        }
    }

    /**
     * Play a channel by its RTP multicast URL.
     * e.g. "rtp://239.3.1.118:8001"
     */
    fun play(url: String) {
        try {
            Log.d(TAG, "Playing: $url")

            // Stop current playback first
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }

            val media = Media(libVLC, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=2000")
            media.addOption(":live-caching=2000")

            mediaPlayer.media = media
            mediaPlayer.play()

            media.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: $url", e)
        }
    }

    /**
     * Stop playback.
     */
    fun stop() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop", e)
        }
    }

    /**
     * Update video surface size (call after layout changes).
     */
    fun updateVideoSize(width: Int, height: Int) {
        try {
            if (width > 0 && height > 0 && surfaceAttached) {
                mediaPlayer.vlcVout.setWindowSize(width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update size", e)
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        try {
            mediaPlayer.stop()
            detachSurface()
            mediaPlayer.release()
            libVLC.release()
            Log.d(TAG, "VLC released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release", e)
        }
    }
}
