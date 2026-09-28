package com.example.lumeocrtest.research

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
private const val TOTAL_BUDGET_MS = 25_000L
private val STABLE_TYPES = setOf("contagem", "quantidade_periodo", "fato_historico")
private val CATEGORY_RANK = mapOf("direto" to 3, "anterior" to 2, "contexto" to 1, "descartado" to 0)
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
)

/** Uma afirmação (modo "unica") ou várias avaliadas separadamente (modo "partes"). */
data class Evaluation(val idConsulta: String, val modo: String, val status: String, val mensagem: String,
                      val partes: List<ResearchResult>)

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

    private suspend fun enrich(groups: List<Group>, interp: Interpretation, deadline: Long) = coroutineScope {
        val q = interp.quantity
        val stable = interp.afirmacao?.tipo in STABLE_TYPES
        val allowed = if (stable) setOf("bing_news", "bing_web", "wikipedia") else setOf("bing_news", "bing_web")
        val ranked = if (q == null) groups else groups.sortedBy { g ->
            // Para quantidades, ler primeiro as páginas que citam a unidade (ex.: "títulos", "%").
            if (g.members.any { (c, _) -> q.chaveUnidade in stemsOfAll(c.title + " " + c.snippet) || (q.chaveUnidade == "%" && "%" in c.snippet) }) 0 else 1
        }
        val cap = if (stable) 8 else 6
        val targets = mutableListOf<Candidate>()
        for (g in ranked) {
            // Até 2 páginas por grupo: confirma se itens de título parecido são mesmo republicações.
            var perGroup = 0
            for ((cand, _) in g.members.sortedByDescending { CONTENT_RANK.getValue(it.first.contentKind) }) {
                if (targets.size >= cap || perGroup >= 2) break
                if (cand.origin in allowed) { targets.add(cand); perGroup++ }
            }
        }
        val budget = minOf(8_000L, maxOf(100L, deadline - System.currentTimeMillis()))
        val infos = targets.map { c -> async { withTimeoutOrNull(budget) { page(c.url) } to c } }.awaitAll()
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
            for (q in queries.take(2)) { jobs.add("google_news" to q); jobs.add("bing_news" to q) }
            queries.firstOrNull()?.let { jobs.add("bing_web" to it) }
            wikiQ = listOf(interp.consultaContextoPropriedade, interp.consultaContexto)
        }
        wikiQ.filter { it.isNotBlank() }.distinct().forEach { jobs.add("wikipedia" to it) }
        val extra = if (stable || queries.size <= 2) emptyList() else listOf("google_news", "bing_news", "bing_web").map { it to queries[2] }
        return jobs to extra
    }

    /** Todo o processamento (análise de HTML, regras) roda fora da thread principal. */
    suspend fun search(text: String, requestId: String): ResearchResult = withContext(Dispatchers.Default) { doSearch(text, requestId) }

    private suspend fun doSearch(text: String, requestId: String): ResearchResult {
        val t0 = System.currentTimeMillis()
        val deadline = t0 + TOTAL_BUDGET_MS
        val now = clock()
        val interp = interpret(text, now)
        if (interp.assunto.isBlank() || !Regex("$W{2,}").containsMatchIn(interp.assunto)) {
            return ResearchResult(requestId, now, interp, "entrada_invalida", "Digite uma notícia, afirmação ou pergunta para pesquisar.")
        }
        logI("[$requestId] entrada: '${interp.assunto.take(120)}' | tipo=${interp.afirmacao?.tipo} | consultas: ${interp.consultas.map { it.texto }}")
        val (jobs, extra) = plan(interp)
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
        val discarded = pairs.filter { it.second.category == "descartado" }
        val groups = groupCandidates(pairs.filter { it.second.category != "descartado" })
            .sortedWith(compareBy({ g -> -g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } }, { g -> -g.members.maxOf { it.second.score } }))
        if (fetchPages && groups.isNotEmpty()) enrich(groups, interp, deadline)

        val evGroups = groups.take(20)
        val nDirect = groups.count { g -> g.members.maxOf { CATEGORY_RANK.getValue(it.second.category) } == 3 }
        var sintese = synthesize(interp, evGroups, nDirect)
        val evByGroup = sintese.usadas.groupBy { it.group }
        val buckets = linkedMapOf<String, MutableList<Group>>("responde" to mutableListOf(), "direto" to mutableListOf(),
            "anterior" to mutableListOf(), "contexto" to mutableListOf(), "contexto_geral" to mutableListOf())
        groups.forEachIndexed { i, g ->
            var cat = g.members.maxBy { CATEGORY_RANK.getValue(it.second.category) }.second.category
            if (i in evByGroup) cat = "responde" else if (g.lead.first.origin == "wikipedia") cat = "contexto_geral"
            buckets.getValue(cat).add(g)
        }
        val limits = mapOf("responde" to 6, "direto" to 6, "anterior" to 3, "contexto" to 3, "contexto_geral" to 2)
        val index = groups.withIndex().associate { (i, g) -> System.identityHashCode(g) to i }
        val results = buckets.mapValues { (k, v) -> v.take(limits.getValue(k)).map { g -> card(g, k, evByGroup[index[System.identityHashCode(g)]]) } }

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
                val s2 = synthesize(i2, evGroups, nDirect)
                extraDetails.add(s2.copy(indicacao = indicate(s2, i2)))
            }
        }
        val answerTexts = sintese.evidencias.map { it.trecho } + extraDetails.flatMap { s -> s.evidencias.map { it.trecho } }
        val contexto = if (status == "erro") emptyList() else selectContext(interp, groups, answerTexts)
        val result = ResearchResult(
            idConsulta = requestId, consultadoEm = now, interpretacao = interp, status = status, mensagem = msg,
            avisos = avisos, sintese = sintese, detalhesAdicionais = extraDetails, contexto = contexto,
            sugestoes = sugestoes, resultados = results, observacoes = observations(interp, buckets.getValue("direto") + buckets.getValue("responde"), groups),
            fontesConsultadas = statuses.sortedWith(compareBy({ it.fonte }, { it.consulta })),
            descartados = discarded.size, duracaoMs = System.currentTimeMillis() - t0,
        )
        logI("[$requestId] status=$status sintese=${sintese.situacao} indicacao=${sintese.indicacao?.rotulo} " +
            "responde=${results.getValue("responde").size} direto=${results.getValue("direto").size} descartados=${discarded.size} " +
            "falhas=${failed.size} (${result.duracaoMs} ms)")
        failed.forEach { logW("[$requestId] fonte ${it.fonte} '${it.consulta}': ${it.erro}") }
        return result
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
    suspend fun evaluate(text: String, requestId: String): Evaluation = withContext(Dispatchers.Default) {
        val parts = splitClaims(text)
        if (parts.size < 2) {
            val r = search(text, requestId)
            return@withContext Evaluation(requestId, "unica", r.status, r.mensagem, listOf(r))
        }
        val results = parts.map { (sentence, query) -> async { search(query, requestId).copy(trechoDaEntrada = sentence) } }.awaitAll()
        val allFailed = results.all { it.status == "erro" }
        Evaluation(requestId, "partes", if (allFailed) "erro" else "ok",
            "A entrada tem ${parts.size} afirmações. Cada uma foi avaliada separadamente; o Lume não classifica a notícia inteira.", results)
    }

    /** Etapa anterior à pesquisa: decide se é preciso perguntar "O que você quer saber?". */
    suspend fun clarify(texto: String, detalhes: String = "", rejeitadas: List<String> = emptyList(), rodada: Int = 0): Clarification =
        withContext(Dispatchers.Default) { clarifyInner(texto, detalhes, rejeitadas, rodada) }

    private suspend fun clarifyInner(texto: String, detalhes: String, rejeitadas: List<String>, rodada: Int): Clarification =
        analyzeClarification(texto, detalhes, rejeitadas, rodada, clock()) { q ->
            val key = "wiki_sentidos|${norm(q)}"
            cache.get<List<Candidate>>(key, clock())?.first ?: wikipedia(q, fetcher, limit = 8).also { cache.put(key, it, WIKI_TTL, clock()) }
        }.also { logI("desambiguação: escolher=${it.precisaEscolher} opções=${it.opcoes.map { o -> o.texto }} motivo=${it.motivo} ${it.aviso}") }

    // ---- montagem ----------------------------------------------------------------------------
    private fun card(g: Group, category: String, evItems: List<EvidenceItem>?): ResultCard {
        val (cand, _) = g.lead
        val best = g.members.map { it.second }.maxWith(compareBy({ CATEGORY_RANK.getValue(it.category) }, { it.score }))
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
            alertas = (best.alerts + cand.alerts).distinct(), republicacoes = others,
        )
    }

    private fun observations(interp: Interpretation, direct: List<Group>, all: List<Group>): List<String> {
        val obs = mutableListOf<String>()
        val republished = all.sumOf { it.members.size - 1 }
        if (republished > 0) obs.add("$republished publicação(ões) repetiam conteúdo já listado e foram agrupadas; republicações não são confirmações independentes.")
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
