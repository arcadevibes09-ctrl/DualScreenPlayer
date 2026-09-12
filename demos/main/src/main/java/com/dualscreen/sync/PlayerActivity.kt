package com.dualscreen.sync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2.ExoPlayer

/** Both phones must have the SAME video file locally (v1 assumption — see README). */
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var canvas: DualScreenCanvas
    private lateinit var syncController: PlaybackSyncController
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val socket = SyncSession.socket ?: error("Pair the phones first (PairingActivity)")
        val clock = SyncSession.clockSync ?: error("Pair the phones first (PairingActivity)")
        val arrangement = SyncSession.arrangement ?: error("Choose an arrangement first (PairingActivity)")

        player = ExoPlayer.Builder(this).build()
        canvas = DualScreenCanvas(this)
        canvas.playerView.player = player
        setContentView(canvas)

        syncController = PlaybackSyncController(player, socket, clock)
        syncController.attachPlayerListener()

        // Hand the live socket connection over from pairing to playback.
        socket.listener = { msg -> syncController.handleRemoteMessage(msg) }

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                canvas.applyArrangement(arrangement, videoSize.width, videoSize.height)
            }
        })

        intent.getStringExtra("video_uri")?.let { uri ->
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            player.prepare()
        }

        // Only the host's copy of this activity should actually call maybeSendHeartbeat() —
        // the check happens inside PlaybackSyncController's own gating, but the intent is
        // "one phone is the timing source"; make that phone SyncSession.isHost.
        handler.post(object : Runnable {
            override fun run() {
                if (SyncSession.isHost) syncController.maybeSendHeartbeat()
                handler.postDelayed(this, 500)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
