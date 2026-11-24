package com.reaper.engine.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val TAG = "ReaperShizuku"
    private const val USER_SERVICE_ARGS_TAG = "reaper_user_service"

    private var service: IReaperService? = null
    private var connected = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.reaper.engine", "com.reaper.engine.shizuku.ReaperUserService")
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(false)
        .version(1)
        .tag(USER_SERVICE_ARGS_TAG)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IReaperService.Stub.asInterface(binder)
            connected = true
            Log.d(TAG, "UserService bağlandı")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected = false
            Log.d(TAG, "UserService ayrıldı")
        }
    }

    fun bind(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shizuku çalışmıyor")
                return false
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
                return false
            }
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            true
        } catch (e: Exception) {
            Log.e(TAG, "bind hatası: ${e.message}")
            false
        }
    }

    fun unbind() {
        try {
            if (connected) Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {}
        connected = false
        service = null
    }

    fun isConnected() = connected && service != null

    fun injectScript(script: String): Boolean {
        return try {
            service?.injectScript(script)
            true
        } catch (e: Exception) {
            Log.e(TAG, "inject hatası: ${e.message}")
            false
        }
    }

    fun getRobloxPid(): Int = try {
        service?.getRobloxPid() ?: -1
    } catch (_: Exception) { -1 }
}
