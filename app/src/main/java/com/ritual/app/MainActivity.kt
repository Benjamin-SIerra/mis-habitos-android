package com.ritual.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/** Interfaz y funciones de hábitos incluidas en la aplicación. */
class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(255, 248, 240)
        window.navigationBarColor = Color.rgb(255, 248, 240)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(255, 250, 245))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // Conserva perfil, agenda y avances en el teléfono.
            settings.allowFileAccess = true // Solo carga el HTML de assets incluido en esta APK.
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            addJavascriptInterface(HabitosBridge(this@MainActivity), "HabitosAndroid")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    // La app funciona sin red y no abre páginas externas desde este contenedor.
                    return request?.url?.scheme != "file"
                }
            }
            loadUrl("file:///android_asset/HabitosApp.html")
        }
        setContentView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) ReminderScheduler.resyncSaved(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            requestExactAlarmAccess()
            ReminderScheduler.resyncSaved(this)
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with API 26+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXACT_ALARM) ReminderScheduler.resyncSaved(this)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("HabitosAndroid")
            webView.destroy()
        }
        super.onDestroy()
    }

    internal fun requestReminderPermissions() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            } else {
                requestExactAlarmAccess()
            }
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarms = getSystemService(AlarmManager::class.java)
            if (!alarms.canScheduleExactAlarms()) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                )
                runCatching { startActivityForResult(intent, REQUEST_EXACT_ALARM) }
            }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 701
        private const val REQUEST_EXACT_ALARM = 702
    }
}

/** Puente mínimo entre la interfaz existente y los permisos/avisos del sistema Android. */
private class HabitosBridge(private val activity: MainActivity) {
    private val database by lazy { LocalStateDatabase(activity.applicationContext) }

    @JavascriptInterface
    fun loadState(): String = database.loadState() ?: ""

    @JavascriptInterface
    fun saveState(stateJson: String) { database.saveState(stateJson) }

    @JavascriptInterface
    fun clearState() { database.clearState() }

    @JavascriptInterface
    fun syncReminders(habitsJson: String) {
        ReminderScheduler.sync(activity.applicationContext, habitsJson)
    }

    @JavascriptInterface
    fun requestReminderPermissions() {
        activity.requestReminderPermissions()
    }
}

