package app.lume.mvp

import android.animation.ValueAnimator
import android.content.*
import android.database.ContentObserver
import android.graphics.Canvas
import android.os.*
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import kotlin.math.sin
import kotlin.random.Random

/** Two-frame character animation. Drawing never changes the overlay's touch/drag bounds. */
class MascotView(c: Context) : ImageView(c) {
    private val preferences = c.getSharedPreferences("lume", Context.MODE_PRIVATE)
    private val power = c.getSystemService(PowerManager::class.java)
    private val closedFrame = c.getDrawable(R.drawable.lume_mascot_blink)!!.mutate()
    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var blink = 0f
    private var blinkAt = 0L
    private var greetingAt = 0L
    private var dragAngle = 0f
    private var dragging = false
    private var initialized = false
    private var listening = false
    var thinking = false
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "animate_mascot") refreshMotion()
    }
    private val settingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshMotion()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshMotion()
    }
    init { initialized = true }

    private fun motionAllowed() = preferences.getBoolean("animate_mascot", true) &&
        ValueAnimator.areAnimatorsEnabled() && power.isInteractive && !power.isPowerSaveMode

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingObserver)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(powerReceiver, filter)
        listening = true
        refreshMotion()
    }
    override fun onDetachedFromWindow() {
        stopMotion()
        if (listening) {
            preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
            context.contentResolver.unregisterContentObserver(settingObserver)
            context.unregisterReceiver(powerReceiver)
            listening = false
        }
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (initialized) refreshMotion()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (initialized) refreshMotion()
    }
    private fun refreshMotion() {
        if (!isAttachedToWindow || windowVisibility != View.VISIBLE || !isShown || !motionAllowed()) {
            stopMotion(); return
        }
        if (animator != null) return
        blinkAt = SystemClock.uptimeMillis() + Random.nextLong(3600, 7200)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { value ->
                phase = value.animatedValue as Float
                val now = SystemClock.uptimeMillis()
                val elapsed = now - blinkAt
                blink = if (dragging || elapsed < 0) 0f else when {
                    elapsed < 55 -> elapsed / 55f
                    elapsed < 140 -> 1f
                    elapsed < 220 -> 1f - (elapsed - 140) / 80f
                    else -> { blinkAt = now + Random.nextLong(3600, 7200); 0f }
                }
                invalidate()
            }
            start()
        }
    }
    private fun stopMotion() {
        animator?.cancel(); animator = null
        phase = 0f; blink = 0f; greetingAt = 0L; dragAngle = 0f
        invalidate()
    }
    fun greet() { if (motionAllowed()) { greetingAt = SystemClock.uptimeMillis(); invalidate() } }
    fun drag(offset: Float?) {
        dragging = offset != null
        dragAngle = if (offset == null) 0f else (offset / (resources.displayMetrics.density * 8f)).coerceIn(-8f, 8f)
        invalidate()
    }
    override fun performClick(): Boolean { greet(); return super.performClick() }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        if (animator != null) {
            val wave = sin(phase * Math.PI * 2).toFloat()
            val greeting = if (greetingAt == 0L) 1f else ((SystemClock.uptimeMillis() - greetingAt) / 650f).coerceIn(0f, 1f)
            val nod = if (greeting < 1f) sin(greeting * Math.PI * 2).toFloat() * 5f else 0f
            val tilt = if (dragging) dragAngle else nod + wave * if (thinking) 3f else .6f
            canvas.translate(0f, if (dragging) 0f else -minOf(width, height) * .008f * (wave + 1f))
            canvas.rotate(tilt, width / 2f, height * .7f)
            if (!dragging) canvas.scale(1f + wave * .004f, 1f - wave * .006f, width / 2f, height * .8f)
        }
        super.onDraw(canvas)
        if (blink > 0f && drawable != null) {
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
            canvas.concat(imageMatrix)
            closedFrame.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            closedFrame.alpha = (blink * 255).toInt().coerceIn(0, 255)
            closedFrame.draw(canvas)
        }
        canvas.restoreToCount(save)
    }
}
