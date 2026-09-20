package com.quicktodo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews

/**
 * 진행 중인 Todo 하나당 알림 하나를 표시합니다.
 * 각 알림 왼쪽에는 원형 완료 버튼이 있고, 누르면 완료 처리됩니다.
 */
object Notifier {
    const val CHANNEL_ID = "todos"
    private const val GROUP = "todo_group"
    private const val SUMMARY_ID = 1
    private const val BASE_ID = 1000

    fun hasPermission(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                c.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = c.getString(R.string.channel_desc)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    /** 저장소 상태에 맞게 알림을 전부 맞춰줍니다(추가/완료/삭제 공통). */
    fun sync(context: Context) {
        val c = context.applicationContext
        if (!hasPermission(c)) return
        try {
            ensureChannel(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            val active = TodoStore.active(c)
            val wanted = active.map { BASE_ID + it.id }.toSet()

            for (sbn in nm.activeNotifications) {
                if (sbn.id >= BASE_ID && sbn.id !in wanted) nm.cancel(sbn.id)
            }
            if (active.isEmpty()) {
                nm.cancel(SUMMARY_ID)
                return
            }
            for (t in active) nm.notify(BASE_ID + t.id, buildTodo(c, t))
            nm.notify(SUMMARY_ID, buildSummary(c, active.size))
        } catch (e: SecurityException) {
            // 알림 권한이 없는 경우 무시
        }
    }

    private fun openAppIntent(c: Context): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun resyncIntent(c: Context): PendingIntent {
        val i = Intent(c, TodoActionReceiver::class.java)
            .setAction(TodoActionReceiver.ACTION_RESYNC)
        return PendingIntent.getBroadcast(
            c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun completeIntent(c: Context, id: Int): PendingIntent {
        val i = Intent(c, TodoActionReceiver::class.java)
            .setAction(TodoActionReceiver.ACTION_COMPLETE)
            .putExtra(TodoActionReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id + 10, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildTodo(c: Context, t: Todo): Notification {
        val views = RemoteViews(c.packageName, R.layout.notification_todo)
        views.setTextViewText(R.id.todo_title, t.title)
        views.setOnClickPendingIntent(R.id.todo_done, completeIntent(c, t.id))

        return Notification.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_todo)
            .setColor(c.getColor(R.color.accent))
            .setContentTitle(t.title)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setContentIntent(openAppIntent(c))
            .setDeleteIntent(resyncIntent(c))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setGroup(GROUP)
            .build()
    }

    private fun buildSummary(c: Context, count: Int): Notification {
        return Notification.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_todo)
            .setColor(c.getColor(R.color.accent))
            .setContentTitle("할 일 ${count}개")
            .setContentText("완료하지 않은 할 일이 있어요")
            .setContentIntent(openAppIntent(c))
            .setDeleteIntent(resyncIntent(c))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .build()
    }
}
