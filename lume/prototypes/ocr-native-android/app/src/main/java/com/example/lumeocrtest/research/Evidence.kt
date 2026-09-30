package com.example.lumeocrtest.research

/*
 * Compara o detalhe verificável com o que as fontes dizem.
 * Por regras, só compara formatos estruturados: quantidade + unidade (+ categoria e período) e o
 * ano de um fato. Outros tipos ficam "sem_comparacao" (Inconclusiva). Nunca usa contagem de
 * resultados, reputação ou ausência de notícias como evidência.
 */

val STRONG_CONTENT = setOf("completo", "resumo")
/** Primeiras palavras típicas de páginas de lista/tema na Wikipédia (não são nomes de entidade). */
val ARTICLE_TYPE_WORDS = setOf("lista", "listas", "titulos", "confrontos", "historia", "temporada", "temporadas",
    "resultados", "estatisticas", "elenco", "jogadores", "presidentes", "treinadores", "eleicoes", "eleicao",
    "controversias", "cronologia")
val SITUACAO_FRASE = mapOf(
    "apoiam" to "As fontes consultadas apoiam essa informação.",
    "contradizem" to "As fontes consultadas contradizem essa informação.",
    "divergentes" to "As fontes apresentam informações divergentes.",
    "insuficiente" to "Não encontramos evidência suficiente para esclarecer esse detalhe.",
    "resposta" to "As fontes consultadas indicam o valor abaixo.",
    "nao_verificavel" to "Esta entrada expressa opinião ou previsão; não há um fato único para comparar.",
    "sem_comparacao" to "Encontramos fontes sobre o assunto, mas as regras do Lume não comparam o sentido deste tipo de afirmação. Leia o que cada fonte diz abaixo.",
    "erro_tecnico" to "A pesquisa não pôde ser feita por falha técnica.",
)

data class SourceRef(val veiculo: String, val url: String)
data class Explanation(val texto: String, val fontes: List<SourceRef> = emptyList())
data class Option(val texto: String, val diferenca: String = "significado_ou_entidade")
data class Indication(val rotulo: String, val texto: String, val motivo: String, val aviso: String)

class EvidenceItem(
    val group: Int,
    val veiculo: String,
    val url: String,
    val conteudo: String,
    val publicado: Long?,
    val valor: Double,
    val textoValor: String,
    val trecho: String,
    val qualificadores: List<String>,
    val anos: List<Int>,
    val relacao: String, // apoia | contradiz | valor | limite | outra_categoria | outro_periodo | sem_categoria | categoria_nao_informada
    val explicito: Boolean = true, // a frase cita a entidade (e não só o título da página)
    var entidadeFonte: String? = null, // nome mais longo que contém a entidade (outra organização homônima)
)

data class Synthesis(
    val situacao: String,
    val frase: String,
    val detalhe: String?,
    val metodo: String,
    val explicacao: List<Explanation> = emptyList(),
    val evidencias: List<EvidenceItem> = emptyList(),
    val opcoes: List<Option> = emptyList(),
    /** Evidências fortes usadas na decisão (definem as "fontes que esclarecem o detalhe"). */
    val usadas: List<EvidenceItem> = emptyList(),
    val indicacao: Indication? = null,
)

private val EV_SENTENCE = Regex("(?<=[.!?;])\\s+|\\n+")
private val WORDS = Regex("[\\p{L}\\p{N}_]+")
private val PAREN_SUFFIX = Regex("\\s*\\(.*?\\)\\s*$")

private fun evSentences(text: String) = text.split(EV_SENTENCE).map { it.trim() }.filter { it.length >= 15 }

private fun sourceTexts(c: Candidate): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    if (c.contentKind == "completo" && c.pageText.isNotEmpty()) out.add("completo" to c.pageText)
    else if (c.snippet.isNotEmpty()) out.add((if (c.origin == "wikipedia") "resumo" else "trecho") to c.snippet)
    out.add("titulo" to c.title)
    return out
}

/** Artigo de enciclopédia cujo título é a própria entidade: o texto trata dela. */
fun pageIsEntity(c: Candidate, entity: String?): Boolean =
    entity != null && c.origin == "wikipedia" && norm(PAREN_SUFFIX.replace(c.title, "")) == norm(entity)

