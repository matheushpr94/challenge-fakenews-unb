package com.example.lumeocrtest.mascot

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.lumeocrtest.MainActivity
import com.example.lumeocrtest.R
import com.example.lumeocrtest.capture.CaptureActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** User-enabled overlay only. Does not read or capture any other application's screen. */
class MascotService : Service() {
    companion object {
        private val mutableActive = MutableStateFlow(false)
        val active = mutableActive.asStateFlow()
        const val OPEN_ANALYSIS = "com.example.lumeocrtest.OPEN_ANALYSIS"
        const val STOP = "com.example.lumeocrtest.STOP_MASCOT"
        const val HIDE_FOR_CAPTURE = "com.example.lumeocrtest.HIDE_FOR_CAPTURE"
        const val SHOW_AFTER_CAPTURE = "com.example.lumeocrtest.SHOW_AFTER_CAPTURE"
        private const val CHANNEL = "lume_mascot"
    }
    private lateinit var manager: WindowManager
    private var bubble: ImageView? = null
    private var panel: View? = null
    private var closingPanel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var snap: ValueAnimator? = null
    private var screenOn = true
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            if (!screenOn) {
                snap?.cancel(); bubble?.animate()?.cancel(); hidePanel()
            }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { dismissMascot(); return START_NOT_STICKY }
        if (intent?.action == HIDE_FOR_CAPTURE) {
            hidePanel()
            bubble?.visibility = View.INVISIBLE
            return START_NOT_STICKY
        }
        if (intent?.action == SHOW_AFTER_CAPTURE) {
            bubble?.visibility = View.VISIBLE
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            if (bubble == null) showBubble()
            val notifications = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, "Mascote Lume", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 2, Intent(this, MascotService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_lume_notification).setContentTitle("Lume está ao seu lado")
                .setContentText("Toque na gotinha para abrir a análise.").setOngoing(true).setContentIntent(open)
                .addAction(0, "Desativar", stop).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(24, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(24, notification)
            mutableActive.value = true
        } catch (e: Exception) {
            android.util.Log.e("LumeMascot", "Não foi possível ativar sobreposição", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun windowParams(w: Int, h: Int) = WindowManager.LayoutParams(w, h, overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }

    private fun bounds(width: Int, height: Int): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            // TOP/LEFT coordinates are relative to the available window area, excluding system bars.
            return intArrayOf((metrics.bounds.width() - insets.left - insets.right - width).coerceAtLeast(0),
                (metrics.bounds.height() - insets.top - insets.bottom - height).coerceAtLeast(0))
        }
        val rect = android.graphics.Point()
        @Suppress("DEPRECATION") manager.defaultDisplay.getSize(rect)
        return intArrayOf((rect.x-width).coerceAtLeast(0), (rect.y-height-dp(32)).coerceAtLeast(0))
    }
    private fun showBubble() {
        val size = dp(76)
        val lp = windowParams(size, size)
        val limit = bounds(size, size)
        lp.x = limit[0]; lp.y = limit[1] / 3
        val image = ImageView(this).apply {
            setImageResource(R.drawable.lume_mascot)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Lume. Toque para abrir as opções ou arraste para mover."
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true; isClickable = true
            setOnClickListener { togglePanel() }
        }
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var dragged = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        image.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snap?.cancel(); image.animate().cancel(); hidePanel()
                    startX = lp.x; startY = lp.y; touchX = event.rawX; touchY = event.rawY; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX-touchX; val dy = event.rawY-touchY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        val b = bounds(size,size)
                        lp.x = (startX+dx.toInt()).coerceIn(0,b[0]); lp.y = (startY+dy.toInt()).coerceIn(0,b[1])
                        update(image,lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) view.performClick() else snapToEdge()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { snapToEdge(); true }
                else -> false
            }
        }
        manager.addView(image, lp)
        bubble = image; params = lp
        if (animationsEnabled()) {
            image.alpha = 0f; image.scaleX = .9f; image.scaleY = .9f
            image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        }
    }
    private fun update(view: View, lp: WindowManager.LayoutParams) {
        try { manager.updateViewLayout(view,lp) } catch (e: Exception) { stopSelf() }
    }
    private fun snapToEdge() {
        val view = bubble ?: return; val lp = params ?: return
        val limit = bounds(lp.width,lp.height)
        val target = if (lp.x < limit[0]/2) 0 else limit[0]
        if (!animationsEnabled() || !screenOn) { lp.x=target; update(view,lp); return }
        snap = ValueAnimator.ofInt(lp.x,target).apply {
            duration=300; interpolator=OvershootInterpolator(.65f)
            addUpdateListener { lp.x=(it.animatedValue as Int).coerceIn(0,limit[0]); update(view,lp) }
            start()
        }
    }
    private fun togglePanel() {
        if (panel != null) { hidePanel(animated=true); return }
        removeClosingPanel()
        val lp = params ?: return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),dp(12),dp(16),dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(250,250,245)); cornerRadius=dp(24).toFloat()
            }
            elevation=dp(8).toFloat()
        }
        container.addView(TextView(this).apply {
            text="Uma dúvida? Me chama."; textSize=17f; setTextColor(android.graphics.Color.rgb(43,63,52))
        })
        container.addView(TextView(this).apply {
            text="Escolha o trecho da tela ou abra o Lume para colar texto e importar uma imagem."; textSize=14f
            setPadding(0,dp(8),0,dp(8)); setTextColor(android.graphics.Color.rgb(100,111,99))
        })
        fun button(text: String, action: () -> Unit) {
            container.addView(Button(this).apply {
                this.text=text; isAllCaps=false; minHeight=dp(48)
                setTextColor(android.graphics.Color.WHITE)
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.rgb(43,64,53)); cornerRadius=dp(24).toFloat()
                }
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        button("Abrir análise") {
            hidePanel()
            startActivity(Intent(this,MainActivity::class.java).setAction(OPEN_ANALYSIS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        button("Ler esta tela") {
            hidePanel()
            startActivity(Intent(this,CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        button("Fechar opções") { hidePanel(animated=true) }
        button("Desativar mascote") {
            dismissMascot()
        }
        val available=bounds(0,0)
        val width=dp(268).coerceAtMost(available[0])
        container.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val height=container.measuredHeight.coerceAtMost(available[1])
        val scroll=ScrollView(this).apply { addView(container); isFillViewport=true }
        val b=bounds(width,height)
        val pp=windowParams(width,height)
        pp.x=(lp.x - width + lp.width).coerceIn(0,b[0]); pp.y=(lp.y+lp.height).coerceIn(0,b[1])
        manager.addView(scroll,pp); panel=scroll
        if (animationsEnabled()) { scroll.alpha=0f; scroll.translationY=dp(8).toFloat(); scroll.animate().alpha(1f).translationY(0f).setDuration(180).start() }
    }
    private fun dismissMascot() {
        hidePanel(animated=true)
        val view=bubble
        if(view!=null && animationsEnabled() && screenOn) {
            snap?.cancel()
            view.animate().cancel()
            view.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(160).withEndAction { stopSelf() }.start()
        } else stopSelf()
    }
    private fun removeClosingPanel() {
        closingPanel?.let { it.animate().cancel(); runCatching { manager.removeView(it) } }
        closingPanel=null
    }
    private fun hidePanel(animated: Boolean = false) {
        removeClosingPanel()
        val view=panel ?: return
        panel=null; view.animate().cancel()
        if(animated && animationsEnabled() && screenOn) {
            closingPanel=view
            view.animate().alpha(0f).translationY(dp(8).toFloat()).setDuration(150).withEndAction {
                runCatching { manager.removeView(view) }
                if(closingPanel===view) closingPanel=null
            }.start()
        } else runCatching { manager.removeView(view) }
    }
    private fun animationsEnabled() = getSharedPreferences("lume_preferences",0).getBoolean("animations",true) && Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE,1f) > 0f
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hidePanel(); snap?.cancel()
        val lp=params ?: return; val view=bubble ?: return; val b=bounds(lp.width,lp.height)
        lp.x=lp.x.coerceIn(0,b[0]); lp.y=lp.y.coerceIn(0,b[1]); update(view,lp)
    }
    override fun onDestroy() {
        mutableActive.value=false
        snap?.cancel(); hidePanel()
        bubble?.let { it.animate().cancel(); runCatching { manager.removeView(it) } }
        bubble=null
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }
}
