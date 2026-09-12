package com.dualscreen.sync

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer

private const val DRIFT_THRESHOLD_MS = 90L
private const val HEARTBEAT_MS = 2000L

/**
 * Keeps this phone's ExoPlayer in lockstep with the peer's ExoPlayer. Either phone can send
 * play/pause/seek (whichever the user taps), but every REMOTE command is applied with a
 * network-delay correction, and a periodic heartbeat catches any slow drift in between.
 */
class PlaybackSyncController(
    private val player: ExoPlayer,
    private val socket: ControlSocket,
    private val clock: ClockSync
) {
    private val handler = Handler(Looper.getMainLooper())
    private var suppressLocalEcho = false
    private var lastHeartbeatSentAt = 0L

    fun onLocalPlay() { if (!suppressLocalEcho) socket.send(SyncMessage.Play(player.currentPosition)) }
    fun onLocalPause() { if (!suppressLocalEcho) socket.send(SyncMessage.Pause(player.currentPosition)) }
    fun onLocalSeek(positionMs: Long) { if (!suppressLocalEcho) socket.send(SyncMessage.Seek(positionMs)) }

    /** Feed every message coming off ControlSocket.listener into this. */
    fun handleRemoteMessage(msg: SyncMessage) {
        when (msg) {
            is SyncMessage.Ping -> clock.handlePing(msg)
            is SyncMessage.Pong -> clock.handlePong(msg)
            is SyncMessage.Play -> applyRemote(msg.positionMs, msg.senderClockMs) { player.play() }
            is SyncMessage.Pause -> applyRemote(msg.positionMs, msg.senderClockMs) { player.pause() }
            is SyncMessage.Seek -> applyRemote(msg.positionMs, msg.senderClockMs) {}
            is SyncMessage.Heartbeat -> correctDrift(msg.positionMs, msg.senderClockMs)
            else -> {}
        }
    }

    private fun applyRemote(positionMs: Long, senderClockMs: Long, andThen: () -> Unit) {
        handler.post {
            suppressLocalEcho = true
            val elapsedSinceSend = SystemClock.elapsedRealtime() - clock.toLocalClock(senderClockMs)
            player.seekTo(positionMs + elapsedSinceSend.coerceAtLeast(0))
            andThen()
            suppressLocalEcho = false
        }
    }

    private fun correctDrift(positionMs: Long, senderClockMs: Long) {
        handler.post {
            val elapsedSinceSend = SystemClock.elapsedRealtime() - clock.toLocalClock(senderClockMs)
            val expected = positionMs + elapsedSinceSend.coerceAtLeast(0)
            val drift = player.currentPosition - expected
            if (Math.abs(drift) > DRIFT_THRESHOLD_MS) {
                suppressLocalEcho = true
                player.seekTo(expected)
                suppressLocalEcho = false
            }
        }
    }

    /** Call every ~500ms from ONE agreed phone (the host) only. */
    fun maybeSendHeartbeat() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeartbeatSentAt >= HEARTBEAT_MS && player.isPlaying) {
            lastHeartbeatSentAt = now
            socket.send(SyncMessage.Heartbeat(player.currentPosition))
        }
    }

    fun attachPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onLocalPlay() else onLocalPause()
            }
        })
    }
}
