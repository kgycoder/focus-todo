package com.quicktodo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

object Ui {
    fun dp(c: Context, v: Float): Int =
        (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(c: Context, v: Int): Int = dp(c, v.toFloat())

    fun rounded(c: Context, color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * c.resources.displayMetrics.density
        }

    fun text(
        c: Context,
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(c).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
    }
}

/** 원형 체크 버튼: 미완료=파란 테두리 원, 완료=파란 채움 + 체크 */
class CheckCircle(context: Context) : View(context) {
    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val stroke = Ui.dp(context, 1.8f).toFloat()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        strokeWidth = Ui.dp(context, 2.2f).toFloat()
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val accent = context.getColor(R.color.accent)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - stroke
        if (checked) {
            fill.color = accent
            canvas.drawCircle(cx, cy, r + stroke / 2f, fill)
            path.reset()
            path.moveTo(cx - r * 0.42f, cy + r * 0.02f)
            path.lineTo(cx - r * 0.1f, cy + r * 0.34f)
            path.lineTo(cx + r * 0.44f, cy - r * 0.28f)
            canvas.drawPath(path, tick)
        } else {
            ring.color = accent
            canvas.drawCircle(cx, cy, r, ring)
        }
    }
}

/** 왼쪽으로 밀어서 삭제하는 행 */
class SwipeRow(
    context: Context,
    private val content: View,
    private val onDelete: () -> Unit
) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        val bg = Ui.text(context, "삭제", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, Ui.dp(context, 24), 0)
            setBackgroundColor(context.getColor(R.color.danger))
        }
        addView(bg, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && dx < -slop && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    content.translationX = (ev.x - downX).coerceIn(-width.toFloat(), 0f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    if (content.translationX < -width * 0.4f) {
                        content.animate().translationX(-width.toFloat()).setDuration(150)
                            .withEndAction { onDelete() }.start()
                    } else {
                        content.animate().translationX(0f).setDuration(180).start()
                    }
                }
            }
        }
        return true
    }
}

class MaxHeightScrollView(context: Context, private val maxPx: Int) : ScrollView(context) {
    init {
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST)
        )
    }
}

/** 알약 모양 입력창 + 파란 원형 추가 버튼 */
class InputBar(
    context: Context,
    private val onSubmit: (String) -> Unit
) : LinearLayout(context) {

    val editText: EditText
    private val addButton: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        editText = EditText(context).apply {
            hint = "새로운 할 일"
            setHintTextColor(context.getColor(R.color.text_secondary))
            setTextColor(context.getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            background = Ui.rounded(context, context.getColor(R.color.field), 22f)
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit()
                    true
                } else false
            }
        }
        addView(
            editText,
            LayoutParams(0, Ui.dp(context, 44), 1f)
        )

        addButton = TextView(context).apply {
            text = "↑"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = Ui.rounded(context, context.getColor(R.color.accent), 22f)
            alpha = 0.35f
            setOnClickListener { submit() }
        }
        addView(
            addButton,
            LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)).apply {
                marginStart = Ui.dp(context, 10)
            }
        )

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                addButton.alpha = if (s.isNullOrBlank()) 0.35f else 1f
            }
        })
    }

    private fun submit() {
        val value = editText.text?.toString().orEmpty().trim()
        if (value.isEmpty()) return
        editText.setText("")
        onSubmit(value)
    }
}
