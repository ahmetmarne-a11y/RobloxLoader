package com.reaper.engine

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.reaper.engine.shizuku.ShizukuManager
import rikka.shizuku.Shizuku

class DashboardActivity : AppCompatActivity() {

    private val ROBLOX_PKG = "com.roblox.client"
    private val games = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            connectShizuku()
        } else {
            setStatus("Shizuku izni reddedildi.", "#E53935")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val listView = findViewById<ListView>(R.id.gameList)
        val addBtn   = findViewById<FloatingActionButton>(R.id.addGameBtn)
        statusText   = findViewById(R.id.statusText)

        adapter = ArrayAdapter(this, R.layout.list_item_game, R.id.gameItemText, games)
        listView.adapter = adapter

        // overlay izni
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                1001
            )
        }

        // Shizuku listener kaydet
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        // Shizuku bağlan
        connectShizuku()

        addBtn.setOnClickListener { addRoblox() }

        listView.setOnItemClickListener { _, _, pos, _ ->
            launchWithAutoInject(games[pos])
        }
    }

    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku çalışmıyor — Shizuku uygulamasını aç.", "#E53935")
                return
            }
            val bound = ShizukuManager.bind()
            if (bound) {
                // bağlantı async, 1sn sonra kontrol et
                handler.postDelayed({
                    if (ShizukuManager.isConnected()) {
                        setStatus("Shizuku bağlı ✓ — Otomatik inject hazır", "#4CAF50")
                    } else {
                        setStatus("Shizuku bağlantısı bekleniyor...", "#FF9800")
                    }
                }, 1000)
            }
        } catch (e: Exception) {
            setStatus("Shizuku hatası: ${e.message}", "#E53935")
        }
    }

    private fun addRoblox() {
        try {
            packageManager.getPackageInfo(ROBLOX_PKG, 0)
            if (!games.contains("Roblox")) {
                games.add("Roblox")
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Roblox eklendi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Zaten ekli.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(this, "Roblox yüklü değil!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchWithAutoInject(game: String) {
        // 1. Overlay servisini başlat (ESP canvas + UDP listener)
        startForegroundService(Intent(this, OverlayService::class.java))

        // 2. Roblox'u aç
        val launchIntent = packageManager.getLaunchIntentForPackage(ROBLOX_PKG)
        if (launchIntent == null) {
            Toast.makeText(this, "Roblox başlatılamadı.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(launchIntent)

        // 3. Roblox açılınca Shizuku ile otomatik inject
        // Roblox'un açılması ~3sn sürer, 4sn sonra inject dene
        setStatus("Roblox açılıyor, inject bekleniyor...", "#FF9800")
        handler.postDelayed({
            autoInject()
        }, 4000)
    }

    private fun autoInject() {
        if (!ShizukuManager.isConnected()) {
            setStatus("Shizuku bağlı değil — script manuel yükle.", "#E53935")
            return
        }

        val pid = ShizukuManager.getRobloxPid()
        if (pid == -1) {
            // Roblox henüz açılmadı, 2sn daha bekle
            handler.postDelayed({ autoInject() }, 2000)
            return
        }

        val ok = ShizukuManager.injectScript(ESP_SCRIPT)
        if (ok) {
            setStatus("ESP inject edildi ✓ — Overlay menüden aç", "#4CAF50")
        } else {
            setStatus("Inject başarısız — Delta/Arceus X yüklü mü?", "#E53935")
        }
    }

    private fun setStatus(msg: String, color: String) {
        handler.post {
            statusText.text = msg
            statusText.setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuManager.unbind()
    }

    companion object {
        val ESP_SCRIPT = """
-- ReaperEngine ESP v1.0
-- Shizuku ile otomatik inject edildi

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local Camera = workspace.CurrentCamera
local LocalPlayer = Players.LocalPlayer
local MAX_DIST = 280

-- UDP socket kurulumu
local udp
pcall(function()
    local socket = require("socket")
    udp = socket.udp()
    udp:setpeername("127.0.0.1", 7777)
    udp:settimeout(0)
end)

local function send(data)
    if udp then pcall(function() udp:send(data) end) end
end

local function getJoints(character)
    local parts = {
        "Head","UpperTorso","LeftUpperArm","RightUpperArm",
        "LeftLowerArm","RightLowerArm",
        "LeftUpperLeg","RightUpperLeg",
        "LeftLowerLeg","RightLowerLeg",
        "LeftFoot","RightFoot"
    }
    local t = {}
    for _, name in ipairs(parts) do
        local p = character:FindFirstChild(name)
        if p then
            local s, on = Camera:WorldToViewportPoint(p.Position)
            table.insert(t, ("%.1f,%.1f,%s"):format(s.X, s.Y, on and "1" or "0"))
        else
            table.insert(t, "0,0,0")
        end
    end
    return table.concat(t, "|")
end

RunService.RenderStepped:Connect(function()
    local vp = Camera.ViewportSize
    local entries = {}

    for _, pl in ipairs(Players:GetPlayers()) do
        if pl == LocalPlayer then continue end
        local ch = pl.Character
        if not ch then continue end

        local root = ch:FindFirstChild("HumanoidRootPart")
        local head = ch:FindFirstChild("Head")
        local hum  = ch:FindFirstChildOfClass("Humanoid")
        if not root or not head or not hum then continue end

        local dist = (Camera.CFrame.Position - root.Position).Magnitude
        if dist > MAX_DIST then continue end

        local topP = head.Position + Vector3.new(0, head.Size.Y/2, 0)
        local botP = root.Position - Vector3.new(0, root.Size.Y/2, 0)
        local topS, onScreen = Camera:WorldToViewportPoint(topP)
        local botS = Camera:WorldToViewportPoint(botP)
        if not onScreen then continue end

        local h  = math.abs(topS.Y - botS.Y)
        local w  = h * 0.6
        local hp = math.floor(hum.Health / math.max(hum.MaxHealth,1) * 100)

        table.insert(entries, ("%s;%.1f;%.1f;%.1f;%.1f;%d;%d;%s"):format(
            pl.Name, topS.X, topS.Y, w, h,
            math.floor(dist), hp, getJoints(ch)
        ))
    end

    send("ESP:" .. table.concat(entries,"##") .. ":VPORT:" .. vp.X .. "," .. vp.Y)
end)

print("[ReaperEngine] ESP aktif")
""".trimIndent()
    }
}
