package com.dualscreen.sync

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.NetworkInterface
import kotlin.concurrent.thread

class PairingActivity : ComponentActivity() {
    private var peerInfo: DeviceScreenInfo? = null
    private lateinit var status: TextView
    private lateinit var qrImageView: ImageView
    private lateinit var socket: ControlSocket
    private lateinit var clockSync: ClockSync

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedIp = result.contents.trim()
            status.text = "Scanned IP: $scannedIp. Connecting..."
            connectToHost(scannedIp)
        } else {
            status.text = "Scan cancelled"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        socket = ControlSocket()
        clockSync = ClockSync(socket)
        SyncSession.socket = socket
        SyncSession.clockSync = clockSync

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val myIp = localIpAddress() ?: "192.168.43.1"
        val ipLabel = TextView(this).apply { text = "This device IP: $myIp" }
        val hostBtn = Button(this).apply { text = "Host (Show QR Code)" }
        val scanBtn = Button(this).apply { text = "Scan QR to Join" }
        status = TextView(this).apply { text = "Not connected" }
        qrImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(600, 600).apply {
                topMargin = 30
                bottomMargin = 30
            }
            visibility = View.GONE
        }

        val sideRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (side in Side.values()) {
            sideRow.addView(Button(this).apply {
                text = "Peer is ${side.name}"
                setOnClickListener {
                    SyncSession.mySide = side
                    thread { socket.send(SyncMessage.ArrangementMsg(side.name)) }
                    tryBuildArrangement(side, status)
                }
            })
        }

        root.addView(ipLabel)
        root.addView(hostBtn)
        root.addView(qrImageView)
        root.addView(scanBtn)
        root.addView(status)
        root.addView(sideRow)
        setContentView(root)

        socket.listener = { msg -> runOnUiThread { handleMessage(msg, socket, clockSync, status) } }

        hostBtn.setOnClickListener {
            SyncSession.isHost = true
            thread {
                socket.host {
                    runOnUiThread { status.text = "Client connected!"; sendHello(socket) }
                }
            }
            showQrCode(myIp)
            status.text = "Hosting... Show this QR to Phone B"
        }

        scanBtn.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Point camera at Phone A's screen")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
            scanLauncher.launch(options)
        }
    }

    private fun showQrCode(content: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600)
            qrImageView.setImageBitmap(bitmap)
            qrImageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            status.text = "QR generation error: ${e.message}"
        }
    }

    private fun connectToHost(hostIp: String) {
        SyncSession.isHost = false
        thread {
            socket.join(
                hostIp,
                onConnected = {
                    runOnUiThread {
                        status.text = "Connected to host!"
                        sendHello(socket)
                    }
                },
                onFailed = { e ->
                    runOnUiThread { status.text = "Connection failed: ${e.message}" }
                }
            )
        }
    }

    private fun sendHello(socket: ControlSocket) {
        thread {
            val info = DeviceScreenInfo.current(this)
            socket.send(SyncMessage.Hello(info.widthMm, info.heightMm))
        }
    }

    private fun handleMessage(msg: SyncMessage, socket: ControlSocket, clockSync: ClockSync, status: TextView) {
        when (msg) {
            is SyncMessage.Hello -> {
                peerInfo = DeviceScreenInfo(msg.widthMm, msg.heightMm)
                status.text = "Peer screen: ${msg.widthMm.toInt()}x${msg.heightMm.toInt()}mm"
            }
            is SyncMessage.Ping -> thread { clockSync.handlePing(msg) }
            is SyncMessage.Pong -> thread { clockSync.handlePong(msg) }
            is SyncMessage.ArrangementMsg -> {
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
        status.text = "Arrangement ready — open player!"
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
