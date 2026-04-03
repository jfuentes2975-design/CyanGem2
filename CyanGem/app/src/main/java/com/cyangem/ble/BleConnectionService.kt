package com.cyangem.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class BleConnectionService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@BleConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private fun buildNotification(): Notification {
        val channelId = "cyangem_ble"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Glasses Connection",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("CyanGem")
            .setContentText("Connected to glasses")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
