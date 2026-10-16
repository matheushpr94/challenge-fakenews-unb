package com.example.lumeocrtest

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.lumeocrtest.mascot.MascotService
import com.example.lumeocrtest.capture.CaptureActivity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LumeApp(openRequest: Int = 0, sharedText: String? = null, sharedImage: String? = null, captureError: String? = null) {
    val context=LocalContext.current
    val active by MascotService.active.collectAsState()
    var page by rememberSaveable { mutableStateOf("home") }
    var importRequest by rememberSaveable { mutableIntStateOf(0) }
    var analysisVisited by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    var pendingPermission by rememberSaveable { mutableStateOf(false) }
    var explanation by remember { mutableStateOf(false) }
    var guided by rememberSaveable { mutableStateOf(false) }          // configuração guiada em andamento
    var lastMissing by rememberSaveable { mutableIntStateOf(0) }       // quantas permissões faltavam ao abrir o último passo
    var message by remember { mutableStateOf<String?>(null) }
    val prefs=remember { context.getSharedPreferences("lume_preferences",0) }
    val analysisState = remember(context) { androidx.lifecycle.ViewModelProvider(context as ComponentActivity)[AnalysisState::class.java] }
    val historyRevision by analysisState.historyRevision
    var saveHistory by remember { mutableStateOf(ResearchHistory.enabled(context)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("animations",true)) }
    val owner=LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var motionScale by remember { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)) }
    var away by remember { mutableStateOf(false) }   // o Lume saiu de cena (abriu uma tela de permissão)
    var setupTick by remember { mutableIntStateOf(0) }   // muda ao voltar das telas de permissão, para reler o que já foi liberado
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event ->
            resumed=event==Lifecycle.Event.ON_RESUME || owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event==Lifecycle.Event.ON_PAUSE) away=true
            if (event==Lifecycle.Event.ON_RESUME) {
                if (away) { away=false; setupTick++ }   // voltou de uma tela de permissão: reavalia o que foi liberado
                motionScale=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
                if (!Settings.canDrawOverlays(context)) context.stopService(Intent(context,MascotService::class.java))
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val motion=animations && motionScale>0f
    fun startMascot() {
        if (!Settings.canDrawOverlays(context)) { message="Autorize o Lume a aparecer sobre outros aplicativos para ativar o mascote."; return }
        runCatching { ContextCompat.startForegroundService(context,Intent(context,MascotService::class.java)) }
            .onFailure { message="Não foi possível ativar o mascote. Tente novamente." }
    }
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (!guided) startMascot() }
    fun activate() {
        if (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notification.launch(Manifest.permission.POST_NOTIFICATIONS) else startMascot()
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingPermission) {
            pendingPermission=false
            if (guided) { /* a avaliação acontece quando o Lume volta à tela */ }
            else if (Settings.canDrawOverlays(context)) activate() else message="Permissão não concedida. Você pode continuar usando texto ou imagem."
        }
    }
    // ---- Configuração inicial guiada: pop-up (sobreposição), notificações e acessibilidade ----
    // O Android não deixa um app ligar essas permissões sozinho; o Lume abre direto cada tela certa, uma de cada vez.
    fun overlayOk() = Settings.canDrawOverlays(context)
    fun notifOk() = Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun a11yOk() = com.example.lumeocrtest.capture.PageUrlService.enabled ||
        (Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains("PageUrlService")==true)
    fun missing() = listOf(overlayOk(),notifOk(),a11yOk()).count { !it }
    fun runNextStep() {
        lastMissing=missing(); away=false
        when {
            !overlayOk() -> { pendingPermission=true; permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}"))) }
            !notifOk() -> notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            !a11yOk() -> {
                message="Toque em “Lume (leitura completa)” e ligue a chave."
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            else -> { guided=false; if(!active) activate() }
        }
    }
    fun startGuided() { guided=true; runNextStep() }
    LaunchedEffect(setupTick) {
        kotlinx.coroutines.delay(600)   // junta duas voltas de tela seguidas (resultado + retomada) numa só avaliação
        if (overlayOk() && notifOk() && !active) startMascot()   // a gotinha liga assim que o essencial sai
        if (!guided || setupTick==0) return@LaunchedEffect   // só avalia depois que a pessoa saiu e voltou ao Lume
        val m=missing()
        when {
            m==0 -> { guided=false; message=null }
            m<lastMissing -> runNextStep()      // o passo anterior foi liberado: abre o próximo
            else -> guided=false                // a pessoa não liberou: para de insistir
        }
    }
    LaunchedEffect(Unit) {   // primeira abertura do app: já começa a configuração
        if (!prefs.getBoolean("setup_auto",false) && missing()>0) { prefs.edit().putBoolean("setup_auto",true).apply(); startGuided() }
    }
    LaunchedEffect(openRequest) { if(openRequest>0) { analysisVisited=true; page="analysis" } }
    LaunchedEffect(page) { if(page!="analysis") focus.clearFocus() }
    BackHandler(page=="analysis" || page=="profile") { page="home" }
    CompositionLocalProvider(LocalLumeMotion provides motion) {
    Scaffold(
        bottomBar={
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected=page!="profile",onClick={page="home"},icon={Icon(Icons.Outlined.DocumentScanner,"Analisar")},label={Text("Analisar")})
                NavigationBarItem(selected=page=="profile",onClick={page="profile"},icon={Icon(Icons.Outlined.Settings,"Ajustes")},label={Text("Ajustes")})
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
        // Keep the analysis composed while navigating so image, edits and results survive.
        if (analysisVisited) {
            val visible = page=="analysis"
            val opacity by animateFloatAsState(if(visible) 1f else 0f, if(motion) tween(180) else snap(), label="Análise")
            Column(Modifier.fillMaxSize().imePadding().graphicsLayer { alpha=opacity }
                .then(if(visible) Modifier else Modifier.clearAndSetSemantics {})
                .layout { measurable, constraints ->
                    val measured=measurable.measure(constraints)
                    layout(if(visible) measured.width else 0, if(visible) measured.height else 0) { if(visible) measured.place(0,0) }
                }) {
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={page="home"}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Voltar") }
                    Text("Análise",style=MaterialTheme.typography.titleMedium)
                }
                OcrScreen(Modifier.weight(1f), importRequest, openRequest, sharedText, sharedImage, captureError)
            }
        }
        AnimatedContent(targetState=page, modifier=Modifier.fillMaxSize(), label="Navegação",
            transitionSpec={ if(motion) (fadeIn(tween(180))+slideInVertically(tween(220)){it/30}) togetherWith fadeOut(tween(120))
                else EnterTransition.None togetherWith ExitTransition.None }) { screen ->
            when(screen) {
                "analysis" -> Unit
                "profile" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("Ajustes do Lume",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(horizontal=4.dp))
                    SectionHeader("Preferências neste aparelho",modifier=Modifier.padding(horizontal=4.dp))
                    LumeCard {
                        SettingSwitch("Animações suaves","Transições e movimentos do Lume. Também segue a configuração de animações do Android.",animations) {
                            animations=it;prefs.edit().putBoolean("animations",it).apply()
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        SettingSwitch("Salvar pesquisas e rascunho","Ficam só neste aparelho.",saveHistory) { saveHistory=it;ResearchHistory.setEnabled(context,it) }
                    }
                    SectionHeader("Mascote",modifier=Modifier.padding(horizontal=4.dp))
                    LumeCard {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("O mascote está ${if(active) "ativado" else "desativado"}.",style=MaterialTheme.typography.bodyLarge)
                            if(active) OutlinedButton(onClick={context.startService(Intent(context,MascotService::class.java).setAction(MascotService.STOP))},modifier=Modifier.heightIn(min=48.dp)) { Text("Desativar mascote") }
                        }
                    }
                    SectionHeader("Servidor dos modelos (sem cabo)",modifier=Modifier.padding(horizontal=4.dp))
                    LumeCard {
                        var addr by remember { mutableStateOf(com.example.lumeocrtest.research.ServerConfig.address) }
                        var key by remember { mutableStateOf(com.example.lumeocrtest.research.ServerConfig.token) }
                        var status by remember { mutableStateOf("") }
                        var testing by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Cole o endereço e a chave que o script do Mac mostra (iniciar_servidor_remoto.sh). Com o Mac ligado, o celular usa os modelos sem cabo. Sem servidor, o Lume funciona só com as regras.",style=MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.OutlinedTextField(value=addr,onValueChange={addr=it},label={Text("Endereço (https://…)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(value=key,onValueChange={key=it},label={Text("Chave")},singleLine=true,
                                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                            OutlinedButton(enabled=!testing,onClick={
                                scope.launch {
                                    testing=true; status="Testando…"
                                    val saved=com.example.lumeocrtest.research.ServerConfig.save(addr,key)
                                    status = when {
                                        saved==null -> "Endereço inválido. Use o https://… que o script mostrou."
                                        saved.isEmpty() -> "Servidor removido. O Lume usa só o que há neste aparelho."
                                        else -> com.example.lumeocrtest.research.testServer(saved,key)
                                    }
                                    testing=false
                                }
                            },modifier=Modifier.heightIn(min=48.dp)) { Text(if(testing) "Testando…" else "Salvar e testar") }
                            if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.secondary)
                            Text("O endereço e a chave ficam só neste aparelho. O texto da pesquisa passa pelo túnel até o Mac do grupo; nenhuma IA em nuvem é usada.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                        }
                    }
                    SectionHeader("Leitura completa",modifier=Modifier.padding(horizontal=4.dp))
                    LumeCard {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            val on = com.example.lumeocrtest.capture.PageUrlService.enabled
                            Text(if(on) "Ativada. Ao tocar em 'Ler esta tela', o Lume reconhece a notícia aberta no navegador."
                                else "Desativada. Ative para o Lume ler a notícia inteira, mesmo quando só a manchete aparece na tela.",style=MaterialTheme.typography.bodyLarge)
                            OutlinedButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))},modifier=Modifier.heightIn(min=48.dp)) { Text("Abrir ajustes de acessibilidade") }
                            Text("Em Acessibilidade, escolha 'Lume (leitura completa)'. Ele só enxerga navegadores, só quando você toca no Lume, e não grava nada.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                        }
                    }
                    SectionHeader("Histórico neste aparelho",modifier=Modifier.padding(horizontal=4.dp))
                    val saved = remember(historyRevision, page) { ResearchHistory.list(context) }
                    if (saved.isEmpty()) Text("Nenhuma pesquisa salva.",color=MaterialTheme.colorScheme.secondary,modifier=Modifier.padding(horizontal=4.dp))
                    saved.forEach { entry ->
                        LumeCard(onClick={
                            val result=ResearchHistory.restore(entry)
                            if(result!=null) {
                                analysisState.evaluation.value=result
                                analysisState.queryText.value=entry.query
                                analysisState.searchedText.value=entry.query
                                analysisState.clarification.value=null
                                analysisVisited=true;page="analysis"
                            }
                        }) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(entry.query,maxLines=2,style=MaterialTheme.typography.titleLarge)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.savedAt)),
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    if(saved.isNotEmpty() || ResearchHistory.draft(context).isNotEmpty())
                        TextButton(onClick={ResearchHistory.clear(context);analysisState.historyRevision.intValue++}) { Text("Apagar histórico e rascunho") }
                    SectionHeader("Sobre a leitura",modifier=Modifier.padding(horizontal=4.dp))
                    LumeCard(color=MaterialTheme.colorScheme.surfaceContainerHigh,border=androidx.compose.ui.graphics.Color.Transparent) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("O Lume lê a imagem escolhida ou uma tela autorizada por você. Ao pesquisar, o texto é usado em consultas na internet. A gotinha só inicia a captura quando você toca em 'Ler esta tela' e aceita a permissão do Android.",style=MaterialTheme.typography.bodyMedium)
                            Text("A pesquisa consulta fontes públicas e, em textos longos, mostra o estilo da escrita. O Lume não confirma nem desmente fatos.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.secondary)
                            Text("A comparação contextual por IA usa o modelo gratuito instalado neste computador quando ele está conectado ao app.",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text("Pesquisa de fontes · Sem conta ou login",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.padding(horizontal=4.dp))
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("lume",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                    Text("Seu companheiro de leitura.",color=MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { HomeMascot(motion && resumed && page=="home") }
                    Text("Uma dúvida? Me chama.",Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
                    Text("Cole um texto, compartilhe um link, importe uma imagem ou peça ao Lume para ler a tela com sua autorização.",
                        Modifier.fillMaxWidth().padding(horizontal=8.dp),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    if (setupTick>=0 && missing()>0) LumeCard {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Ative o Lume completo",style=MaterialTheme.typography.titleMedium)
                            Text("São 3 toques rápidos para ele ler a notícia que você está vendo:",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.secondary)
                            Text("${if(overlayOk()) "✓" else "○"}  Gotinha sempre à mão")
                            Text("${if(notifOk()) "✓" else "○"}  Avisos do Lume")
                            Text("${if(a11yOk()) "✓" else "○"}  Leitura completa da notícia")
                            Text("O Android vai mostrar um aviso padrão de segurança. O Lume só usa essa permissão para reconhecer a página que você está lendo, quando você toca nele. Nada é gravado.",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                            Button(onClick={ startGuided() },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Ativar agora") }
                            if(!a11yOk()) TextButton(onClick={ runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) } }) {
                                Text("Não consegue ligar? Toque aqui")
                            }
                        }
                    }
                    Button(onClick={ if(Settings.canDrawOverlays(context)) activate() else explanation=true },enabled=!active,
                        modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                        Icon(Icons.Outlined.WaterDrop,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if(active) "Mascote ativado" else "Ativar mascote")
                    }
                    FilledTonalButton(onClick={analysisVisited=true;page="analysis"},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                        Icon(Icons.Outlined.Search,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Colar texto ou link")
                    }
                    val importButton: @Composable (Modifier) -> Unit = { m ->
                        OutlinedButton(onClick={analysisVisited=true;importRequest++;page="analysis"},modifier=m.heightIn(min=52.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                            Icon(Icons.Outlined.Image,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Importar imagem",textAlign=TextAlign.Center)
                        }
                    }
                    val readButton: @Composable (Modifier) -> Unit = { m ->
                        OutlinedButton(onClick={context.startActivity(Intent(context,CaptureActivity::class.java))},modifier=m.heightIn(min=52.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                            Icon(Icons.Outlined.DocumentScanner,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ler esta tela",textAlign=TextAlign.Center)
                        }
                    }
                    if (largeText()) { importButton(Modifier.fillMaxWidth()); readButton(Modifier.fillMaxWidth()) }
                    else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { importButton(Modifier.weight(1f)); readButton(Modifier.weight(1f)) }
                    AnimatedVisibility(active,enter=if(motion) fadeIn()+expandVertically() else EnterTransition.None,exit=if(motion) fadeOut()+shrinkVertically() else ExitTransition.None) {
                        OutlinedButton(onClick={context.startService(Intent(context,MascotService::class.java).setAction(MascotService.STOP))},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Desativar mascote") }
                    }
                    message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                    LumeCard(color=MaterialTheme.colorScheme.surfaceContainerHigh,border=androidx.compose.ui.graphics.Color.Transparent) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Você decide quando ele lê",style=MaterialTheme.typography.titleMedium)
                            Text("A gotinha abre as opções quando você toca. Para capturar a tela, o Android pede sua autorização a cada sessão.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Lume · Pesquisa de fontes e contexto",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                }
            }
        }
        }
    }
    }
    if(explanation) AlertDialog(onDismissRequest={explanation=false},title={Text("Lume ao seu lado")},
        text={Text("Permita que a gotinha apareça sobre outros aplicativos. Ela só abre as opções quando você toca e não lê nem captura sua tela.")},
        confirmButton={TextButton(onClick={explanation=false;pendingPermission=true;permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}){Text("Continuar")}},
        dismissButton={TextButton(onClick={explanation=false}){Text("Agora não")}})
}

/** Linha de ajuste com título, explicação curta e chave; a linha inteira alterna o valor. */
@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role=androidx.compose.ui.semantics.Role.Switch, onValueChange=onChange)
        .padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(hint,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked=checked,onCheckedChange=null)
    }
}

@Composable
private fun HomeMascot(motion: Boolean) {
    val scope=rememberCoroutineScope()
    val touch=remember { Animatable(1f) }

    // Respiração
    val breathe=if(motion) {
        val loop=rememberInfiniteTransition(label="Respiração")
        loop.animateFloat(0f,3f,infiniteRepeatable(tween(2300,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Balanço").value
    } else 0f

    // Rotação para o "tchau"
    val waveRotation = remember { Animatable(0f) }

    // Piscar de olhos
    var blinkAlpha by remember { mutableFloatStateOf(0f) }

    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared=true
        if (motion) {
            // Tchau ao abrir a tela
            waveRotation.animateTo(8f, tween(150))
            waveRotation.animateTo(-6f, tween(200))
            waveRotation.animateTo(4f, tween(150))
            waveRotation.animateTo(0f, tween(150))
        }
    }

    LaunchedEffect(motion) {
        if (motion) {
            while (true) {
                kotlinx.coroutines.delay(kotlin.random.Random.nextLong(3000, 7000))
                blinkAlpha = 1f
                kotlinx.coroutines.delay(120)
                blinkAlpha = 0f
            }
        } else {
            blinkAlpha = 0f
        }
    }

    val alpha by animateFloatAsState(if(appeared) 1f else 0f,if(motion) tween(350) else snap(),label="Entrada do Lume")

    Box(
        modifier = Modifier
            .size(156.dp)
            .graphicsLayer {
                translationY = -breathe
                scaleX = touch.value
                scaleY = touch.value - (breathe * 0.01f) // Suave achatamento na respiração
                rotationZ = waveRotation.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.8f) // Eixo base para o tchau
                this.alpha = alpha
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if(motion && !waveRotation.isRunning) {
                    scope.launch {
                        touch.animateTo(1.03f,tween(90)); touch.animateTo(1f,spring(dampingRatio=.65f))
                    }
                    scope.launch {
                        // Tchau ao tocar
                        waveRotation.animateTo(10f, tween(150))
                        waveRotation.animateTo(-8f, tween(200))
                        waveRotation.animateTo(6f, tween(150))
                        waveRotation.animateTo(0f, tween(150))
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.lume_mascot),
            contentDescription = "Lume, seu companheiro de leitura",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        if (blinkAlpha > 0f) {
            Image(
                painter = painterResource(R.drawable.lume_mascot_blink),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = blinkAlpha },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}
