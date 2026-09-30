package com.example.lumeocrtest.mascot

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
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
    private var bubble: SlimeView? = null
    private var panel: View? = null
    private var closingPanel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var screenOn = true
    private var motion: MascotMotion? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Lado e altura relativa em que a gotinha repousa, para manter o lugar ao girar a tela. */
    private var atRight = true
    private var relativeY = 1f / 3
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            if (!screenOn) {
                motion?.stop(); hidePanel()
            } else if (bubble?.visibility == View.VISIBLE) motion?.start()
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
            motion?.stop()
            bubble?.visibility = View.INVISIBLE
            return START_NOT_STICKY
        }
        if (intent?.action == SHOW_AFTER_CAPTURE) {
            val view = bubble
            val lp = params
            if (view != null && lp != null) {
                view.visibility = View.VISIBLE
                // Volta emergindo da borda, numa versão curta da entrada.
                if (screenOn) { motion?.start(); view.post { motion?.enter(restX(lp), atRight, lp.width, short = true) } }
            }
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
    /** Mesmo desenho, decodificado com folga sobre o tamanho exibido (nitidez ao esticar; o original tem 1254 px). */
    private fun mascotBitmap(res: Int, sizePx: Int): android.graphics.Bitmap {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        android.graphics.BitmapFactory.decodeResource(resources, res, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx * 3 / 2) sample *= 2
        return android.graphics.BitmapFactory.decodeResource(resources, res,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })
    }
    /** Posição de repouso (x da janela) na borda atual. */
    private fun restX(lp: WindowManager.LayoutParams) = if (atRight) bounds(lp.width, lp.height)[0] else 0
    private fun animationScale() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    private fun showBubble() {
        // A janela tem uma folga em volta do desenho para a gotinha esticar e inclinar sem ser cortada.
        val drawSize = dp(76)
        val size = dp(92)
        val lp = windowParams(size, size).apply { flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS }
        val limit = bounds(size, size)
        lp.x = limit[0]; lp.y = limit[1] / 3
        atRight = true; relativeY = 1f / 3
        val eyesOpen = mascotBitmap(R.drawable.lume_mascot, drawSize)
        val eyesClosed = mascotBitmap(R.drawable.lume_mascot_blink, drawSize)
        val image = SlimeView(this, eyesOpen, drawSize).apply {
            contentDescription = "Lume. Toque para abrir as opções ou arraste para mover."
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true; isClickable = true
            setOnClickListener { togglePanel() }
        }
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var dragged = false
        var tracker: VelocityTracker? = null
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        image.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    motion?.press()
                    startX = lp.x; startY = lp.y; touchX = event.rawX; touchY = event.rawY; dragged = false
                    tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    val dx = event.rawX-touchX; val dy = event.rawY-touchY
                    if (!dragged && (abs(dx) > slop || abs(dy) > slop)) { dragged = true; hidePanel(); motion?.dragStart() }
                    if (dragged) {
                        // A janela segue o dedo no mesmo quadro; só o corpo reage com atraso.
                        val b = bounds(size,size)
                        lp.x = (startX+dx.toInt()).coerceIn(0,b[0]); lp.y = (startY+dy.toInt()).coerceIn(0,b[1])
                        update(image,lp)
                        tracker?.let { it.computeCurrentVelocity(1000); motion?.dragMove(it.xVelocity, it.yVelocity) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event); tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle(); tracker = null
                    if (!dragged && event.actionMasked == MotionEvent.ACTION_UP) { motion?.release(); view.performClick() }
                    else snapToEdge(vx)
                    true
                }
                else -> false
            }
        }
        // Começa fora da tela, atrás da borda: nenhum quadro da gotinha parada aparece antes da entrada.
        val restAt = lp.x
        if (animationsEnabled()) { lp.x = restAt + size; image.alpha = 0f }
        manager.addView(image, lp)
        bubble = image; params = lp
        motion = MascotMotion(image, mainHandler, resources.displayMetrics.density, eyesOpen, eyesClosed,
            { x -> lp.x = x; update(image, lp) }, ::animationsEnabled, ::animationScale)
        motion?.start()
        // A entrada começa depois do primeiro layout (a gotinha precisa estar na tela para animar).
        image.post { if (bubble === image) motion?.enter(restAt, atRight, size) }
    }
    private fun update(view: View, lp: WindowManager.LayoutParams) {
        try { manager.updateViewLayout(view,lp) } catch (e: Exception) { stopSelf() }
    }
    private fun snapToEdge(vx: Float = 0f) {
        val view = bubble ?: return; val lp = params ?: return
        val limit = bounds(lp.width,lp.height)
        // Um gesto rápido decide o lado; devagar, vale a borda mais próxima.
        val fling = abs(vx) > dp(900)
        val target = if (fling) (if (vx > 0) limit[0] else 0) else MascotTiming.snapTarget(lp.x, limit[0])
        atRight = target > 0; relativeY = if (limit[1] > 0) lp.y.toFloat() / limit[1] else 0f
        if (!animationsEnabled() || !screenOn) { lp.x=target; update(view,lp); motion?.dragEnd(target, target, 0f); return }
        motion?.dragEnd(lp.x, target, vx)
    }
    /** Momento em que o painel fechou por um toque fora dele (o próprio toque na gotinha não deve reabri-lo). */
    private var closedByOutsideAt = 0L

    private fun togglePanel() {
        if (panel != null) { hidePanel(animated=true); motion?.panelClosed(); return }
        if (SystemClock.uptimeMillis() - closedByOutsideAt < 350) return
        removeClosingPanel()
        val lp = params ?: return
        val ui = MascotPanel(this)
        val content = ui.content(
            onRead = {
                hidePanel(); motion?.acknowledged()
                startActivity(Intent(this,CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onOpen = {
                hidePanel(); motion?.acknowledged()
                startActivity(Intent(this,MainActivity::class.java).setAction(OPEN_ANALYSIS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            },
            onClose = { hidePanel(animated=true); motion?.panelClosed() },
            onDisable = { dismissMascot() },
        )
        // Área útil (sem barras do sistema) e tamanho do cartão: largura fixa, altura pelo conteúdo, limitada à tela.
        val area = bounds(0,0)
        val margin = dp(8)
        val cardW = dp(248).coerceAtMost(area[0] - 2*margin - ui.tail)
        content.measure(View.MeasureSpec.makeMeasureSpec(cardW,View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val cardH = content.measuredHeight.coerceAtMost(area[1] - 2*margin - ui.tail)
        // A imagem da gotinha tem margem transparente: a ponta encosta no desenho, não na caixa da imagem.
        val inset = dp(18)
        val place = PanelLayout.place(lp.x + inset, lp.y + inset, lp.width - 2*inset, area[0], area[1], cardW, cardH,
            ui.tail, margin, ui.corner)
        val root = ui.wrap(content, place, cardW, cardH)
        val horizontal = place.side == TailSide.LEFT || place.side == TailSide.RIGHT
        val winW = cardW + (if (horizontal) ui.tail else 0) + 2*ui.shadow
        val winH = cardH + (if (horizontal) 0 else ui.tail) + 2*ui.shadow
        val pp = windowParams(winW, winH).apply {
            // Só o cartão recebe toques; um toque fora dele fecha o painel e segue para o app de baixo.
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            x = (if (place.side == TailSide.LEFT) place.x - ui.tail else place.x) - ui.shadow
            y = (if (place.side == TailSide.TOP) place.y - ui.tail else place.y) - ui.shadow
        }
        root.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                closedByOutsideAt = SystemClock.uptimeMillis(); hidePanel(animated=true); motion?.panelClosed(); true
            } else false
        }
        manager.addView(root,pp); panel=root
        motion?.panelOpened(towardLeft = place.side == TailSide.RIGHT)
        if (animationsEnabled()) {
            val card = root.getChildAt(0)
            card.alpha=0f; card.scaleX=.92f; card.scaleY=.92f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
    }
    private fun dismissMascot() {
        hidePanel(animated=true)
        val view=bubble; val lp=params; val m=motion
        if(view!=null && lp!=null && m!=null && animationsEnabled() && screenOn) {
            // Sai escorregando pela borda em que está.
            m.exit(lp.x, atRight, lp.width) { stopSelf() }
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
            // Volta para a ponta do balão, rápido.
            val card=(view as? ViewGroup)?.getChildAt(0) ?: view
            card.animate().cancel()
            card.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(120).setInterpolator(android.view.animation.AccelerateInterpolator()).withEndAction {
                runCatching { manager.removeView(view) }
                if(closingPanel===view) closingPanel=null
            }.start()
        } else runCatching { manager.removeView(view) }
    }
    private fun animationsEnabled() = getSharedPreferences("lume_preferences",0).getBoolean("animations",true) && Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE,1f) > 0f
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hidePanel(); motion?.cancelWindow()
        val lp=params ?: return; val view=bubble ?: return; val b=bounds(lp.width,lp.height)
        // Ao girar, continua na mesma borda e na mesma altura relativa (sem ficar no meio da tela).
        lp.x=if (atRight) b[0] else 0; lp.y=(relativeY*b[1]).toInt().coerceIn(0,b[1]); update(view,lp)
    }
    override fun onDestroy() {
        mutableActive.value=false
        motion?.stop(); motion=null; mainHandler.removeCallbacksAndMessages(null)
        hidePanel()
        bubble?.let { runCatching { manager.removeView(it) } }
        bubble=null
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }
}