fun mentionsEntity(entity: String, text: String): Boolean {
    val cn = norm(text)
    return entityMatch(entity, cn, cn.split(" ").toSet(), acronymsOf(text))
}

/** Se o título nomeia uma entidade mais longa que contém a da afirmação, devolve esse nome. */
fun sourceEntity(c: Candidate, entity: String?): String? {
    if (entity == null) return null
    val names = if (c.origin == "wikipedia") listOf(c.title) else extractEntities(c.title)
    for (raw in names) {
        val name = PAREN_SUFFIX.replace(raw, "").trim()
        if (norm(name).split(" ").firstOrNull() in ARTICLE_TYPE_WORDS) continue // lista/tema sobre a entidade
        if (norm(name) != norm(entity) && mentionsEntity(entity, name) && norm(name).length > norm(entity).length) return name
    }
    return null
}

/** A frase fala de outro sujeito nomeado (e não da entidade da afirmação)? */
private fun otherSubject(sent: String, knownStems: Set<String>): Boolean = extractEntities(sent).any { e ->
    val es = norm(e).split(" ").filter { it.length >= 3 }.map { stem(it) }.toSet()
    es.isNotEmpty() && (es intersect knownStems).isEmpty()
}

fun quantityEvidence(interp: Interpretation, groups: List<Group>): List<EvidenceItem> {
    val q = interp.quantity ?: return emptyList()
    val entity = interp.entidades.firstOrNull()
    val entTokens = entity?.let { norm(it).split(" ").toSet() } ?: emptySet()
    val qualStems = q.qualificadores.map { stem(it) }.toSet()
    val propStems = interp.termos.map { stem(it) }.filter { it != q.chaveUnidade }.toSet() - qualStems
    val generic = q.chaveUnidade == "%" || norm(q.unidade) in GENERIC_UNITS
    val claimQuals = qualifierKeys(q)
    val known = interp.entidades.flatMap { norm(it).split(" ") }.map { stem(it) }.toSet() + qualStems + propStems + q.chaveUnidade
    val items = mutableListOf<EvidenceItem>()
    groups.forEachIndexed { gi, g ->
        for ((cand, _) in g.members) {
            val titleHasEntity = entity != null && mentionsEntity(entity, cand.title)
            val seen = HashSet<Pair<Double, String>>()
            for ((kind, text) in sourceTexts(cand)) {
                val sentences = if (kind != "titulo") evSentences(text) else listOf(text)
                for (sent in sentences) {
                    val found = extractQuantities(sent, entTokens, setOf(q.chaveUnidade)).filter { it.chaveUnidade == q.chaveUnidade }
                    if (found.isEmpty()) continue
                    val sstems = norm(sent).split(" ").filter { it.length >= 3 }.map { stem(it) }.toSet()
                    val inSent = entity != null && mentionsEntity(entity, sent)
                    if (entity != null && !inSent && !(titleHasEntity && !otherSubject(sent, known))) continue
                    if ((generic || entity == null) && propStems.isNotEmpty() && (sstems intersect propStems).isEmpty() &&
                        !(qualStems.isNotEmpty() && (qualStems intersect sstems).isNotEmpty())) continue
                    val years = YEAR_RE.findAll(sent).map { it.groupValues[1].toInt() }.toSortedSet().toList()
                    for (x in found) {
                        if (!seen.add((x.valor ?: 0.0) to sent.take(60))) continue
                        val xq = qualifierKeys(x)
                        val rel = when {
                            claimQuals.isNotEmpty() && xq.isNotEmpty() && (claimQuals intersect xq).isEmpty() -> "outra_categoria"
                            claimQuals.isNotEmpty() && xq.isEmpty() -> "sem_categoria" // não diz a qual categoria se refere
                            claimQuals.isEmpty() && xq.isNotEmpty() -> "categoria_nao_informada" // a entrada não diz a categoria
                            interp.anos.isNotEmpty() && years.isNotEmpty() && (interp.anos intersect years.toSet()).isEmpty() -> "outro_periodo"
                            q.valor == null -> if (x.comparador == "eq") "valor" else "limite"
                            x.comparador in setOf("min", "min_excl", "max", "max_excl") && compatible(q, x) -> "limite"
                            else -> if (compatible(q, x)) "apoia" else "contradiz"
                        }
                        items.add(EvidenceItem(gi, cand.sourceName, cand.url, kind, cand.published, x.valor ?: 0.0,
                            x.texto ?: "", sent.take(320), x.qualificadores, years, rel,
                            explicito = inSent || entity == null || pageIsEntity(cand, entity)))
                    }
                }
            }
        }
    }
    return items
}

