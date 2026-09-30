package com.example.lumeocrtest.mascot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** De que lado do cartão fica a ponta do balão (voltada para a gotinha). */
enum class TailSide { LEFT, RIGHT, TOP, BOTTOM }

/** Posição do cartão na área útil da tela e onde a ponta encosta, medida ao longo da borda do cartão. */
data class PanelPlacement(val x: Int, val y: Int, val side: TailSide, val tailAt: Int)

/**
 * Onde o cartão cabe ao lado da gotinha. Coordenadas relativas à área útil (sem barras do sistema).
 * Prefere o lado oposto à borda em que a gotinha está, alinhado à altura dela; se a largura não couber,
 * vai para cima ou para baixo. Nunca sai da área útil.
 */
object PanelLayout {
    fun place(bubbleX: Int, bubbleY: Int, bubble: Int, areaW: Int, areaH: Int, cardW: Int, cardH: Int,
              tail: Int, margin: Int, corner: Int): PanelPlacement {
        val centerX = bubbleX + bubble / 2
        val centerY = bubbleY + bubble / 2
        val onRight = centerX > areaW / 2
        val sideRoom = if (onRight) bubbleX - margin else areaW - (bubbleX + bubble) - margin
        if (sideRoom >= cardW + tail) {
            val x = if (onRight) bubbleX - tail - cardW else bubbleX + bubble + tail
            val y = (centerY - cardH / 2).coerceIn(margin, (areaH - cardH - margin).coerceAtLeast(margin))
            val at = (centerY - y).coerceIn(corner + tail, (cardH - corner - tail).coerceAtLeast(corner + tail))
            return PanelPlacement(x, y, if (onRight) TailSide.RIGHT else TailSide.LEFT, at)
        }
        // Tela estreita: acima ou abaixo da gotinha, onde houver mais espaço.
        val below = areaH - (bubbleY + bubble) >= bubbleY
        val x = (centerX - cardW / 2).coerceIn(margin, (areaW - cardW - margin).coerceAtLeast(margin))
        val y = if (below) (bubbleY + bubble + tail).coerceAtMost((areaH - cardH - margin).coerceAtLeast(margin))
            else (bubbleY - tail - cardH).coerceAtLeast(margin)
        val at = (centerX - x).coerceIn(corner + tail, (cardW - corner - tail).coerceAtLeast(corner + tail))
        return PanelPlacement(x, y, if (below) TailSide.TOP else TailSide.BOTTOM, at)
    }
}

/** Fundo do balão: cartão arredondado com uma ponta pequena e arredondada voltada para a gotinha. */
private class BalloonDrawable(private val side: TailSide, private val tailAt: Float, private val tail: Float,
                              private val corner: Float, fill: Int, stroke: Int, strokeWidth: Float) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke; style = Paint.Style.STROKE; this.strokeWidth = strokeWidth }
    private val path = Path()
    val body = RectF()

    override fun onBoundsChange(b: android.graphics.Rect) {
        body.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        when (side) {
            TailSide.LEFT -> body.left += tail
            TailSide.RIGHT -> body.right -= tail
            TailSide.TOP -> body.top += tail
            TailSide.BOTTOM -> body.bottom -= tail
        }
        path.reset()
        path.addRoundRect(body, corner, corner, Path.Direction.CW)
        val half = tail * 1.05f
        val tip = Path()
        when (side) {
            TailSide.LEFT -> { tip.moveTo(body.left + 1, body.top + tailAt - half); tip.quadTo(body.left - tail * 0.35f, body.top + tailAt - half * 0.2f, body.left - tail + 1, body.top + tailAt); tip.quadTo(body.left - tail * 0.35f, body.top + tailAt + half * 0.2f, body.left + 1, body.top + tailAt + half) }
            TailSide.RIGHT -> { tip.moveTo(body.right - 1, body.top + tailAt - half); tip.quadTo(body.right + tail * 0.35f, body.top + tailAt - half * 0.2f, body.right + tail - 1, body.top + tailAt); tip.quadTo(body.right + tail * 0.35f, body.top + tailAt + half * 0.2f, body.right - 1, body.top + tailAt + half) }
            TailSide.TOP -> { tip.moveTo(body.left + tailAt - half, body.top + 1); tip.quadTo(body.left + tailAt - half * 0.2f, body.top - tail * 0.35f, body.left + tailAt, body.top - tail + 1); tip.quadTo(body.left + tailAt + half * 0.2f, body.top - tail * 0.35f, body.left + tailAt + half, body.top + 1) }
            TailSide.BOTTOM -> { tip.moveTo(body.left + tailAt - half, body.bottom - 1); tip.quadTo(body.left + tailAt - half * 0.2f, body.bottom + tail * 0.35f, body.left + tailAt, body.bottom + tail - 1); tip.quadTo(body.left + tailAt + half * 0.2f, body.bottom + tail * 0.35f, body.left + tailAt + half, body.bottom - 1) }
        }
        tip.close()
        path.op(tip, Path.Op.UNION)
    }

    override fun draw(canvas: Canvas) { canvas.drawPath(path, fillPaint); canvas.drawPath(path, strokePaint) }
    override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha; strokePaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fillPaint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Cartão compacto em forma de balão, ligado à gotinha. Ações por importância: "Ler esta tela" (principal),
 * "Abrir análise" (secundária), fechar (ícone pequeno) e "Desativar mascote" (texto discreto no rodapé).
 */
