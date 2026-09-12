package com.dualscreen.sync

import android.os.SystemClock
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Estimates the offset between the two phones' elapsedRealtime clocks, so a timestamp made
 * on one device can be translated into the other device's clock. Small NTP-style ping/pong,
 * with a rolling median across the last few samples to smooth out Wi-Fi jitter.
 */
class ClockSync(private val socket: ControlSocket) {
    private val samples = ConcurrentLinkedDeque<Long>()

    @Volatile var offsetMs: Long = 0L
        private set

    fun handlePing(ping: SyncMessage.Ping) {
        socket.send(SyncMessage.Pong(ping.t0, SystemClock.elapsedRealtime()))
    }

    fun handlePong(pong: SyncMessage.Pong) {
        val t3 = SystemClock.elapsedRealtime()
        val roundTrip = t3 - pong.t0
        val offset = (pong.t1 - pong.t0) - roundTrip / 2
        samples.addLast(offset)
        if (samples.size > 9) samples.pollFirst()
        offsetMs = samples.sorted()[samples.size / 2]
    }

    fun sendPing() {
        socket.send(SyncMessage.Ping(SystemClock.elapsedRealtime()))
    }

    /** Converts a timestamp taken on the OTHER device into an equivalent local elapsedRealtime. */
    fun toLocalClock(remoteClockMs: Long): Long = remoteClockMs + offsetMs
}
