package com.yigit.isiklar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.yigit.isiklar.MainActivity
import com.yigit.isiklar.R
import com.yigit.isiklar.data.LightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ana ekran widget'ı: Hepsini aç / kapat + ilk iki sahne. */
class LightsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_ALL_ON && action != ACTION_ALL_OFF && action != ACTION_SCENE) return

        val pending = goAsync()
        val app = context.applicationContext
        val sceneId = intent.getStringExtra(EXTRA_SCENE)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val controller = LightController.get(app)
                when (action) {
                    ACTION_ALL_ON -> controller.allPower(true)
                    ACTION_ALL_OFF -> controller.allPower(false)
                    ACTION_SCENE -> {
                        val scene = controller.scenes.value.firstOrNull { it.id == sceneId }
                        if (scene != null) controller.applyScene(scene)
                    }
                }
            } catch (e: Exception) {
                val text = e.message ?: "Işıklar: işlem başarısız"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, text, Toast.LENGTH_LONG).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_ALL_ON = "com.yigit.isiklar.widget.ALL_ON"
        private const val ACTION_ALL_OFF = "com.yigit.isiklar.widget.ALL_OFF"
        private const val ACTION_SCENE = "com.yigit.isiklar.widget.SCENE"
        private const val EXTRA_SCENE = "scene_id"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LightsWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lights)

            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.btn_app, openApp)
            views.setOnClickPendingIntent(R.id.btn_on, broadcast(context, ACTION_ALL_ON, null, 1))
            views.setOnClickPendingIntent(R.id.btn_off, broadcast(context, ACTION_ALL_OFF, null, 2))

            val scenes = LightController.get(context).scenes.value
            listOf(R.id.btn_scene1, R.id.btn_scene2).forEachIndexed { i, viewId ->
                val scene = scenes.getOrNull(i)
                if (scene == null) {
                    views.setViewVisibility(viewId, View.GONE)
                } else {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, scene.name)
                    views.setOnClickPendingIntent(viewId, broadcast(context, ACTION_SCENE, scene.id, 10 + i))
                }
            }
            return views
        }

        private fun broadcast(context: Context, action: String, sceneId: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, LightsWidget::class.java).setAction(action)
            if (sceneId != null) intent.putExtra(EXTRA_SCENE, sceneId)
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
