package com.iosclock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var previewImage: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshPreview()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val prefs = getSharedPreferences(ClockWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 64)
            setBackgroundColor(0xFF121214.toInt())
        }

        val title = TextView(this).apply {
            text = "Виджет Часы 19:45 (iOS Style)"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title)

        previewImage = ImageView(this).apply {
            val lp = LinearLayout.LayoutParams(620, 620)
            lp.topMargin = 48
            lp.bottomMargin = 48
            layoutParams = lp
        }
        root.addView(previewImage)

        val btnTheme = Button(this).apply {
            text = "Переключить тему (Светлая / Тёмная / OLED)"
            setOnClickListener {
                val next = (prefs.getInt("theme", 0) + 1) % 3
                prefs.edit().putInt("theme", next).apply()
                refreshPreview()
                ClockWidgetProvider.updateAllWidgets(this@MainActivity)
            }
        }
        root.addView(btnTheme)

        val btnTicks = Button(this).apply {
            text = "Стиль засечек (Сектор как на фото / Заполнение / Курсор)"
            setOnClickListener {
                val next = (prefs.getInt("tick_style", 0) + 1) % 3
                prefs.edit().putInt("tick_style", next).apply()
                refreshPreview()
                ClockWidgetProvider.updateAllWidgets(this@MainActivity)
            }
        }
        root.addView(btnTicks)

        val btnPin = Button(this).apply {
            text = "Добавить виджет на главный экран"
            setOnClickListener {
                ClockWidgetProvider.updateAllWidgets(this@MainActivity)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(Activity.RESULT_OK, resultValue)
                    finish()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val mgr = getSystemService(AppWidgetManager::class.java)
                    val provider = ComponentName(this@MainActivity, ClockWidgetProvider::class.java)
                    if (mgr.isRequestPinAppWidgetSupported) {
                        mgr.requestPinAppWidget(provider, null, null)
                    }
                }
            }
        }
        root.addView(btnPin)

        setContentView(root)
    }

    private fun refreshPreview() {
        val prefs = getSharedPreferences(ClockWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val theme = ClockRenderer.ThemeMode.values().getOrElse(prefs.getInt("theme", 0)) { ClockRenderer.ThemeMode.LIGHT }
        val tickStyle = ClockRenderer.TickStyle.values().getOrElse(prefs.getInt("tick_style", 0)) { ClockRenderer.TickStyle.IOS_SECTOR }
        val bmp = ClockRenderer.renderClockBitmap(
            sizePx = 600,
            calendar = Calendar.getInstance(),
            is24Hour = prefs.getBoolean("is_24h", true),
            theme = theme,
            tickStyle = tickStyle,
            blinkColon = prefs.getBoolean("blink_colon", false)
        )
        previewImage.setImageBitmap(bmp)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }
}
