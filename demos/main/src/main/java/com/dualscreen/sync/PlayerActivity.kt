package com.dualscreen.sync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.video.VideoSize

class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var canvas: DualScreenCanvas
    private lateinit var syncController: PlaybackSyncController
    private val handler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (SyncSession.isHost) {
                syncController.maybeSendHeartbeat()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep the display turned on and hide system navigation bars for a clean canvas
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val socket = SyncSession.socket ?: error("Pair first")
        val clock = SyncSession.clockSync ?: error("Pair first")
        val arrangement = SyncSession.arrangement ?: error("Arrangement missing")

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }

        canvas = DualScreenCanvas(this)
        // Enable controller so taps trigger play/pause syncing
        canvas.playerView.useController = true
        canvas.playerView.player = player
        setContentView(canvas)

        syncController = PlaybackSyncController(player, socket, clock)
        syncController.attachPlayerListener()

        // Transfer socket listener to playback control
        socket.listener = { msg -> syncController.handleRemoteMessage(msg) }

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    canvas.applyArrangement(arrangement, videoSize.width, videoSize.height)
                }
            }
        })

        if (SyncSession.videoUrls.isNotEmpty()) {
            val mediaItems = SyncSession.videoUrls.map { MediaItem.fromUri(Uri.parse(it)) }
            player.setMediaItems(mediaItems)
            player.prepare()
            if (SyncSession.isHost) {
                player.play()
            }
        }

        handler.post(heartbeatRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatRunnable)
        player.release()
        if (SyncSession.isHost) {
            SyncSession.videoServer?.stop()
        }
    }
}
