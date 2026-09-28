package com.example.lumeocrtest

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.lumeocrtest.ocr.ArticleMetadata
import com.example.lumeocrtest.ocr.ArticleMetadataExtractor
import com.example.lumeocrtest.ocr.ArticleTextExtractor
import com.example.lumeocrtest.ocr.ClaimExtractor
import com.example.lumeocrtest.ocr.OcrBlock
import com.example.lumeocrtest.ocr.OcrRect
import com.example.lumeocrtest.research.Clarification
import com.example.lumeocrtest.research.Evaluation
import com.example.lumeocrtest.research.RequestGate
import com.example.lumeocrtest.research.ResearchService
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen
import com.example.lumeocrtest.ui.theme.LumeOCRTestTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "LumeUi"

class MainActivity : ComponentActivity() {
    private var openRequest by mutableIntStateOf(0)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if(intent.action == com.example.lumeocrtest.mascot.MascotService.OPEN_ANALYSIS) openRequest++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if(intent.action == com.example.lumeocrtest.mascot.MascotService.OPEN_ANALYSIS) openRequest++
        setContent { LumeOCRTestTheme { LumeApp(openRequest) } }

    }
}

private data class OcrOutcome(val text: String, val cleaned: String, val metadata: ArticleMetadata, val mainClaim: String?,
                              val otherClaims: List<String>)

private suspend fun runOcr(context: android.content.Context, uri: String, onPreview: (ImageBitmap?) -> Unit): OcrOutcome {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val inputImage = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, Uri.parse(uri)) }
        onPreview(inputImage.bitmapInternal?.asImageBitmap())
        val result: MlText = suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
        val blocks = result.textBlocks.map { block ->
            OcrBlock(text = block.text,
                boundingBox = block.boundingBox?.let { OcrRect(it.left, it.top, it.right, it.bottom) },
                lines = block.lines.map { it.text })
        }
        val metadataResult = ArticleMetadataExtractor().extractMetadata(blocks)
        val metadata = metadataResult.metadata
        val cleaned = ArticleTextExtractor().extractArticle(blocks, inputImage.width, inputImage.height, metadataResult.consumedBlockIndexes)
        val extracted = ClaimExtractor().extractClaims(cleaned)
        fun isMetadata(claim: String) = listOfNotNull(metadata.author, metadata.source, metadata.publishedAt)
            .any { claim.contains(it, ignoreCase = true) }
        return OcrOutcome(result.text.ifBlank { "Nenhum texto encontrado." }, cleaned, metadata,
            extracted.mainClaim?.takeUnless { isMetadata(it) }, extracted.otherClaims.filterNot { isMetadata(it) })
    } finally {
        recognizer.close()
    }
}

