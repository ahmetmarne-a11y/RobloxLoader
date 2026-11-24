package com.reaper.engine.shizuku

import android.content.Intent
import android.os.IBinder
import java.io.File

class ReaperUserService : IReaperService.Stub() {

    companion object {
        const val ROBLOX_PKG = "com.roblox.client"
        const val DELTA_PKG = "com.delta.executor"
        const val ARCEUS_PKG = "com.arceus.x"
    }

    override fun isReady(): Boolean = true

    override fun getRobloxPid(): Int {
        return try {
            File("/proc").listFiles()?.forEach { f ->
                if (f.isDirectory && f.name.all { it.isDigit() }) {
                    val cmdline = File(f, "cmdline").readText().replace("\u0000", "")
                    if (cmdline.contains(ROBLOX_PKG)) return f.name.toInt()
                }
            }
            -1
        } catch (e: Exception) { -1 }
    }

    override fun injectScript(scriptContent: String) {
        if (tryDeltaInject(scriptContent)) return
        if (tryArceusInject(scriptContent)) return
        fallbackWriteToFile(scriptContent)
    }

    private fun tryDeltaInject(script: String): Boolean {
        return try {
            val intent = Intent("com.delta.executor.EXECUTE_SCRIPT").apply {
                setPackage(DELTA_PKG)
                putExtra("script", script)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            execBroadcast(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun tryArceusInject(script: String): Boolean {
        return try {
            val intent = Intent("com.arceus.x.EXECUTE").apply {
                setPackage(ARCEUS_PKG)
                putExtra("script_content", script)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            execBroadcast(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun execBroadcast(intent: Intent) {
        // am broadcast komutu ile ADB yetkisiyle gönder
        val pkg = intent.`package` ?: return
        val action = intent.action ?: return
        val script = intent.getStringExtra("script")
            ?: intent.getStringExtra("script_content") ?: return

        val tmp = File("/data/local/tmp/reaper_script.lua")
        tmp.writeText(script)

        Runtime.getRuntime().exec(arrayOf(
            "am", "broadcast",
            "-a", action,
            "-n", "$pkg/.ScriptReceiver",
            "--es", "script_path", tmp.absolutePath
        ))
    }

    private fun fallbackWriteToFile(script: String) {
        try { File("/sdcard/reaper_esp.lua").writeText(script) } catch (_: Exception) {}
    }

    override fun destroy() {}
}
