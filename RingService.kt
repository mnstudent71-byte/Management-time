package com.example.timemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class RingService : Service() {
    companion object {
        const val CH = "alarm_ring"
        const val STOPPED = "com.example.timemanager.STOPPED"
    }

    private var mp: MediaPlayer? = null
    private var vib: Vibrator? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == "STOP") { finishRinging(); return START_NOT_STICKY }
        val id = i?.getIntExtra("id", -1) ?: -1
        val a = Store.load(this).find { it.id == id }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "زنگ هشدار", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        val full = PendingIntent.getActivity(
            this, id, Intent(this, RingActivity::class.java).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a?.label?.takeIf { it.isNotEmpty() } ?: "زنگ هشدار")
            .setContentText("برای خاموش کردن بزنید")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(Notification.Action.Builder(0, "خاموش", stop).build())
            .build()
        startForeground(1, n)

        cleanup()
        val pm = getSystemService(PowerManager::class.java)
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timemanager:ring").apply { acquire(30 * 60 * 1000L) }

        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6).toInt(), 0)

        val chosen = a?.tone?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
        if (chosen == null || !play(chosen)) {
            play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }

        vib = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        @Suppress("DEPRECATION")
        vib?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        return START_NOT_STICKY
    }

    private fun play(uri: Uri): Boolean = try {
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(this@RingService, uri)
            isLooping = true
            prepare(); start()
        }
        true
    } catch (e: Exception) { mp?.release(); mp = null; false }

    private fun cleanup() {
        try { mp?.stop() } catch (e: Exception) {}
        mp?.release(); mp = null
        vib?.cancel(); vib = null
        if (wl?.isHeld == true) wl?.release()
        wl = null
    }

    private fun finishRinging() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(STOPPED).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
}
