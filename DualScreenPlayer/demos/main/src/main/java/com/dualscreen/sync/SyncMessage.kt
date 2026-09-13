package com.dualscreen.sync

import android.os.SystemClock
import org.json.JSONObject

/** Every message sent over the control socket between the two phones. */
sealed class SyncMessage {
    abstract fun toJson(): JSONObject

    data class Hello(val widthMm: Float, val heightMm: Float) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "HELLO").put("w", widthMm).put("h", heightMm)
    }
    data class ArrangementMsg(val peerSideFromSender: String) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "ARRANGEMENT").put("side", peerSideFromSender)
    }
    data class Ping(val t0: Long) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "PING").put("t0", t0)
    }
    data class Pong(val t0: Long, val t1: Long) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "PONG").put("t0", t0).put("t1", t1)
    }
    data class Play(val positionMs: Long, val senderClockMs: Long = SystemClock.elapsedRealtime()) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "PLAY").put("pos", positionMs).put("clock", senderClockMs)
    }
    data class Pause(val positionMs: Long, val senderClockMs: Long = SystemClock.elapsedRealtime()) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "PAUSE").put("pos", positionMs).put("clock", senderClockMs)
    }
    data class Seek(val positionMs: Long, val senderClockMs: Long = SystemClock.elapsedRealtime()) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "SEEK").put("pos", positionMs).put("clock", senderClockMs)
    }
    data class Heartbeat(val positionMs: Long, val senderClockMs: Long = SystemClock.elapsedRealtime()) : SyncMessage() {
        override fun toJson(): JSONObject = JSONObject().put("type", "HEARTBEAT").put("pos", positionMs).put("clock", senderClockMs)
    }

    companion object {
        fun parse(line: String): SyncMessage? {
            val o = JSONObject(line)
            return when (o.getString("type")) {
                "HELLO" -> Hello(o.getDouble("w").toFloat(), o.getDouble("h").toFloat())
                "ARRANGEMENT" -> ArrangementMsg(o.getString("side"))
                "PING" -> Ping(o.getLong("t0"))
                "PONG" -> Pong(o.getLong("t0"), o.getLong("t1"))
                "PLAY" -> Play(o.getLong("pos"), o.getLong("clock"))
                "PAUSE" -> Pause(o.getLong("pos"), o.getLong("clock"))
                "SEEK" -> Seek(o.getLong("pos"), o.getLong("clock"))
                "HEARTBEAT" -> Heartbeat(o.getLong("pos"), o.getLong("clock"))
                else -> null
            }
        }
    }
}
