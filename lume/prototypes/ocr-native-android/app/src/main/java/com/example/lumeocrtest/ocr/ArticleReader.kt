package com.example.lumeocrtest.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Papel de cada bloco do OCR na página. */
enum class BlockRole(val label: String) {
    TITULO("título"), SUBTITULO("subtítulo"), ASSINATURA("assinatura/data"), CORPO("corpo"),
    LEGENDA("legenda de imagem"), CREDITO_IMAGEM("crédito de imagem"), TEXTO_NA_IMAGEM("texto dentro de imagem"),
    LATERAL("coluna lateral"), CABECALHO("cabeçalho/navegação"), INTERFACE("botões/interface"),
    RECOMENDACAO("recomendações"), ANUNCIO("anúncio"), CATEGORIA("editoria"), NAO_USADO("não usado"),
}

data class BlockDecision(val index: Int, val role: BlockRole, val reason: String, val text: String, val box: OcrRect?)

/** Resultado da leitura: cada campo vem com a linha do OCR que o sustenta (ou fica nulo). */
data class ArticleReading(
    val title: String?,
    /** Título visível apenas em parte (cortado na captura): não é usado como afirmação. */
    val partialTitle: String?,
    val subtitle: String?,
    val metadata: ArticleMetadata,
    val body: String,
    val decisions: List<BlockDecision>,
    /** Candidatos a título com a pontuação (diagnóstico). */
    val titleCandidates: List<Pair<String, Double>> = emptyList(),
) {
    fun roleOf(index: Int) = decisions.firstOrNull { it.index == index }?.role

    fun report(): String = buildString {
        appendLine("título=$title${partialTitle?.let { " | título parcial=“$it”" } ?: ""}")
        appendLine("candidatos a título=$titleCandidates")
        appendLine("subtítulo=$subtitle")
        val m = metadata
        appendLine("veículo=${m.source} (${m.sourceOrigin}; “${m.sourceEvidence}”)")
        appendLine("autor=${m.author} (${m.authorEvidence}) | assinatura institucional=${m.institutionalByline}")
        appendLine("data=${m.publishedAt} (${m.dateEvidence}) | atualizado=${m.updated} | local=${m.place}")
        appendLine("créditos=${m.imageCredits} | legendas=${m.captions}")
        appendLine("corpo=${body.replace("\n", " ⏎ ").take(700)}")
        decisions.sortedBy { it.box?.top ?: 0 }.forEach {
            appendLine("  [${it.index}] ${it.box?.let { b -> "${b.left},${b.top}-${b.right},${b.bottom}" }} ${it.role.label}: “${it.text.replace("\n", " / ").take(90)}” — ${it.reason}")
        }
    }
}

/**
 * Lê uma captura de notícia a partir da geometria dos blocos: tamanho de letra, alinhamento com a coluna
 * do texto, faixas sem texto (imagens), linhas de assinatura e marcadores de interface. Não usa nomes de
 * veículos, pessoas, assuntos nem posições fixas.
 */
class ArticleReader {
    private data class B(val i: Int, val block: OcrBlock, val box: OcrRect, val lh: Double, val words: Int, val text: String,
                         val conf: Float?)

