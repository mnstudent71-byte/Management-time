package com.example.timemanager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object Fa {
    val nf: NumberFormat = NumberFormat.getInstance(Locale("fa", "IR")).apply { isGroupingUsed = false }
    fun two(n: Int) = (if (n < 10) nf.format(0) else "") + nf.format(n)
}

data class Alarm(
    val id: Int, var hour: Int, var minute: Int, var label: String,
    var daily: Boolean, var enabled: Boolean, var tone: String,
    var date: String = "", var jd: String = ""
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Alarm> {
        val out = mutableListOf<Alarm>()
        val arr = JSONArray(sp(c).getString("list", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Alarm(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("label"),
                o.optBoolean("daily"), o.optBoolean("on", true), o.optString("tone"),
                o.optString("date"), o.optString("jd")))
        }
        return out
    }

    fun save(c: Context, l: List<Alarm>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute)
                .put("label", it.label).put("daily", it.daily).put("on", it.enabled).put("tone", it.tone).put("date", it.date).put("jd", it.jd))
        }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun nextId(c: Context): Int {
        val n = sp(c).getInt("nid", 1)
        sp(c).edit().putInt("nid", n + 1).apply()
        return n
    }

    fun flag(c: Context, k: String) = sp(c).getBoolean(k, false)
    fun setFlag(c: Context, k: String) = sp(c).edit().putBoolean(k, true).apply()
}

object Scheduler {
    private fun pi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun nextTime(a: Alarm): Long {
        if (!a.daily && a.date.isNotEmpty()) {
            val p = a.date.split("-").map { it.toInt() }
            return Calendar.getInstance().apply {
                set(p[0], p[1] - 1, p[2], a.hour, a.minute, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context, a: Alarm) {
        if (!a.enabled) return
        if (!a.daily && a.date.isNotEmpty() && nextTime(a) <= System.currentTimeMillis()) return
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // setAlarmClock: exact, works in Doze, and needs no special permission.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTime(a), show), pi(c, a.id))
    }

    fun cancel(c: Context, a: Alarm) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, a.id))

    fun rescheduleAll(c: Context) = Store.load(c).forEach { schedule(c, it) }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val list = Store.load(c)
        val a = list.find { it.id == id } ?: return
        if (!a.enabled) return
        if (a.daily) Scheduler.schedule(c, a) else { a.enabled = false; Store.save(c, list) }
        c.startForegroundService(Intent(c, RingService::class.java).putExtra("id", id))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.rescheduleAll(c)
}
