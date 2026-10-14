package com.example.lumeocrtest.research

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Orquestra interpretação, busca, leitura de páginas, comparação e montagem do resultado,
 * diretamente no aparelho (sem servidor). Porte do protótipo web do Lume.
 */

private const val TAG = "LumeResearch"
private const val NEWS_TTL = 10 * 60_000L
private const val WEB_TTL = 30 * 60_000L
private const val WIKI_TTL = 6 * 3_600_000L
private const val PAGE_TTL = 3_600_000L
private const val TOTAL_BUDGET_MS = 45_000L
private const val AVISO_MODELO = "O modelo de linguagem indica que pode ser outro fato; confira a fonte."
private const val SIGNAL_TIMEOUT_MS = 2_500L
private const val OWN_PAGE_BUDGET_MS = 7_000L
private const val PAGE_FIRST_WINDOW_MS = 3_500L
private const val PAGE_MAX_WAIT_MS = 6_000L
private val STABLE_TYPES = setOf("contagem", "quantidade_periodo", "fato_historico")
private val CATEGORY_RANK = mapOf("direto" to 4, "incerto" to 3, "anterior" to 2, "contexto" to 1, "descartado" to 0, "nao_independente" to -1)
val CONTENT_LABEL = mapOf("completo" to "Conteúdo da página consultado", "trecho" to "Trecho fornecido pelo buscador",
    "titulo" to "Apenas título disponível", "resumo" to "Resumo da página (Wikipédia)")
val SOURCE_LABEL = mapOf("google_news" to "Google Notícias (RSS)", "bing_news" to "Bing Notícias (RSS)",
    "bing_web" to "Bing Web (RSS)", "wikipedia" to "Wikipédia (API)")

data class SourceStatus(val fonte: String, val nome: String, val consulta: String, val status: String, val quantidade: Int,
                        val erro: String? = null, val emCache: Boolean = false, val obtidoEm: Long? = null, val ms: Long = 0)

data class Republication(val veiculo: String, val url: String, val titulo: String, val data: Long?)

data class ResultCard(
    val titulo: String, val url: String, val observacaoLink: String?, val veiculo: String, val autor: String?,
    val data: Long?, val periodo: String, val tipoFonte: String, val relacao: String, val relacaoTexto: String,
    val conteudo: String, val conteudoTexto: String, val trecho: String?, val trechosPagina: List<String>,
    val alertas: List<String>, val republicacoes: List<Republication>,
    /** Relação com a afirmação (RelationKind) e o que a fonte mostra sobre o detalhe. */
    val relacaoTipo: String = RelationKind.INDEFINIDO,
    val resumoRelacao: String = "",
    val trechoRelacao: String? = null,
    /** pagina | trecho | titulo: quanto do conteúdo o Lume realmente leu. */
    val baseLeitura: String = "titulo",
    val detalheStatus: String = DetailStatus.SEM_DETALHE,
    /** Comparação do acontecimento: quem, ação, assunto, data, números (frases simples, sem jargão). */
    val motivos: List<String>? = null,
    /** Trechos literais da matéria importada e da fonte usados na comparação. */
    val trechosComparacao: List<EventEvidence>? = null,
)

data class ResearchResult(
    val idConsulta: String,
    val consultadoEm: Long,
    val interpretacao: Interpretation,
    val status: String, // ok | insuficiente | ambigua | erro | entrada_invalida
    val mensagem: String,
    val avisos: List<String> = emptyList(),
    val sintese: Synthesis? = null,
    val detalhesAdicionais: List<Synthesis> = emptyList(),
    val contexto: List<ContextItem> = emptyList(),
    val sugestoes: List<Option> = emptyList(),
    val resultados: Map<String, List<ResultCard>> = emptyMap(),
    val observacoes: List<String> = emptyList(),
    val fontesConsultadas: List<SourceStatus> = emptyList(),
    val descartados: Int = 0,
    val duracaoMs: Long = 0,
    val trechoDaEntrada: String? = null,
    val comparacaoSemanticaLocal: Boolean = false,
    /** Diagnóstico de onde cada fonte entrou ou saiu. Não é salvo no histórico. */
    @Transient val diagnostico: ResearchTrace? = null,
)

/**
 * Uma linha do diagnóstico. `etapa`: triagem | duplicata | republicacao | limite | exibida.
 * Permite distinguir "o buscador não trouxe" de "o app recebeu e descartou".
 */
data class TraceItem(val etapa: String, val provedor: String, val consulta: String, val veiculo: String,
                     val titulo: String, val url: String, val resultado: String, val motivo: String)

data class ResearchTrace(val consultas: List<SourceStatus>, val itens: List<TraceItem>,
                         /** Candidatos recebidos (título, trecho, página lida), para montar casos de teste reproduzíveis. */
                         val candidatos: List<Candidate> = emptyList()) {
    fun count(etapa: String, resultado: String? = null) = itens.count { it.etapa == etapa && (resultado == null || it.resultado == resultado) }

    fun report(): String = buildString {
        appendLine("CONSULTAS (${consultas.size})")
        consultas.forEach { appendLine("  [${it.fonte}] '${it.consulta}' -> ${it.status} ${it.quantidade}${it.erro?.let { e -> " ($e)" } ?: ""}") }
        for (etapa in listOf("triagem", "independencia", "acontecimento", "duplicata", "republicacao", "releitura", "limite", "exibida", "relacao")) {
            val rows = itens.filter { it.etapa == etapa }
            appendLine("${etapa.uppercase()} (${rows.size})")
            rows.forEach { appendLine("  ${it.resultado} | ${it.veiculo} | ${it.titulo.take(110)} | ${it.motivo}") }
        }
    }
}

/** Uma afirmação (modo "unica") ou várias avaliadas separadamente (modo "partes"). */
data class Evaluation(val idConsulta: String, val modo: String, val status: String, val mensagem: String,
                      val partes: List<ResearchResult>,
                      /** Estilo do texto de entrada (opcional; não verifica fatos nem altera as fontes). */
                      val sinalTexto: TextSignal? = null)

fun sourceType(domain: String, origin: String): String = when {
    origin == "wikipedia" -> "enciclopedia"
    Regex("\\.(gov|gob|mil|leg|jus|mp|def)(\\.[a-z]{2})?$").containsMatchIn(domain) -> "instituicao_publica"
    Regex("\\.edu(\\.[a-z]{2})?$|\\.ac\\.[a-z]{2}$").containsMatchIn(domain) || listOf("scielo.org", "scielo.br", "nih.gov",
        "fiocruz.br", "nature.com", "science.org", "thelancet.com", "nejm.org", "bmj.com", "cochrane.org", "arxiv.org")
        .any { domain.endsWith(it) } || Regex("(^|\\.)u[a-z]{1,8}\\.br$").containsMatchIn(domain) -> "cientifica"
    listOf("instagram.com", "facebook.com", "x.com", "twitter.com", "tiktok.com", "youtube.com", "threads.net", "kwai.com")
        .any { domain.endsWith(it) } -> "rede_social"
    domain.endsWith(".int") -> "organismo_internacional"
    origin == "google_news" || origin == "bing_news" -> "noticia"
    else -> "web"
}

