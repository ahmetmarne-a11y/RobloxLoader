package com.reaper.engine

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.IBinder
import android.view.*
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var menuView: View
    private lateinit var espCanvas: ESPCanvas

    private var udpSocket: DatagramSocket? = null
    private var udpThread: Thread? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(42, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupESPCanvas()
        setupMenu()
        startUDP()
    }

    private fun setupESPCanvas() {
        espCanvas = ESPCanvas(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(espCanvas, p)
    }

    private fun setupMenu() {
        menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 150 }

        var dX = 0f; var dY = 0f
        menuView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dX = p.x - e.rawX; dY = p.y - e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (e.rawX + dX).toInt(); p.y = (e.rawY + dY).toInt()
                    wm.updateViewLayout(menuView, p)
                }
            }
            true
        }

        menuView.findViewById<Switch>(R.id.switchPlayerEsp).setOnCheckedChangeListener { _, on ->
            espCanvas.espEnabled = on
        }

        wm.addView(menuView, p)
    }

    private fun startUDP() {
        running = true
        udpThread = Thread {
            try {
                udpSocket = DatagramSocket(7777)
                val buf = ByteArray(65535)
                val pkt = DatagramPacket(buf, buf.size)
                while (running) {
                    udpSocket!!.receive(pkt)
                    parse(String(pkt.data, 0, pkt.length))
                }
            } catch (_: Exception) {}
        }.also { it.isDaemon = true; it.start() }
    }

    // ESP:name;x;y;w;h;dist;hp;joints##...:VPORT:w,h
    private fun parse(msg: String) {
        try {
            if (!msg.startsWith("ESP:")) return
            val vpi  = msg.indexOf(":VPORT:")
            val esp  = if (vpi > 0) msg.substring(4, vpi) else msg.substring(4)
            val vp   = if (vpi > 0) msg.substring(vpi + 7) else null

            vp?.split(",")?.let {
                if (it.size == 2) espCanvas.robloxVP = PointF(
                    it[0].toFloatOrNull() ?: return@let,
                    it[1].toFloatOrNull() ?: return@let
                )
            }

            if (esp.isBlank()) { espCanvas.players = listOf(); return }

            val list = mutableListOf<ESPCanvas.ESPPlayer>()
            for (entry in esp.split("##")) {
                if (entry.isBlank()) continue
                val p = entry.split(";")
                if (p.size < 8) continue
                val joints = mutableListOf<PointF>()
                for (j in p[7].split("|")) {
                    val jp = j.split(",")
                    joints.add(if (jp.size == 3 && jp[2] == "1")
                        PointF(jp[0].toFloatOrNull() ?: 0f, jp[1].toFloatOrNull() ?: 0f)
                    else PointF(0f, 0f))
                }
                list.add(ESPCanvas.ESPPlayer(
                    p[0],
                    p[1].toFloatOrNull() ?: continue,
                    p[2].toFloatOrNull() ?: continue,
                    p[3].toFloatOrNull() ?: continue,
                    p[4].toFloatOrNull() ?: continue,
                    p[5].toIntOrNull() ?: continue,
                    p[6].toIntOrNull() ?: 100,
                    joints
                ))
            }
            espCanvas.players = list
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        udpSocket?.close()
        udpThread?.interrupt()
        if (::menuView.isInitialized) wm.removeView(menuView)
        if (::espCanvas.isInitialized) wm.removeView(espCanvas)
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("reaper_ch", "ReaperEngine", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "reaper_ch")
            .setContentTitle("ReaperEngine aktif")
            .setContentText("ESP hazır")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
