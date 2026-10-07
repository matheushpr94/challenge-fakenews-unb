package com.example.lumeocrtest

import android.net.Uri
import android.content.Intent
import java.io.File
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.lumeocrtest.ocr.ClaimExtractor
import com.example.lumeocrtest.ocr.toOcrBlocks
import com.example.lumeocrtest.research.ArticleContext
import com.example.lumeocrtest.research.Clarification
import com.example.lumeocrtest.research.Evaluation
import com.example.lumeocrtest.research.RequestGate
import com.example.lumeocrtest.research.ResearchService
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.example.lumeocrtest.ui.theme.Amber
import com.example.lumeocrtest.ui.theme.AmberSoft
import com.example.lumeocrtest.ui.theme.Hairline
import com.example.lumeocrtest.ui.theme.InkSoft
import com.example.lumeocrtest.ui.theme.LumeMist
import com.example.lumeocrtest.ui.theme.Sheet
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen
import com.example.lumeocrtest.ui.theme.LumeOCRTestTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "LumeUi"

class MainActivity : ComponentActivity() {
    companion object {
        const val OPEN_CAPTURE = "com.example.lumeocrtest.OPEN_CAPTURE"
        const val CAPTURE_URI = "capture_uri"
        const val CAPTURE_ERROR = "capture_error"
    }
    private var openRequest by mutableIntStateOf(0)
    private var sharedText by mutableStateOf<String?>(null)
    private var sharedImage by mutableStateOf<String?>(null)
    private var captureError by mutableStateOf<String?>(null)

    @Suppress("DEPRECATION")
    private fun receive(intent: Intent?) {
        Log.i(TAG, "Entrada recebida: ${intent?.action}; tipo=${intent?.type}")
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                captureError = null
                sharedImage = if (intent.type?.startsWith("image/") == true)
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.toString() else null
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                openRequest++
            }
            Intent.ACTION_PROCESS_TEXT -> {
                captureError = null
                sharedImage = null
                sharedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.takeIf { it.isNotBlank() }
                openRequest++
            }
            OPEN_CAPTURE -> {
                sharedImage = intent.getStringExtra(CAPTURE_URI)
                sharedText = null
                captureError = intent.getStringExtra(CAPTURE_ERROR)
                openRequest++
            }
            com.example.lumeocrtest.mascot.MascotService.OPEN_ANALYSIS -> {
                sharedText = null
                sharedImage = null
                captureError = null
                openRequest++
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receive(intent)
        setContent { LumeOCRTestTheme { LumeApp(openRequest, sharedText, sharedImage, captureError) } }

    }
}