/** Cache em memória com prazo. Listas de resultados são copiadas para não vazar alterações entre pesquisas. */
class TtlCache(private val maxItems: Int = 300) {
    private class Entry(val value: Any, val storedAt: Long, val expiresAt: Long)
    private val data = LinkedHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Any> get(key: String, now: Long): Pair<T, Long>? {
        val e = data[key] ?: return null
        if (now > e.expiresAt) { data.remove(key); return null }
        val v = e.value
        val copy = if (v is List<*> && v.firstOrNull() is Candidate) (v as List<Candidate>).map { it.copy() } else v
        return (copy as T) to e.storedAt
    }

    @Synchronized
    fun put(key: String, value: Any, ttl: Long, now: Long) {
        if (data.size >= maxItems) data.remove(data.keys.first())
        val stored = if (value is List<*> && value.firstOrNull() is Candidate) value.map { (it as Candidate).copy() } else value
        data[key] = Entry(stored, now, now + ttl)
    }
}

private fun logI(msg: String) { runCatching { Log.i(TAG, msg) } }
private fun logW(msg: String) { runCatching { Log.w(TAG, msg) } }

class ResearchService(
    private val fetcher: Fetcher = OkHttpFetcher(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fetchPages: Boolean = true,
    private val cache: TtlCache = TtlCache(),
    private val semanticRanker: SemanticRanker? = null,
    private val pairScorer: PairScorer? = null,
    /** Cosseno do MPNet AJUSTADO, só para o modelo de "mesmo fato"; a regra segue usando [semanticRanker], inalterado. */
    private val pairRanker: SemanticRanker? = null,
    /** Estilo do texto (SVM do FakeRecogna, servidor local). Roda em paralelo com a busca e nunca a atrasa. */
    private val textSignal: TextSignalScorer? = null,
) {
    // ---- fontes ------------------------------------------------------------------------------
    private suspend fun call(name: String, query: String): Pair<List<Candidate>, SourceStatus> {
        val ttl = when (name) { "google_news", "bing_news" -> NEWS_TTL; "bing_web" -> WEB_TTL; else -> WIKI_TTL }
        val key = "$name|${norm(query)}"
        val label = SOURCE_LABEL.getValue(name)
        cache.get<List<Candidate>>(key, clock())?.let { (items, stored) ->
            return items to SourceStatus(name, label, query, if (items.isEmpty()) "vazio" else "ok", items.size, emCache = true, obtidoEm = stored)
        }
        val t0 = System.currentTimeMillis()
        var err: SourceException? = null
        var items: List<Candidate> = emptyList()
        for (attempt in 0..1) {
            try {
                items = when (name) {
                    "google_news" -> googleNews(query, fetcher)
                    "bing_news" -> bing(query, fetcher, "news")
                    "bing_web" -> bing(query, fetcher, "web")
                    else -> wikipedia(query, fetcher)
                }
                err = null
                break
            } catch (e: SourceException) {
                err = e
                if (e.kind !in setOf("tempo_esgotado", "rede") || attempt == 1) break
                logW("$name '$query': ${e.message}; tentando de novo")
            }
        }
        val ms = System.currentTimeMillis() - t0
        val e = err
        return if (e != null) {
            logW("$name '$query': FALHA ${e.kind}: ${e.message}")
            emptyList<Candidate>() to SourceStatus(name, label, query, "erro", 0, "${e.kind}: ${e.message}", false, clock(), ms)
        } else {
            cache.put(key, items, ttl, clock())
            logI("$name '$query': ${items.size} itens em $ms ms")
            items to SourceStatus(name, label, query, if (items.isEmpty()) "vazio" else "ok", items.size, null, false, clock(), ms)
        }
    }

    private suspend fun run(jobs: List<Pair<String, String>>, deadline: Long): Pair<List<Candidate>, List<SourceStatus>> = coroutineScope {
        val results = jobs.map { (name, q) ->
            async {
                withTimeoutOrNull(maxOf(100L, deadline - System.currentTimeMillis())) { call(name, q) }
                    ?: (emptyList<Candidate>() to SourceStatus(name, SOURCE_LABEL.getValue(name), q, "erro", 0,
                        "tempo_esgotado: prazo total da pesquisa", false, clock()))
            }
        }.awaitAll()
        results.flatMap { it.first } to results.map { it.second }
    }

    // ---- páginas -----------------------------------------------------------------------------
    private suspend fun page(url: String): PageInfo? {
        cache.get<PageInfo>("pagina|$url", clock())?.let { return it.first }
        return try {
            val info = fetchPage(url, fetcher)
            cache.put("pagina|$url", info, PAGE_TTL, clock())
            logI("página lida $url (${info.paragraphs.size} parágrafos, ${info.injectionDropped} trechos suspeitos ignorados)")
            info
        } catch (e: SourceException) {
            logI("página indisponível $url: ${e.kind} ${e.message}")
            null
        }
    }

    private suspend fun enrich(groups: List<Group>, interp: Interpretation, deadline: Long, similarity: Map<String, Double> = emptyMap()) = coroutineScope {
        val q = interp.quantity
        val stable = interp.afirmacao?.tipo in STABLE_TYPES
        val allowed = if (stable) setOf("bing_news", "bing_web", "wikipedia") else setOf("bing_news", "bing_web")
        val ranked = if (q == null) groups.sortedWith(compareBy({ g -> -g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } },
            { g -> -(g.members.maxOfOrNull { similarity[it.first.url] ?: 0.0 } ?: 0.0) })) else groups.sortedBy { g ->
            // Para quantidades, ler primeiro as páginas que citam a unidade (ex.: "títulos", "%").
            if (g.members.any { (c, _) -> q.chaveUnidade in stemsOfAll(c.title + " " + c.snippet) || (q.chaveUnidade == "%" && "%" in c.snippet) }) 0 else 1
        }
        val cap = 8
        val targets = mutableListOf<Candidate>()
        for (g in ranked) {
            // Até 2 páginas por grupo: confirma se itens de título parecido são mesmo republicações.
            var perGroup = 0
            for ((cand, _) in g.members.sortedByDescending { CONTENT_RANK.getValue(it.first.contentKind) }) {
                if (targets.size >= cap || perGroup >= 2) break
                if (cand.origin in allowed) { targets.add(cand); perGroup++ }
            }
        }
        // Não espera a página mais lenta: depois de PAGE_FIRST_WINDOW_MS segue com o que chegou, desde que pelo menos
        // metade das páginas tenha respondido; senão espera até PAGE_MAX_WAIT_MS. Quem não chegou fica com o trecho do buscador.
        val budget = minOf(8_000L, maxOf(100L, deadline - System.currentTimeMillis()))
        val got = java.util.concurrent.atomic.AtomicReferenceArray<PageInfo?>(targets.size)
        val jobs = targets.mapIndexed { i, c -> async { got.set(i, withTimeoutOrNull(budget) { page(c.url) }) } }
        val start = System.currentTimeMillis()
        withTimeoutOrNull(minOf(PAGE_FIRST_WINDOW_MS, budget)) { jobs.joinAll() }
        if (jobs.count { it.isCompleted } * 2 < jobs.size) withTimeoutOrNull(maxOf(0L, minOf(PAGE_MAX_WAIT_MS, budget) - (System.currentTimeMillis() - start))) { jobs.joinAll() }
        val late = jobs.count { !it.isCompleted }
        jobs.forEach { if (!it.isCompleted) it.cancel() }
        if (late > 0) logI("leitura de páginas: $late de ${jobs.size} não chegaram a tempo; seguem com o trecho do buscador")
        val infos = targets.mapIndexed { i, c -> got.get(i) to c }
        val claimStems = interp.termos.map { stem(it) }.toSet() + interp.entidades.flatMap { it.split(" ") }.map { stem(it) }
        for ((info, cand) in infos) {
            if (info == null) {
                cand.alerts.add("Página indisponível para leitura; exibido apenas o trecho do buscador.")
                continue
            }
            if (info.injectionDropped > 0) cand.alerts.add("A página continha texto com instruções para sistemas automáticos; foi ignorado.")
            val body = info.paragraphs.joinToString(" ")
            if (body.length < 500) {
                cand.alerts.add("Não foi possível extrair o texto da matéria (conteúdo curto, fechado ou dinâmico).")
                continue
            }
            cand.contentKind = "completo"
            cand.pageText = body.take(20_000)
            // Na Wikipédia a data da página é a de criação, não a da informação.
            if (cand.origin != "wikipedia") cand.published = cand.published ?: info.published
            cand.pageAuthor = info.author.take(120)
            if (info.siteName.isNotEmpty() && cand.origin == "bing_web") cand.sourceName = info.siteName.take(80)
            cand.pageExcerpts = body.split(Regex("(?<=[.!?])\\s+")).filter { it.length in 50..450 }
                .map { s -> (Regex("[\\p{L}\\p{N}_]+").findAll(s).map { it.value }.filter { it.length >= 3 }.map { stem(it) }.toSet() intersect claimStems).size to s }
                .filter { it.first >= 2 }.sortedByDescending { it.first }.take(2).map { it.second }
        }
    }

    private fun stemsOfAll(text: String) = norm(text).split(" ").map { stem(it) }.toSet()

    // ---- pesquisa ----------------------------------------------------------------------------
    private fun plan(interp: Interpretation): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
        val queries = interp.consultas.map { it.texto }
        val stable = interp.afirmacao?.tipo in STABLE_TYPES
        val jobs = mutableListOf<Pair<String, String>>()
        val wikiQ: List<String>
        if (stable) {
            // Fatos estáveis e quantidades: priorizar referências e registros, não só notícias recentes.
            queries.take(3).forEach { jobs.add("bing_web" to it) }
            queries.firstOrNull()?.let { jobs.add("google_news" to it); jobs.add("bing_news" to it) }
            wikiQ = listOf(interp.consultaContextoPropriedade, interp.consultaContexto) + queries.drop(1).take(1)
        } else {
            // Duas consultas nos dois buscadores de notícias; variações de redação e detalhe só no Google Notícias
            // (mais veículos por consulta), para ampliar a cobertura sem multiplicar requisições.
            for (q in queries.take(2)) { jobs.add("google_news" to q); jobs.add("bing_news" to q) }
            for (q in queries.drop(2).take(3)) jobs.add("google_news" to q)
            queries.firstOrNull()?.let { jobs.add("bing_web" to it) }
            wikiQ = listOf(interp.consultaContextoPropriedade, interp.consultaContexto)
        }
        wikiQ.filter { it.isNotBlank() }.distinct().forEach { jobs.add("wikipedia" to it) }
        val extra = emptyList<Pair<String, String>>()
        return jobs to extra
    }

    /** Todo o processamento (análise de HTML, regras) roda fora da thread principal. */
    suspend fun search(text: String, requestId: String, context: ArticleContext? = null,
                       ownText: java.util.concurrent.atomic.AtomicReference<String?>? = null,
                       onPreview: ((ResearchResult) -> Unit)? = null): ResearchResult =
        withContext(Dispatchers.Default) { doSearch(text, requestId, context, ownText, onPreview) }

    private suspend fun doSearch(text: String, requestId: String, context: ArticleContext?,
                                 ownText: java.util.concurrent.atomic.AtomicReference<String?>? = null,
                                 onPreview: ((ResearchResult) -> Unit)? = null): ResearchResult {
        val t0 = System.currentTimeMillis()
        val deadline = t0 + TOTAL_BUDGET_MS
        val now = clock()
        val interp = interpret(text, now, context)
        if (interp.assunto.isBlank() || !Regex("$W{2,}").containsMatchIn(interp.assunto)) {
            return ResearchResult(requestId, now, interp, "entrada_invalida", "Digite uma notícia, afirmação ou pergunta para pesquisar.")
        }
        logI("[$requestId] entrada: '${interp.assunto.take(120)}' | tipo=${interp.afirmacao?.tipo} | siglas=${interp.aliases} | " +
            "variações=${interp.vocabulario} | prazos=${interp.prazos} | consultas: ${interp.consultas.map { it.texto }}")
        val (jobs, extra) = plan(interp)
        val marks = linkedMapOf<String, Long>()
        var lastMark = t0
        fun mark(etapa: String) { val n = System.currentTimeMillis(); marks[etapa] = n - lastMark; lastMark = n }
        val (cands0, st0) = run(jobs, deadline)
        val cands = cands0.toMutableList()
        val statuses = st0.toMutableList()
        val pairs = cands.map { it to assess(interp, it, now) }.toMutableList()
        if (pairs.count { it.second.category == "direto" } < 3 && extra.isNotEmpty()) {
            val (more, st2) = run(extra, deadline)
            cands += more
            pairs += more.map { it to assess(interp, it, now) }
            statuses += st2
        }
        // Similaridade de sentido (modelo local, opcional): só ordena candidatas e, na comparação estruturada,
        // desempata se o ASSUNTO é equivalente quando quem agiu e a ação já coincidem. Não classifica sozinha.
        mark("busca")
        val simByUrl = HashMap<String, Double>()
        // Modelo de "mesmo fato" (opcional): cosseno do MPNet ajustado + sobreposição + nota do BERTimbau ajustado.
        // Só promove fontes que a regra deixou em dúvida a "provável mesmo fato"; nunca rebaixa nem vira veredito.
        val modelByUrl = HashMap<String, Double>()
        // As três consultas locais dependem só dos candidatos: rodam juntas, não em fila.
        val news = pairs.map { it.first }.filter { it.origin != "wikipedia" }.distinctBy { it.url }.take(60)
        val titles = news.map { it.title }
        if (news.isNotEmpty()) coroutineScope {
            fun left(cap: Long) = maxOf(100L, minOf(cap, deadline - System.currentTimeMillis()))
            val sem = semanticRanker?.let { r -> async { withTimeoutOrNull(left(20_000L)) { r.similarities(interp.assunto, titles) } } }
            val useModel = pairScorer != null && pairRanker != null
            val cos = if (useModel) async { withTimeoutOrNull(left(20_000L)) { pairRanker!!.similarities(interp.assunto, titles) } } else null
            val bert = if (useModel) async { withTimeoutOrNull(left(25_000L)) { pairScorer!!.scores(interp.assunto, titles) } } else null
            sem?.await()?.let { values -> news.zip(values).forEach { (c, v) -> simByUrl[c.url] = v } }
            val cosines = cos?.await()
            val notas = bert?.await()
            if (cosines != null && notas != null) news.indices.forEach { i ->
                modelByUrl[news[i].url] = PairModel.nota(cosines[i], PairModel.sobreposicao(interp.assunto, news[i].title), notas[i])
            }
        }
        if (pairScorer != null) logI("modelo mesmo fato: ${modelByUrl.size} notas, ${modelByUrl.count { it.value >= PairModel.CORTE }} acima do corte ${PairModel.CORTE}")
        mark("sentido_e_modelo")
        val semanticUsed = simByUrl.isNotEmpty()
        // Comparação estruturada do acontecimento (quem, ação e etapa, assunto, data, números, negação) e
        // independência em relação à matéria importada. Vale para afirmações sobre acontecimentos com ação reconhecida.
        val original = if (interp.afirmacao?.tipo !in STABLE_TYPES) originalEvent(interp.assunto, context, now).takeIf { it.frame.action != null } else null
        val comparisons = java.util.IdentityHashMap<Candidate, EventComparison>()
        val independence = java.util.IdentityHashMap<Candidate, IndependenceCheck>()
        fun judgeEvent(c: Candidate, a: Assessment) {
            if (c.origin == "wikipedia") return
            independenceOf(context, c)?.let { ind ->
                independence[c] = ind; a.category = "nao_independente"; a.relation = ind.motivo; a.strong = false; return
            }
            val o = original ?: run {
                // Sem ação reconhecida não há comparação estruturada: o modelo só promove o que a triagem por termos não aceitou.
                val nota = modelByUrl[c.url]
                if (nota != null) logI("modelo par: regra=sem_acao:${a.category} nota=${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(100)}")
                if (nota != null && nota < PairModel.AVISO_ABAIXO && a.category == "direto") {
                    a.alerts.add(AVISO_MODELO)
                    logI("modelo avisa (regra disse mesmo): nota ${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(90)}")
                }
                if (nota != null && nota >= PairModel.CONFIRMA_SEM_ACAO && a.category in setOf("contexto", "descartado", "incerto")) {
                    a.category = "direto"; a.strong = false
                    a.relation = "Mesmo acontecimento, confirmado pelo modelo de linguagem"
                    a.alerts.add("A regra do app não confirmou; o modelo de linguagem deu nota alta. Confira a fonte.")
                    logI("modelo confirmou (sem ação): nota ${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(90)}")
                } else if (nota != null && nota >= PairModel.CORTE && a.category in setOf("contexto", "descartado")) {
                    a.category = "incerto"; a.strong = false
                    a.relation = "Provável mesmo fato (modelo de linguagem); a regra do app não confirmou"
                    a.alerts.add("Indicação do modelo de linguagem, sem confirmação pelas regras do app; confira a fonte.")
                    logI("modelo promoveu (sem ação): nota ${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(90)}")
                }
                return
            }
            var cmp = compareEvent(o, c, now, simByUrl[c.url])
            val nota = modelByUrl[c.url]
            if (nota != null) logI("modelo par: regra=${cmp.kind} nota=${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(100)}")
            if (nota != null) {
                val passa = nota >= PairModel.CORTE_COM_ACAO
                val linha = "Modelo de linguagem: nota ${"%.2f".format(java.util.Locale.US, nota)} (corte ${"%.2f".format(java.util.Locale.US, PairModel.CORTE_COM_ACAO)}) — " +
                    if (passa) "indica o mesmo fato" else "não indica o mesmo fato"
                cmp = if (nota >= PairModel.CONFIRMA_COM_ACAO && cmp.kind in setOf(EventRelation.INCERTA, EventRelation.CONTEXTO, EventRelation.OUTRO) && !cmp.anterior) {
                    cmp.copy(kind = EventRelation.MESMO, resumo = "Mesmo acontecimento, confirmado pelo modelo de linguagem",
                        motivos = cmp.motivos + linha, alertas = cmp.alertas + "A regra do app não confirmou; o modelo de linguagem deu nota alta. Confira a fonte.")
                } else if (passa && cmp.kind in setOf(EventRelation.INCERTA, EventRelation.CONTEXTO, EventRelation.OUTRO) && !cmp.anterior) {
                    val recente = c.published?.let { now - it <= 2 * 86_400_000L } == true
                    cmp.copy(kind = EventRelation.INCERTA,
                        resumo = if (recente) "Provável mesmo fato (modelo de linguagem); a regra do app não confirmou" else "Mesmo assunto, confira a data (modelo de linguagem); a regra do app não confirmou",
                        motivos = cmp.motivos + linha, alertas = cmp.alertas + "Indicação do modelo de linguagem, sem confirmação pelas regras do app; confira a fonte.")
                } else if (nota < PairModel.AVISO_ABAIXO && cmp.kind in setOf(EventRelation.MESMO, EventRelation.DIVERGENTE)) {
                    logI("modelo avisa (regra disse mesmo): nota ${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(90)}")
                    cmp.copy(motivos = cmp.motivos + linha, alertas = cmp.alertas + AVISO_MODELO)
                } else cmp.copy(motivos = cmp.motivos + linha)
            }
            comparisons[c] = cmp
            if (nota != null && cmp.resumo.contains("modelo de linguagem")) logI("modelo promoveu: nota ${"%.2f".format(java.util.Locale.US, nota)} | ${c.title.take(90)}")
            a.strong = false
            when (cmp.kind) {
                EventRelation.MESMO, EventRelation.DIVERGENTE -> { a.category = "direto"; a.relation = cmp.resumo }
                EventRelation.INCERTA -> { a.category = "incerto"; a.relation = cmp.resumo }
                EventRelation.CONTEXTO -> { a.category = if (cmp.anterior) "anterior" else "contexto"; a.relation = cmp.resumo }
                else -> { a.category = "descartado"; a.reason = "Outro acontecimento: ${cmp.resumo}" }
            }
        }
        pairs.forEach { (c, a) -> judgeEvent(c, a) }
        val trace = mutableListOf<TraceItem>()
        fun row(etapa: String, c: Candidate, resultado: String, motivo: String) =
            trace.add(TraceItem(etapa, c.origin, c.query, c.sourceName, c.title, c.url, resultado, motivo))
        pairs.forEach { (c, a) -> row("triagem", c, a.category, if (a.category == "descartado") a.reason else a.relation) }
        pairs.forEach { (c, _) -> independence[c]?.let { row("independencia", c, it.kind, it.motivo) } }
        pairs.forEach { (c, _) -> comparisons[c]?.let { cmp ->
            row("acontecimento", c, cmp.kind, "${cmp.resumo} | ${cmp.motivos.joinToString("; ")}")
        } }
        val discarded = pairs.filter { it.second.category == "descartado" }
        val ownGroups = pairs.filter { it.second.category == "nao_independente" }.distinctBy { canonical(it.first.url) }
            .map { Group(mutableListOf(it)) }
        var groups = groupCandidates(pairs.filter { it.second.category !in setOf("descartado", "nao_independente") }) { dropped, kept ->
            row("duplicata", dropped, "removida", "mesma URL já recebida de ${kept.origin}")
        }.sortedWith(compareBy({ g -> -g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } }, { g -> -g.members.maxOf { it.second.score } }))
        groups.forEach { g -> g.members.filter { it !== g.lead }.forEach { (c, _) ->
            row("republicacao", c, "agrupada", "título/trecho quase igual a ${g.lead.first.sourceName}")
        } }
        // Monta o resultado a partir dos grupos. Chamada uma vez com prévia (antes de ler as páginas, em cópias dos grupos)
        // e uma vez no final; a prévia não altera candidatos nem o diagnóstico do resultado final.
        suspend fun assemble(groupsIn: List<Group>, trace: MutableList<TraceItem>, final: Boolean): ResearchResult {
            fun row(etapa: String, c: Candidate, resultado: String, motivo: String) =
                trace.add(TraceItem(etapa, c.origin, c.query, c.sourceName, c.title, c.url, resultado, motivo))
            var groups = groupsIn
            // Depois de ler a página, compara de novo com o conteúdo lido: pode confirmar (outras palavras no título),
            // rebaixar (outra data, outro envolvido) ou revelar republicação creditada ao veículo da captura.
            for (g in groups) for (i in g.members.indices) {
                val (c, old) = g.members[i]
                if (c.pageText.isBlank()) continue
                val re = assess(interp, c, now, c.pageText)
                val eventMode = original != null || independenceOf(context, c) != null
                if (eventMode) {
                    judgeEvent(c, re)
                    if (re.category != old.category || re.relation != old.relation) {
                        g.members[i] = c to re
                        row("releitura", c, re.category, "página lida: ${if (re.category == "descartado") re.reason else re.relation}")
                    }
                } else {
                    // Sem ação reconhecida, a triagem é por palavras em comum. A página só pode PROMOVER a fonte pelo que trata
                    // como assunto principal (resumo e frases de abertura que retomam o título); palavras achadas mais abaixo
                    // (antecedentes, outras notícias, menções) não fazem da matéria um relato do mesmo acontecimento.
                    val lead = leadOf(c)
                    val reLead = if (lead.isBlank()) old else assess(interp, c, now, lead)
                    if (CATEGORY_RANK.getValue(reLead.category) > CATEGORY_RANK.getValue(old.category)) {
                        g.members[i] = c to reLead
                        row("releitura", c, reLead.category, "página lida (abertura): ${reLead.relation}")
                    } else if (CATEGORY_RANK.getValue(re.category) > CATEGORY_RANK.getValue(old.category)) {
                        row("releitura", c, old.category, "página lida: os termos aparecem só no corpo, não na abertura; mantido como ${old.category}")
                    }
                }
            }
            // Fonte que, lida a página, virou "outro acontecimento" ou republicação sai dos grupos exibidos.
            groups = groups.onEach { g -> g.members.removeAll { it.second.category in setOf("descartado", "nao_independente") } }
                .filter { it.members.isNotEmpty() }
            groups = groups.sortedWith(compareBy({ g -> -g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } }, { g -> -g.members.maxOf { it.second.score } }))

            if (final) mark("releitura")
            val evGroups = groups.take(20)
            val nDirect = groups.count { g -> g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } == CATEGORY_RANK.getValue("direto") }
            // Afirmação sobre algo que acontece numa data (traz "hoje", "amanhã"… ou vem de uma publicação datada e descreve
            // uma ação): só fontes sobre o mesmo acontecimento, no mesmo período, esclarecem o detalhe. Publicações
            // anteriores e fontes relacionadas viram contexto. Contagens e fatos estáveis continuam aceitando fontes antigas.
            val happening = interp.quantity != null && interp.afirmacao?.tipo != "quantidade_periodo" && (interp.tempoRelativo.isNotEmpty() ||
                interp.dataReferencia != null && originalEvent(interp.assunto, context, now).frame.action != null)
            val contextOnly: (Candidate, Assessment) -> String? = { c, a ->
                val pub = c.published.takeIf { c.origin != "wikipedia" }
                val ref = interp.dataReferencia
                when {
                    !happening -> null
                    a.category == "anterior" -> "contexto_anterior"
                    pub != null && ref != null && pub < ref - REFERENCE_WINDOW_MS -> "contexto_anterior"
                    pub != null && ref == null && interp.tempoRelativo.isNotEmpty() && now - pub > RELATIVE_MAX_AGE_MS -> "contexto_anterior"
                    a.category != "direto" -> "contexto_relacionado"
                    else -> null
                }
            }
            var sintese = synthesize(interp, evGroups, nDirect, contextOnly)
            val evByGroup = sintese.usadas.groupBy { it.group }
            val buckets = linkedMapOf<String, MutableList<Group>>("responde" to mutableListOf(), "direto" to mutableListOf(),
                "incerto" to mutableListOf(), "anterior" to mutableListOf(), "contexto" to mutableListOf(), "contexto_geral" to mutableListOf(),
                "nao_independente" to ownGroups.toMutableList())
            groups.forEachIndexed { i, g ->
                var cat = g.members.maxBy { CATEGORY_RANK.getValue(it.second.category) }.second.category
                if (i in evByGroup) cat = "responde" else if (g.lead.first.origin == "wikipedia") cat = "contexto_geral"
                buckets.getValue(cat).add(g)
            }
            val limits = mapOf("responde" to 6, "direto" to 20, "incerto" to 6, "anterior" to 4, "contexto" to 8, "contexto_geral" to 2, "nao_independente" to 6)
            val index = groups.withIndex().associate { (i, g) -> System.identityHashCode(g) to i }
            if (final) mark("sintese")
            val results = buckets.mapValues { (k, v) -> v.take(limits.getValue(k)).map { g -> card(g, k, evByGroup[index[System.identityHashCode(g)]], interp, comparisons, independence) } }
            buckets.forEach { (k, v) ->
                v.drop(limits.getValue(k)).forEach { g -> row("limite", g.lead.first, k, "acima do limite de ${limits.getValue(k)} na seção") }
                v.take(limits.getValue(k)).forEach { g -> row("exibida", g.lead.first, k, g.lead.second.relation) }
            }
            results.values.flatten().forEach { c ->
                trace.add(TraceItem("relacao", "", "", c.veiculo, c.titulo, c.url, c.relacaoTipo, "${c.resumoRelacao} [leitura: ${c.baseLeitura}]${c.trechoRelacao?.let { t -> " «${t.take(140)}»" } ?: ""}"))
            }

            if (final) mark("cartoes")
            val searchOk = statuses.any { it.status in setOf("ok", "vazio") && it.fonte in setOf("google_news", "bing_news", "bing_web") }
            val failed = statuses.filter { it.status == "erro" }
            val status: String
            val msg: String
            var sugestoes = emptyList<Option>()
            when {
                !searchOk -> {
                    status = "erro"
                    msg = "Não foi possível consultar as fontes. Verifique a conexão e tente novamente."
                    sintese = Synthesis("erro_tecnico", SITUACAO_FRASE.getValue("erro_tecnico"), interp.afirmacao?.detalheVerificavel, "nenhum")
                }
                sintese.opcoes.isNotEmpty() -> {
                    status = "ambigua"
                    msg = "O nome pesquisado corresponde a mais de uma entidade nas fontes. O que você quer saber?"
                    sugestoes = sintese.opcoes
                }
                results.getValue("responde").isNotEmpty() || results.getValue("direto").isNotEmpty() -> {
                    status = "ok"; msg = "Pesquisa concluída."
                }
                results.getValue("incerto").isNotEmpty() -> {
                    status = "insuficiente"
                    msg = "Encontramos publicações parecidas, mas o que foi lido não basta para dizer se tratam do mesmo acontecimento. " +
                        "Isso não significa que seja falso."
                }
                else -> {
                    status = "insuficiente"
                    msg = "A pesquisa foi concluída, mas não encontrou publicações que tratem diretamente deste detalhe. " +
                        "Isso não significa que seja falso." +
                        if (results.getValue("anterior").isNotEmpty()) " Há publicações anteriores sobre assunto semelhante; elas não mostram que o fato ocorreu agora." else ""
                }
            }
            val avisos = if (failed.isNotEmpty() && searchOk)
                listOf("Algumas fontes não responderam; os resultados podem estar incompletos.") else emptyList()
            sintese = sintese.copy(indicacao = indicate(sintese, interp))

            val extraDetails = mutableListOf<Synthesis>()
            val first = interp.quantity
            if (first != null && status != "erro") {
                // Outros valores na mesma frase ("8 títulos nacionais e 3 estaduais") são avaliados separadamente.
                val entity = interp.entidades.firstOrNull()
                val entTokens = entity?.let { norm(it).split(" ").toSet() } ?: emptySet()
                for (q2 in extractQuantities(interp.assunto, entTokens).take(4)) {
                    if (q2.posicao == first.posicao || extraDetails.size >= 2) continue
                    val i2 = interp.copy(quantity = q2, afirmacao = interp.afirmacao?.copy(quantidade = q2, detalheNegado = q2.negada,
                        detalheVerificavel = detailText(q2, entity)))
                    val s2 = synthesize(i2, evGroups, nDirect, contextOnly)
                    extraDetails.add(s2.copy(indicacao = indicate(s2, i2)))
                }
            }
            val answerTexts = sintese.evidencias.map { it.trecho } + extraDetails.flatMap { s -> s.evidencias.map { it.trecho } }
            if (final) mark("detalhes")
            val contexto = if (status == "erro") emptyList() else selectContext(interp, groups, answerTexts)
            if (final) mark("contexto")
            val result = ResearchResult(
                idConsulta = requestId, consultadoEm = now, interpretacao = interp, status = status, mensagem = msg,
                avisos = avisos, sintese = sintese, detalhesAdicionais = extraDetails, contexto = contexto,
                sugestoes = sugestoes, resultados = results,
                observacoes = observations(interp, buckets.getValue("direto") + buckets.getValue("responde"), groups) + ownObservations(independence.values.toList(), context),
                fontesConsultadas = statuses.sortedWith(compareBy({ it.fonte }, { it.consulta })),
                descartados = discarded.size, duracaoMs = System.currentTimeMillis() - t0,
                comparacaoSemanticaLocal = semanticUsed,
                diagnostico = ResearchTrace(statuses.toList(), trace.toList(), cands.map { it.copy() }),
            )
            if (final) {
                logI("[$requestId] status=$status sintese=${sintese.situacao} indicacao=${sintese.indicacao?.rotulo} " +
                    "responde=${results.getValue("responde").size} direto=${results.getValue("direto").size} descartados=${discarded.size} " +
                    "falhas=${failed.size} (${result.duracaoMs} ms)")
                logI("[$requestId] tempos: ${marks.entries.joinToString(" ") { "${it.key}=${it.value}ms" }} total=${result.duracaoMs}ms")
                failed.forEach { logW("[$requestId] fonte ${it.fonte} '${it.consulta}': ${it.erro}") }
            }
            return result
        }
        mark("julgamento")
        if (fetchPages && groups.isNotEmpty()) {
            // Prévia: o que a busca já mostra (títulos e trechos), enquanto as páginas são lidas.
            if (onPreview != null) runCatching {
                onPreview(assemble(groups.map { Group(it.members.toMutableList()) }, mutableListOf(), false))
                logI("[$requestId] prévia entregue em ${System.currentTimeMillis() - t0} ms")
            }
                .onFailure { logW("prévia falhou: ${it.message}") }
            coroutineScope {
                // A própria matéria importada sai dos grupos exibidos, mas o corpo dela alimenta o sinal do texto.
                val ownJob = if (ownText == null) null else async {
                    withTimeoutOrNull(OWN_PAGE_BUDGET_MS) { readOwn(requestId, pairs.map { it.first }, independence, context, text) }
                        ?.let { ownText.set(it) }
                }
                enrich(groups, interp, deadline, simByUrl)
                ownJob?.await()
            }
        } else if (ownText != null) {
            withTimeoutOrNull(OWN_PAGE_BUDGET_MS) { readOwn(requestId, pairs.map { it.first }, independence, context, text) }?.let { ownText.set(it) }
        }
        mark("paginas")
        return assemble(groups, trace, true)
    }

    /**
     * Candidato cujo corpo representa a matéria importada: a própria (endereço, ou veículo e título iguais) ou, sem veículo
     * identificado na captura, a de título igual (o texto de uma republicação é o mesmo). Nunca outro veículo com título parecido.
     */
    /**
     * Corpo da matéria importada, para o sinal do texto, na ordem: (1) link completo visível na captura; (2) candidato da
     * busca que é a própria matéria; (3) busca restrita ao domínio visível na captura (`site:domínio "título"`), que devolve o
     * endereço direto mesmo quando o buscador só entregou links de redirecionamento. Nunca usa outro veículo.
     */
    private suspend fun readOwn(requestId: String, cands: List<Candidate>, independence: Map<Candidate, IndependenceCheck>,
                                context: ArticleContext?, entry: String): String? {
        val title = context?.title?.takeIf { it.isNotBlank() } ?: entry.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val shown = context?.url?.trim()?.takeIf { it.isNotEmpty() }
        val shownUrl = shown?.let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
        val domain = shownUrl?.let { domainOf(it) }.orEmpty()
        // O Chrome corta o endereço na barra ("g1.globo.com/mundo/uc"): só vale como link se terminar como uma página.
        val hasPath = (shownUrl?.let { runCatching { java.net.URI(it).path }.getOrNull() }?.trim('/')?.length ?: 0) > 3
        val complete = (context?.urlComplete == true && hasPath) || shownUrl?.let { runCatching { java.net.URI(it).path }.getOrNull() }
            ?.let { Regex("(?i)\\.(?:s?html?|ghtml|php|aspx?|amp)$|/\\d{5,}/?$").containsMatchIn(it) } == true
        suspend fun read(u: String, how: String): String? {
            val body = page(u)?.paragraphs?.joinToString(" ").orEmpty()
            val ok = body.length >= 500
            logI("[$requestId] própria matéria: via $how | ${u.take(110)} | ${if (ok) "${body.split(' ').size} palavras lidas" else "página sem texto utilizável"}")
            return if (ok) body.take(MAX_SIGNAL_CHARS) else null
        }
        if (complete && shownUrl != null && safeLink(shownUrl)) read(shownUrl, "link visível na captura")?.let { return it }
        ownCandidate(cands, independence, context, entry)?.let { c0 ->
            directPage(c0)?.let { d -> read(d.url, "candidato da busca (${d.sourceName}, ${d.origin})")?.let { return it } }
        }
        if (domain.isNotEmpty() && title.length >= 20) {
            val q = "site:$domain \"${title.take(140)}\""
            val inDomain = call("bing_web", q).first.filter { c ->
                "news.google.com" !in c.url && domainOf(c.url).let { d -> d == domain || d.endsWith(".$domain") || domain.endsWith(".$d") }
            }
            // O domínio já restringe ao veículo; o título lido por OCR pode ter pequenas diferenças (aspas, sufixos).
            val tk = titleKey(title)
            val scored = inDomain.map { c -> c to jaccard(tk, titleKey(c.title)) }
            val hit = scored.filter { (c, j) -> j >= 0.6 && numbersIn(title) == numbersIn(c.title) }.maxByOrNull { it.second }?.first
            if (hit == null) logI("[$requestId] própria matéria: busca no domínio sem título compatível (${inDomain.size} do domínio; melhor " +
                "${"%.2f".format(java.util.Locale.US, scored.maxOfOrNull { it.second } ?: 0.0)}: ${scored.maxByOrNull { it.second }?.first?.title?.take(80)})")
            if (hit != null) read(hit.url, "busca no domínio $domain")?.let { return it }
        }
        logI("[$requestId] própria matéria: não localizada (domínio na captura: ${domain.ifEmpty { "não" }}; título: ${title.take(60)})")
        return null
    }

    /** O Google News só entrega um link de redirecionamento: procura a mesma matéria (título igual) com o endereço direto. */
    private suspend fun directPage(c: Candidate): Candidate? {
        if ("news.google.com" !in c.url) return c
        val title = c.title.substringBeforeLast(" - ").trim().ifEmpty { c.title }
        for (src in listOf("bing_news", "bing_web")) {
            call(src, title).first.firstOrNull { "news.google.com" !in it.url && sameTitleAs(title, it) }?.let { return it }
        }
        return null
    }

    private fun ownCandidate(cands: List<Candidate>, independence: Map<Candidate, IndependenceCheck>,
                             context: ArticleContext?, entry: String): Candidate? {
        val entryTitle = if (context?.title.isNullOrBlank()) entry.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.length in 20..300 } else null
        fun isOwn(c: Candidate): Boolean {
            val i = independence[c]
            return i?.kind == Independence.PROPRIA ||
                (i?.kind == Independence.REPUBLICACAO && i.motivo.startsWith("mesmo título da matéria importada")) ||
                // Sem matéria lida na captura (texto digitado, colado ou compartilhado só com a manchete): título igual ao da entrada.
                (entryTitle != null && c.origin != "wikipedia" && sameTitleAs(entryTitle, c))
        }
        val own = cands.filter { isOwn(it) }
        // Links de redirecionamento do Google News não são a página: prefere o endereço direto da mesma matéria.
        return own.firstOrNull { "news.google.com" !in it.url && it.origin != "google_news" && independence[it]?.kind == Independence.PROPRIA }
            ?: own.firstOrNull { "news.google.com" !in it.url }
            ?: own.firstOrNull()
    }

    /** Abertura da matéria lida: frases do início da página que retomam o título (pula menus, legendas e antecedentes). */
    private fun leadOf(c: Candidate): String {
        val titleStems = stemsOf(c.title)
        return sentencesIn(c.pageText.take(4000)).filter { (stemsOf(it) intersect titleStems).size >= 2 }.take(3).joinToString(" ")
    }

    /** Frases com afirmação própria. Frases sem nome próprio herdam as entidades da primeira. */
    fun splitClaims(text: String, limit: Int = 3): List<Pair<String, String>> {
        val clean = BYLINE_RE.replace(cleanInput(text), " ")
        val claims = mutableListOf<Pair<String, String>>()
        var mainEnts: List<String>? = null
        for (sent in sentencesOf(clean)) {
            val content = norm(sent).split(" ").filter { it !in STOP_NORM && it.length >= 3 }
            if (content.size < 4 && extractQuantities(sent).isEmpty()) continue
            val ents = extractEntities(sent)
            if (mainEnts == null) mainEnts = ents
            val me = mainEnts
            val query = if (ents.isNotEmpty() || me.isEmpty()) sent else "${sent.trimEnd(' ', '.')} – ${me.take(2).joinToString(", ")}"
            claims.add(sent to query)
            if (claims.size >= limit) break
        }
        return claims
    }

    /** Uma afirmação: pesquisa normal. Várias: cada uma é pesquisada e avaliada separadamente. */
    suspend fun evaluate(text: String, requestId: String, context: ArticleContext? = null,
                         onPreview: ((Evaluation) -> Unit)? = null): Evaluation = withContext(Dispatchers.Default) {
        // O sinal do texto começa junto com a busca quando a entrada já tem texto suficiente. Se for curta (manchete),
        // a pesquisa localiza a própria matéria, lê a página e o sinal é calculado sobre o corpo dela; se não achar, a entrada é usada.
        val signalSource = textSignal?.takeIf { it.configured }?.let { signalText(context, text) }
        val ready = java.util.concurrent.atomic.AtomicReference<TextSignal?>(null)
        val ownText = java.util.concurrent.atomic.AtomicReference<String?>(null)
        suspend fun runSignal(src: String, origem: String): TextSignal? {
            val t0 = System.currentTimeMillis()
            val sig = withTimeoutOrNull(SIGNAL_TIMEOUT_MS) { runCatching { textSignal!!.score(src) }.getOrNull() }
            if (sig != null) {
                ready.set(sig)
                logI("[$requestId] sinal do texto ($origem): faixa=${sig.faixa} posicao=${"%.2f".format(java.util.Locale.US, sig.posicao)} " +
                    "palavras=${sig.palavras} regime=${sig.regime} (${System.currentTimeMillis() - t0} ms)")
            }
            return sig
        }
        val signalJob = signalSource?.let { src -> async { runSignal(src, "entrada") } }
        suspend fun lateSignal(): TextSignal? {
            signalJob?.await()?.let { return it }
            if (textSignal == null) return null
            val body = ownText.get()?.let { signalText(null, it) } ?: return null
            return runSignal(body, "própria matéria")
        }
        val parts = splitClaims(text)
        if (parts.size < 2) {
            val r = search(text, requestId, context, if (signalSource == null && textSignal?.configured == true) ownText else null, onPreview?.let { cb ->
                { p: ResearchResult -> cb(Evaluation(requestId, "unica", p.status, p.mensagem, listOf(p), ready.get())) }
            })
            val waitStart = System.currentTimeMillis()
            val sig = lateSignal()
            if (signalJob != null) logI("[$requestId] sinal do texto: esperou ${System.currentTimeMillis() - waitStart} ms no fim da pesquisa")
            return@withContext Evaluation(requestId, "unica", r.status, r.mensagem, listOf(r), sig)
        }
        val results = parts.map { (sentence, query) -> async { search(query, requestId, context).copy(trechoDaEntrada = sentence) } }.awaitAll()
        val allFailed = results.all { it.status == "erro" }
        Evaluation(requestId, "partes", if (allFailed) "erro" else "ok",
            "A entrada tem ${parts.size} afirmações. Cada uma foi avaliada separadamente; o Lume não classifica a notícia inteira.", results,
            signalJob?.await())
    }

    /** Etapa anterior à pesquisa: decide se é preciso perguntar "O que você quer saber?". */
    suspend fun clarify(texto: String, detalhes: String = "", rejeitadas: List<String> = emptyList(), rodada: Int = 0): Clarification =
        withContext(Dispatchers.Default) {
            val t = System.currentTimeMillis()
            clarifyInner(texto, detalhes, rejeitadas, rodada).also { logI("tempos: desambiguacao=${System.currentTimeMillis() - t}ms") }
        }

    private suspend fun clarifyInner(texto: String, detalhes: String, rejeitadas: List<String>, rodada: Int): Clarification =
        analyzeClarification(texto, detalhes, rejeitadas, rodada, clock()) { q ->
            val key = "wiki_sentidos|${norm(q)}"
            cache.get<List<Candidate>>(key, clock())?.first ?: wikipedia(q, fetcher, limit = 8).also { cache.put(key, it, WIKI_TTL, clock()) }
        }.also { logI("desambiguação: escolher=${it.precisaEscolher} opções=${it.opcoes.map { o -> o.texto }} motivo=${it.motivo} ${it.aviso}") }

    // ---- montagem ----------------------------------------------------------------------------
    private fun card(g: Group, category: String, evItems: List<EvidenceItem>?, interp: Interpretation,
                     comparisons: Map<Candidate, EventComparison>, independence: Map<Candidate, IndependenceCheck>): ResultCard {
        // Representante: o membro com a relação mais forte (e, entre eles, o conteúdo mais completo).
        // Quando o cartão aparece por esclarecer o detalhe, mostra a publicação de onde o trecho saiu (mesma data e link).
        val evUrls = evItems?.map { it.url }?.toSet().orEmpty()
        val (cand, best) = g.members.maxWith(compareBy({ if (it.first.url in evUrls) 1 else 0 }, { CATEGORY_RANK.getValue(it.second.category) }, { CONTENT_RANK.getValue(it.first.contentKind) }, { it.second.score }))
        val cmp = comparisons[cand]
        val ind = independence[cand]
        val rel = when {
            ind != null -> SourceRelation(ind.kind, DetailStatus.SEM_DETALHE, when (ind.kind) {
                Independence.PROPRIA -> "É a própria matéria importada"
                Independence.MESMO_VEICULO -> "Publicação do mesmo veículo da matéria importada"
                else -> "Republicação da matéria importada"
            } + " (${ind.motivo})", null, if (cand.pageText.isNotBlank()) "pagina" else if (cand.snippet.isNotBlank()) "trecho" else "titulo")
            cmp != null -> fromComparison(cmp)
            else -> relate(interp, cand, best)
        }
        val seenSrc = mutableSetOf(norm(cand.sourceName))
        val others = g.members.map { it.first }.filter { it !== cand && seenSrc.add(norm(it.sourceName)) }
            .map { Republication(it.sourceName, it.url, it.title, it.published) }
        return ResultCard(
            titulo = cand.title, url = cand.url, observacaoLink = cand.linkNote.ifEmpty { null }, veiculo = cand.sourceName,
            autor = cand.pageAuthor.ifEmpty { null }, data = cand.published, periodo = periodOf(cand, clock()),
            tipoFonte = sourceType(cand.domain, cand.origin), relacao = category,
            relacaoTexto = if (category == "responde") "Informa o detalhe pesquisado" else best.relation,
            conteudo = cand.contentKind, conteudoTexto = CONTENT_LABEL.getValue(cand.contentKind),
            trecho = cand.snippet.take(500).ifEmpty { null },
            trechosPagina = evItems?.map { it.trecho }?.distinct()?.take(2) ?: cand.pageExcerpts,
            // Avisos da triagem pelo título perdem sentido quando a página lida confirma o mesmo número.
            alertas = ((if (cmp != null || ind != null) emptyList() else best.alerts) + cand.alerts + rel.alertas).distinct()
                .filterNot { rel.detalhe == DetailStatus.CITA && it.startsWith("Cita números diferentes") }, republicacoes = others,
            relacaoTipo = rel.tipo, resumoRelacao = rel.resumo, trechoRelacao = rel.trecho, baseLeitura = rel.base,
            detalheStatus = rel.detalhe, motivos = cmp?.motivos, trechosComparacao = cmp?.trechos,
        )
    }

    /** Relação exibida a partir da comparação estruturada (tipo, detalhe, trecho da fonte e limites de leitura). */
    private fun fromComparison(cmp: EventComparison): SourceRelation {
        val tipo = when (cmp.kind) {
            EventRelation.MESMO -> RelationKind.MESMO_FATO
            EventRelation.DIVERGENTE -> RelationKind.DIFERENTE
            EventRelation.CONTEXTO -> if (cmp.anterior) RelationKind.ANTERIOR else RelationKind.CONTEXTO
            EventRelation.OUTRO -> RelationKind.OUTRO
            else -> RelationKind.INDEFINIDO
        }
        val detail = when {
            cmp.motivos.any { it.startsWith("Detalhe:") && " × " in it } -> DetailStatus.DIFERENTE
            cmp.motivos.any { it.startsWith("Detalhe: cita") } -> DetailStatus.CITA
            cmp.motivos.any { it.startsWith("Detalhe:") } -> DetailStatus.NAO_CITA
            else -> DetailStatus.SEM_DETALHE
        }
        // Trecho da fonte que mais sustenta a decisão: frase da página/resumo; senão, o título.
        val quote = cmp.trechos.lastOrNull { it.onde == "pagina" || it.onde == "resumo" }?.texto
        return SourceRelation(tipo, detail, cmp.resumo, quote, cmp.base, cmp.alertas)
    }

    private fun ownObservations(found: List<IndependenceCheck>, context: ArticleContext?): List<String> {
        if (found.isEmpty()) return emptyList()
        val own = found.count { it.kind == Independence.PROPRIA }
        val rep = found.count { it.kind == Independence.REPUBLICACAO }
        val same = found.count { it.kind == Independence.MESMO_VEICULO }
        return listOfNotNull(
            if (own > 0) "A própria matéria importada${context?.source?.let { " ($it)" } ?: ""} apareceu na busca e não conta como fonte independente." else null,
            if (rep > 0) "$rep publicação(ões) repetem o título da matéria importada (republicação) e não contam como fontes independentes." else null,
            if (same > 0) "$same publicação(ões) são do mesmo veículo da matéria importada e não contam como fontes independentes." else null,
        )
    }

    private fun observations(interp: Interpretation, direct: List<Group>, all: List<Group>): List<String> {
        val obs = mutableListOf<String>()
        val republished = all.sumOf { it.members.size - 1 }
        if (republished > 0) obs.add("$republished publicação(ões) repetiam conteúdo já listado e foram agrupadas; cópias da mesma matéria não são fontes distintas de informação.")
        // O que o texto cita como prova ou como origem não é verificado só por haver matérias sobre o fato.
        interp.materialCitado?.let { obs.add("A afirmação cita $it. A pesquisa compara o acontecimento descrito; não avalia se esse material é autêntico nem se mostra o que o texto diz.") }
        trailingAttribution(interp.textoOriginal.lineSequence().firstOrNull().orEmpty())?.let {
            obs.add("O texto atribui a informação a “$it”. Isso é o que o texto diz: só conta como fonte se a publicação aparecer entre os resultados.")
        }
        if (interp.negacoes.isNotEmpty()) obs.add("A entrada contém negação (“${interp.negacoes[0]}…”). Confira nos trechos se as fontes afirmam o mesmo sentido.")
        val dated = direct.flatMap { g -> g.members.mapNotNull { it.first.published } }
        val now = clock()
        if (interp.tempoRelativo.isNotEmpty() && direct.isNotEmpty() && dated.isNotEmpty() && dated.all { now - it > 14 * Dates.DAY_MS }) {
            obs.add("A entrada fala em “${interp.tempoRelativo[0]}”, mas as publicações encontradas são anteriores.")
        }
        if (dated.isNotEmpty() && interp.anos.isEmpty() && interp.tempoRelativo.isEmpty() && dated.all { now - it > 365 * Dates.DAY_MS }) {
            obs.add("As publicações diretamente relacionadas têm mais de um ano; não indicam que o fato se repetiu agora.")
        }
        return obs
    }
}
