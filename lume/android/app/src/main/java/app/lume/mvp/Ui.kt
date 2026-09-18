package app.lume.mvp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*

class MascotView(c: Context) : ImageView(c) {
    override fun performClick(): Boolean = super.performClick()
}

object Ui {
    val paper = Color.rgb(247, 248, 242)
    val ink = Color.rgb(32, 43, 36)
    val green = Color.rgb(37, 71, 56)
    val mint = Color.rgb(215, 234, 199)
    val muted = Color.rgb(104, 114, 99)
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun shape(color: Int, radius: Int = 22) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    fun column(c: Context, padding: Int = 20) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, padding), dp(c, padding), dp(c, padding), dp(c, padding))
    }
    fun text(c: Context, value: String, size: Float = 16f, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }
    fun gap(c: Context, parent: LinearLayout, height: Int = 12) { parent.addView(View(c), LinearLayout.LayoutParams(1, dp(c,height))) }
    fun button(c: Context, label: String, primary: Boolean = true, action: () -> Unit) = Button(c).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) Color.WHITE else green)
        background = shape(if (primary) green else mint, dp(c,16))
        minHeight = dp(c,48)
        layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(c,8) }
        setPadding(dp(c,14), dp(c,10), dp(c,14), dp(c,10))
        setOnClickListener { action() }
    }
    fun quietButton(c: Context, label: String, action: () -> Unit) = button(c,label,false,action).apply {
        background = shape(Color.TRANSPARENT,dp(c,12))
        textSize = 14f
    }
    fun disclosure(c: Context, parent: LinearLayout, label: String, fill: (LinearLayout) -> Unit) {
        val details = column(c,0).apply { visibility = View.GONE }
        fill(details)
        lateinit var toggle: Button
        toggle = quietButton(c,"$label +") {
            val expanded = details.visibility != View.VISIBLE
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            toggle.text = "$label ${if (expanded) "−" else "+"}"
            toggle.contentDescription = "$label. ${if (expanded) "Expandido" else "Recolhido"}"
        }
        toggle.contentDescription = "$label. Recolhido"
        parent.addView(toggle); parent.addView(details)
    }
    fun mascot(c: Context, size: Int) = MascotView(c).apply {
        setImageResource(R.drawable.lume_mascot); contentDescription = "Lume, seu assistente de leitura"
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(c,size), dp(c,size))
    }
}
