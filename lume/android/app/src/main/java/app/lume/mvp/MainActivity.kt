package app.lume.mvp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var pendingStart = false
    private var pendingShared = false
    private val io = Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val body = Ui.column(this,24)
        body.setBackgroundColor(Ui.paper)
        body.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(Ui.dp(this,24), insets.systemWindowInsetTop + Ui.dp(this,24), Ui.dp(this,24), insets.systemWindowInsetBottom + Ui.dp(this,24)); insets
        }
        scroll.addView(body); setContentView(scroll)
        body.addView(Ui.text(this,"lume",46f,true))
        body.addView(Ui.text(this,if (BuildConfig.DEMO_ONLY) "DEMONSTRAÇÃO · SEM INTERNET" else "PILOTO CONECTADO",12f).apply { setTextColor(Ui.muted) })
        body.addView(Ui.mascot(this,180).apply { layoutParams = LinearLayout.LayoutParams(-1,Ui.dp(this@MainActivity,200)) })
        body.addView(Ui.text(this,"Uma dúvida? Me chama.",25f,true))
        Ui.gap(this,body)
        body.addView(Ui.text(this,if (BuildConfig.DEMO_ONLY) "Teste o mascote e explore respostas de exemplo. Nenhuma chamada à IA, nenhum consumo de tokens." else "Ative o mascote, abra uma notícia e toque nele. Você revisa a captura antes de enviá-la para análise."))
        body.addView(Ui.button(this,"Ativar mascote") { enable() })
        if (BuildConfig.DEMO_ONLY) body.addView(Ui.button(this,"Testar um exemplo",false) {
            Reading.clear(); Reading.isDemoExample = true; Reading.text = DemoData.scenarios[0].excerpt
            pendingShared = true; enable()
        })
        Ui.gap(this,body,24)
        body.addView(Ui.text(this,if (BuildConfig.DEMO_ONLY) "Capturas ficam no aparelho. As respostas são fictícias e não analisam a notícia da sua tela." else "Você escolhe quando capturar e enviar. A análise usa um provedor externo de IA.",13f).apply { setTextColor(Ui.muted) })
        Ui.disclosure(this,body,"Mais opções") { options ->
            options.addView(Ui.quietButton(this,"Colar texto ou link") { paste() })
            if (!BuildConfig.DEMO_ONLY) options.addView(Ui.quietButton(this,"Configurar conexão") { settings() })
            options.addView(Ui.quietButton(this,"Desativar mascote") {
                stopService(Intent(this,OverlayService::class.java)); stopService(Intent(this,CaptureService::class.java)); Reading.clear()
                Toast.makeText(this,"Lume desativado",Toast.LENGTH_SHORT).show()
            })
            options.addView(Ui.text(this,"Versão ${BuildConfig.VERSION_NAME} · Android 8+",12f))
        }
        if (Intent.ACTION_SEND == intent.action) receiveShared(intent)
    }
    private fun enable() {
        pendingStart = true
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            Toast.makeText(this,"Permita que o Lume apareça sobre outros apps.",Toast.LENGTH_LONG).show()
        } else startMascot()
    }
    override fun onResume() { super.onResume(); if (pendingStart && Settings.canDrawOverlays(this)) startMascot() }
    private fun startMascot() {
        pendingStart = false
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),7)
        }
        startForegroundService(Intent(this,OverlayService::class.java).setAction(if (pendingShared) "review" else "show"))
        pendingShared = false
    }
    private fun paste() {
        val input = EditText(this).apply { hint = "Cole o trecho ou o link"; minLines = 4; maxLines = 10; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        AlertDialog.Builder(this).setTitle("O que vamos ler?").setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Continuar") { _,_ ->
                if (input.text.isNotBlank()) { Reading.clear(); Reading.text = input.text.toString().take(24000); pendingShared = true; enable() }
            }.show()
    }
    @Suppress("DEPRECATION")
    private fun receiveShared(source: Intent) {
        Reading.clear()
        if (source.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= 33) source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri == null) return
            io.execute {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    var sample = 1
                    while (maxOf(bounds.outWidth,bounds.outHeight) / sample > 2000) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) } ?: error("Imagem inválida")
                    runOnUiThread { if (!isDestroyed) { Reading.image = Reading.fit(bitmap); pendingShared = true; enable() } }
                } catch (_: Exception) { runOnUiThread { Toast.makeText(this,"Não foi possível abrir a imagem.",Toast.LENGTH_LONG).show() } }
            }
        } else {
            Reading.text = source.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(24000)
            if (Reading.text.isNotBlank()) { pendingShared = true; enable() }
        }
    }
    private fun settings() {
        val prefs = getSharedPreferences("lume",MODE_PRIVATE)
        val layout = Ui.column(this)
        layout.addView(Ui.text(this,"Endereço do servidor",14f,true))
        val endpoint = EditText(this).apply { setText(prefs.getString("endpoint","http://127.0.0.1:8787")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        layout.addView(endpoint)
        layout.addView(Ui.text(this,"Token do piloto",14f,true))
        val token = EditText(this).apply { setText(prefs.getString("token","")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(token)
        layout.addView(Ui.text(this,"A chave do provedor de IA fica apenas no servidor. Peça o token do piloto a quem configurou a conexão.",13f))
        val dialog = AlertDialog.Builder(this).setTitle("Conexão do piloto").setView(layout).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = endpoint.text.toString().trim()
            val uri = Uri.parse(value)
            if (uri.host.isNullOrBlank() || (uri.scheme != "https" && !(BuildConfig.DEBUG && uri.scheme == "http"))) { endpoint.error = "Use um endereço HTTPS válido"; return@setOnClickListener }
            if (token.text.trim().length < 32) { token.error = "O token precisa ter pelo menos 32 caracteres"; return@setOnClickListener }
            prefs.edit().putString("endpoint",value).putString("token",token.text.toString().trim()).apply(); dialog.dismiss()
        } }
        dialog.show()
    }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }
}
