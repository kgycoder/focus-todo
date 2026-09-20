package com.quicktodo

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 앱 본문과 오버레이에서 함께 쓰는 목록 뷰.
 * - inset 카드(iOS 그룹 리스트) 스타일
 * - 원형 버튼: 완료/되돌리기, 왼쪽으로 밀기: 삭제
 */
class TodoListView(
    context: Context,
    private val showCompleted: Boolean,
    private val emptyMessage: String,
    private val useCardBackground: Boolean
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    fun refresh() {
        removeAllViews()
        val active = TodoStore.active(context)
        val done = if (showCompleted) TodoStore.completed(context) else emptyList()

        if (active.isEmpty() && done.isEmpty()) {
            addView(emptyView())
            return
        }
        if (active.isNotEmpty()) addView(buildCard(active))
        if (done.isNotEmpty()) {
            addView(sectionHeader())
            addView(buildCard(done))
        }
    }

    private fun emptyView(): View {
        val v = Ui.text(context, emptyMessage, 16f, context.getColor(R.color.text_secondary))
        v.gravity = Gravity.CENTER
        v.setPadding(0, Ui.dp(context, 36), 0, Ui.dp(context, 36))
        v.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        return v
    }

    private fun sectionHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), Ui.dp(context, 8))
        }
        val title = Ui.text(context, "완료됨", 13f, context.getColor(R.color.text_secondary), true)
        row.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val clear = Ui.text(context, "모두 삭제", 14f, context.getColor(R.color.accent))
        clear.setPadding(Ui.dp(context, 8), Ui.dp(context, 4), 0, Ui.dp(context, 4))
        clear.setOnClickListener { TodoStore.clearCompleted(context) }
        row.addView(clear, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun buildCard(items: List<Todo>): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            if (useCardBackground) {
                background = Ui.rounded(context, context.getColor(R.color.card), 14f)
                clipToOutline = true
            }
        }
        items.forEachIndexed { index, t ->
            if (index > 0) card.addView(separator())
            card.addView(
                buildRow(t),
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
        }
        return card
    }

    private fun separator(): View {
        val v = View(context)
        v.setBackgroundColor(context.getColor(R.color.separator))
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, maxOf(1, Ui.dp(context, 0.5f)))
        lp.marginStart = Ui.dp(context, 56)
        v.layoutParams = lp
        return v
    }

    private fun buildRow(t: Todo): View {
        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(context.getColor(R.color.card))
            setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 4))
            minimumHeight = Ui.dp(context, 52)
        }

        val circle = CheckCircle(context).apply { checked = t.done }
        val hit = FrameLayout(context)
        hit.addView(
            circle,
            FrameLayout.LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26), Gravity.CENTER)
        )
        hit.setOnClickListener {
            // 살짝 채워지는 모습을 보여준 뒤 상태를 저장합니다.
            circle.checked = !t.done
            circle.postDelayed({ TodoStore.setDone(context, t.id, !t.done) }, 200)
        }
        content.addView(hit, LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)))

        val title = Ui.text(
            context,
            t.title,
            17f,
            context.getColor(if (t.done) R.color.text_secondary else R.color.text_primary)
        ).apply {
            includeFontPadding = true
            if (t.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        val tlp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = Ui.dp(context, 4)
        tlp.topMargin = Ui.dp(context, 8)
        tlp.bottomMargin = Ui.dp(context, 8)
        content.addView(title, tlp)

        return SwipeRow(context, content) { TodoStore.delete(context, t.id) }
    }
}
