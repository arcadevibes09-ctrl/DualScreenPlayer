package com.dualscreen.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

const val SYNC_PORT = 57321

/** Newline-delimited JSON socket between the two phones. One side calls [host], the other [join].
 *  Assign [listener] any time — from PairingActivity first, then reassign from PlayerActivity
 *  once it takes over, so the same live connection carries pairing AND playback messages. */
class ControlSocket {
    var listener: (SyncMessage) -> Unit = {}

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile var isConnected = false
        private set

    fun host(onClientConnected: () -> Unit) {
        pool.execute {
            val server = ServerSocket(SYNC_PORT)
            val client = server.accept() // blocks until the other phone connects
            attach(client)
            onClientConnected()
        }
    }

    fun join(hostIp: String, onConnected: () -> Unit, onFailed: (Exception) -> Unit) {
        pool.execute {
            try {
                val client = Socket(hostIp, SYNC_PORT)
                attach(client)
                onConnected()
            } catch (e: Exception) {
                onFailed(e)
            }
        }
    }

    private fun attach(client: Socket) {
        socket = client
        writer = PrintWriter(client.getOutputStream(), true)
        isConnected = true
        pool.execute {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            while (isConnected) {
                val line = try { reader.readLine() } catch (e: Exception) { null } ?: break
                SyncMessage.parse(line)?.let { listener(it) }
            }
            isConnected = false
        }
    }

    fun send(message: SyncMessage) {
        writer?.println(message.toJson().toString())
    }

    fun close() {
        isConnected = false
        socket?.close()
    }
}