class MascotPanel(private val context: Context) {
    companion object {
        private val SHEET = Color.WHITE
        private val HAIRLINE = Color.rgb(0xE3, 0xE6, 0xDC)
        private val INK = Color.rgb(0x1F, 0x2A, 0x22)
        private val INK_SOFT = Color.rgb(0x5C, 0x66, 0x5A)
        private val DARK_GREEN = Color.rgb(0x2B, 0x40, 0x35)
        private val LIGHT_GREEN = Color.rgb(0xD9, 0xE5, 0xD0)
        private val MIST = Color.rgb(0xEF, 0xF4, 0xEA)
    }
    private val density = context.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).toInt()
    val tail = dp(11f)
    val corner = dp(20f)
    /** Espaço em volta do cartão, dentro da janela, para a sombra aparecer sem ser cortada. */
    val shadow = dp(10f)

    private fun ripple(color: Int, radius: Float, pressed: Int) = RippleDrawable(ColorStateList.valueOf(pressed),
        GradientDrawable().apply { setColor(color); cornerRadius = radius }, null)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun action(text: String, primary: Boolean, icon: Int?, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        minimumHeight = dp(48f); setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        background = if (primary) ripple(DARK_GREEN, dp(24f).toFloat(), Color.argb(60, 255, 255, 255))
            else ripple(MIST, dp(24f).toFloat(), Color.argb(40, 43, 64, 53))
        isClickable = true; isFocusable = true; contentDescription = text
        setOnClickListener { onClick() }
        icon?.let { addView(ImageView(context).apply {
            setImageResource(it); setColorFilter(if (primary) Color.WHITE else DARK_GREEN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }) }
        addView(label(text, 15f, if (primary) Color.WHITE else DARK_GREEN, bold = true).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
    }

    /** Conteúdo do cartão (sem a ponta). */
    fun content(onRead: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit, onDisable: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(6f), dp(8f), dp(6f))
            // Título e fechar na mesma linha.
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(label("Posso ajudar?", 17f, INK, bold = true).apply {
                    if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageView(context).apply {
                    setImageResource(com.example.lumeocrtest.R.drawable.ic_lume_close); setColorFilter(INK_SOFT)
                    scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(15f), dp(15f), dp(15f), dp(15f))
                    background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 43, 64, 53)), null,
                        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
                    contentDescription = "Fechar opções"; isClickable = true; isFocusable = true
                    setOnClickListener { onClose() }
                }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
            }, LinearLayout.LayoutParams(-1, -2))
            val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(8f), 0) }
            actions.addView(action("Ler esta tela", true, com.example.lumeocrtest.R.drawable.ic_lume_read_screen, onRead), LinearLayout.LayoutParams(-1, -2))
            actions.addView(action("Abrir análise", false, null, onOpen), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            // Rodapé discreto: desativar continua a um toque, sem competir com as ações.
            actions.addView(TextView(context).apply {
                text = "Desativar mascote"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(INK_SOFT)
                gravity = Gravity.CENTER; minHeight = dp(44f); isClickable = true; isFocusable = true
                background = ripple(Color.TRANSPARENT, dp(16f).toFloat(), Color.argb(30, 43, 64, 53))
                setOnClickListener { onDisable() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
            addView(actions, LinearLayout.LayoutParams(-1, -2))
        }

    /** Janela do balão: ponta + cartão + margem de sombra. Com [cardH] menor que o conteúdo, o conteúdo rola. */
    fun wrap(content: View, place: PanelPlacement, cardW: Int, cardH: Int): FrameLayout {
        val horizontal = place.side == TailSide.LEFT || place.side == TailSide.RIGHT
        val w = cardW + (if (horizontal) tail else 0)
        val h = cardH + (if (horizontal) 0 else tail)
        val balloon = BalloonDrawable(place.side, place.tailAt.toFloat(), tail.toFloat(), corner.toFloat(), SHEET, HAIRLINE, density)
        val card = FrameLayout(context).apply {
            background = balloon
            elevation = dp(6f).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val b = balloon.body
                    outline.setRoundRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt(), corner.toFloat())
                    outline.alpha = 0.55f
                }
            }
            setPadding(if (place.side == TailSide.LEFT) tail else 0, if (place.side == TailSide.TOP) tail else 0,
                if (place.side == TailSide.RIGHT) tail else 0, if (place.side == TailSide.BOTTOM) tail else 0)
            addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(content) }, FrameLayout.LayoutParams(cardW, cardH))
            // A ponta é o ponto de onde o balão "sai" nas animações.
            pivotX = when (place.side) { TailSide.LEFT -> 0f; TailSide.RIGHT -> w.toFloat(); else -> place.tailAt.toFloat() }
            pivotY = when (place.side) { TailSide.TOP -> 0f; TailSide.BOTTOM -> h.toFloat(); else -> place.tailAt.toFloat() }
        }
        return FrameLayout(context).apply {
            clipChildren = false; clipToPadding = false
            setPadding(shadow, shadow, shadow, shadow)
            addView(card, FrameLayout.LayoutParams(w, h))
        }
    }
}
