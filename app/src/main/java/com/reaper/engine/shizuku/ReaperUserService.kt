package com.reaper.engine.shizuku

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File

// Bu servis Shizuku tarafından ADB yetkisiyle başlatılır
// Normal uygulama yetkisinin üzerinde çalışır
class ReaperUserService : IReaperService.Stub() {

    companion object {
        const val ROBLOX_PKG = "com.roblox.client"
        // Delta Executor intent action
        const val DELTA_PKG = "com.delta.executor"
        const val DELTA_INJECT_ACTION = "com.delta.executor.EXECUTE_SCRIPT"
        // Arceus X fallback
        const val ARCEUS_PKG = "com.arceus.x"
        const val ARCEUS_INJECT_ACTION = "com.arceus.x.EXECUTE"
    }

    override fun isReady(): Boolean = true

    override fun getRobloxPid(): Int {
        return try {
            // /proc altında Roblox process'ini bul
            File("/proc").listFiles()?.forEach { f ->
                if (f.isDirectory && f.name.all { it.isDigit() }) {
                    val cmdline = File(f, "cmdline").readText().replace("\u0000", "")
                    if (cmdline.contains(ROBLOX_PKG)) {
                        return f.name.toInt()
                    }
                }
            }
            -1
        } catch (e: Exception) {
            -1
        }
    }

    override fun injectScript(scriptContent: String) {
        // Önce Delta dene
        if (tryDeltaInject(scriptContent)) return
        // Arceus X dene
        if (tryArceusInject(scriptContent)) return
        // Hiçbiri yoksa dosyaya yaz, kullanıcı bilgilendirilir
        fallbackWriteToFile(scriptContent)
    }

    private fun tryDeltaInject(script: String): Boolean {
        return try {
            // Delta'nın broadcast receiver'ına script gönder
            val intent = Intent(DELTA_INJECT_ACTION).apply {
                setPackage(DELTA_PKG)
                putExtra("script", script)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            // ADB yetkisiyle sendBroadcast çalışır
            sendBroadcastWithShizuku(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun tryArceusInject(script: String): Boolean {
        return try {
            val intent = Intent(ARCEUS_INJECT_ACTION).apply {
                setPackage(ARCEUS_PKG)
                putExtra("script_content", script)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            sendBroadcastWithShizuku(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ADB yetkisiyle broadcast gönder
    private fun sendBroadcastWithShizuku(intent: Intent) {
        // ActivityManager'ı reflection ile çağır (ADB yetki gerekir)
        val am = Class.forName("android.app.ActivityManager")
        val getService = am.getMethod("getService")
        val iAm = getService.invoke(null)
        val broadcastIntent = iAm.javaClass.getMethod(
            "broadcastIntent",
            android.app.IApplicationThread::class.java,
            Intent::class.java,
            String::class.java,
            android.content.IIntentReceiver::class.java,
            Int::class.java,
            String::class.java,
            android.os.Bundle::class.java,
            Array<String>::class.java,
            Int::class.java,
            android.os.Bundle::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Int::class.java
        )
        broadcastIntent.invoke(
            iAm, null, intent, null, null,
            0, null, null, null, -1, null,
            false, false, -1
        )
    }

    private fun fallbackWriteToFile(script: String) {
        try {
            File("/sdcard/reaper_esp.lua").writeText(script)
        } catch (_: Exception) {}
    }

    override fun destroy() {}
}
