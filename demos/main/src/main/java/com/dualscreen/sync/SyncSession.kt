package com.dualscreen.sync

/** In-process holder so the socket/clock/arrangement survive the jump from PairingActivity
 *  to PlayerActivity. Swap for a proper ViewModel/DI if your app already has one. */
object SyncSession {
    var socket: ControlSocket? = null
    var clockSync: ClockSync? = null
    var arrangement: Arrangement? = null
    var mySide: Side? = null
    var isHost: Boolean = false
}
