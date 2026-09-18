package app.lume.mvp

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.net.Uri
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.util.concurrent.Executors

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var surface: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var bubbleX = 0
    private var bubbleY = 180
    private var busy = false
    private var lastResult: JSONObject? = null
    private var demoIndex = 0
    private var lastDemoMode: String? = null
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); wm = getSystemService(WindowManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("lume", "Mascote Lume", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this,2,Intent(this,OverlayService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,"lume").setSmallIcon(R.drawable.lume_icon).setContentTitle("Lume está por perto")
            .setContentText("Toque no mascote para ler. Nenhuma captura em andamento.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Desativar",stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(21,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(21,notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            "stop" -> { stopService(Intent(this,CaptureService::class.java)); stopSelf() }
            "hide" -> removeSurface()
            "review" -> { lastResult = null; lastDemoMode = null; if (Reading.isDemoExample) demoIndex = 0; review() }
            "error" -> message("Não consegui ler esta tela",intent.getStringExtra("message") ?: "Tente novamente ou compartilhe um print.")
            else -> showBubble()
        }
        return START_NOT_STICKY
    }
    private fun removeSurface() { surface?.let { if (it.isAttachedToWindow) wm.removeView(it) }; surface = null }
    @Suppress("DEPRECATION")
    private fun attach(view: View, width: Int, height: Int, gravity: Int, x: Int = 0, y: Int = 0, focusable: Boolean = false): WindowManager.LayoutParams {
        removeSurface()
        val params = WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT).apply { this.gravity = gravity; this.x = x; this.y = y; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        try { wm.addView(view,params); surface = view } catch (_: Exception) { stopSelf() }
        return params
    }
    @android.annotation.SuppressLint("RtlHardcoded") // Drag coordinates are absolute display pixels, independent of text direction.
    private fun showBubble() {
        val image = Ui.mascot(this,68).apply {
            setPadding(Ui.dp(this@OverlayService,3),0,Ui.dp(this@OverlayService,3),0)
            contentDescription = "Lume. Toque para ler a tela; arraste para mover."
            setOnClickListener { menu() }
        }
        val size = Ui.dp(this,68)
        val metrics = resources.displayMetrics
        if (bubbleX == 0) bubbleX = metrics.widthPixels - size - Ui.dp(this,8)
        val p = attach(image,size,size,Gravity.TOP or Gravity.LEFT,bubbleX,bubbleY)
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragged = false
        image.setOnTouchListener { view,event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = p.x; initialY = p.y; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX-downX) + kotlin.math.abs(event.rawY-downY) > Ui.dp(this,8)) dragged = true
                    p.x = (initialX + event.rawX-downX).toInt().coerceIn(0,(metrics.widthPixels-size).coerceAtLeast(0))
                    p.y = (initialY + event.rawY-downY).toInt().coerceIn(0,(metrics.heightPixels-size-Ui.dp(this,32)).coerceAtLeast(0))
                    if (view.isAttachedToWindow) wm.updateViewLayout(view,p); true
                }
                MotionEvent.ACTION_UP -> { bubbleX = p.x; bubbleY = p.y; if (!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    private fun panel(title: String, subtitle: String, content: (LinearLayout) -> Unit) {
        val scroll = ScrollView(this).apply { background = Ui.shape(Ui.paper,Ui.dp(this@OverlayService,26)); elevation = Ui.dp(this@OverlayService,12).toFloat(); isFillViewport = false }
        val body = Ui.column(this)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Ui.mascot(this,42))
        header.addView(Ui.text(this,"lume",25f,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Ui.quietButton(this,"Fechar") { closeReading() }.apply { layoutParams = LinearLayout.LayoutParams(Ui.dp(this@OverlayService,90),Ui.dp(this@OverlayService,48)) })
        body.addView(header); Ui.gap(this,body)
        body.addView(Ui.text(this,title,23f,true)); Ui.gap(this,body,8)
        if (subtitle.isNotBlank()) body.addView(Ui.text(this,subtitle,14f))
        Ui.gap(this,body); content(body)
        scroll.addView(body)
        val metrics = resources.displayMetrics
        val width = minOf(metrics.widthPixels - Ui.dp(this,20),Ui.dp(this,410))
        val height = minOf((metrics.heightPixels * .72f).toInt(),Ui.dp(this,580))
        attach(scroll,width,height,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,y=Ui.dp(this,32),focusable=true)
    }
    private fun closeReading() { generation++; busy = false; lastResult = null; lastDemoMode = null; Api.cancel(); Reading.clear(); showBubble() }
    private fun menu() {
        panel("O que vamos entender?",if (BuildConfig.DEMO_ONLY) "Modo demonstração · sem internet" else "Eu leio só quando você pede.") { body ->
            body.addView(Ui.button(this,"Ler esta tela") {
                generation++; lastResult = null; lastDemoMode = null; Reading.clear(); removeSurface()
                startActivity(Intent(this,CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            if (BuildConfig.DEMO_ONLY) body.addView(Ui.button(this,"Testar um exemplo",false) {
                Reading.clear(); Reading.isDemoExample = true; Reading.text = DemoData.scenarios[demoIndex].excerpt
                lastDemoMode = null; review()
            })
            if (Reading.image != null || Reading.text.isNotBlank()) body.addView(Ui.button(this,"Retomar leitura",false) {
                if (BuildConfig.DEMO_ONLY) lastDemoMode?.let { demoResult(it) } ?: review()
                else lastResult?.let { result(it) } ?: review()
            })
            body.addView(Ui.quietButton(this,"Abrir Lume") {
                showBubble(); startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            Ui.gap(this,body)
            body.addView(Ui.text(this,if (BuildConfig.DEMO_ONLY) "A captura é real e fica no aparelho. As respostas usam exemplos fictícios, sem analisar sua tela." else "O Android vai pedir autorização para capturar. Depois, você escolhe o que enviar para análise.",14f))
        }
    }
    private fun review() {
        if (Reading.image == null && Reading.text.isBlank()) { showBubble(); return }
        busy = false
        if (BuildConfig.DEMO_ONLY) { demoReview(); return }
        panel("Vamos olhar isso juntos?","Confira o conteúdo antes de enviar.") { body ->
            Reading.image?.let { bitmap -> body.addView(ImageView(this).apply {
                setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Captura que será enviada para análise"
            },LinearLayout.LayoutParams(-1,Ui.dp(this,160))) }
            if (Reading.text.isNotBlank()) body.addView(Ui.text(this,Reading.text.take(1200),14f))
            val question = EditText(this).apply { hint = "Sua dúvida (opcional)"; maxLines = 3; textSize = 16f }
            body.addView(question)
            body.addView(Ui.text(this,"Ao continuar, este conteúdo será enviado ao servidor do piloto e à OpenAI para análise. Na checagem, termos derivados do conteúdo também poderão ser usados em buscas na web.",12f))
            body.addView(Ui.button(this,"Explicar este conteúdo") { analyze("explain",question.text.toString()) })
            body.addView(Ui.button(this,"Checar com fontes",false) { analyze("verify",question.text.toString()) })
            body.addView(Ui.button(this,"Descartar captura",false) { closeReading() })
        }
    }
    private fun analyze(mode: String, question: String) {
        if (busy) return
        if (BuildConfig.DEMO_ONLY) { demoResult(mode); return }
        busy = true; val current = ++generation
        val bitmap = Reading.image; val text = Reading.text
        panel(if(mode == "verify") "Buscando evidências…" else "Lendo com atenção…","Você pode cancelar fechando este painel.") { body ->
            body.addView(ProgressBar(this)); Ui.gap(this,body)
            body.addView(Ui.text(this,"A análise pode levar alguns segundos.",14f))
        }
        io.execute {
            try {
                val result = Api.analyze(this,mode,question.take(1000),bitmap,text)
                handler.post { if (generation == current) { busy = false; result(result) } }
            } catch (error: Exception) {
                handler.post { if (generation == current) { busy = false; message("Não foi possível analisar",error.message ?: "Confira sua conexão e tente novamente.",true) } }
            }
        }
    }
    private fun demoReview() {
        val example = DemoData.scenarios[demoIndex]
        panel("Vamos testar?","Exemplo fictício · nenhuma chamada à IA") { body ->
            body.addView(Ui.text(this,example.title,21f,true))
            Ui.gap(this,body,8)
            body.addView(Ui.text(this,example.excerpt,15f))
            body.addView(Ui.quietButton(this,"Trocar exemplo") { chooseDemo() })
            if (!Reading.isDemoExample) {
                body.addView(Ui.text(this,"Seu conteúdo fica no aparelho. A resposta a seguir usa apenas o exemplo acima.",13f).apply { setTextColor(Ui.muted) })
                Ui.disclosure(this,body,"Ver conteúdo capturado") { details ->
                    Reading.image?.let { bitmap -> details.addView(ImageView(this).apply {
                        setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Captura local. Não será enviada nem analisada nesta demonstração."
                    },LinearLayout.LayoutParams(-1,Ui.dp(this,150))) }
                    if (Reading.text.isNotBlank()) details.addView(Ui.text(this,Reading.text.take(1200),14f))
                }
            }
            body.addView(Ui.button(this,"Ver índice de evidências") { analyze("verify","") })
            body.addView(Ui.quietButton(this,"Explicar exemplo") { analyze("explain","") })
        }
    }
    private fun chooseDemo() {
        panel("Escolha um exemplo","Todos os conteúdos são fictícios.") { body ->
            DemoData.scenarios.forEachIndexed { index, example ->
                body.addView(Ui.button(this,example.name,index == demoIndex) {
                    demoIndex = index; lastDemoMode = null
                    if (Reading.isDemoExample) Reading.text = example.excerpt
                    demoReview()
                })
            }
            body.addView(Ui.quietButton(this,"Voltar") { demoReview() })
        }
    }
    private fun demoResult(mode: String) {
        lastDemoMode = mode
        val example = DemoData.scenarios[demoIndex]
        val verify = mode == "verify"
        panel(if (verify) "Índice de evidências" else "Vamos por partes", "Exemplo fictício · sem consulta à IA") { body ->
            if (verify) {
                example.score?.let { score ->
                    body.addView(Ui.text(this,"$score%",56f).apply { setTextColor(Ui.green) })
                }
                body.addView(Ui.text(this,example.verdict,20f,true))
                Ui.gap(this,body,6)
                body.addView(Ui.text(this,if (example.score == null) "Sem percentual: faltam informações para avaliar." else "Valor demonstrativo. Não é a chance de a notícia ser verdadeira.",12f).apply { setTextColor(Ui.muted) })
                Ui.gap(this,body)
            }
            body.addView(Ui.text(this,example.explanation,16f))
            Ui.disclosure(this,body,"Entender a avaliação") { details ->
                details.addView(Ui.text(this,"Afirmação em análise",13f,true))
                details.addView(Ui.text(this,example.claim,15f))
                if (verify) example.criteria.forEach { criterion ->
                    Ui.gap(this,details)
                    details.addView(Ui.text(this,"${criterion.title} · ${criterion.points}/${criterion.weight} pts",14f,true))
                    details.addView(Ui.text(this,criterion.reason,13f))
                }
                if (verify && example.score != null) {
                    Ui.gap(this,details)
                    details.addView(Ui.text(this,"Somamos quatro critérios: apoio (40), fonte original (20), confirmação (20) e contexto (20). Cada um recebe zero, metade ou todos os pontos. Pesos e faixas ainda não foram validados com notícias reais.",12f))
                }
                Ui.gap(this,details)
                details.addView(Ui.text(this,"Fontes deste exemplo",13f,true))
                details.addView(Ui.text(this,example.sources,13f))
                Ui.gap(this,details)
                details.addView(Ui.text(this,"O que vale conferir",13f,true))
                details.addView(Ui.text(this,example.nextStep,13f))
            }
            if (!verify) body.addView(Ui.button(this,"Ver índice de evidências") { demoResult("verify") })
            body.addView(Ui.button(this,"Continuar navegando",verify) { closeReading() })
            body.addView(Ui.quietButton(this,"Experimentar outro exemplo") { chooseDemo() })
        }
    }
    private fun sourceLink(url: String) = object : URLSpan(url) {
        override fun onClick(widget: View) {
            try {
                showBubble()
                startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { Toast.makeText(this@OverlayService,"Não foi possível abrir esta fonte.",Toast.LENGTH_LONG).show() }
        }
    }
    private fun result(result: JSONObject) {
        lastResult = result
        val citations = result.optJSONArray("citations")
        val text = result.optString("text")
        val linked = SpannableString(text)
        if (citations != null) for (i in 0 until citations.length()) {
            val cite = citations.getJSONObject(i); val start = cite.optInt("start",-1); val end = cite.optInt("end",-1)
            if (start >= 0 && end > start && end <= text.length) linked.setSpan(sourceLink(cite.getString("url")),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        panel(if(result.optString("mode") == "verify") "O que encontrei" else "Vamos por partes",if(result.optBoolean("searched")) "Análise com consulta à web" else "Explicação do conteúdo · sem checagem externa") { body ->
            body.addView(Ui.text(this,text).apply { this.text = linked; movementMethod = LinkMovementMethod.getInstance(); setLinkTextColor(Ui.green) })
            if (citations != null && citations.length() > 0) {
                Ui.gap(this,body); body.addView(Ui.text(this,"Fontes consultadas",16f,true))
                val seen = mutableSetOf<String>()
                for (i in 0 until citations.length()) {
                    val source = citations.getJSONObject(i); val url = source.getString("url")
                    if (seen.add(url)) {
                        val label = source.optString("title",url)
                        val span = SpannableString(label).apply { setSpan(sourceLink(url),0,label.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                        body.addView(Ui.text(this,label,14f).apply { this.text = span; movementMethod = LinkMovementMethod.getInstance(); setPadding(0,Ui.dp(this@OverlayService,10),0,Ui.dp(this@OverlayService,10)); setLinkTextColor(Ui.green) })
                    }
                }
            }
            body.addView(Ui.button(this,"Perguntar sobre este conteúdo",false) { review() })
        }
    }
    private fun message(title: String, value: String, retry: Boolean = false) {
        panel(title,value) { body ->
            if (retry) body.addView(Ui.button(this,"Voltar ao conteúdo") { review() })
            body.addView(Ui.button(this,if (BuildConfig.DEMO_ONLY) "Abrir Lume" else "Abrir configurações",false) {
                showBubble(); startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
    override fun onDestroy() {
        generation++; Api.cancel(); handler.removeCallbacksAndMessages(null); io.shutdownNow(); Reading.clear(); removeSurface(); super.onDestroy()
    }
}
