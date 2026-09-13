package com.dualscreen.sync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private lateinit var openPlayerBtn: Button
    private lateinit var pickVideoBtn: Button
    private var hostIp: String = "192.168.43.1"

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedIp = result.contents.trim()
            status.text = "Scanned IP: $scannedIp. Connecting..."
            connectToHost(scannedIp)
        } else {
            status.text = "Scan cancelled"
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startQrScanner() else status.text = "Camera permission denied."
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            SyncSession.videoServer = LocalVideoServer(this).apply {
                setVideos(uris)
                start()
            }
            val urls = uris.indices.map { "http://$hostIp:8080/video_$it" }
            SyncSession.videoUrls.clear()
            SyncSession.videoUrls.addAll(urls)
            thread { socket.send(SyncMessage.PrepareVideos(urls)) }
            status.text = "Loaded ${uris.size} video(s)! Pick arrangement."
            checkCanLaunch()
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
            setPadding(40, 50, 40, 40)
        }

        hostIp = localIpAddress() ?: "192.168.43.1"
        val ipLabel = TextView(this).apply { text = "This device IP: $hostIp" }
        val hostBtn = Button(this).apply { text = "Host (Show QR Code)" }
        val scanBtn = Button(this).apply { text = "Scan QR to Join" }
        pickVideoBtn = Button(this).apply {
            text = "Select Video(s) to Play"
            visibility = View.GONE
            setOnClickListener { videoPickerLauncher.launch(arrayOf("video/*")) }
        }
        openPlayerBtn = Button(this).apply {
            text = "OPEN DUAL-SCREEN PLAYER"
            visibility = View.GONE
            setOnClickListener {
                thread { socket.send(SyncMessage.LaunchPlayer) }
                launchPlayerActivity()
            }
        }
        status = TextView(this).apply { text = "Not connected" }
        qrImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(600, 600).apply {
                topMargin = 20
                bottomMargin = 20
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
                    tryBuildArrangement(side)
                }
            })
        }

        root.addView(ipLabel)
        root.addView(hostBtn)
        root.addView(qrImageView)
        root.addView(scanBtn)
        root.addView(pickVideoBtn)
        root.addView(sideRow)
        root.addView(openPlayerBtn)
        root.addView(status)
        setContentView(root)

        socket.listener = { msg -> runOnUiThread { handleMessage(msg) } }

        hostBtn.setOnClickListener {
            SyncSession.isHost = true
            thread {
                socket.host {
                    runOnUiThread {
                        status.text = "Client connected!"
                        pickVideoBtn.visibility = View.VISIBLE
                        sendHello()
                    }
                }
            }
            showQrCode(hostIp)
            status.text = "Hosting... Show this QR to Phone B"
        }

        scanBtn.setOnClickListener { checkCameraPermissionAndScan() }
    }

    private fun checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan Host's QR Code")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    private fun showQrCode(content: String) {
        try {
            val bitmap: Bitmap = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600)
            qrImageView.setImageBitmap(bitmap)
            qrImageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            status.text = "QR Error: ${e.message}"
        }
    }

    private fun connectToHost(scannedIp: String) {
        SyncSession.isHost = false
        thread {
            socket.join(
                scannedIp,
                onConnected = {
                    runOnUiThread {
                        status.text = "Connected to host!"
                        sendHello()
                    }
                },
                onFailed = { e -> runOnUiThread { status.text = "Failed: ${e.message}" } }
            )
        }
    }

    private fun sendHello() {
        thread {
            val info = DeviceScreenInfo.current(this)
            socket.send(SyncMessage.Hello(info.widthMm, info.heightMm))
        }
    }

    private fun handleMessage(msg: SyncMessage) {
        when (msg) {
            is SyncMessage.Hello -> {
                peerInfo = DeviceScreenInfo(msg.widthMm, msg.heightMm)
                status.text = "Peer screen: ${msg.widthMm.toInt()}x${msg.heightMm.toInt()}mm"
            }
            is SyncMessage.Ping -> thread { clockSync.handlePing(msg) }
            is SyncMessage.Pong -> thread { clockSync.handlePong(msg) }
            is SyncMessage.PrepareVideos -> {
                SyncSession.videoUrls.clear()
                SyncSession.videoUrls.addAll(msg.urls)
                status.text = "Received ${msg.urls.size} shared video(s)!"
                checkCanLaunch()
            }
            is SyncMessage.LaunchPlayer -> launchPlayerActivity()
            is SyncMessage.ArrangementMsg -> {
                if (SyncSession.mySide == null) {
                    val mirrored = when (Side.valueOf(msg.peerSideFromSender)) {
                        Side.LEFT -> Side.RIGHT; Side.RIGHT -> Side.LEFT
                        Side.TOP -> Side.BOTTOM; Side.BOTTOM -> Side.TOP
                    }
                    SyncSession.mySide = mirrored
                    tryBuildArrangement(mirrored)
                }
            }
            else -> {}
        }
    }

    private fun tryBuildArrangement(side: Side) {
        val peer = peerInfo ?: return
        val local = DeviceScreenInfo.current(this)
        SyncSession.arrangement = Arrangement.build(local, peer, side)
        status.text = "Arrangement set!"
        checkCanLaunch()
    }

    private fun checkCanLaunch() {
        if (SyncSession.arrangement != null && SyncSession.videoUrls.isNotEmpty()) {
            if (SyncSession.isHost) {
                openPlayerBtn.visibility = View.VISIBLE
            } else {
                status.text = "Ready! Waiting for host to launch player..."
            }
        }
    }

    private fun launchPlayerActivity() {
        startActivity(Intent(this, PlayerActivity::class.java))
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