/** Fato histórico com ano: procura o ano logo após o termo do fato (ex.: "fundado em 1895"). */
fun yearEvidence(interp: Interpretation, groups: List<Group>): List<EvidenceItem> {
    val entity = interp.entidades.firstOrNull()
    val termStems = interp.termos.map { stem(it) }.toSet()
    val items = mutableListOf<EvidenceItem>()
    groups.forEachIndexed { gi, g ->
        for ((cand, _) in g.members) {
            for ((kind, text) in sourceTexts(cand)) {
                if (kind == "titulo") continue
                val titleHasEntity = entity != null && mentionsEntity(entity, cand.title)
                for (sent in evSentences(text)) {
                    val inSent = entity != null && mentionsEntity(entity, sent)
                    if (entity != null && !(inSent || titleHasEntity)) continue
                    val words = WORDS.findAll(sent).map { it.value }.toList()
                    for ((i, w) in words.withIndex()) {
                        if (w.length < 3 || stem(w) !in termStems) continue
                        val window = words.subList(i + 1, minOf(words.size, i + 16)).joinToString(" ")
                        val m = YEAR_RE.find(window) ?: continue
                        val y = m.groupValues[1].toInt()
                        items.add(EvidenceItem(gi, cand.sourceName, cand.url, kind, cand.published, y.toDouble(), "$y",
                            sent.take(320), emptyList(), listOf(y), if (y in interp.anos) "apoia" else "contradiz",
                            explicito = inSent || entity == null || pageIsEntity(cand, entity)))
                        break
                    }
                }
            }
        }
    }
    return items
}

private fun tagVariants(items: List<EvidenceItem>, groups: List<Group>, entity: String?): List<EvidenceItem> {
    for (i in items) {
        val cand = groups[i.group].members.firstOrNull { it.first.url == i.url }?.first ?: groups[i.group].lead.first
        i.entidadeFonte = if (cand.origin == "wikipedia") sourceEntity(cand, entity) else null
    }
    return items
}

/** Período do fato = ano citado no trecho. A data da página não conta como período do fato. */
private fun factPeriod(i: EvidenceItem): Int? = i.anos.maxOrNull()

fun describe(i: EvidenceItem): String {
    var what = i.textoValor
    if (i.qualificadores.isNotEmpty()) what += " (" + i.qualificadores.joinToString(" ") + ")"
    val whenParts = mutableListOf<String>()
    if (i.anos.isNotEmpty()) whenParts.add("período citado: " + i.anos.joinToString(", "))
    i.publicado?.let { whenParts.add("publicado em ${Dates.format(it)}") }
    val label = mapOf("completo" to "página consultada", "resumo" to "resumo da página", "trecho" to "trecho do buscador",
        "titulo" to "apenas título").getValue(i.conteudo)
    return "${i.veiculo} ($label${if (whenParts.isNotEmpty()) "; " + whenParts.joinToString("; ") else ""}): $what"
}

private fun src(i: EvidenceItem) = SourceRef(i.veiculo, i.url)

private fun same(a: Double, b: Double) = Math.abs(a - b) <= 0.005 * maxOf(Math.abs(a), Math.abs(b), 1e-9)

private fun periodNote(reps: List<EvidenceItem>): Explanation {
    val periods = reps.mapNotNull { factPeriod(it) }.map { it.toString() }.toSortedSet()
    if (periods.size > 1) return Explanation("Os valores vêm de períodos diferentes (${periods.joinToString(", ")}); confira a qual data cada fonte se refere antes de compará-los.")
    return Explanation("As fontes indicam valores diferentes para o mesmo período ou sem período informado; podem usar definições ou contagens diferentes.")
}

