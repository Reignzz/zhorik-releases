package com.beamox.launcher.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

data class DeviceSnapshot(
    val batteryPct: Int,
    val charging: Boolean,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val ramFreeGb: Double,
    val ramTotalGb: Double,
)

object DeviceInfo {
    fun read(ctx: Context): DeviceSnapshot {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val st = StatFs(Environment.getDataDirectory().path)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = 1024.0 * 1024 * 1024
        return DeviceSnapshot(
            batteryPct = if (level >= 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            storageFreeGb = st.availableBytes / gb,
            storageTotalGb = st.totalBytes / gb,
            ramFreeGb = mi.availMem / gb,
            ramTotalGb = mi.totalMem / gb,
        )
    }
}
