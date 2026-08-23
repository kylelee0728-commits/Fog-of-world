package com.fogofworld.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fogofworld.MainActivity
import com.fogofworld.R
import com.fogofworld.data.FogStore
import com.fogofworld.data.Format
import com.fogofworld.data.LocaleHelper
import com.fogofworld.data.Settings
import kotlin.math.roundToInt

/**
 * 首頁小工具：今天走了多少，以及每日目標的進度。
 *
 * 小工具的行程與 App 是分開的，所以要自己先 FogStore.init()；
 * 語系也要自己包一層，否則會用系統語言而不是 App 裡選的那個。
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val ctx = LocaleHelper.wrap(context)
        FogStore.init(ctx)
        ids.forEach { manager.updateAppWidget(it, buildViews(ctx)) }
    }

    companion object {
        /** 資料變動時叫一次，讓桌面上的小工具跟著更新 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val ctx = LocaleHelper.wrap(context)
            val views = buildViews(ctx)
            ids.forEach { runCatching { manager.updateAppWidget(it, views) } }
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val today = FogStore.todayDistance()
            val goal = Settings.dailyGoal(ctx)

            return RemoteViews(ctx.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widgetLabel, ctx.getString(R.string.widget_title))
                setTextViewText(
                    R.id.widgetDistance,
                    Format.distanceValue(ctx, today) + " " + Format.distanceUnit(ctx),
                )
                if (goal > 0) {
                    val pct = ((today / goal) * 100).roundToInt().coerceIn(0, 100)
                    setProgressBar(R.id.widgetProgress, 100, pct, false)
                    setTextViewText(
                        R.id.widgetGoal,
                        Format.distance(ctx, today) + " / " + Format.distance(ctx, goal.toDouble()),
                    )
                } else {
                    setProgressBar(R.id.widgetProgress, 100, 0, false)
                    setTextViewText(R.id.widgetGoal, ctx.getString(R.string.widget_no_goal))
                }
                setOnClickPendingIntent(R.id.widgetDistance, openApp(ctx))
                setOnClickPendingIntent(R.id.widgetLabel, openApp(ctx))
            }
        }

        private fun openApp(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