fun synthesize(interp: Interpretation, groups: List<Group>, directCount: Int): Synthesis {
    val af = interp.afirmacao
    val tipo = af?.tipo
    val detail = af?.detalheVerificavel
    if (tipo == "opiniao" || tipo == "previsao") {
        return Synthesis("nao_verificavel", SITUACAO_FRASE.getValue("nao_verificavel"), detail, "regras",
            listOf(Explanation("Previsões e promessas só podem ser comparadas com o que for anunciado oficialmente; opiniões não têm valor verdadeiro único.")))
    }
    val q = interp.quantity
    val entity = interp.entidades.firstOrNull()
    val items = when {
        q != null -> tagVariants(quantityEvidence(interp, groups), groups, entity)
        tipo == "fato_historico" && interp.anos.isNotEmpty() -> tagVariants(yearEvidence(interp, groups), groups, entity)
        directCount > 0 -> return Synthesis("sem_comparacao", SITUACAO_FRASE.getValue("sem_comparacao"), detail, "nenhum")
        else -> return Synthesis("insuficiente", SITUACAO_FRASE.getValue("insuficiente"), detail, "nenhum",
            listOf(Explanation("Nenhuma publicação encontrada trata diretamente deste detalhe. Isso não indica que seja falso.")))
    }
    return decide(interp, q, items, detail, groups)
}

