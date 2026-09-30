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
    var message by remember { mutableStateOf<String?>(null) }
    val prefs=remember { context.getSharedPreferences("lume_preferences",0) }
    val analysisState = remember(context) { androidx.lifecycle.ViewModelProvider(context as ComponentActivity)[AnalysisState::class.java] }
    val historyRevision by analysisState.historyRevision
    var saveHistory by remember { mutableStateOf(ResearchHistory.enabled(context)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("animations",true)) }
    val owner=LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var motionScale by remember { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event ->
            resumed=event==Lifecycle.Event.ON_RESUME || owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event==Lifecycle.Event.ON_RESUME) {
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
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startMascot() }
    fun activate() {
        if (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notification.launch(Manifest.permission.POST_NOTIFICATIONS) else startMascot()
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingPermission) {
            pendingPermission=false
            if (Settings.canDrawOverlays(context)) activate() else message="Permissão não concedida. Você pode continuar usando texto ou imagem."
        }
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
                TextButton(onClick={page="home"}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,null); Spacer(Modifier.width(8.dp)); Text("Voltar") }
                OcrScreen(Modifier.weight(1f), importRequest, openRequest, sharedText, sharedImage, captureError)
            }
        }
        AnimatedContent(targetState=page, modifier=Modifier.fillMaxSize(), label="Navegação",
            transitionSpec={ if(motion) (fadeIn(tween(180))+slideInVertically(tween(220)){it/30}) togetherWith fadeOut(tween(120))
                else EnterTransition.None togetherWith ExitTransition.None }) { screen ->
            when(screen) {
                "analysis" -> Unit
                "profile" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    Text("Ajustes do Lume",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Preferências neste aparelho",color=MaterialTheme.colorScheme.secondary)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Animações suaves",Modifier.weight(1f))
                        Switch(checked=animations,onCheckedChange={animations=it;prefs.edit().putBoolean("animations",it).apply()})
                    }
                    Text("O mascote está ${if(active) "ativado" else "desativado"}.")
                    if(active) OutlinedButton(onClick={context.startService(Intent(context,MascotService::class.java).setAction(MascotService.STOP))}) { Text("Desativar mascote") }
                    Text("Sobre a leitura",style=MaterialTheme.typography.titleMedium)
                    Text("O Lume lê a imagem escolhida ou uma tela autorizada por você. Ao pesquisar, o texto é usado em consultas na internet. A gotinha só inicia a captura quando você toca em 'Ler esta tela' e aceita a permissão do Android.")
                    Text("A pesquisa consulta fontes públicas. A classificação de veracidade ainda não está disponível.",color=MaterialTheme.colorScheme.secondary)
                    Text("Histórico neste aparelho",style=MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Salvar pesquisas e rascunho",Modifier.weight(1f))
                        Switch(checked=saveHistory,onCheckedChange={saveHistory=it;ResearchHistory.setEnabled(context,it)})
                    }
                    val saved = remember(historyRevision, page) { ResearchHistory.list(context) }
                    if (saved.isEmpty()) Text("Nenhuma pesquisa salva.",color=MaterialTheme.colorScheme.secondary)
                    saved.forEach { entry ->
                        Card(onClick={
                            val result=ResearchHistory.restore(entry)
                            if(result!=null) {
                                analysisState.evaluation.value=result
                                analysisState.queryText.value=entry.query
                                analysisState.searchedText.value=entry.query
                                analysisState.clarification.value=null
                                analysisVisited=true;page="analysis"
                            }
                        },modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(entry.query,maxLines=2,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.savedAt)),
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    if(saved.isNotEmpty() || ResearchHistory.draft(context).isNotEmpty())
                        TextButton(onClick={ResearchHistory.clear(context);analysisState.historyRevision.intValue++}) { Text("Apagar histórico e rascunho") }
                    Text("A comparação contextual por IA usa o modelo gratuito instalado neste computador quando ele está conectado ao app.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                    Text("Pesquisa de fontes · Sem conta ou login",style=MaterialTheme.typography.bodySmall)
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("lume",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Seu companheiro de leitura.",color=MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { HomeMascot(motion && resumed && page=="home") }
                    Text("Uma dúvida? Me chama.",Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text("Cole um texto, compartilhe um link, importe uma imagem ou peça ao Lume para ler a tela com sua autorização.",
                        Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick={ if(Settings.canDrawOverlays(context)) activate() else explanation=true },enabled=!active,
                        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(32.dp)) {
                        Icon(Icons.Outlined.AutoAwesome,null); Spacer(Modifier.width(8.dp)); Text(if(active) "Mascote ativado" else "Ativar mascote",fontWeight=FontWeight.Bold)
                    }
                    Button(onClick={analysisVisited=true;page="analysis"},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(32.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer)) {
                        Icon(Icons.Outlined.Search,null); Spacer(Modifier.width(8.dp)); Text("Colar texto ou link",fontWeight=FontWeight.Bold)
                    }
                    TextButton(onClick={analysisVisited=true;importRequest++;page="analysis"},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image,null); Spacer(Modifier.width(8.dp));Text("Importar imagem") }
                    TextButton(onClick={context.startActivity(Intent(context,CaptureActivity::class.java))},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.DocumentScanner,null); Spacer(Modifier.width(8.dp)); Text("Ler esta tela")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Você decide quando ele lê",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("A gotinha abre as opções quando você toca. Para capturar a tela, o Android pede sua autorização a cada sessão.",color=MaterialTheme.colorScheme.secondary)
                    AnimatedVisibility(active,enter=if(motion) fadeIn()+expandVertically() else EnterTransition.None,exit=if(motion) fadeOut()+shrinkVertically() else ExitTransition.None) {
                        OutlinedButton(onClick={context.startService(Intent(context,MascotService::class.java).setAction(MascotService.STOP))},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Desativar mascote") }
                    }
                    message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(28.dp))
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
            .size(180.dp)
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