@Composable
internal fun OcrScreen(modifier: Modifier = Modifier, importRequest: Int = 0) {
    val context = LocalContext.current
    val state = remember(context) { androidx.lifecycle.ViewModelProvider(context as ComponentActivity)[AnalysisState::class.java] }
    var selectedUri by state.selectedUri
    var preview by state.preview
    var recognizedText by state.recognizedText
    var cleanedText by state.cleanedText
    var metadata by state.metadata
    var mainClaim by state.mainClaim
    var claims by state.claims
    var showRawText by state.showRawText
    var ocrError by state.ocrError
    var ocrState by state.ocrState

    // Pesquisa nativa (sem servidor): esclarecimento -> busca -> leitura -> comparação.
    val service = state.service
    val gate = state.gate
    val scope = state.scope
    var job by state.job
    var queryText by state.queryText
    var busyText by state.busyText
    var evaluation by state.evaluation
    var clarification by state.clarification
    var lastOptions by state.lastOptions
    var searchError by state.searchError
    var notice by state.notice
    var searchedText by state.searchedText
    var correcting by state.correcting
    var correctionText by state.correctionText
    var flowOriginal by state.flowOriginal
    var flowDetails by state.flowDetails
    var flowRejected by state.flowRejected
    var flowRound by state.flowRound
    var enteringDetails by state.enteringDetails
    var detailsText by state.detailsText
    var lastAction by state.lastAction
    val busy = busyText.isNotEmpty()

    fun resetResults() {
        evaluation = null; clarification = null; searchError = null; notice = null; correcting = false; enteringDetails = false
    }

    fun runSearch(text: String) {
        job?.cancel()
        val ticket = gate.next()
        resetResults()
        searchedText = text
        busyText = "Pesquisando e lendo as fontes…"
        lastAction = { runSearch(text) }
        job = scope.launch {
            try {
                val result = service.evaluate(text, "req-$ticket")
                if (!gate.deliver(ticket, result) { evaluation = it }) Log.i(TAG, "resposta descartada: pertence a uma pesquisa anterior (req-$ticket)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "pesquisa falhou", e)
                gate.deliver(ticket, e) { searchError = "Não foi possível concluir a pesquisa. Verifique a conexão e tente novamente." }
            } finally {
                if (gate.isCurrent(ticket)) busyText = ""
            }
        }
    }

    fun clarifyThenSearch() {
        job?.cancel()
        val ticket = gate.next()
        resetResults()
        searchedText = null
        busyText = "Entendendo a pergunta…"
        lastAction = { clarifyThenSearch() }
        job = scope.launch {
            try {
                val c = service.clarify(flowOriginal, flowDetails, flowRejected, flowRound)
                if (!gate.isCurrent(ticket)) return@launch
                if (c.precisaEscolher) {
                    clarification = c
                    lastOptions = c
                    busyText = ""
                    if (c.aviso.isNotEmpty()) Log.w(TAG, c.aviso)
                } else {
                    runSearch(c.textoPesquisa)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "esclarecimento falhou", e)
                if (gate.isCurrent(ticket)) runSearch(flowOriginal) // sem esclarecimento, pesquisa como escrito
            }
        }
    }

    fun startFlow(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val uri=runCatching { java.net.URI(t) }.getOrNull()
        if (uri?.scheme?.lowercase() in listOf("http","https") && !uri?.host.isNullOrBlank()) {
            job?.cancel()
            val ticket=gate.next()
            resetResults(); busyText="Lendo o link…"; lastAction={ startFlow(t) }
            job=scope.launch {
                try {
                    val page=withContext(Dispatchers.IO) { com.example.lumeocrtest.research.fetchPage(t,state.linkFetcher) }
                    if (!gate.isCurrent(ticket)) return@launch
                    val text=listOf(page.title,page.description).filter { it.isNotBlank() }.distinct().joinToString(". ").take(1200)
                    if(text.length<15) throw IllegalArgumentException("Texto indisponível")
                    queryText=text
                    flowOriginal=text; flowDetails=""; flowRejected=emptyList(); flowRound=0; lastOptions=null
                    clarifyThenSearch()
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    Log.w(TAG,"Link não pôde ser lido",e)
                    if(gate.isCurrent(ticket)) {
                        searchError="Não conseguimos ler este link. Cole o texto da publicação ou escolha um print."
                        busyText=""
                    }
                }
            }
            return
        }
        flowOriginal = t; flowDetails = ""; flowRejected = emptyList(); flowRound = 0; lastOptions = null
        clarifyThenSearch()
    }

    fun cancelSearch() {
        job?.cancel()
        gate.invalidate()
        busyText = ""
        notice = "Pesquisa cancelada."
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedUri = uri.toString()
    }

    LaunchedEffect(importRequest) {
        if (importRequest > state.consumedImport) {
            state.consumedImport=importRequest
            picker.launch("image/*")
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (uri==state.startedUri) return@LaunchedEffect
        state.startedUri=uri
        state.ocrJob?.cancel()
        state.ocrJob=scope.launch {
        // Nova imagem: nenhuma resposta da pesquisa anterior pode aparecer.
        job?.cancel()
        gate.invalidate()
        busyText = ""
        resetResults()
        searchedText = null
        ocrState = "Lendo a imagem..."
        preview = null; recognizedText = ""; cleanedText = ""; metadata = null; mainClaim = null; claims = emptyList()
        showRawText = false; ocrError = null; queryText = ""
        try {
            val out = runOcr(context.applicationContext, uri) { preview = it }
            recognizedText = out.text; cleanedText = out.cleaned; metadata = out.metadata
            mainClaim = out.mainClaim; claims = out.otherClaims
            val target = out.cleaned.takeIf { it.isNotBlank() } ?: out.mainClaim ?: out.otherClaims.firstOrNull()
            ocrState = ""
            if (target != null) {
                queryText = target
                startFlow(target)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR falhou", e)
            ocrError = "Não foi possível ler o texto desta imagem. Tente outra imagem."
        } finally {
            if(state.startedUri==uri) ocrState = ""
        }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = R.drawable.lume_home), contentDescription = "Lume", modifier = Modifier.height(48.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lume", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = LumeDarkGreen)
        }

        Button(onClick = { picker.launch("image/*") }, enabled = ocrState.isEmpty()) { Text("Simular captura") }
        preview?.let { Image(bitmap = it, contentDescription = "Imagem selecionada", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
        if (ocrState.isNotEmpty()) Text(ocrState)
        ocrError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (recognizedText.isNotEmpty()) {
            Text("Imagem lida no aparelho", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("Sobre esta publicação", style = MaterialTheme.typography.titleMedium, color = LumeDarkGreen)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val md = metadata
                    if (md?.author != null || md?.source != null) {
                        Text(buildString {
                            append("Publicado")
                            md.author?.let { append(" por $it") }
                            md.source?.let { append(" em $it") }
                        } + ".")
                    } else {
                        Text("Autor e fonte não identificados com segurança.")
                    }
                    md?.publishedAt?.let { Text(it) }
                    md?.url?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
            Text("Conteúdo identificado", style = MaterialTheme.typography.titleMedium, color = LumeDarkGreen)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(cleanedText.ifEmpty { "Não foi possível extrair o texto principal." }, modifier = Modifier.padding(16.dp))
            }
            if (claims.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Esconder outras afirmações" else "Ver outras afirmações desta publicação")
                }
                LumeVisibility(expanded) { Column { claims.forEach { claim ->
                    Surface(onClick = { queryText = claim }, shape = RoundedCornerShape(8.dp),
                        color = if (queryText == claim) LumeLightGreen else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(claim, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                } } }
            }
            TextButton(onClick = { showRawText = !showRawText }) { Text(if (showRawText) "Ocultar texto bruto" else "Ver texto bruto") }
            LumeVisibility(showRawText) { SelectionContainer {
                Card(modifier = Modifier.fillMaxWidth()) { Text(recognizedText, modifier = Modifier.padding(16.dp)) }
            } }
        }

        // ---- pergunta e pesquisa -----------------------------------------------------------------
        Text(if (mainClaim != null) "O Lume entendeu (edite se precisar):" else "Cole uma notícia, afirmação ou pergunta",
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = queryText, onValueChange = { queryText = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto ou link para pesquisar") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (queryText.isNotBlank() && !busy) startFlow(queryText) }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startFlow(queryText) }, enabled = queryText.isNotBlank() && !busy) { Text("Pesquisar") }
            if (busy) OutlinedButton(onClick = { cancelSearch() }) { Text("Cancelar") }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(busyText)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        clarification?.let { c ->
            ClarificationChooser(c.opcoes, onOption = { queryText = it; runSearch(it) },
                onNotThis = { enteringDetails = true })
            if (enteringDetails) {
                OutlinedTextField(value = detailsText, onValueChange = { detailsText = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Conte um pouco mais sobre o que você quis dizer.") })
                Button(enabled = detailsText.isNotBlank() && !busy, onClick = {
                    flowRejected = flowRejected + c.opcoes.map { it.texto }
                    flowDetails = listOf(flowDetails, detailsText.trim()).filter { it.isNotBlank() }.joinToString(". ")
                    flowRound += 1
                    detailsText = ""
                    clarifyThenSearch()
                }) { Text("Continuar") }
            }
        }

        searchedText?.let { q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pesquisando: ", style = MaterialTheme.typography.bodySmall)
                Text(q, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { correcting = !correcting; correctionText = q }) { Text("Corrigir") }
            }
            if (correcting) {
                OutlinedTextField(value = correctionText, onValueChange = { correctionText = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Corrigir a pergunta") })
                Button(enabled = correctionText.isNotBlank(), onClick = { queryText = correctionText; runSearch(correctionText.trim()) }) {
                    Text("Pesquisar esta pergunta")
                }
                lastOptions?.let { c -> ClarificationChooser(c.opcoes, onOption = { queryText = it; runSearch(it) }, onNotThis = null) }
            }
        }

        searchError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { lastAction?.invoke() }) { Text("Tentar novamente") }
        }
        evaluation?.let { e ->
            if (e.status == "erro") {
                Text("Não foi possível consultar as fontes agora. Verifique a conexão e tente novamente.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { lastAction?.invoke() }) { Text("Tentar novamente") }
            } else {
                ResearchResults(e, onOption = { queryText = it; runSearch(it) })
            }
        }
    }
}
