package com.firstvoice.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors device battery level and provides warnings
 * for crisis scenarios where charging is limited.
 */
class BatteryMonitor(private val context: Context) {

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _isLowPowerMode = MutableStateFlow(false)
    val isLowPowerMode: StateFlow<Boolean> = _isLowPowerMode

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            if (level >= 0 && scale > 0) {
                _batteryLevel.value = (level * 100) / scale
            }

            _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }

    /**
     * Enable low power mode — disables map rendering and mesh background scanning.
     */
    fun enableLowPowerMode() {
        _isLowPowerMode.value = true
    }

    /**
     * Disable low power mode.
     */
    fun disableLowPowerMode() {
        _isLowPowerMode.value = false
    }

    /**
     * Check if battery warning should be shown.
     */
    fun shouldShowWarning(): Boolean = _batteryLevel.value <= 20

    /**
     * Check if low power mode should be offered.
     */
    fun shouldOfferLowPowerMode(): Boolean = _batteryLevel.value <= 10
}
