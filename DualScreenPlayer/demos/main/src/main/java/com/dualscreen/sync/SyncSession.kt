package com.dualscreen.sync

object SyncSession {
    var socket: ControlSocket? = null
    var clockSync: ClockSync? = null
    var arrangement: Arrangement? = null
    var mySide: Side? = null
    var isHost: Boolean = false
    var videoServer: LocalVideoServer? = null
    val videoUrls: MutableList<String> = mutableListOf()
}
