package dev.faraway.livetv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import dev.faraway.livetv.ui.TVOverlay

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private var player: ExoPlayerImpl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set immersive mode", e)
        }

        setContent {
            LiveTVApp(viewModel = viewModel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.isChannelListOpen) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.listUp(); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.listDown(); true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.switchCategory(-1); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.switchCategory(1); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val url = viewModel.selectFromList()
                    player?.play(url)
                    true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    viewModel.closeChannelList(); true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        } else {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    viewModel.channelUp(); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    viewModel.channelDown(); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val url = viewModel.confirmSwitch()
                    if (url != null) {
                        player?.play(url)
                    } else {
                        viewModel.showCurrentChannelInfo()
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                    viewModel.toggleChannelList(); true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (viewModel.isChannelInfoVisible) {
                        viewModel.cancelSwitch(); true
                    } else {
                        super.onKeyDown(keyCode, event)
                    }
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
    }

    fun setActivePlayer(player: ExoPlayerImpl?) {
        this.player = player
    }

    override fun onStop() {
        super.onStop()
        player?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@Composable
fun LiveTVApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as MainActivity

    val player = remember {
        try {
            ExoPlayerImpl(context).also { activity.setActivePlayer(it) }
        } catch (e: Exception) {
            Log.e("LiveTVApp", "Failed to create player", e)
            null
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val surfaceView = SurfaceView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                Log.d("LiveTVApp", "Surface created")
                                this@apply.post {
                                    player?.attachSurface(this@apply)
                                    player?.play(viewModel.currentChannel.url)
                                }
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                Log.d("LiveTVApp", "Surface changed: ${width}x${height}")
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                Log.d("LiveTVApp", "Surface destroyed")
                                player?.detachSurface()
                            }
                        })
                    }
                    addView(surfaceView)
                }
            }
        )

        TVOverlay(viewModel = viewModel)
    }
}
