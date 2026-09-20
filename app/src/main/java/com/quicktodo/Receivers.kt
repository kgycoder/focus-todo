package com.quicktodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 알림의 원형 완료 버튼 / 알림 스와이프 시 동작 */
class TodoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                if (id > 0) TodoStore.setDone(context, id, true)
            }
            ACTION_RESYNC -> Notifier.sync(context)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.quicktodo.action.COMPLETE"
        const val ACTION_RESYNC = "com.quicktodo.action.RESYNC"
        const val EXTRA_ID = "todo_id"
    }
}

/** 재부팅/앱 업데이트 후 알림 복원 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.sync(context)
    }
}
