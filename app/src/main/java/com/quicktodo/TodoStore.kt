package com.quicktodo

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

data class Todo(
    val id: Int,
    val title: String,
    val done: Boolean,
    val createdAt: Long,
    val doneAt: Long
)

/**
 * Todo 저장소. SharedPreferences(JSON)에 즉시(commit) 저장하므로
 * 앱을 종료하거나 기기를 재부팅해도 목록과 완료 상태가 유지됩니다.
 */
object TodoStore {
    private const val PREFS = "todo_store"
    private const val KEY_ITEMS = "items"
    private const val KEY_NEXT_ID = "next_id"

    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach { it() } }
    }

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(c: Context): MutableList<Todo> {
        val raw = prefs(c).getString(KEY_ITEMS, null) ?: return mutableListOf()
        val out = mutableListOf<Todo>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Todo(
                        id = o.getInt("id"),
                        title = o.getString("title"),
                        done = o.optBoolean("done", false),
                        createdAt = o.optLong("createdAt", 0L),
                        doneAt = o.optLong("doneAt", 0L)
                    )
                )
            }
        } catch (e: JSONException) {
            // 손상된 데이터는 무시
        }
        return out
    }

    private fun write(c: Context, list: List<Todo>, nextId: Int? = null) {
        val arr = JSONArray()
        for (t in list) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("done", t.done)
                    .put("createdAt", t.createdAt)
                    .put("doneAt", t.doneAt)
            )
        }
        val editor = prefs(c).edit().putString(KEY_ITEMS, arr.toString())
        if (nextId != null) editor.putInt(KEY_NEXT_ID, nextId)
        editor.commit()
    }

    private fun afterChange(c: Context) {
        Notifier.sync(c)
        notifyChanged()
    }

    fun all(c: Context): List<Todo> = synchronized(lock) { read(c) }

    /** 진행 중인 Todo (작성 순서) */
    fun active(c: Context): List<Todo> =
        all(c).filter { !it.done }.sortedBy { it.createdAt }

    /** 완료된 Todo (최근 완료 순) */
    fun completed(c: Context): List<Todo> =
        all(c).filter { it.done }.sortedByDescending { it.doneAt }

    fun add(c: Context, rawTitle: String): Todo? {
        val title = rawTitle.trim()
        if (title.isEmpty()) return null
        val todo: Todo
        synchronized(lock) {
            val list = read(c)
            val next = prefs(c).getInt(KEY_NEXT_ID, 1)
            todo = Todo(next, title, false, System.currentTimeMillis(), 0L)
            list.add(todo)
            write(c, list, next + 1)
        }
        afterChange(c)
        return todo
    }

    fun setDone(c: Context, id: Int, done: Boolean) {
        var changed = false
        synchronized(lock) {
            val list = read(c)
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0 && list[idx].done != done) {
                list[idx] = list[idx].copy(
                    done = done,
                    doneAt = if (done) System.currentTimeMillis() else 0L
                )
                write(c, list)
                changed = true
            }
        }
        if (changed) afterChange(c)
    }

    fun delete(c: Context, id: Int) {
        synchronized(lock) {
            val list = read(c)
            list.removeAll { it.id == id }
            write(c, list)
        }
        afterChange(c)
    }

    fun clearCompleted(c: Context) {
        synchronized(lock) {
            val list = read(c)
            list.removeAll { it.done }
            write(c, list)
        }
        afterChange(c)
    }
}
