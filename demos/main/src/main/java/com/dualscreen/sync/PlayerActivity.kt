package com.dualscreen.sync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val socket = SyncSession.socket ?: run {
            Toast.makeText(this, "Pair first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val clock = SyncSession.clockSync ?: run {
            Toast.makeText(this, "Pair first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val arrangement = SyncSession.arrangement ?: run {
            Toast.makeText(this, "Arrangement missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }

        canvas = DualScreenCanvas(this)
        canvas.playerView.player = player
        setContentView(canvas)

        syncController = PlaybackSyncController(player, socket, clock)
        syncController.attachPlayerListener()

        socket.listener = { msg ->
            runOnUiThread {
                syncController.handleRemoteMessage(msg)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    canvas.applyArrangement(arrangement, videoSize.width, videoSize.height)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
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
