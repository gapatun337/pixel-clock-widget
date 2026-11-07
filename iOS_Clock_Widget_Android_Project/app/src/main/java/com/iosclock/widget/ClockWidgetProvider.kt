package com.iosclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.util.Calendar

class ClockWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK_SECOND = "com.iosclock.widget.ACTION_TICK_SECOND"
        const val PREFS_NAME = "ios_clock_widget_prefs"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        fun updateSingleWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val themeOrd = prefs.getInt("theme", 0)
            val tickOrd = prefs.getInt("tick_style", 0)
            val is24h = prefs.getBoolean("is_24h", true)
            val blink = prefs.getBoolean("blink_colon", false)

            val theme = ClockRenderer.ThemeMode.values().getOrElse(themeOrd) { ClockRenderer.ThemeMode.LIGHT }
            val tickStyle = ClockRenderer.TickStyle.values().getOrElse(tickOrd) { ClockRenderer.TickStyle.IOS_SECTOR }

            val bitmap = ClockRenderer.renderClockBitmap(
                sizePx = 600,
                calendar = Calendar.getInstance(),
                is24Hour = is24h,
                theme = theme,
                tickStyle = tickStyle,
                blinkColon = blink
            )

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.widget_canvas_image, bitmap)

            // По нажатию на виджет открываем системные Часы / Будильник
            val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingClick = PendingIntent.getActivity(
                context,
                appWidgetId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingClick)

            manager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Безопасное планирование следующего тика без сбоев SecurityException.
         * Поддерживает Android 12, 13, 14, 15+ с проверкой canScheduleExactAlarms().
         */
        fun scheduleNextSecondTick(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) return

            // Запускаем фоновый сервис тиканья
            ClockTickService.start(context)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ClockWidgetProvider::class.java).apply {
                action = ACTION_TICK_SECOND
            }
            val pending = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = SystemClock.elapsedRealtime() + 1000L

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                    } else {
                        // Если системное разрешение отключено, используем безопасный вызов set()
                        alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                }
            } catch (e: SecurityException) {
                // Страховочный fallback при строгой политике ОС
                try {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ClockTickService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ClockTickService.stop(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleNextSecondTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK_SECOND,
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAllWidgets(context)
                scheduleNextSecondTick(context)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }
}