private data class OcrOutcome(val text: String, val cleaned: String, val metadata: ArticleMetadata, val mainClaim: String?,
                              val otherClaims: List<String>, val subtitle: String?,
                              val reading: com.example.lumeocrtest.ocr.ArticleReading,
                              val choice: com.example.lumeocrtest.ocr.ClaimChoice)

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
        val blocks = result.toOcrBlocks()
        // Leitura por papéis: título, assinatura, corpo, legenda, crédito, lateral, anúncio… (ver ArticleReader).
        val reader = com.example.lumeocrtest.ocr.ArticleReader()
        val reading = reader.read(blocks, inputImage.width, inputImage.height)
        val choice = reader.claimFor(reading)
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            reading.report().chunked(3500).forEach { Log.d("LumeOcr", it) }
            Log.d("LumeOcr", "afirmação=${choice.claim} escolher=${choice.needsChoice} alternativas=${choice.alternatives}")
        }
        return OcrOutcome(result.text.ifBlank { "Nenhum texto encontrado." }, reading.body, reading.metadata,
            choice.claim, choice.alternatives, reading.subtitle, reading, choice)
    } finally {
        recognizer.close()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OcrScreen(modifier: Modifier = Modifier, importRequest: Int = 0,
                       sharedRequest: Int = 0, sharedText: String? = null, sharedImage: String? = null,
                       captureError: String? = null) {
    val context = LocalContext.current
    val state = remember(context) { androidx.lifecycle.ViewModelProvider(context as ComponentActivity)[AnalysisState::class.java] }
    var selectedUri by state.selectedUri
    var preview by state.preview
    var recognizedText by state.recognizedText
    var cleanedText by state.cleanedText
    var metadata by state.metadata
    var mainClaim by state.mainClaim
    var subtitle by state.subtitle
    var claims by state.claims
    var showRawText by state.showRawText
    var ocrError by state.ocrError
    var ocrState by state.ocrState
    var reading by state.reading
    var claimChoice by state.claimChoice
    var articleContext by state.articleContext

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

    LaunchedEffect(Unit) { if (queryText.isBlank()) queryText = ResearchHistory.draft(context) }
    LaunchedEffect(queryText) {
        delay(600)
        ResearchHistory.saveDraft(context, queryText)
    }

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
                val result = service.evaluate(text, "req-$ticket", articleContext, languageText = state.languageText.takeIf { it.isNotBlank() })
                result.partes.forEach { p -> p.diagnostico?.report()?.chunked(3500)?.forEach { Log.d("LumeTrace", it) } }
                if (gate.deliver(ticket, result) { evaluation = it }) {
                    ResearchHistory.save(context, text, result)
                    state.historyRevision.intValue++
                } else Log.i(TAG, "resposta descartada: pertence a uma pesquisa anterior (req-$ticket)")
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
            selectedUri = null; state.startedUri = null; preview = null; recognizedText = ""; cleanedText = ""
            metadata = null; mainClaim = null; subtitle = null; claims = emptyList()
            reading = null; claimChoice = null; articleContext = null
            job=scope.launch {
                try {
                    val page=withContext(Dispatchers.IO) { com.example.lumeocrtest.research.fetchPage(t,state.linkFetcher) }
                    if (!gate.isCurrent(ticket)) return@launch
                    val body = page.paragraphs.joinToString("\n\n")
                    val extracted = ClaimExtractor().extractClaims(body)
                    val pageTitle = page.title.trim().let { title ->
                        val site = page.siteName.trim()
                        if (site.isNotEmpty()) title.removeSuffix(" | $site").removeSuffix(" - $site").trim() else title
                    }
                    val text = pageTitle.takeIf { it.length >= 20 && it.split(Regex("\\s+")).size >= 4 }
                        ?: extracted.mainClaim?.takeIf { it.isNotBlank() }
                        ?: listOf(page.title, page.description).filter { it.isNotBlank() }.distinct().joinToString(". ")
                    if(text.length<15) throw IllegalArgumentException("Texto indisponível")
                    queryText=text
                    mainClaim=text
                    claims=extracted.otherClaims
                    cleanedText=body
                    notice=if(body.isNotBlank()) "Confira a afirmação extraída da matéria antes de pesquisar."
                        else "O corpo da matéria não pôde ser lido. Confira o título e a descrição antes de pesquisar."
                    busyText=""
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
        state.languageText = t
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

    LaunchedEffect(sharedRequest) {
        if (sharedRequest > 0) {
            if (sharedImage != null) selectedUri = sharedImage
            else if (sharedText != null) {
                state.ocrJob?.cancel()
                selectedUri = null
                state.startedUri = null
                preview = null; recognizedText = ""; cleanedText = ""; metadata = null
                mainClaim = null; subtitle = null; claims = emptyList(); ocrError = null; ocrState = ""
                reading = null; claimChoice = null; articleContext = null
                job?.cancel(); gate.invalidate(); busyText = ""; resetResults()
                queryText = sharedText
                notice = "Conteúdo recebido. Confira o texto antes de pesquisar."
            } else if (captureError != null) {
                ocrError = captureError
            }
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
        preview = null; recognizedText = ""; cleanedText = ""; metadata = null; mainClaim = null; subtitle = null; claims = emptyList()
        reading = null; claimChoice = null; articleContext = null
        showRawText = false; ocrError = null; queryText = ""
        try {
            val out = runOcr(context.applicationContext, uri) { preview = it }
            recognizedText = out.text; cleanedText = out.cleaned; metadata = out.metadata
            mainClaim = out.mainClaim; subtitle = out.subtitle; claims = out.otherClaims
            reading = out.reading; claimChoice = out.choice
            articleContext = ArticleContext(out.reading.title, out.reading.subtitle, out.reading.body,
                out.metadata.source, out.metadata.publishedAtMs, author = out.metadata.author)
            ocrState = ""
            // Só preenche a pesquisa quando há uma afirmação clara; senão, pede a escolha ao usuário.
            queryText = out.choice.claim.orEmpty()
            notice = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR falhou", e)
            ocrError = "Não foi possível ler o texto desta imagem. Tente outra imagem."
        } finally {
            val captured = Uri.parse(uri)
            if (captured.scheme == "file") {
                val file = captured.path?.let(::File)
                if (file != null && file.name.startsWith("lume-capture-") && file.parentFile == context.cacheDir) {
                    file.delete()
                }
            }
            if(state.startedUri==uri) ocrState = ""
        }
        }
    }

    val progressView = remember { BringIntoViewRequester() }
    val resultsView = remember { BringIntoViewRequester() }
    // Traz o cabeçalho "Pesquisado" para o topo, deixando o resumo logo abaixo visível.
    val topArea = with(LocalDensity.current) { androidx.compose.ui.geometry.Rect(0f, -12.dp.toPx(), 1f, 520.dp.toPx()) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- captura: prévia enquadrada + o que foi lido ------------------------------------------
        if (ocrState.isNotEmpty()) {
            preview?.let { img ->
                Image(bitmap = img, contentDescription = "Captura enviada", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium)
                        .border(BorderStroke(1.dp, Hairline), MaterialTheme.shapes.medium))
            }
            StageProgress(ocrState)
        }
        ocrError?.let { ErrorNote(it, null) }
        reading?.let { r -> Reveal(key = r) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ReadingSummary(r, evaluation, preview, recognizedText) } } }

        // ---- pergunta e pesquisa -----------------------------------------------------------------
        SectionHeader(if (reading != null) "O que vamos pesquisar?" else "Cole uma notícia, afirmação ou pergunta",
            claimChoice?.let { it.note ?: "Esta é a afirmação principal que encontramos na imagem. Confira e edite, se precisar, antes de pesquisar." })
        OutlinedTextField(
            value = queryText, onValueChange = { queryText = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (reading != null) "Afirmação a pesquisar" else "Texto ou link para pesquisar") },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Sheet, unfocusedContainerColor = Sheet,
                unfocusedBorderColor = Hairline),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (queryText.isNotBlank() && !busy) startFlow(queryText) }),
        )
        if (reading != null) com.example.lumeocrtest.ocr.vagueSubject(queryText)?.let { vague ->
            Text("A frase começa com “$vague”, que depende do que vem antes na matéria. Para uma busca melhor, " +
                "diga de que se trata (por exemplo, o nome da medida ou do órgão).",
                style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        claimChoice?.alternatives?.takeIf { it.isNotEmpty() }?.let { alts ->
            val mustChoose = claimChoice?.needsChoice == true
            val label = when {
                mustChoose -> "Frases encontradas na imagem"
                reading?.socialPost == true -> "Ver outros detalhes do post"
                else -> "Escolher outra frase da matéria"
            }
            Collapsible(label, key = alts, initiallyOpen = mustChoose) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { alts.forEach { alt ->
                    val selected = queryText == alt
                    LumeCard(color = if (selected) LumeMist else Sheet, border = if (selected) LumeLightGreen else Hairline,
                        onClick = { queryText = alt }) {
                        Text(alt, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                } }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startFlow(queryText) }, enabled = queryText.isNotBlank() && !busy,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Pesquisar")
            }
            if (busy) OutlinedButton(onClick = { cancelSearch() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
            else OutlinedButton(onClick = { picker.launch("image/*") }, enabled = ocrState.isEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (reading != null) "Outra imagem" else "Importar imagem")
            }
        }
        // A etapa em andamento e, depois, o início dos resultados são trazidos para a área visível.
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        // Ao iniciar a busca, recolhe o teclado para o andamento e os resultados ficarem visíveis.
        LaunchedEffect(busy) { if (busy) { focus.clearFocus(); delay(120); runCatching { progressView.bringIntoView() } } }
        LumeVisibility(busy) { StageProgress(busyText.ifEmpty { "Pesquisando…" }, Modifier.bringIntoViewRequester(progressView)) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }

        clarification?.let { c ->
            Reveal(key = c) { ClarificationChooser(c.opcoes, onOption = { queryText = it; runSearch(it) },
                onNotThis = { enteringDetails = true }) }
            LumeVisibility(enteringDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = detailsText, onValueChange = { detailsText = it }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
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
        }

        searchedText?.let { q ->
            HorizontalDivider(color = Hairline)
            Row(Modifier.bringIntoViewRequester(resultsView), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Overline(if (busy) "Pesquisando" else "Pesquisado")
                    Text(q, style = MaterialTheme.typography.bodyLarge, maxLines = if (largeText()) 6 else 3, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { correcting = !correcting; correctionText = q }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Corrigir") }
            }
            LumeVisibility(correcting) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = correctionText, onValueChange = { correctionText = it }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium, label = { Text("Corrigir a pergunta") })
                    Button(enabled = correctionText.isNotBlank(), onClick = { queryText = correctionText; state.languageText = correctionText.trim(); runSearch(correctionText.trim()) }) {
                        Text("Pesquisar esta pergunta")
                    }
                    lastOptions?.let { c -> ClarificationChooser(c.opcoes, onOption = { queryText = it; runSearch(it) }, onNotThis = null) }
                }
            }
        }

        LaunchedEffect(evaluation, searchError) {
            if ((evaluation != null || searchError != null) && !busy) { delay(80); runCatching { resultsView.bringIntoView(topArea) } }
        }
        searchError?.let { err -> ErrorNote(err) { lastAction?.invoke() } }
        evaluation?.let { e ->
            if (e.status == "erro") {
                ErrorNote("Não foi possível consultar as fontes agora. Verifique a conexão e tente novamente.") { lastAction?.invoke() }
            } else {
                Reveal(key = e) { ResearchResults(e, onOption = { queryText = it; runSearch(it) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Erro em linguagem simples, com a ação de tentar de novo quando houver. */
@Composable
private fun ErrorNote(text: String, onRetry: (() -> Unit)?) {
    LumeCard(color = AmberSoft, border = Color.Transparent) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Amber)
            onRetry?.let { OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tentar novamente") } }
        }
    }
}
