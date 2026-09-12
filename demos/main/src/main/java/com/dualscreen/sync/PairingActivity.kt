package com.dualscreen.sync

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.NetworkInterface

/** Starter pairing screen: connect over the socket, exchange screen sizes, pick an arrangement. */
class PairingActivity : Activity() {
    private var peerInfo: DeviceScreenInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val socket = ControlSocket()
        val clockSync = ClockSync(socket)
        SyncSession.socket = socket
        SyncSession.clockSync = clockSync

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40) }
        val ipLabel = TextView(this).apply { text = "This phone's IP: ${localIpAddress() ?: "unknown"}" }
        val hostBtn = Button(this).apply { text = "Host (wait for other phone)" }
        val ipInput = EditText(this).apply { hint = "Host's IP address" }
        val joinBtn = Button(this).apply { text = "Join" }
        val status = TextView(this).apply { text = "Not connected" }
        val sideRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        for (side in Side.entries) {
            sideRow.addView(Button(this).apply {
                text = "Peer is ${side.name}"
                setOnClickListener {
                    SyncSession.mySide = side
                    socket.send(SyncMessage.ArrangementMsg(side.name))
                    tryBuildArrangement(side, status)
                }
            })
        }

        root.addView(ipLabel); root.addView(hostBtn); root.addView(ipInput)
        root.addView(joinBtn); root.addView(status); root.addView(sideRow)
        setContentView(root)

        socket.listener = { msg -> runOnUiThread { handleMessage(msg, socket, clockSync, status) } }

        hostBtn.setOnClickListener {
            SyncSession.isHost = true
            socket.host { runOnUiThread { status.text = "Client connected"; sendHello(socket) } }
        }
        joinBtn.setOnClickListener {
            SyncSession.isHost = false
            socket.join(ipInput.text.toString(),
                onConnected = { runOnUiThread { status.text = "Connected to host"; sendHello(socket) } },
                onFailed = { e -> runOnUiThread { status.text = "Failed: ${e.message}" } })
        }
    }

    private fun sendHello(socket: ControlSocket) {
        val info = DeviceScreenInfo.current(this)
        socket.send(SyncMessage.Hello(info.widthMm, info.heightMm))
    }

    private fun handleMessage(msg: SyncMessage, socket: ControlSocket, clockSync: ClockSync, status: TextView) {
        when (msg) {
            is SyncMessage.Hello -> {
                peerInfo = DeviceScreenInfo(msg.widthMm, msg.heightMm)
                status.text = "Peer screen: ${msg.widthMm.toInt()}x${msg.heightMm.toInt()}mm"
            }
            is SyncMessage.Ping -> clockSync.handlePing(msg)
            is SyncMessage.Pong -> clockSync.handlePong(msg)
            is SyncMessage.ArrangementMsg -> {
                // Peer told us where THEY think we are; treat it as our side if we haven't picked yet.
                if (SyncSession.mySide == null) {
                    val mirrored = when (Side.valueOf(msg.peerSideFromSender)) {
                        Side.LEFT -> Side.RIGHT; Side.RIGHT -> Side.LEFT
                        Side.TOP -> Side.BOTTOM; Side.BOTTOM -> Side.TOP
                    }
                    SyncSession.mySide = mirrored
                    tryBuildArrangement(mirrored, status)
                }
            }
            else -> {}
        }
    }

    private fun tryBuildArrangement(side: Side, status: TextView) {
        val peer = peerInfo ?: return
        val local = DeviceScreenInfo.current(this)
        SyncSession.arrangement = Arrangement.build(local, peer, side)
        status.text = "Arrangement ready — open the player."
    }

    private fun localIpAddress(): String? {
        NetworkInterface.getNetworkInterfaces().asSequence().forEach { intf ->
            intf.inetAddresses.asSequence().forEach { addr ->
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) return addr.hostAddress
            }
        }
        return null
    }
}
