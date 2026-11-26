package com.example.timemanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class RingActivity : Activity() {
    private val stopped = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val a = Store.load(this).find { it.id == intent.getIntExtra("id", -1) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0F766E.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 48, 48, 48)
        }
        col.addView(TextView(this).apply {
            text = a?.let { Fa.two(it.hour) + ":" + Fa.two(it.minute) } ?: "زنگ"
            textSize = 72f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = a?.label?.takeIf { it.isNotEmpty() } ?: "زنگ هشدار"
            textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        col.addView(Button(this).apply {
            text = "خاموش کردن"; textSize = 26f
            setPadding(96, 48, 96, 48)
            setOnClickListener {
                startService(Intent(this@RingActivity, RingService::class.java).setAction("STOP"))
                finish()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 120 })
        setContentView(col)

        val f = IntentFilter(RingService.STOPPED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(stopped, f)
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopped) } catch (e: Exception) {}
        super.onDestroy()
    }
}
