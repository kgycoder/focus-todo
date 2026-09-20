package com.quicktodo

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var subtitle: TextView
    private lateinit var banner: View
    private lateinit var list: TodoListView
    private lateinit var scroll: ScrollView
    private val listener: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24),
                Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24))
        }

        val title = Ui.text(this, "할 일", 34f, getColor(R.color.text_primary), true)
        column.addView(title)

        subtitle = Ui.text(this, "", 15f, getColor(R.color.text_secondary))
        column.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = Ui.dp(this@MainActivity, 6)
                bottomMargin = Ui.dp(this@MainActivity, 20)
            }
        )

        banner = buildBanner()
        column.addView(
            banner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(this@MainActivity, 20) }
        )

        list = TodoListView(this, true, "할 일이 없어요\n아래에서 새로 추가해보세요", true)
        column.addView(list)

        scroll.addView(column)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val input = InputBar(this) { value ->
            TodoStore.add(this, value)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12))
        root.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    private fun buildBanner(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@MainActivity, getColor(R.color.card), 14f)
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 14),
                Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 14))
        }
        card.addView(Ui.text(this, "어디서든 빠르게 추가", 16f, getColor(R.color.text_primary), true))
        val desc = Ui.text(
            this,
            "접근성 설정에서 ‘할 일 빠른 추가’를 켜면, 접근성 버튼으로 현재 화면 위에서 바로 작성할 수 있어요.",
            13f,
            getColor(R.color.text_secondary)
        ).apply { includeFontPadding = true }
        card.addView(
            desc,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(this@MainActivity, 6) }
        )
        val open = Ui.text(this, "접근성 설정 열기", 15f, getColor(R.color.accent), true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@MainActivity, 10), 0, 0)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        card.addView(open)
        return card
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, QuickAddService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    private fun refresh() {
        list.refresh()
        val remaining = TodoStore.active(this).size
        subtitle.text = if (remaining == 0) "모두 완료했어요" else "남은 할 일 ${remaining}개"
        banner.visibility = if (isAccessibilityEnabled()) View.GONE else View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        TodoStore.addListener(listener)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Notifier.sync(this)
    }

    override fun onStop() {
        TodoStore.removeListener(listener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notifier.sync(this)
    }
}
