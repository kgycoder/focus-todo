package com.quicktodo

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * 접근성 버튼을 누르면 현재 화면 위에 "빠른 추가" 오버레이를 띄웁니다.
 * 화면 내용은 읽지 않습니다(canRetrieveWindowContent=false).
 */
class QuickAddService : AccessibilityService() {

    private var overlay: OverlayView? = null
    private var callback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val cb = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    toggleOverlay()
                }
            }
            accessibilityButtonController.registerAccessibilityButtonCallback(cb)
            callback = cb
        } catch (e: Exception) {
            // 접근성 버튼을 지원하지 않는 기기
        }
        Notifier.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideOverlay()
        callback?.let {
            try {
                accessibilityButtonController.unregisterAccessibilityButtonCallback(it)
            } catch (e: Exception) {
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun toggleOverlay() {
        if (overlay != null) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themed = ContextThemeWrapper(this, R.style.Theme_QuickTodo)
        val view = OverlayView(themed) { hideOverlay() }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE

        try {
            wm.addView(view, lp)
            overlay = view
        } catch (e: Exception) {
            overlay = null
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }
}

/** 화면 위에 뜨는 iOS 스타일 카드: 입력창 + 진행 중인 할 일 */
class OverlayView(
    context: Context,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val listView = TodoListView(context, false, "진행 중인 할 일이 없어요", false)
    private val inputBar: InputBar
    private val refresh: () -> Unit = { listView.refresh() }

    init {
        setBackgroundColor(Color.parseColor("#66000000"))
        isFocusableInTouchMode = true
        setOnClickListener { onClose() }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(context, context.getColor(R.color.card), 22f)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 8))
            isClickable = true
            elevation = Ui.dp(context, 12).toFloat()
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            Ui.text(context, "새로운 할 일", 20f, context.getColor(R.color.text_primary), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val close = Ui.text(context, "닫기", 17f, context.getColor(R.color.accent), true).apply {
            setPadding(Ui.dp(context, 12), Ui.dp(context, 6), 0, Ui.dp(context, 6))
            setOnClickListener { onClose() }
        }
        header.addView(close)
        card.addView(header)

        inputBar = InputBar(context) { value -> TodoStore.add(context, value) }
        card.addView(
            inputBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = Ui.dp(context, 12)
                bottomMargin = Ui.dp(context, 6)
            }
        )

        val scroll = MaxHeightScrollView(context, Ui.dp(context, 200))
        scroll.addView(listView)
        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else 0
        val lp = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        lp.setMargins(
            Ui.dp(context, 14), statusBar + Ui.dp(context, 14), Ui.dp(context, 14), 0
        )
        addView(card, lp)

        // 등장 애니메이션
        card.alpha = 0f
        card.translationY = -Ui.dp(context, 24).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TodoStore.addListener(refresh)
        listView.refresh()
        inputBar.editText.requestFocus()
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputBar.editText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    override fun onDetachedFromWindow() {
        TodoStore.removeListener(refresh)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