/** SQLite local: una fila contiene el estado completo del perfil y se actualiza de forma atómica. */
private class LocalStateDatabase(context: Context) : SQLiteOpenHelper(context, "mis_habitos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE app_state (id INTEGER PRIMARY KEY CHECK(id = 1), payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
    @Synchronized fun loadState(): String? = readableDatabase.rawQuery("SELECT payload FROM app_state WHERE id = 1", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    @Synchronized fun saveState(payload: String) {
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply { put("id", 1); put("payload", payload) }
            writableDatabase.insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun clearState() { writableDatabase.delete("app_state", "id = ?", arrayOf("1")) }
}

/** Programa un aviso por día elegido y guarda la configuración para el reinicio del teléfono. */
private object ReminderScheduler {
    private const val PREFS = "ritual_reminders"
    private const val KEY_HABITS = "habits_json"
    private const val KEY_ALARMS = "alarm_uris"
    private const val ACTION_REMINDER = "com.ritual.app.ACTION_REMINDER"
    private const val ACTION_MOTIVATION = "com.ritual.app.ACTION_MOTIVATION"
    private val motivationalQuotes = listOf(
        "“No son las cosas las que inquietan a las personas, sino sus juicios sobre ellas.” — Epicteto, Enquiridión, §5",
        "“El alma se tiñe con los pensamientos.” — Marco Aurelio, Meditaciones, V.16",
        "“No es que tengamos poco tiempo, sino que perdemos mucho.” — Séneca, Sobre la brevedad de la vida, 1.3",
    )

    fun sync(context: Context, habitsJson: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousUris = prefs.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toSet()
        previousUris.forEach { cancel(context, it) }

        val alarmUris = mutableSetOf<String>()
        val payload = runCatching { JSONObject(habitsJson) }.getOrNull()
        val habits = payload?.optJSONArray("habits")
            ?: runCatching { JSONArray(habitsJson) }.getOrElse { JSONArray() }
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayIndex = today.dayOfWeek.value - 1
        val completedToday = payload?.optJSONObject("completed")?.optJSONArray(today.toString())
        val completedIds = buildSet {
            if (completedToday != null) {
                for (index in 0 until completedToday.length()) add(completedToday.optString(index))
            }
        }
        for (i in 0 until habits.length()) {
            val habit = habits.optJSONObject(i) ?: continue
            if (!habit.optBoolean("reminder", false)) continue

            val habitId = habit.optString("id", "habit-$i")
            val habitName = habit.optString("name", "tu hábito")
            val startTime = habit.optString("start", "")
            if (startTime.isBlank()) continue
            val lead = habit.optInt("reminderLead", 10).coerceIn(1, 180)
            val days = habit.optJSONArray("days") ?: continue

            for (dayPosition in 0 until days.length()) {
                val dayIndex = days.optInt(dayPosition, -1)
                if (dayIndex !in 0..6) continue
                val uri = reminderUri(habitId, dayIndex)
                val baseTrigger = runCatching {
                    ReminderTime.nextTriggerMillis(
                        nowMillis, dayIndex, startTime, lead, zone,
                    )
                }.getOrNull() ?: continue
                val triggerAt = ReminderTime.skipCompletedOccurrenceIfToday(
                    baseTrigger,
                    nowMillis,
                    dayIndex,
                    dayIndex == todayIndex && habitId in completedIds,
                    zone,
                )

                val intent = reminderIntent(context, uri).apply {
                    putExtra("habit_name", habitName)
                    putExtra("start_time", startTime)
                    putExtra("lead_minutes", lead)
                }
                val alarmIntent = PendingIntent.getBroadcast(
                    context,
                    uri.hashCode() and Int.MAX_VALUE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setAlarm(context, triggerAt, alarmIntent)
                alarmUris += uri
            }
        }

        scheduleMotivational(context, payload, nowMillis, alarmUris)

        prefs.edit()
            .putString(KEY_HABITS, habitsJson)
            .putStringSet(KEY_ALARMS, alarmUris)
            .apply()
    }

    private fun scheduleMotivational(context: Context, payload: JSONObject?, nowMillis: Long, alarmUris: MutableSet<String>) {
        val settings = payload?.optJSONObject("motivation") ?: return
        if (!settings.optBoolean("enabled", false)) return
        val times = settings.optJSONArray("times") ?: return
        for (index in 0 until minOf(times.length(), 3)) {
            val time = times.optString(index, "")
            val parts = time.split(":")
            if (parts.size != 2) continue
            val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: continue
            val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: continue
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            val uri = "ritual://motivation/$index"
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_MOTIVATION).setData(Uri.parse(uri))
                .putExtra("motivation_quote", motivationalQuotes[index % motivationalQuotes.size])
            val pending = PendingIntent.getBroadcast(context, uri.hashCode() and Int.MAX_VALUE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setAlarm(context, calendar.timeInMillis, pending)
            alarmUris += uri
        }
    }

    fun resyncSaved(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HABITS, "[]") ?: "[]"
        sync(context, saved)
    }

    private fun setAlarm(context: Context, triggerAt: Long, alarmIntent: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
                } else {
                    // Sin acceso especial, Android conserva un aviso aproximado en segundo plano.
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
                }
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
            }
        } else {
            alarms.setExact(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
        }
    }

    private fun cancel(context: Context, uri: String) {
        val intent = if (uri.startsWith("ritual://motivation/")) {
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_MOTIVATION).setData(Uri.parse(uri))
        } else reminderIntent(context, uri)
        val pending = PendingIntent.getBroadcast(
            context,
            uri.hashCode() and Int.MAX_VALUE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun reminderUri(habitId: String, dayIndex: Int) =
        "ritual://reminder/${Uri.encode(habitId)}/$dayIndex"

    private fun reminderIntent(context: Context, uri: String) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData(Uri.parse(uri))
}

/** Muestra la notificación y prepara la repetición semanal siguiente del mismo plan local. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isMotivation = intent.action == "com.ritual.app.ACTION_MOTIVATION"
        val name = intent.getStringExtra("habit_name") ?: "tu hábito"
        val start = intent.getStringExtra("start_time") ?: ""
        val lead = intent.getIntExtra("lead_minutes", 10)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "ritual_habit_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Recordatorios de hábitos", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Avisos programados para tus hábitos" },
            )
        }

        val canNotify = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val openApp = PendingIntent.getActivity(
                context,
                9001,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = if (isMotivation) intent.getStringExtra("motivation_quote") ?: "Un paso a la vez: hoy puedes comenzar de nuevo."
                else "En $lead min: $name. Tu horario empieza a las $start."
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (isMotivation) "Una idea para acompañarte" else "Mis Hábitos")
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setColor(Color.rgb(173, 98, 77))
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            manager.notify((intent.dataString ?: name).hashCode(), notification)
        }

        ReminderScheduler.resyncSaved(context)
    }
}

/** Android borra las alarmas al reiniciar; este receptor vuelve a registrarlas desde datos locales. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderScheduler.resyncSaved(context)
        }
    }
}