private fun decide(interp: Interpretation, q: Quantity?, items: List<EvidenceItem>, detail: String?, groups: List<Group>): Synthesis {
    val expl = mutableListOf<Explanation>()
    var used = emptyList<EvidenceItem>()
    val claimed = when {
        q?.valor != null -> q.texto + if (q.qualificadores.isNotEmpty()) " (${q.qualificadores.joinToString(" ")})" else ""
        q == null && interp.anos.isNotEmpty() -> interp.anos.joinToString(", ")
        else -> null
    }
    if (claimed != null) expl.add(Explanation("Informado na entrada: $claimed."))
    // Valores deixados de fora da comparação: explicados DEPOIS da comparação, no máximo 2.
    val reasons = mapOf("outra_categoria" to "refere-se a outra categoria", "outro_periodo" to "refere-se a outro período",
        "categoria_nao_informada" to "a fonte especifica uma categoria que a entrada não informa")
    val excluded = items.filter { it.relacao in reasons && it.conteudo in STRONG_CONTENT }.take(2)
        .map { Explanation("Não comparado — ${describe(it)}: ${reasons.getValue(it.relacao)}.", listOf(src(it))) }
    val rel = items.filter { it.relacao in setOf("apoia", "contradiz", "valor") }
    val strong = rel.filter { it.conteudo in STRONG_CONTENT }
    val negated = interp.afirmacao?.detalheNegado == true
    var opcoes = emptyList<Option>()

    fun out(sit0: String, extra: List<Explanation> = emptyList()): Synthesis {
        var sit = sit0
        expl.addAll(extra + excluded)
        if (negated && (sit == "apoiam" || sit == "contradizem")) {
            // A entrada NEGA o valor: fontes com o mesmo valor contradizem a entrada, e vice-versa.
            sit = if (sit == "apoiam") "contradizem" else "apoiam"
            expl.add(Explanation("A entrada nega esse valor (“${interp.afirmacao?.negacaoTexto ?: "não"}…”); a indicação considera essa negação."))
        }
        return Synthesis(sit, SITUACAO_FRASE.getValue(sit), detail, "regras", expl.toList(), items.take(12), opcoes,
            if (sit == "insuficiente") emptyList() else used)
    }

    if (rel.isEmpty()) return out("insuficiente", listOf(Explanation("As fontes encontradas não informam esse valor de forma comparável. Ausência de informação não é contradição.")))
    if (strong.isEmpty()) {
        return out("insuficiente", rel.take(3).map { Explanation("Pista, sem leitura da página: " + describe(it), listOf(src(it))) } +
            Explanation("Só havia título ou trecho do buscador; isso não basta para concluir. Abra os links para conferir."))
    }
    // Um valor por grupo independente (republicações já estão agrupadas).
    val byGroup = LinkedHashMap<String, MutableList<EvidenceItem>>()
    for (i in strong) byGroup.getOrPut("${i.group}") { mutableListOf() }.add(i)
    // Páginas agrupadas por título parecido que informam valores diferentes não são cópias: separa por URL.
    for ((g, its) in byGroup.entries.toList()) {
        val perUrl = its.groupBy { it.url }.mapValues { (_, v) -> v.minOf { it.valor } }
        val values = perUrl.values.toList()
        if (perUrl.size > 1 && values.drop(1).any { !same(values[0], it) }) {
            byGroup.remove(g)
            for (i in its) byGroup.getOrPut("$g|${i.url}") { mutableListOf() }.add(i)
        }
    }
    var reps = byGroup.values.map { all ->
        val its = all.filter { it.explicito }.ifEmpty { all } // frases que nomeiam a entidade têm prioridade
        val counts = its.groupingBy { it.valor }.eachCount()
        val best = counts.maxByOrNull { it.value }!!.key
        its.first { it.valor == best }
    }
    val onlyOtherEntity = reps.mapNotNull { it.entidadeFonte }.toSet().singleOrNull()
    if (onlyOtherEntity != null && reps.all { it.entidadeFonte == onlyOtherEntity }) {
        return out("insuficiente", listOf(Explanation(
            "A fonte encontrada trata de $onlyOtherEntity, outra entidade. Esse dado não responde à afirmação pesquisada.")))
    }
    if (reps.none { it.explicito }) {
        return out("insuficiente", reps.take(2).map { Explanation("Valor encontrado sem citar a entidade na mesma frase: " + describe(it), listOf(src(it))) } +
            Explanation("Sem menção explícita à entidade, não dá para afirmar que o número se refere a ela."))
    }
    reps = reps.filter { it.explicito } // só frases que nomeiam a entidade (ou artigos sobre ela)
    val named = reps.filter { it.entidadeFonte != null }
    val variants = named.map { it.entidadeFonte!! }.toSet()
    if (variants.size > 1 && named.map { it.valor }.toSet().size > 1) {
        // O mesmo nome aparece ligado a entidades diferentes: é ambiguidade real, não contradição.
        val entity = interp.entidades.firstOrNull() ?: ""
        val names = variants.toMutableList()
        for (g in groups) {
            val c = g.lead.first
            if (c.origin == "wikipedia" && !c.disambiguation) {
                if (norm(c.title).split(" ").firstOrNull() in ARTICLE_TYPE_WORDS) continue
                val v = sourceEntity(c, entity)
                if (v != null && v !in names) names.add(v)
            }
        }
        val subject = interp.assunto.replace(Regex("^(o|a|os|as)\\s+", RegexOption.IGNORE_CASE), "")
        opcoes = names.take(4).map { n ->
            Option(if (entity.isNotEmpty()) subject.replaceFirst(Regex(Regex.escape(entity), RegexOption.IGNORE_CASE), Regex.escapeReplacement(n)) else n)
        }
        return out("insuficiente", listOf(Explanation("O nome “$entity” aparece ligado a entidades diferentes nas fontes: " +
            reps.take(4).joinToString("; ") { describe(it) + (it.entidadeFonte?.let { v -> " [$v]" } ?: "") } +
            ". Escolha a qual delas a afirmação se refere.", reps.take(4).map { src(it) })))
    }
    // Uma organização cujo nome contém o nome pesquisado não responde pela entidade original.
    if (named.isNotEmpty()) {
        val exact = reps.filter { it.entidadeFonte == null }
        if (exact.isEmpty()) return out("insuficiente", listOf(Explanation(
            "O dado encontrado se refere a ${named.first().entidadeFonte}, outra entidade; não foi comparado com a afirmação.",
            named.take(2).map { src(it) })))
        reps = exact
    }
    used = reps
    val distinct = mutableListOf<Double>()
    for (v in reps.map { it.valor }.sorted()) if (distinct.isEmpty() || !same(distinct.last(), v)) distinct.add(v)
    val lines = reps.take(5).map { Explanation(describe(it), listOf(src(it))) }.toMutableList()
    if (reps.size == 1) lines.add(Explanation("Apenas uma fonte independente com conteúdo lido informou esse valor."))
    val weakOther = rel.filter { it.conteudo !in STRONG_CONTENT && distinct.none { v -> same(it.valor, v) } }
    if (weakOther.isNotEmpty()) {
        lines.add(Explanation("Títulos ou trechos não lidos mencionam outro valor (não confirmado): " +
            weakOther.take(2).joinToString("; ") { describe(it) }, weakOther.take(2).map { src(it) }))
    }
    if (q != null && q.valor == null) { // pergunta sem valor alegado
        return if (distinct.size == 1) out("resposta", lines) else out("divergentes", lines + periodNote(reps))
    }
    val sup = reps.filter { it.relacao == "apoia" }
    val con = reps.filter { it.relacao == "contradiz" }
    val tipo = interp.afirmacao?.tipo
    if (con.isNotEmpty() && sup.isEmpty()) {
        // Nenhuma fonte lida confirma o valor informado. Se elas divergem entre si, isso é explicado.
        val extra = if (distinct.size == 1) emptyList() else listOf(Explanation("As fontes não concordam entre si (" +
            reps.map { it.textoValor }.toSortedSet().joinToString(", ") + "), mas nenhuma indica o valor informado. " + periodNote(reps).texto))
        return out("contradizem", lines + extra)
    }
    if (distinct.size > 1 && (tipo == "contagem" || tipo == "acontecimento_recente")) {
        // Quantidade que muda com o tempo: só vale "o dado mais recente" se o PERÍODO DO FATO estiver
        // explícito nos trechos. Data de publicação mais nova não torna um valor mais correto.
        if (reps.all { factPeriod(it) != null }) {
            val latest = reps.maxOf { factPeriod(it)!! }
            val newest = reps.filter { factPeriod(it) == latest }
            if (newest.map { it.relacao }.toSet().size == 1 && newest.all { same(it.valor, newest[0].valor) }) {
                val older = reps.filter { it !in newest }
                val note = Explanation("Os valores diferentes se referem a períodos anteriores (" +
                    older.mapNotNull { factPeriod(it) }.map { it.toString() }.toSortedSet().joinToString(", ") +
                    "); o trecho mais recente se refere a $latest: ${newest[0].textoValor} (${newest[0].veiculo}).", older.take(3).map { src(it) })
                return out(if (newest[0].relacao == "apoia") "apoiam" else "contradizem", lines + note)
            }
        }
        return out("divergentes", lines + periodNote(reps))
    }
    if (sup.isNotEmpty() && con.isEmpty()) return out("apoiam", lines)
    return out("divergentes", lines + periodNote(reps))
}