    companion object {
        private val CHROME_WORDS = setOf("menu", "buscar", "busca", "pesquisar", "entrar", "login", "assine", "assinar",
            "compartilhe", "compartilhar", "comentário", "comentários", "comentar", "enquete", "proposta", "publicidade",
            "anúncio", "ok", "aceitar", "fechar", "voltar", "home", "início", "ouvir", "ouça", "salvar", "seguir",
            "whatsapp", "facebook", "twitter", "telegram", "linkedin", "f", "x", "in", "e", "newsletter", "podcast",
            "vídeos", "versão", "em", "áudio", "audio", "imprimir", "copiar", "link", "tempo", "de", "leitura", "min")
        private val CHROME_CONTAINS = Regex("(?iu)cookies|política de privacidade|continuar navegando|consulte aqui|" +
            "termos de uso|remover anúncio|assine já|receba notícias|ative as notificações")
        private val AD_TEXT = Regex("(?iu)^(?:publicidade|anúncio|continua depois da publicidade|continua após a publicidade|patrocinado|conteúdo patrocinado)$")
        private val END_SECTION = Regex("(?iu)^(?:leia também|leia mais|mais lidas|mais notícias|notícias relacionadas|" +
            "recomendad[oa]s?|você também pode gostar|veja também|principais notícias|últimas notícias|relacionadas|saiba mais)\\s*:?$")
        private val NUMERIC_ONLY = Regex("^[\\d\\s,.:%R$€US()+\\-−/↑↓]*\\d[\\d\\s,.:%R$€US()+\\-−/↑↓]*$")
        private val PLAYER = Regex("\\d{1,2}:\\d{2}\\s*/\\s*\\d{1,2}:\\d{2}")
        private val CREDIT = Regex("(?iu)^(?:©|foto|fotos|imagem|crédito|reprodução|divulgação|arquivo pessoal|getty|reuters|afp|ap photo|efe)|" +
            "(?:/\\s*(?:agência|agencia|divulgação|reprodução|getty|reuters|afp|arquivo)\\b)|\\bgetty\\s+ima|\\bshutterstock\\b")
        private val EDITORIAL_TAIL = Regex("(?iu)^(?:entenda|veja|saiba|confira|leia|assista|ouça|vídeo|video|ao vivo|infográfico|como|o que|por que|quem|quais)\\b")
        private val QUESTION_START = Regex("(?iu)^(?:por que|porque|como|o que|quem|qual|quais|quando|onde|quanto|será)\\b")
        private val SENTENCE_END = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}\"“])")
    }

    fun read(blocks: List<OcrBlock>, width: Int, height: Int): ArticleReading {
        val decisions = HashMap<Int, BlockDecision>()
        fun decide(b: B, role: BlockRole, reason: String) { decisions[b.i] = BlockDecision(b.i, role, reason, b.text, b.box) }
        val all = blocks.withIndex().filter { it.value.boundingBox != null && it.value.text.isNotBlank() }.map { (i, b) ->
            val boxes = b.lineDetails.mapNotNull { it.box }
            val lh = if (boxes.isNotEmpty()) boxes.map { it.height.toDouble() }.sorted()[boxes.size / 2]
                else b.boundingBox!!.height.toDouble() / max(1, b.lines.size)
            val confs = b.lineDetails.mapNotNull { it.confidence }
            B(i, b, b.boundingBox!!, lh, b.text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) },
                b.text.replace(Regex("\\s+"), " ").trim(), if (confs.isEmpty()) null else confs.average().toFloat())
        }
        if (all.isEmpty() || width <= 0 || height <= 0) {
            val text = blocks.joinToString("\n\n") { it.text.trim() }.trim()
            return ArticleReading(null, null, null, ArticleMetadataExtractor().extractMetadata(blocks).metadata, text, emptyList())
        }

        // 1. Interface: botões, cookies, player, cotações, textos de baixa confiança, avisos de publicidade.
        val content = mutableListOf<B>()
        val adMarkers = mutableListOf<B>()
        for (b in all) {
            val words = b.text.lowercase().split(Regex("[\\s/|·•]+")).filter { it.isNotBlank() }
            val reason = when {
                AD_TEXT.matches(b.text.trim()) -> "aviso de publicidade"
                CHROME_CONTAINS.containsMatchIn(b.text) -> "aviso de cookies/assinatura/notificações"
                words.isNotEmpty() && words.all { it.trim('(', ')', ':', '.', '!') in CHROME_WORDS } && b.words <= 6 -> "rótulos de botões ou menus"
                PLAYER.containsMatchIn(b.text) -> "controle de áudio/vídeo"
                NUMERIC_ONLY.matches(b.text) -> "somente números/cotações"
                b.conf != null && b.conf < 0.6f && b.words <= 2 -> "leitura de baixa confiança e muito curta"
                b.text.count { it.isLetterOrDigit() } < 2 -> "sem texto legível"
                else -> null
            }
            when {
                reason == "aviso de publicidade" -> { decide(b, BlockRole.ANUNCIO, reason); adMarkers += b }
                reason != null -> decide(b, BlockRole.INTERFACE, reason)
                else -> content += b
            }
        }
        val bylineLike = content.filter { b -> b.text.length <= 160 && b.block.lines.any { BylineParser.parse(it).isByline } }.toSet()
        val paragraphLike = content.filter { it !in bylineLike && isParagraphLike(it) }

        // 1b. Anúncio: do aviso de publicidade até o próximo parágrafo de texto.
        for (m in adMarkers) {
            val next = paragraphLike.filter { it.box.top > m.box.bottom }.minOfOrNull { it.box.top } ?: height
            content.filter { it.box.top > m.box.bottom && it.box.bottom <= next && it !in paragraphLike }
                .forEach { decide(it, BlockRole.ANUNCIO, "entre o aviso de publicidade e o texto seguinte") }
        }
        content.removeAll { it.i in decisions }

        // 2. Letra do corpo: quartil inferior da altura de linha dos parágrafos (títulos de várias linhas não puxam a referência).
        val bodyLh = (paragraphLike.filter { it.i !in decisions }.ifEmpty { content }).map { it.lh }.sorted()
            .let { if (it.isEmpty()) 12.0 else it[it.size / 4] }

        // 3. Título: letra bem maior que a do corpo, frase com várias palavras, seguido de assinatura ou texto.
        data class TitleGroup(val members: List<B>, val score: Double)
        val groups = mutableListOf<TitleGroup>()
        for (start in content.filter { it.lh >= bodyLh * 1.3 }.sortedBy { it.box.top }) {
            if (groups.any { g -> start in g.members }) continue
            val members = mutableListOf(start)
            var last = start
            while (true) {
                val next = content.filter { it !in members && it.box.top >= last.box.bottom - last.lh * 0.3 &&
                    it.box.top - last.box.bottom <= last.lh * 0.9 && abs(it.lh - start.lh) <= start.lh * 0.25 &&
                    (abs(it.box.left - start.box.left) <= width * 0.06 || abs(center(it.box) - center(start.box)) <= width * 0.06) }
                    .minByOrNull { it.box.top } ?: break
                members += next; last = next
            }
            val text = members.joinToString(" ") { it.text }
            val words = members.sumOf { it.words }
            val bottom = members.maxOf { it.box.bottom }
            val bylineBelow = bylineLike.any { it !in members && it.box.top >= bottom - 4 && it.box.top - bottom <= max(start.lh * 10, height * 0.3) }
            val textBelow = paragraphLike.any { it !in members && it.box.top > bottom && overlapX(it.box, start.box) > 0 }
            val orgLike = BylineParser.ORG_WORDS.containsMatchIn(text) && words <= 5
            var score = 2 * min(start.lh / bodyLh, 3.0) + (if (words >= 4) 2.0 else if (words < 3) -4.0 else 0.0) +
                (if (bylineBelow) 3.0 else 0.0) + (if (textBelow) 2.0 else 0.0) - (if (isAllCaps(text)) 3.0 else 0.0) -
                (if (orgLike) 3.0 else 0.0) - start.box.top.toDouble() / height
            if (text.first().isLowerCase()) score -= 3.0 // começa no meio de uma frase: título cortado
            groups += TitleGroup(members, score)
        }
        val best = groups.maxByOrNull { it.score }?.takeIf { it.score > 1.0 }
        val titleCandidates = groups.sortedByDescending { it.score }
            .map { g -> g.members.joinToString(" ") { it.text }.take(80) to Math.round(g.score * 10) / 10.0 }
        val titleMembers = best?.members.orEmpty()
        val titleText = titleMembers.joinToString(" ") { it.text }.takeIf { it.isNotBlank() }
        val titleCut = titleText != null && (titleText.first().isLowerCase() || titleMembers.sumOf { it.words } < 3)
        titleMembers.forEach { decide(it, BlockRole.TITULO, if (titleCut) "letra de título, mas começa no meio da frase (cortado)" else "maior letra da matéria, seguida de assinatura/texto") }
        val titleTop = titleMembers.minOfOrNull { it.box.top }
        val titleBottom = titleMembers.maxOfOrNull { it.box.bottom } ?: -1
        val titleLh = titleMembers.firstOrNull()?.lh ?: 0.0

        // 4. Coluna da matéria: título + parágrafos alinhados a ele (ou, sem título, o maior parágrafo).
        val anchorBox = titleMembers.firstOrNull()?.box ?: paragraphLike.maxByOrNull { it.text.length }?.box
        val column = if (anchorBox == null) null else {
            val aligned = paragraphLike.filter { overlapX(it.box, anchorBox) >= min(it.box.width, anchorBox.width) * 0.5 }
            val boxes = aligned.map { it.box } + titleMembers.map { it.box } + anchorBox
            boxes.minOf { it.left } to boxes.maxOf { it.right }
        }
        val flow = (paragraphLike + titleMembers).distinct()
        fun lateralReason(b: B): String? {
            if (column == null) return null
            val (cl, cr) = column
            if (b.box.right <= cl + 2 || b.box.left >= cr - 2) return "fora da coluna do texto principal"
            val mine = lineBoxes(b).toSet()
            val sameRow = flow.filter { it !== b }.flatMap { lineBoxes(it) }.filter { l -> l !in mine && l.top < b.box.bottom && l.bottom > b.box.top }
            if (sameRow.isNotEmpty() && sameRow.none { l -> l.left < b.box.right && l.right > b.box.left } && b.words <= 12)
                return "ao lado das linhas do texto principal (área lateral)"
            return null
        }

        // 5. Laterais, cabeçalho e editoria.
        for (b in content.filter { it.i !in decisions }) {
            val lateral = lateralReason(b)
            if (lateral != null) { decide(b, BlockRole.LATERAL, lateral); continue }
            if (titleTop != null && b.box.bottom <= titleTop + 2) {
                val gap = titleTop - b.box.bottom
                if (gap <= titleLh * 1.6 && b.words <= 3 && b.lh < titleLh) decide(b, BlockRole.CATEGORIA, "rótulo curto logo acima do título")
                else decide(b, BlockRole.CABECALHO, "acima do título (cabeçalho, menu ou barra do site)")
            }
        }

        // 6. Fim da matéria: “Leia também”, “Mais lidas”… depois de algum texto da própria matéria.
        val below = content.filter { it.i !in decisions && (titleTop == null || it.box.top >= titleTop) }.sortedBy { it.box.top }
        val endMarker = below.firstOrNull { m -> END_SECTION.matches(m.text) && (titleTop == null || m.box.top > titleBottom) &&
            paragraphLike.any { it.box.bottom <= m.box.top && it.box.top > titleBottom } }
        if (endMarker != null) below.filter { it.box.top >= endMarker.box.top }
            .forEach { decide(it, BlockRole.RECOMENDACAO, "depois de “${endMarker.text}”") }

        // 7. Assinatura/data: da base do título até o 2º parágrafo (o 1º pode ser o subtítulo/linha fina).
        val paragraphsBelow = paragraphLike.filter { it.i !in decisions && it.box.top > titleBottom }.sortedBy { it.box.top }
        val zoneEnd = paragraphsBelow.getOrNull(1)?.box?.top ?: height
        var author: String? = null; var authorEv: String? = null
        var org: String? = null; var orgEv: String? = null
        var date: String? = null; var dateEv: String? = null; var dateMs: Long? = null
        var updated: String? = null; var place: String? = null
        val bylineBlocks = mutableListOf<B>()
        val bareNames = mutableListOf<Pair<B, String>>()
        val zone = content.filter { it.i !in decisions && it.box.top >= titleBottom - 2 && it.box.top < zoneEnd &&
            it.text.length <= 160 && it !in paragraphLike }.sortedBy { it.box.top }
        val zoneLines = zone.flatMap { b -> linesOf(b).map { b to it } }
        var k = 0
        while (k < zoneLines.size) {
            val (b, line) = zoneLines[k]
            var text = line.text.trim()
            var joined: B? = null
            // "NOME - REPÓRTER DA" + "AGÊNCIA X": a linha termina num conector e continua logo abaixo.
            if (Regex("(?iu)(?:\\s(?:d[aoe]s?|para)|[-–—,])$").containsMatchIn(text) && k + 1 < zoneLines.size) {
                val (nb, nl) = zoneLines[k + 1]
                val gap = (nl.box?.top ?: nb.box.top) - (line.box?.bottom ?: b.box.bottom)
                if (gap in -4..(b.lh * 1.5).toInt()) { text = "$text ${nl.text.trim()}"; joined = nb; k++ }
            }
            val p = BylineParser.parse(text)
            if (p.isByline) {
                bylineBlocks += b; joined?.let { bylineBlocks += it }
                if (author == null && p.author != null) { author = p.author; authorEv = text }
                if (org == null && p.organization != null) { org = p.organization; orgEv = text }
                if (date == null && p.date != null) { date = p.date; dateMs = p.dateMs; dateEv = text }
                if (updated == null) updated = p.updated
                if (place == null) place = p.place
            } else if (p.updated != null && bylineBlocks.isNotEmpty()) {
                bylineBlocks += b; updated = updated ?: p.updated
            } else if (p.bareName != null) {
                bareNames += b to p.bareName
            } else if (bylineBlocks.isNotEmpty() && place == null && BylineParser.isPlace(text) &&
                verticalGap(bylineBlocks.last().box, b.box) <= b.lh * 1.5 && abs(bylineBlocks.last().box.left - b.box.left) <= width * 0.05) {
                bylineBlocks += b; place = text
            }
            k++
        }
        // Nome isolado só é autor se estiver colado a uma linha de assinatura (ex.: "Nome >" sobre "Da Org em Cidade").
        if (author == null) {
            val near = bareNames.firstOrNull { (b, _) -> bylineBlocks.any { o -> o !== b && verticalGap(o.box, b.box) <= max(b.lh, o.lh) * 1.5 &&
                abs(o.box.left - b.box.left) <= width * 0.05 } }
            if (near != null) { author = near.second; authorEv = "“${near.first.text}” junto da linha “${orgEv ?: dateEv}”"; bylineBlocks += near.first }
        }
        bylineBlocks.distinct().forEach { decide(it, BlockRole.ASSINATURA, "linha de autoria/veículo/data") }

        // 8. Subtítulo: frase logo abaixo do título (completo ou cortado), antes da assinatura.
        val firstByline = bylineBlocks.minOfOrNull { it.box.top } ?: height
        val subtitleBlocks = if (titleMembers.isEmpty()) emptyList() else content.filter { b ->
            b.i !in decisions && b.box.top >= titleBottom - 2 && b.box.top - titleBottom <= max(titleLh, bodyLh) * 3 &&
                b.text.length >= 25 && b.words >= 5 && b.box.top < firstByline
        }.sortedBy { it.box.top }.take(1)
        subtitleBlocks.forEach { decide(it, BlockRole.SUBTITULO, "frase logo abaixo do título, antes da assinatura") }

        // 9. Corpo: parágrafos na coluna, abaixo do título/assinatura, com letra de texto (não de título).
        val bodyBlocks = paragraphLike.filter { it.i !in decisions && (titleTop == null || it.box.top > titleBottom) &&
            it.lh < max(titleLh, bodyLh * 1.3) * 0.95 }.toMutableList()
        val bodyRef = bodyBlocks.filter { it.block.lines.size >= 2 }
        // Linha isolada alinhada ao corpo e com a mesma letra (parágrafo curto ou cortado no fim da captura).
        content.filter { b -> b.i !in decisions && b !in bodyBlocks && b.words >= 4 && bodyRef.any { r -> b.box.top > r.box.top &&
            abs(b.box.left - r.box.left) <= width * 0.04 && abs(b.lh - r.lh) <= r.lh * 0.2 } }
            .forEach { bodyBlocks += it }
        val bodyFlow = (titleMembers + subtitleBlocks + bylineBlocks + bodyBlocks).distinct()

        // 10. Faixas de imagem: legenda, crédito, texto dentro da imagem.
        val imageCandidates = content.filter { it.i !in decisions && it !in bodyBlocks && (titleTop == null || it.box.top > titleTop) }
        for (b in imageCandidates) {
            val above = bodyFlow.filter { it !== b && it.box.bottom <= b.box.top + 2 && overlapX(it.box, b.box) > 0 }.maxOfOrNull { it.box.bottom }
            val freeAbove = b.box.top - (above ?: 0)
            val nextFlow = bodyFlow.filter { it !== b && it.box.top >= b.box.bottom - 2 }.minOfOrNull { it.box.top }
            // Nome solto só é crédito se estiver no pé da imagem: colado à legenda ou ao texto que vem depois.
            val captionNear = imageCandidates.any { c -> c !== b && c.words >= 5 && !isAllCaps(c.text) && verticalGap(c.box, b.box) <= bodyLh * 2 }
            val nameCredit = b.words in 2..4 && BylineParser.isPersonName(b.text) && b.lh <= bodyLh * 1.1 &&
                (captionNear || (nextFlow != null && nextFlow - b.box.bottom <= bodyLh * 4))
            when {
                (CREDIT.containsMatchIn(b.text) || nameCredit) && b.words <= 8 ->
                    decide(b, BlockRole.CREDITO_IMAGEM, "crédito junto à imagem (não é autoria da matéria)")
                b.block.lines.size <= 2 && b.words >= 5 && freeAbove >= bodyLh * 4 && !isAllCaps(b.text) &&
                    (nextFlow == null || nextFlow - b.box.bottom >= b.lh * 1.4 || b.box.bottom >= height - b.lh * 2) ->
                    decide(b, BlockRole.LEGENDA, "frase curta no pé de uma área sem texto (imagem)")
                else -> decide(b, BlockRole.TEXTO_NA_IMAGEM, "texto curto fora do fluxo do texto (imagem, selo ou arte)")
            }
        }
        bodyBlocks.filter { it.i !in decisions }.forEach { decide(it, BlockRole.CORPO, "parágrafo na coluna do texto principal") }
        content.filter { it.i !in decisions }.forEach { decide(it, BlockRole.NAO_USADO, "sem evidência suficiente para classificar") }

        // 11. Veículo sem assinatura: rótulo "Fonte:", domínio exibido ou (só como possibilidade) o cabeçalho.
        val legacy = ArticleMetadataExtractor().extractMetadata(blocks, (bylineBlocks + zone).map { it.i }.toSet(), titleMembers.map { it.i })
        var source = org; var sourceOrigin = if (org != null) MetadataOrigin.BYLINE else null; var sourceEv = orgEv
        if (org != null && Regex("(?iu)^(?:a\\s+)?(?:redação|redacao|equipe|editoria)$").matches(org)) { source = null; sourceOrigin = null }
        if (source == null && legacy.metadata.source != null && legacy.metadata.sourceOrigin != MetadataOrigin.HEADER_CANDIDATE) {
            source = legacy.metadata.source; sourceOrigin = legacy.metadata.sourceOrigin; sourceEv = legacy.metadata.source
        }
        if (source == null && titleTop != null) {
            // Logotipo/nome do site no topo: bloco curto acima do título que NÃO faz parte de uma fileira de menu.
            val header = all.filter { it.box.bottom <= titleTop + 2 && it.words in 1..4 && (it.conf ?: 1f) >= 0.6f &&
                decisions[it.i]?.role in setOf(BlockRole.CABECALHO, BlockRole.LATERAL, BlockRole.INTERFACE) &&
                it.text.any(Char::isLetter) && it.text.none(Char::isDigit) }
            fun inMenuRow(b: B) = all.count { o -> o !== b && o.box.bottom <= titleTop + 2 && o.words <= 4 &&
                o.box.top < b.box.bottom && o.box.bottom > b.box.top } >= 2
            // Trilha de navegação ("Notícia • Site / Seção / Subseção") que repete o nome confirma o logotipo.
            val crumbs = all.filter { it.box.bottom <= titleTop + 2 && Regex("\\s[/›>»]\\s").containsMatchIn(it.text) }.map { plain(it.text) }
            val options = header.filter { !inMenuRow(it) }
            val confirmed = options.firstOrNull { c -> crumbs.any { cr -> " ${plain(c.text)} " in " $cr " } }
            val masthead = confirmed ?: options.filter { BylineParser.ORG_WORDS.containsMatchIn(it.text) }.minByOrNull { it.box.top }
            if (masthead != null) {
                source = BylineParser.normalizeName(masthead.text).let { if (it == masthead.text && isAllCaps(it)) it.lowercase().replaceFirstChar { c -> c.uppercase() } else it }
                sourceOrigin = MetadataOrigin.HEADER_CANDIDATE
                sourceEv = if (confirmed != null) "“${masthead.text}” no topo da página, repetido na trilha de navegação" else masthead.text
            }
        }
        if (author == null && legacy.metadata.author != null && legacy.metadata.authorEvidence == "rótulo de autoria") {
            author = legacy.metadata.author; authorEv = "rótulo de autoria"
        }
        if (date == null && legacy.metadata.publishedAt != null) { date = legacy.metadata.publishedAt; dateEv = legacy.metadata.dateEvidence }
        val metadata = ArticleMetadata(
            source = source, sourceOrigin = sourceOrigin, author = author, publishedAt = date, url = legacy.metadata.url,
            authorEvidence = authorEv, dateEvidence = dateEv,
            institutionalByline = if (author == null && org != null) org else null, sourceEvidence = sourceEv,
            place = place, updated = updated,
            imageCredits = decisions.values.filter { it.role == BlockRole.CREDITO_IMAGEM }.map { it.text },
            captions = decisions.values.filter { it.role == BlockRole.LEGENDA }.map { it.text }, publishedAtMs = dateMs,
        )
        val bodyText = ArticleTextExtractor().mergeInOrder(bodyBlocks.sortedBy { it.box.top }.map { it.block })
        return ArticleReading(
            title = titleText?.takeUnless { titleCut }, partialTitle = titleText?.takeIf { titleCut },
            subtitle = subtitleBlocks.firstOrNull()?.text, metadata = metadata, body = bodyText,
            decisions = decisions.values.sortedBy { it.index }, titleCandidates = titleCandidates,
        )
    }

    /** Afirmação a pesquisar: o título (sem “entenda…/veja…”), ou nada quando a captura não permite escolher com segurança. */
    fun claimFor(reading: ArticleReading): ClaimChoice {
        val sentences = reading.body.split("\n").flatMap { it.split(SENTENCE_END) }.map { it.trim() }
            .filter { s -> s.length in 30..320 && s.split(" ").size >= 5 && !s.endsWith("?") }
        val alternatives = (listOfNotNull(reading.subtitle) + sentences).distinct().take(4)
        val title = reading.title
        if (title == null) {
            val why = if (reading.partialTitle != null) "O título aparece cortado na imagem (“…${reading.partialTitle}”)."
                else "Não encontramos um título nesta imagem."
            return ClaimChoice(null, alternatives, true, "$why Escolha uma frase abaixo ou escreva o que quer pesquisar.")
        }
        var claim = title.trim()
        Regex("(?iu)^(?:vídeo|video|ao vivo|exclusivo|urgente|análise|opinião)\\s*[:|-]\\s*").find(claim)?.let { claim = claim.substring(it.range.last + 1) }
        val cut = Regex("\\s*(?::|\\s[-–—]\\s|;)\\s*").findAll(claim).firstOrNull { m -> EDITORIAL_TAIL.containsMatchIn(claim.substring(m.range.last + 1)) }
        if (cut != null) claim = claim.substring(0, cut.range.first)
        claim = claim.trim().trimEnd('.', ':', ';')
        if (claim.endsWith("?") || QUESTION_START.containsMatchIn(claim)) {
            return ClaimChoice(null, alternatives, true,
                "O título é uma pergunta (“$claim”). Escolha uma afirmação para pesquisar ou escreva a sua.")
        }
        return ClaimChoice(claim, alternatives.filter { it != claim }, false, null)
    }

    private fun isParagraphLike(b: B) = (b.block.lines.size >= 2 && b.text.length >= 40 && b.words >= 6) ||
        (b.text.length >= 110 && b.words >= 15)

    private fun linesOf(b: B): List<OcrLine> = b.block.lineDetails.ifEmpty {
        val n = max(1, b.block.lines.size)
        b.block.lines.mapIndexed { idx, t ->
            OcrLine(t, OcrRect(b.box.left, b.box.top + b.box.height * idx / n, b.box.right, b.box.top + b.box.height * (idx + 1) / n))
        }
    }

    private fun lineBoxes(b: B): List<OcrRect> = linesOf(b).mapNotNull { it.box }
    private fun isAllCaps(t: String) = t.filter { it.isLetter() }.let { it.length >= 4 && it.count(Char::isUpperCase) >= it.length * 0.85 }
    private fun center(r: OcrRect) = (r.left + r.right) / 2.0
    /** Texto sem acentos, em minúsculas, só letras e dígitos (para comparar "ESTADÃO" com "Estadāo"). */
    private fun plain(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun overlapX(a: OcrRect, b: OcrRect) = max(0, min(a.right, b.right) - max(a.left, b.left))
    private fun verticalGap(a: OcrRect, b: OcrRect) = max(0, max(a.top, b.top) - min(a.bottom, b.bottom))
}

/**
 * A frase começa por um sujeito que depende do que vem antes ("O texto…", "A medida…", "Ele…")?
 * Retorna o trecho, para a interface pedir que a pessoa diga do que se trata (sem completar por suposição).
 */
fun vagueSubject(sentence: String): String? = Regex(
    "(?iu)^\\s*((?:o|a|os|as|esse|essa|este|esta)\\s+(?:texto|medida|proposta|projeto|decisão|decisao|norma|documento|matéria|materia|regra|ação|acao)|ele|ela|eles|elas|isso|isto)(?![\\p{L}])"
).find(sentence)?.groupValues?.get(1)

/** Afirmação sugerida e alternativas; `needsChoice` = a interface deve pedir que o usuário escolha. */
data class ClaimChoice(val claim: String?, val alternatives: List<String>, val needsChoice: Boolean, val note: String?)
