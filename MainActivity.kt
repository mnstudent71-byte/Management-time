package com.example.timemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.PersianCalendar
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val dows = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
    private var hh = 7
    private var mm = 0
    private var tone = ""
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var timeBtn: Button
    private lateinit var dateBtn: Button
    private var sy = 0
    private var sm = 0
    private var sd = 1
    private lateinit var toneBtn: Button
    private lateinit var labelEt: EditText
    private lateinit var dailyCb: CheckBox
    private lateinit var listBox: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { updateClock(); handler.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(size: Float, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(0xFF1D1D1B.toInt())
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        setContentView(ScrollView(this).apply {
            addView(col); setBackgroundColor(0xFFF6F5F1.toInt()); layoutDirection = View.LAYOUT_DIRECTION_RTL
        })

        date = tv(15f); clock = tv(40f, true)
        col.addView(tv(22f, true).apply { text = "مدیریت زمان" })
        col.addView(date); col.addView(clock)
        col.addView(tv(17f, true).apply { text = "زنگ جدید"; setPadding(0, dp(20), 0, dp(6)) })

        timeBtn = Button(this).apply {
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m -> hh = h; mm = m; showTime() }, hh, mm, true).show()
            }
        }
        val p0 = PersianCalendar()
        sy = p0.get(android.icu.util.Calendar.YEAR); sm = p0.get(android.icu.util.Calendar.MONTH); sd = p0.get(android.icu.util.Calendar.DAY_OF_MONTH)
        dateBtn = Button(this).apply { setOnClickListener { chooseDate() } }
        labelEt = EditText(this).apply { hint = "عنوان (اختیاری)"; setSingleLine(true) }
        dailyCb = CheckBox(this).apply {
            text = "هر روز تکرار شود"
            setOnCheckedChangeListener { _, c -> dateBtn.visibility = if (c) View.GONE else View.VISIBLE }
        }
        toneBtn = Button(this).apply { setOnClickListener { chooseTone() } }
        val fileBtn = Button(this).apply { text = "انتخاب فایل صوتی از پوشه‌های گوشی"; setOnClickListener { pickFile() } }
        val pathBtn = Button(this).apply { text = "وارد کردن مسیر فایل صوتی"; setOnClickListener { askPath() } }
        val add = Button(this).apply { text = "افزودن زنگ"; setOnClickListener { addAlarm() } }
        listOf(timeBtn, dateBtn, labelEt, dailyCb, toneBtn, fileBtn, pathBtn, add).forEach { col.addView(it) }
        showTime(); showDate(); showTone()

        col.addView(tv(17f, true).apply { text = "زنگ‌های من"; setPadding(0, dp(24), 0, dp(6)) })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)

        askPermissions()
    }

    override fun onResume() { super.onResume(); handler.post(tick); refresh() }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun updateClock() {
        val n = java.util.Calendar.getInstance()
        clock.text = Fa.two(n.get(java.util.Calendar.HOUR_OF_DAY)) + ":" + Fa.two(n.get(java.util.Calendar.MINUTE)) + ":" + Fa.two(n.get(java.util.Calendar.SECOND))
        val p = PersianCalendar()
        date.text = dows[p.get(android.icu.util.Calendar.DAY_OF_WEEK) - 1] + " " +
            Fa.nf.format(p.get(android.icu.util.Calendar.DAY_OF_MONTH)) + " " +
            months[p.get(android.icu.util.Calendar.MONTH)] + " " +
            Fa.nf.format(p.get(android.icu.util.Calendar.YEAR))
    }

    private fun showTime() { timeBtn.text = "ساعت: " + Fa.two(hh) + ":" + Fa.two(mm) }
    private fun showTone() { toneBtn.text = "آهنگ زنگ: " + toneName(tone) }
    private fun showDate() { dateBtn.text = "تاریخ: " + Fa.nf.format(sd) + " " + months[sm] + " " + Fa.nf.format(sy) }

    private fun gregKey(y: Int, m: Int, d: Int): String? {
        val pc = PersianCalendar()
        pc.clear()
        pc.set(android.icu.util.Calendar.YEAR, y)
        pc.set(android.icu.util.Calendar.MONTH, m)
        pc.set(android.icu.util.Calendar.DAY_OF_MONTH, d)
        pc.set(android.icu.util.Calendar.HOUR_OF_DAY, 12)
        if (pc.get(android.icu.util.Calendar.YEAR) != y || pc.get(android.icu.util.Calendar.MONTH) != m ||
            pc.get(android.icu.util.Calendar.DAY_OF_MONTH) != d) return null
        val g = java.util.Calendar.getInstance().apply { timeInMillis = pc.timeInMillis }
        return "${g.get(java.util.Calendar.YEAR)}-${g.get(java.util.Calendar.MONTH) + 1}-${g.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun chooseDate() {
        val ny = PersianCalendar().get(android.icu.util.Calendar.YEAR)
        fun np(min: Int, max: Int, v: Int, names: Array<String>) = NumberPicker(this).apply {
            minValue = min; maxValue = max; displayedValues = names; value = v; wrapSelectorWheel = false
        }
        val y = np(ny, ny + 3, sy.coerceIn(ny, ny + 3), Array(4) { Fa.nf.format(ny + it) })
        val m = np(0, 11, sm, months)
        val d = np(1, 31, sd, Array(31) { Fa.nf.format(it + 1) })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(0, dp(16), 0, 0)
            addView(d); addView(m); addView(y)
        }
        AlertDialog.Builder(this).setTitle("تاریخ زنگ").setView(box)
            .setPositiveButton("تأیید") { _, _ ->
                if (gregKey(y.value, m.value, d.value) == null) Toast.makeText(this, "تاریخ نامعتبر است", Toast.LENGTH_SHORT).show()
                else { sy = y.value; sm = m.value; sd = d.value; showDate() }
            }.setNegativeButton("لغو", null).show()
    }

    private fun toneName(s: String): String {
        if (s.isEmpty()) return "پیش‌فرض سیستم"
        return try {
            val u = Uri.parse(s)
            var name: String? = null
            if (u.scheme == "file") {
                name = java.io.File(u.path ?: "").name
            } else if (u.authority == "media" || u.authority == "settings") {
                name = RingtoneManager.getRingtone(this, u)?.getTitle(this)
            } else {
                contentResolver.query(u, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) name = c.getString(i)
                    }
                }
            }
            name ?: "آهنگ انتخابی"
        } catch (e: Exception) { "آهنگ انتخابی" }
    }

    private fun askPath() {
        val et = EditText(this).apply {
            hint = "/storage/emulated/0/Music/song.mp3"; setSingleLine(true)
            setText("/storage/emulated/0/"); setSelection(text.length)
            layoutDirection = View.LAYOUT_DIRECTION_LTR; textDirection = View.TEXT_DIRECTION_LTR
        }
        AlertDialog.Builder(this).setTitle("مسیر فایل صوتی")
            .setMessage("مسیر کامل فایل را وارد کنید (mp3، m4a، wav، ogg، flac، aac، amr، opus و …)")
            .setView(et)
            .setPositiveButton("تأیید") { _, _ ->
                val f = java.io.File(et.text.toString().trim())
                if (f.isFile && f.canRead()) { tone = Uri.fromFile(f).toString(); showTone() }
                else Toast.makeText(this, "فایل پیدا نشد یا اجازهٔ خواندن ندارد", Toast.LENGTH_LONG).show()
            }.setNegativeButton("لغو", null).show()
    }

    private fun pickFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg", "application/x-flac", "video/3gpp", "video/mp4"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 2)
    }

    private fun chooseTone() {
        AlertDialog.Builder(this).setTitle("آهنگ زنگ")
            .setItems(arrayOf("آهنگ‌های سیستم", "انتخاب فایل از گوشی", "وارد کردن مسیر فایل", "پیش‌فرض سیستم")) { _, w ->
                when (w) {
                    0 -> {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        if (tone.isNotEmpty()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(tone))
                        startActivityForResult(i, 1)
                    }
                    1 -> pickFile()
                    2 -> askPath()
                    else -> { tone = ""; showTone() }
                }
            }.show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data == null) return
        if (req == 1) {
            val u: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            tone = u?.toString() ?: ""
        } else if (req == 2) {
            val u = data.data ?: return
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tone = u.toString()
        }
        showTone()
    }

    private fun addAlarm() {
        var dateKey = ""
        var jd = ""
        if (!dailyCb.isChecked) {
            val k = gregKey(sy, sm, sd)
            if (k == null) { Toast.makeText(this, "تاریخ نامعتبر است", Toast.LENGTH_SHORT).show(); return }
            val p = k.split("-").map { it.toInt() }
            val c = java.util.Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2], hh, mm, 0) }
            if (c.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(this, "این زمان گذشته است؛ تاریخ یا ساعت دیگری انتخاب کنید", Toast.LENGTH_LONG).show(); return
            }
            dateKey = k
            jd = Fa.nf.format(sd) + " " + months[sm] + " " + Fa.nf.format(sy)
        }
        val list = Store.load(this)
        val a = Alarm(Store.nextId(this), hh, mm, labelEt.text.toString().trim(), dailyCb.isChecked, true, tone, dateKey, jd)
        list.add(a); Store.save(this, list); Scheduler.schedule(this, a)
        labelEt.setText("")
        val mins = ((Scheduler.nextTime(a) - System.currentTimeMillis()) / 60000).toInt() + 1
        Toast.makeText(this, "زنگ بعد از " + Fa.nf.format(mins / 1440) + " روز و " + Fa.nf.format(mins % 1440 / 60) + " ساعت و " + Fa.nf.format(mins % 60) + " دقیقه به صدا درمی‌آید", Toast.LENGTH_LONG).show()
        refresh()
    }

    private fun refresh() {
        listBox.removeAllViews()
        val l = Store.load(this).sortedBy { it.hour * 60 + it.minute }
        if (l.isEmpty()) listBox.addView(tv(14f).apply { text = "زنگی تنظیم نشده" })
        for (a in l) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(tv(28f, true).apply { text = Fa.two(a.hour) + ":" + Fa.two(a.minute) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
            info.addView(tv(15f).apply { text = a.label.ifEmpty { "زنگ" } })
            info.addView(tv(12f).apply { text = (if (a.daily) "هر روز" else a.jd.ifEmpty { "یک‌بار" }) + " · " + toneName(a.tone) })
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = a.enabled
                setOnCheckedChangeListener { _, on ->
                    val all = Store.load(this@MainActivity)
                    val x = all.find { it.id == a.id } ?: return@setOnCheckedChangeListener
                    x.enabled = on; Store.save(this@MainActivity, all)
                    if (on) Scheduler.schedule(this@MainActivity, x) else Scheduler.cancel(this@MainActivity, x)
                }
            })
            row.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    val all = Store.load(this@MainActivity)
                    all.find { it.id == a.id }?.let { Scheduler.cancel(this@MainActivity, it) }
                    all.removeAll { it.id == a.id }; Store.save(this@MainActivity, all); refresh()
                }
            })
            listBox.addView(row)
        }
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        fun chk(p: String) { if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p) }
        if (Build.VERSION.SDK_INT >= 33) { chk(Manifest.permission.POST_NOTIFICATIONS); chk(Manifest.permission.READ_MEDIA_AUDIO) }
        else chk(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 10) else askFullScreen()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g); askFullScreen()
    }

    private fun askFullScreen() {
        if (Build.VERSION.SDK_INT < 34) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.canUseFullScreenIntent() || Store.flag(this, "fsi")) return
        Store.setFlag(this, "fsi")
        AlertDialog.Builder(this).setTitle("روشن شدن صفحه هنگام زنگ")
            .setMessage("برای اینکه هنگام قفل بودن گوشی صفحه روشن شود، اجازهٔ «اعلان تمام‌صفحه» را فعال کنید.")
            .setPositiveButton("باز کردن تنظیمات") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }.setNegativeButton("بعداً", null).show()
    }
}