val INDICACAO = mapOf("dados_compativeis" to "Dados compatíveis nas fontes", "dados_diferentes" to "Dados diferentes nas fontes",
    "inconclusiva" to "Pesquisa inconclusiva", "resposta" to "Informação encontrada", "sem_resposta" to "Informação insuficiente",
    "falha_tecnica" to "Falha técnica")
const val AVISO_INDICACAO = "Pesquisa de fontes e contexto. A classificação de veracidade ainda não está disponível."

/** Descreve apenas o que as fontes consultadas informam; não classifica veracidade. */
fun indicate(s: Synthesis, interp: Interpretation): Indication {
    val q = interp.quantity
    val questionWithoutClaim = q != null && q.valor == null
    val (key, why) = when {
        s.situacao == "erro_tecnico" -> "falha_tecnica" to "Não foi possível consultar as fontes. Isso é uma falha técnica, não falta de evidências."
        questionWithoutClaim -> when (s.situacao) {
            "resposta" -> "resposta" to "Valor informado por fontes com conteúdo lido."
            "divergentes" -> "sem_resposta" to "As fontes indicam valores diferentes."
            else -> "sem_resposta" to "As fontes consultadas não informam esse valor de forma comparável."
        }
        s.situacao == "apoiam" -> "dados_compativeis" to "Os trechos consultados trazem dados compatíveis com este detalhe. Confira a fonte e o período."
        s.situacao == "contradizem" -> "dados_diferentes" to "Os trechos consultados trazem dados diferentes para este detalhe. Confira a fonte e o período."
        s.situacao == "divergentes" -> "inconclusiva" to "As fontes apresentam informações divergentes."
        s.situacao == "sem_comparacao" -> "inconclusiva" to "As regras do Lume só comparam quantidades, unidades, períodos e anos. Este tipo de afirmação exige compreensão do texto que as regras não oferecem."
        s.situacao == "nao_verificavel" -> "inconclusiva" to "Opinião ou previsão: não há um fato único para comparar."
        s.opcoes.isNotEmpty() -> "inconclusiva" to "O nome corresponde a entidades diferentes; escolha a que você quis dizer."
        else -> "inconclusiva" to "Faltam evidências comparáveis sobre este detalhe. Ausência de evidência não indica falsidade."
    }
    return Indication(key, INDICACAO.getValue(key), why, AVISO_INDICACAO)
}
