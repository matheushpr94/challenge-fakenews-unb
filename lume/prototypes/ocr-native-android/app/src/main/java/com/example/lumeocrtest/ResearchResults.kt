package com.example.lumeocrtest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lumeocrtest.research.AVISO_INDICACAO
import com.example.lumeocrtest.research.ContextItem
import com.example.lumeocrtest.research.Dates
import com.example.lumeocrtest.research.Evaluation
import com.example.lumeocrtest.research.NOT_THIS
import com.example.lumeocrtest.research.Option
import com.example.lumeocrtest.research.QUESTION
import com.example.lumeocrtest.research.ResearchResult
import com.example.lumeocrtest.research.ResultCard
import com.example.lumeocrtest.research.Synthesis
import com.example.lumeocrtest.research.RelationKind
import com.example.lumeocrtest.research.Independence
import com.example.lumeocrtest.research.EventEvidence
import com.example.lumeocrtest.research.DetailStatus
import com.example.lumeocrtest.research.norm
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.example.lumeocrtest.research.jaccard
import com.example.lumeocrtest.research.titleKey
import com.example.lumeocrtest.ui.theme.Amber
import com.example.lumeocrtest.ui.theme.AmberSoft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import com.example.lumeocrtest.research.SignalBand
import com.example.lumeocrtest.research.TextSignal
import com.example.lumeocrtest.research.FactCheckSource
import com.example.lumeocrtest.research.factChecksIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import com.example.lumeocrtest.ui.theme.Ink
import com.example.lumeocrtest.ui.theme.LumeGreen
import com.example.lumeocrtest.ui.theme.Hairline
import com.example.lumeocrtest.ui.theme.InkFaint
import com.example.lumeocrtest.ui.theme.InkSoft
import com.example.lumeocrtest.ui.theme.LumeMist
import com.example.lumeocrtest.ui.theme.Sheet
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen

private val WARN = Amber
private val NEUTRAL = InkSoft

private fun indicationColor(rotulo: String?) = when (rotulo) {
    "dados_compativeis", "resposta" -> LumeDarkGreen
    "dados_diferentes" -> WARN
    "falha_tecnica" -> WARN
    else -> NEUTRAL
}

private fun validUri(url: String): Uri? =
    runCatching { Uri.parse(url) }.getOrNull()?.takeIf { it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() }

@Composable
private fun ResearchLink(title: String, url: String, small: Boolean = false) {
    val context = LocalContext.current
    val uri = validUri(url) ?: return
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } },
        contentPadding = if (small) PaddingValues(horizontal = 6.dp, vertical = 0.dp) else ButtonDefaults.TextButtonContentPadding,
        modifier = if (small) Modifier.heightIn(min = 48.dp) else Modifier) {
        Text(title.ifBlank { url }, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
    }
}

/** Ação principal de cada fonte: abrir a publicação no navegador. */
@Composable
private fun OpenSourceButton(url: String, veiculo: String) {
    val context = LocalContext.current
    val uri = validUri(url) ?: return
    FilledTonalButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Abrir fonte: $veiculo" }) {
        Text("Abrir fonte", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

/** "O que você quer saber?" com opções tocáveis que iniciam a pesquisa. */
@Composable
fun ClarificationChooser(options: List<Option>, onOption: (String) -> Unit, onNotThis: (() -> Unit)?) {
    LumeCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(QUESTION, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            options.forEachIndexed { i, o ->
                Reveal(i, o.texto) {
                    LumeCard(color = LumeMist, border = LumeLightGreen, onClick = { onOption(o.texto) }) {
                        Text(o.texto, Modifier.padding(horizontal = 14.dp, vertical = 14.dp), style = MaterialTheme.typography.titleMedium,
                            color = LumeDarkGreen)
                    }
                }
            }
            if (onNotThis != null) OutlinedButton(onClick = onNotThis, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(NOT_THIS) }
        }
    }
}

@Composable
private fun IndicationCard(s: Synthesis, title: String = "O que as fontes mostram", observations: List<String> = emptyList()) {
    val ind = s.indicacao
    val color = indicationColor(ind?.rotulo)
    LumeCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(title)
            Text(ind?.texto ?: s.frase, style = MaterialTheme.typography.headlineSmall, color = color)
            ind?.motivo?.let {
                Text(if (s.situacao == "sem_comparacao") "As fontes encontradas ainda não permitem esclarecer este detalhe." else it,
                    style = MaterialTheme.typography.bodyMedium)
            }
            s.detalhe?.let { Text("Detalhe comparado: $it", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
            if (s.explicacao.isNotEmpty()) {
                var showAll by remember(s) { mutableStateOf(false) }
                HorizontalDivider(color = Hairline)
                Text("O que encontramos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                val visible = if (showAll) s.explicacao else s.explicacao.take(3)
                visible.forEach { e ->
                    Text(e.texto, style = MaterialTheme.typography.bodyMedium)
                    e.fontes.take(2).forEach { ResearchLink("Abrir: ${it.veiculo}", it.url, small = true) }
                }
                if (s.explicacao.size > 3) ExpanderRow(if (showAll) "Ver menos" else "Ver mais (${s.explicacao.size - 3})", showAll, { showAll = !showAll })
            }
            observations.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }
            Text(ind?.aviso ?: AVISO_INDICACAO, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
    }
}

private fun relationLabel(c: ResultCard): Pair<String, Tone> = when {
    c.relacaoTipo == Independence.PROPRIA -> "A própria matéria" to Tone.Neutral
    c.relacaoTipo == Independence.MESMO_VEICULO -> "Mesmo veículo" to Tone.Neutral
    c.relacaoTipo == Independence.REPUBLICACAO -> "Republicação" to Tone.Neutral
    c.relacaoTipo == RelationKind.DIFERENTE -> "Detalhe divergente" to Tone.Amber
    c.relacaoTipo == RelationKind.INDEFINIDO -> "Relação incerta" to Tone.Amber
    c.detalheStatus == DetailStatus.CITA -> "Cita o mesmo detalhe" to Tone.Green
    c.relacaoTipo == RelationKind.MESMO_FATO -> "Mesmo acontecimento" to Tone.Green
    c.relacaoTipo == RelationKind.ANTERIOR -> "Fato anterior" to Tone.Neutral
    c.relacaoTipo == RelationKind.OUTRO -> "Outro acontecimento" to Tone.Neutral
    c.relacao == "responde" -> "Informa o detalhe pesquisado" to Tone.Green
    else -> "Contexto relacionado" to Tone.Neutral
}

private fun readLabel(base: String) = when (base) {
    "pagina" -> "Página lida"
    "trecho" -> "Só o resumo do buscador"
    else -> "Só o título"
}

/** Fonte indicada só pelo modelo de linguagem (as regras do app não confirmaram). */
private fun fromModel(c: ResultCard) = "modelo de linguagem" in c.resumoRelacao || "modelo de linguagem" in c.relacaoTexto

/** Data relativa curta ("há 3 h", "ontem", "29/09/2026"). */
private fun relativeDate(ms: Long?, now: Long = System.currentTimeMillis()): String? {
    ms ?: return null
    val min = (now - ms) / 60_000L
    return when {
        min < 0 -> Dates.format(ms)
        min < 60 -> "há ${maxOf(1, min)} min"
        min < 24 * 60 -> "há ${min / 60} h"
        min < 48 * 60 -> "ontem"
        min < 7 * 24 * 60 -> "há ${min / (24 * 60)} dias"
        else -> Dates.format(ms)
    }
}

private val AVATAR_COLORS = listOf(Color(0xFF2B4035), Color(0xFF53775B), Color(0xFF2C4F6B), Color(0xFF6B4E2C),
    Color(0xFF5A3D5C), Color(0xFF3D5A5A), Color(0xFF7A4A3A))

/** Inicial do veículo num círculo de cor estável (a mesma cor para o mesmo veículo). */
@Composable
private fun OutletAvatar(name: String) {
    val color = AVATAR_COLORS[Math.floorMod(norm(name).hashCode(), AVATAR_COLORS.size)]
    Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = Color.White,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * Uma publicação, compacta: veículo, data e quanto foi lido → título → selo (só quando difere do padrão da
 * seção) → trecho de evidência → "Por que esta relação?" (folha inferior) e abrir a fonte.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(c: ResultCard, sectionLabel: String? = null) {
    val (label, tone) = when {
        fromModel(c) && c.relacaoTipo == RelationKind.MESMO_FATO -> "Confirmado pelo modelo de linguagem" to Tone.Blue
        fromModel(c) -> "Indicado pelo modelo de linguagem" to Tone.Blue
        else -> relationLabel(c)
    }
    // Na fonte indicada pelo modelo, o selo já diz a origem: a frase fica só com a orientação ("confira a data").
    val summary = c.resumoRelacao.trim().let { if (fromModel(c)) it.substringBefore(" (modelo").trim() else it }
    val summaryIsLabel = summary.isNotEmpty() && (norm(summary).startsWith(norm(label)) ||
        jaccard(titleKey(summary), titleKey(label)) >= 0.5 || "relata o mesmo acontecimento" in norm(summary))
    val tagText = if (norm(summary).startsWith(norm(label)) && summary.length <= 42) summary else label
    val showTag = sectionLabel == null || norm(tagText) != norm(sectionLabel)
    LumeCard(border = if (fromModel(c)) Color(0xFFB9CDE0) else Hairline) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutletAvatar(c.veiculo)
                Column(Modifier.weight(1f)) {
                    Text(c.veiculo, style = MaterialTheme.typography.titleSmall, color = LumeDarkGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(relativeDate(c.data), readLabel(c.baseLeitura),
                        c.autor?.takeIf { norm(it) != norm(c.veiculo) }?.let { "por $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(c.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (showTag) Tag(tagText, tone)
            if (summary.isNotEmpty() && !summaryIsLabel) Text(summary, style = MaterialTheme.typography.bodyMedium, color = InkSoft)

            val reasons = c.motivos.orEmpty()
            val compared = c.trechosComparacao.orEmpty()
            val strongest = compared.lastOrNull { it.onde != "captura" && it.onde != "titulo" }
            val quote: Pair<String, String>? = when {
                strongest != null -> strongest.texto to whereLabel(strongest.onde)
                compared.isNotEmpty() -> null
                (c.trechoRelacao ?: c.trechosPagina.firstOrNull()) != null ->
                    (c.trechoRelacao ?: c.trechosPagina.first()) to (if (c.baseLeitura == "pagina") "Na página da fonte" else "No resumo do buscador")
                c.trecho != null && c.relacaoTipo != RelationKind.MESMO_FATO ->
                    c.trecho.take(300) to (if (c.conteudo == "resumo") "Resumo da Wikipédia" else "No resumo do buscador")
                else -> null
            }
            quote?.takeIf { jaccard(titleKey(it.first), titleKey(c.titulo)) < 0.8 }?.let { QuoteBlock(it.first.take(320), it.second) }

            // Avisos de leitura repetem o que a linha de cima já diz ("Só o resumo do buscador").
            c.alertas.filterNot { it.startsWith("Lemos apenas") || it.startsWith("Indicação do modelo") || it.startsWith("A regra do app não confirmou") || it.startsWith("Página indisponível") || it.startsWith("Não foi possível extrair") }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (fromModel(c)) Color(0xFF2C4F6B) else WARN)
            }
            if (c.republicacoes.isNotEmpty()) {
                Text("Mesmo texto também em: " + c.republicacoes.take(3).joinToString(", ") { it.veiculo } +
                    if (c.republicacoes.size > 3) " e mais ${c.republicacoes.size - 3}" else "",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            var why by remember(c.url) { mutableStateOf(false) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically) {
                if (reasons.isNotEmpty()) TextButton(onClick = { why = true }, contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Por que esta relação?", style = MaterialTheme.typography.labelLarge)
                }
                else Spacer(Modifier.width(1.dp))
                OpenSourceButton(c.url, c.veiculo)
            }
            if (why) ModalBottomSheet(onDismissRequest = { why = false }, containerColor = Sheet) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline("Por que esta relação?")
                    Text(c.veiculo, style = MaterialTheme.typography.titleSmall, color = LumeDarkGreen)
                    Text(c.titulo, style = MaterialTheme.typography.titleMedium)
                    Tag(tagText, tone)
                    ComparisonDetails(reasons, compared)
                }
            }
        }
    }
}

private fun whereLabel(onde: String) = when (onde) {
    "captura" -> "Na matéria importada"
    "titulo" -> "No título da fonte"
    "resumo" -> "No resumo do buscador"
    else -> "Na página da fonte"
}

/** Por que esta relação: quem, ação, assunto, data, números — e os trechos literais comparados. */
@Composable
private fun ComparisonDetails(reasons: List<String>, compared: List<EventEvidence>) {
    Column(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reasons.forEach { r ->
            val (k, v) = r.substringBefore(":", "") to r.substringAfter(":", r)
            if (k.isNotEmpty() && k.length <= 22) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(k, style = MaterialTheme.typography.labelLarge, color = LumeDarkGreen, modifier = Modifier.width(92.dp))
                Text(v.trim(), style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
            } else Text(r, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
        if (compared.isNotEmpty()) HorizontalDivider(color = Hairline)
        compared.forEach { e -> QuoteBlock(e.texto, whereLabel(e.onde)) }
    }
}

@Composable
private fun ContextSection(items: List<ContextItem>) {
    if (items.isEmpty()) return
    SectionHeader("Contexto")
    items.forEach { c ->
        LumeCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuoteBlock(c.texto, "${c.motivo.replaceFirstChar { it.uppercase() }} — ${c.veiculo}")
                ResearchLink(c.titulo.ifBlank { c.veiculo }, c.url, small = true)
            }
        }
    }
}

/** Lista de fontes: os primeiros itens entram em sequência suave; o restante fica atrás de "Ver mais". */
@Composable
private fun CardList(items: List<ResultCard>, visible: Int, moreLabel: String, sectionLabel: String? = null) {
    var expanded by remember(items) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(visible).forEachIndexed { i, c -> Reveal(i, c.url) { SourceCard(c, sectionLabel) } }
        val rest = items.drop(visible)
        if (rest.isNotEmpty()) {
            ExpanderRow(if (expanded) "Ver menos" else "$moreLabel (${rest.size})", expanded, { expanded = !expanded })
            LumeVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { rest.forEach { SourceCard(it, sectionLabel) } } }
        }
    }
}

private val SECTION_COLORS = mapOf("Mesmo fato" to LumeGreen, "Divergente" to Amber, "Incerta" to Color(0xFF8FB0C9),
    "Contexto" to Color(0xFFC9CFC2), "Anteriores" to Color(0xFFDDE1D7), "Não independentes" to Color(0xFFB5B9AE))

/** Barra fina com a proporção de cada tipo de relação (cores das seções). */
@Composable
private fun ProportionBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }
    if (total == 0) return
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).semantics { contentDescription = "Proporção dos tipos de relação" },
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.first > 0 }.forEach { (n, color) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color)) }
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier = Modifier, tone: Color = LumeDarkGreen) {
    if (largeText()) Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = tone)
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = tone)
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

@Composable
private fun ResultBlock(r: ResearchResult, title: String?, onOption: (String) -> Unit, signal: TextSignal? = null) {
    val all = r.resultados.values.flatten().distinctBy { it.url }
    val notIndependent = all.filter { it.relacao == "nao_independente" }
    val independent = all - notIndependent.toSet()
    val same = independent.filter { it.relacaoTipo == RelationKind.MESMO_FATO || it.relacao == "responde" }
    val different = independent.filter { it.relacaoTipo == RelationKind.DIFERENTE }
    val uncertain = independent.filter { it.relacaoTipo == RelationKind.INDEFINIDO && it.relacao != "responde" }
    val context = independent.filter { it.relacaoTipo == RelationKind.CONTEXTO && it.relacao != "responde" }
    val older = independent.filter { it.relacaoTipo == RelationKind.ANTERIOR }
    val detail = r.interpretacao.prazos.firstOrNull() ?: r.interpretacao.numeros.firstOrNull()?.texto
    val outlets = (same + different).flatMap { listOf(it.veiculo) + it.republicacoes.map { p -> p.veiculo } }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        if (r.status == "ambigua" && r.sugestoes.isNotEmpty()) ClarificationChooser(r.sugestoes, onOption, null)

        // Resumo em destaque: o que foi publicado, em números, sem veredito.
        Reveal(0, r.idConsulta) {
            LumeCard(color = if (same.isNotEmpty()) LumeMist else Sheet, border = if (same.isNotEmpty()) LumeLightGreen else Hairline) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline("Resumo da pesquisa", color = InkSoft)
                    if (outlets.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                        Text("${outlets.size}", style = MaterialTheme.typography.displayMedium, color = LumeDarkGreen, fontWeight = FontWeight.SemiBold)
                        Text(if (outlets.size == 1) "outro veículo relatou este mesmo acontecimento"
                            else "outros veículos relataram este mesmo acontecimento", style = MaterialTheme.typography.titleMedium, color = Ink)
                    } else Text(if (uncertain.isNotEmpty()) "Encontramos publicações parecidas, mas não deu para confirmar que tratam do mesmo acontecimento."
                        else "Não encontramos outros veículos relatando este mesmo acontecimento.",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    val publications = same.size + different.size
                    if (outlets.isNotEmpty() && outlets.size != publications)
                        Text("Em $publications publicações; algumas foram republicadas por mais de um veículo.",
                            style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    ProportionBar(listOf(same.size to SECTION_COLORS.getValue("Mesmo fato"), different.size to SECTION_COLORS.getValue("Divergente"),
                        uncertain.size to SECTION_COLORS.getValue("Incerta"), context.size to SECTION_COLORS.getValue("Contexto"),
                        older.size to SECTION_COLORS.getValue("Anteriores")))
                    val dated = (same + different).filter { it.data != null }.sortedBy { it.data }
                    if (dated.size >= 2) Text("Primeiro: ${dated.first().veiculo} (${Dates.format(dated.first().data!!)}) · " +
                        "mais recente: ${dated.last().veiculo} (${relativeDate(dated.last().data)})",
                        style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    if (detail != null && (same.isNotEmpty() || different.isNotEmpty())) {
                        val cites = same.count { it.detalheStatus == DetailStatus.CITA }
                        Text("Sobre o detalhe “$detail”", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                        val warnTone = if (different.isNotEmpty()) WARN else LumeDarkGreen
                        if (largeText()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Stat(cites, "citam o mesmo detalhe"); Stat(different.size, "trazem informação diferente", tone = warnTone)
                            Stat(same.size - cites, "não o mencionam no que foi lido", tone = InkSoft)
                        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat(cites, "citam o mesmo detalhe", Modifier.weight(1f))
                            Stat(different.size, "trazem informação diferente", Modifier.weight(1f), warnTone)
                            Stat(same.size - cites, "não o mencionam no que foi lido", Modifier.weight(1f), InkSoft)
                        }
                    }
                    if (uncertain.isNotEmpty() && (same.isNotEmpty() || different.isNotEmpty()))
                        Text(if (uncertain.size == 1) "Em 1 outra publicação, a relação ficou incerta."
                            else "Em ${uncertain.size} outras publicações, a relação ficou incerta.", style = MaterialTheme.typography.bodyMedium)
                    notIndependent.firstOrNull { it.relacaoTipo == Independence.PROPRIA }?.let {
                        Text("A própria matéria importada (${it.veiculo}) apareceu na busca e não foi contada como outra fonte.",
                            style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Text("Isso mostra o que foi publicado. Não confirma nem desmente a notícia.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint)
                }
            }
        }
        signal?.let { SignalPill(it) }
        // Quantidades comparáveis (ex.: "tem 5 títulos"): síntese própria por regras.
        if (r.interpretacao.quantity != null) r.sintese?.let { IndicationCard(it, observations = r.observacoes) }
        r.detalhesAdicionais.forEach { IndicationCard(it, "Outro detalhe da mesma frase") }
        r.avisos.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = WARN) }

        // Fontes por tipo de relação: um filtro por vez, em vez de várias seções recolhidas com o mesmo peso.
        val sections = listOf(
            Triple("Mesmo fato", same.sortedBy { when (it.detalheStatus) { DetailStatus.CITA -> 0; DetailStatus.NAO_CITA -> 1; else -> 2 } },
                "Outros veículos, com o mesmo envolvido, a mesma ação, o mesmo assunto e data compatível."),
            Triple("Divergente", different, "Mesmo acontecimento com outro número, outro prazo ou o sentido oposto (negação). Confira o trecho."),
            Triple("Incerta", uncertain.sortedBy { if (fromModel(it)) 0 else 1 }, "Parecidas, mas o que foi lido não basta para dizer se é o mesmo fato."),
            Triple("Contexto", context, "Outros fatos sobre o mesmo assunto: outra ação, outro envolvido ou outra etapa."),
            Triple("Anteriores", older, "Fatos parecidos em datas anteriores. Não mostram que o fato ocorreu agora."),
            Triple("Não independentes", notIndependent, "A própria matéria importada, republicações dela e textos do mesmo veículo."),
        ).filter { it.second.isNotEmpty() }
        if (sections.isNotEmpty()) {
            var selected by remember(r.idConsulta) { mutableStateOf(sections.first().first) }
            val current = sections.firstOrNull { it.first == selected } ?: sections.first()
            SectionHeader("Publicações encontradas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sections.forEach { (name, items, _) ->
                    FilterChip(selected = current.first == name, onClick = { selected = name },
                        label = { Text("$name · ${items.size}") }, modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(SECTION_COLORS[name] ?: InkFaint)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LumeLightGreen, selectedLabelColor = LumeDarkGreen))
                }
            }
            Text(current.third, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            val defaultLabel = when (current.first) { "Mesmo fato" -> "Mesmo acontecimento"; "Incerta" -> "Relação incerta"
                "Contexto" -> "Contexto relacionado"; "Anteriores" -> "Fato anterior"; "Divergente" -> "Detalhe divergente"; else -> null }
            key(current.first) { CardList(current.second, 5, "Ver mais", defaultLabel) }
        }
        ContextSection(r.contexto)
        signal?.let { TextSignalCard(it, independentOutlets = outlets.size, checks = factChecksIn(all)) }
        UnknownsSection(r, same, different, uncertain, hasSignal = signal != null)
        AboutSearch(r)
    }
}

/** "O que ainda não sabemos?": limites reais desta pesquisa. */
@Composable
private fun UnknownsSection(r: ResearchResult, same: List<ResultCard>, different: List<ResultCard>, uncertain: List<ResultCard>,
                            hasSignal: Boolean = false) {
    val detail = r.interpretacao.prazos.firstOrNull() ?: r.interpretacao.numeros.firstOrNull()?.texto
    val items = buildList {
        if (r.status == "insuficiente") add(r.mensagem)
        // Material citado como prova e veículo citado pelo texto: matérias sobre o fato não confirmam esses pontos.
        r.observacoes.filter { it.startsWith("A afirmação cita ") || it.startsWith("O texto atribui ") }.forEach { add(it) }
        if (uncertain.isNotEmpty()) add("${uncertain.size} publicação(ões) parecida(s) não puderam ser confirmadas como o mesmo fato com o que foi lido.")
        if (detail != null && same.none { it.detalheStatus == DetailStatus.CITA && it.baseLeitura == "pagina" })
            add("Nenhuma página lida confirmou o detalhe “$detail”.")
        val titleOnly = (same + different).count { it.baseLeitura != "pagina" }
        if (titleOnly > 0) add("$titleOnly publicação(ões) só puderam ser avaliadas pelo título ou pelo resumo do buscador; abra a fonte para conferir.")
        if (different.isNotEmpty()) add("Há fontes com informação diferente sobre o detalhe; o Lume não decide qual está certa.")
        r.fontesConsultadas.filter { it.status == "erro" }.map { it.nome }.distinct().takeIf { it.isNotEmpty() }
            ?.let { add("Não responderam nesta pesquisa: ${it.joinToString(", ")}.") }
        add(if (hasSignal) "O Lume não confirma nem desmente fatos. O sinal do texto mostra só o estilo da escrita, e as fontes acima mostram o que foi publicado."
            else "O Lume não avalia se a notícia é verdadeira. A classificação de veracidade está sendo desenvolvida separadamente.")
    }
    Collapsible("O que ainda não sabemos? (${items.size})", key = r.idConsulta, initiallyOpen = r.status == "insuficiente", strong = true) {
        LumeCard(color = MaterialTheme.colorScheme.surfaceContainerHigh, border = Color.Transparent) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(InkFaint))
                        Text(item, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                }
            }
        }
    }
}

/** Como a pesquisa foi feita: consultas, respostas de cada buscador e o que foi deixado de fora. */
@Composable
private fun AboutSearch(r: ResearchResult) {
    Collapsible("Como a pesquisa foi feita", key = r.idConsulta) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Overline("Buscas feitas")
            r.fontesConsultadas.forEach { s ->
                Text("• ${s.nome}: “${s.consulta}” — " + when (s.status) {
                    "ok" -> "${s.quantidade} resultado(s)"; "vazio" -> "nenhum resultado"; else -> "não respondeu"
                }, style = MaterialTheme.typography.bodySmall)
            }
            r.diagnostico?.let { t ->
                val received = t.itens.filter { it.etapa == "triagem" }.map { it.url }.distinct().size
                Text("Publicações recebidas: $received · repetidas: ${t.count("duplicata")} · deixadas de fora: ${r.descartados}",
                    style = MaterialTheme.typography.bodySmall)
                val dropped = t.itens.filter { it.etapa == "triagem" && it.resultado == "descartado" }.distinctBy { it.url }
                dropped.groupBy { plainReason(it.motivo) }
                    .entries.sortedByDescending { it.value.size }.take(4)
                    .forEach { (why, rows) -> Text("• ${rows.size}× $why", style = MaterialTheme.typography.bodySmall) }
                // Exemplos de "outro acontecimento" com o título, para a pessoa conferir o que ficou de fora.
                dropped.filter { it.motivo.startsWith("Outro acontecimento") }.take(3).forEach {
                    Text("   – ${it.veiculo}: “${it.titulo.take(90)}”", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Cada fonte foi comparada com a matéria importada: quem agiu, qual ação e em que etapa, sobre o quê, " +
                "quando, números e negação. Só foram usados trechos realmente lidos.", style = MaterialTheme.typography.bodySmall)
            Text(if (r.comparacaoSemanticaLocal) "A comparação de sentido (IA local neste computador) só ordenou as fontes e ajudou a ver " +
                "se o assunto era o mesmo quando quem agiu e a ação já coincidiam; ela não decide sozinha."
                else "Comparação de sentido por IA local não usada nesta pesquisa (indisponível); a relação foi decidida só pelos textos.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ResearchResults(e: Evaluation, onOption: (String) -> Unit) {
    var detailsOpen by remember(e) { mutableStateOf(false) }
    var relatedOpen by remember(e) { mutableStateOf(false) }
    val parts = e.partes
    Column(if(LocalLumeMotion.current) Modifier.animateContentSize() else Modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (parts.size <= 1) {
            parts.firstOrNull()?.let { ResultBlock(it, null, onOption, e.sinalTexto) }
        } else {
            Text("O que encontramos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            e.sinalTexto?.let { SignalPill(it) }
            val informative = parts.filter { it.sintese != null && it.sintese.situacao !in listOf("sem_comparacao", "insuficiente", "nao_verificavel") }
            if (informative.isEmpty()) {
                Text("Ainda não conseguimos esclarecer todos os detalhes desta notícia. Veja as publicações encontradas e o que elas relatam.")
            } else {
                informative.forEach { part ->
                    Text(part.trechoDaEntrada.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    part.sintese?.explicacao?.take(3)?.forEach { explanation ->
                        Text(explanation.texto)
                        explanation.fontes.forEach { ResearchLink(it.veiculo, it.url) }
                    }
                }
                if (informative.size < parts.size) Text("Outros detalhes continuam sem esclarecimento suficiente.")
            }
            Text(if (e.sinalTexto != null) "Pesquisa de fontes e contexto. O sinal do texto mede só o estilo da escrita; o Lume não confirma nem desmente fatos."
                else AVISO_INDICACAO, style = MaterialTheme.typography.bodySmall, color = InkFaint)
            parts.filter { it.status == "ambigua" }.flatMap { it.sugestoes }.distinctBy { it.texto }.takeIf { it.isNotEmpty() }
                ?.let { ClarificationChooser(it, onOption, null) }
            ContextSection(parts.flatMap { it.contexto }.distinctBy { it.texto }.take(2))
            val answers = parts.flatMap { it.resultados["responde"].orEmpty() }.distinctBy { it.url }
            if (answers.isNotEmpty()) {
                SectionHeader("Fontes que esclarecem os detalhes")
                answers.forEachIndexed { i, c -> Reveal(i, c.url) { SourceCard(c) } }
            }
            val related = parts.flatMap { it.resultados["direto"].orEmpty() + it.resultados["anterior"].orEmpty() }
                .distinctBy { it.url }.filterNot { candidate -> answers.any { it.url == candidate.url } }.take(8)
            if (related.isNotEmpty()) {
                ExpanderRow("${if(relatedOpen) "Ocultar" else "Ver"} outras publicações relacionadas (${related.size})", relatedOpen, { relatedOpen = !relatedOpen }, strong = true)
                LumeVisibility(relatedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mencionam o tema, mas não necessariamente respondem a todos os detalhes.", style = MaterialTheme.typography.bodySmall)
                        related.forEach { SourceCard(it) }
                    }
                }
            }
            e.sinalTexto?.let { sig ->
                val cards = parts.flatMap { it.resultados.values.flatten() }.distinctBy { it.url }
                TextSignalCard(sig, independentOutlets = cards.filter { it.relacaoTipo == RelationKind.MESMO_FATO }.map { it.veiculo }.distinct().size,
                    checks = factChecksIn(cards))
            }
            ExpanderRow(if(detailsOpen) "Ocultar detalhes da análise" else "Sobre esta análise", detailsOpen, { detailsOpen = !detailsOpen })
            LumeVisibility(detailsOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { part ->
                        Text(part.trechoDaEntrada.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text(part.sintese?.indicacao?.motivo ?: part.mensagem, style = MaterialTheme.typography.bodySmall)
                    }
                    parts.flatMap { it.observacoes }.distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Motivo de descarte em linguagem simples (sem contagens internas). */
private fun plainReason(r: String): String = when {
    r.startsWith("poucos detalhes") -> "poucas palavras em comum com a afirmação"
    r.startsWith("apenas o nome") -> "só o nome coincide; tratam de outro assunto"
    r.startsWith("só coincidem o nome e o número") -> "só o nome e o número coincidem; outro assunto"
    r.startsWith("enciclopédia") -> "verbete de enciclopédia sem o acontecimento"
    r.startsWith("página de desambiguação") -> "página de enciclopédia com vários significados"
    r.startsWith("Outro acontecimento: ") -> "outro acontecimento: " + r.removePrefix("Outro acontecimento: ").replaceFirstChar { it.lowercase() }
    else -> r.substringBefore(" (")
}


// ------------------------------------------------------------------------------------------------
// Sinal do texto

private val ZoneNews = Color(0xFFD5E3EE)
private val ZoneNeutral = Color(0xFFE7E9E0)
private val ZoneHoax = Color(0xFFF2DCB0)
private val InkBlue = Color(0xFF2C4F6B)
private val SoftBlue = Color(0xFFE4EDF5)

/**
 * Estilo da escrita numa escala de três zonas (notícia comum · sem sinal claro · estilo de boato), com o marcador na
 * posição do texto. Nunca mostra número nem "é falso": é uma pista de estilo, ao lado do que as fontes publicaram.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSignalCard(signal: TextSignal, independentOutlets: Int, checks: List<FactCheckSource>) {
    val title = when (signal.faixa) {
        SignalBand.DESINFORMACAO -> "Pelo estilo, o texto tende a lembrar desinformação"
        SignalBand.NOTICIA -> "Pelo estilo, o texto tende a ser de notícia comum"
        else -> "O estilo do texto não aponta para nenhum lado"
    }
    val accent = when (signal.faixa) { SignalBand.DESINFORMACAO -> Amber; SignalBand.NOTICIA -> InkBlue; else -> InkSoft }
    // Palavras só quando o texto cai numa faixa: num texto sem sinal claro elas pesam pouco e só gerariam alarme à toa.
    val words = when (signal.faixa) {
        SignalBand.NOTICIA -> signal.pesamNoticia to "Palavras que lembram notícia comum"
        SignalBand.DESINFORMACAO -> signal.pesamBoato to "Palavras que lembram boato ou checagem"
        else -> emptyList<com.example.lumeocrtest.research.SignalTerm>() to ""
    }
    LumeCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Overline("Sinal do texto", color = InkSoft)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            StyleScale(signal, accent)
            Text("Isto mede só o jeito de escrever: o vocabulário e o estilo comuns em boatos e textos de checagem, ou em notícias. " +
                "Não confirma nem desmente os fatos.", style = MaterialTheme.typography.bodySmall, color = InkSoft)

            // Cruzamento com as fontes: o estilo pode vir de rede social ou de um boato citado, e o fato ter vários relatos.
            if (signal.faixa == SignalBand.DESINFORMACAO && independentOutlets >= 3) {
                Surface(shape = RoundedCornerShape(12.dp), color = LumeMist) {
                    Text("$independentOutlets veículos independentes relataram este mesmo acontecimento. O estilo pode vir de um texto de " +
                        "rede social ou de um boato citado: veja o que as fontes dizem.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = LumeDarkGreen)
                }
            }
            if (checks.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (checks.size == 1) "Já checado por uma agência" else "Já checado por agências", style = MaterialTheme.typography.titleSmall,
                    color = LumeDarkGreen)
                checks.forEach { c -> ResearchLink("${c.agencia}: ${c.titulo.take(80)}", c.url, small = true) }
            }
            if (words.first.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(words.second, style = MaterialTheme.typography.labelMedium, color = InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    words.first.take(5).forEach { t ->
                        Surface(shape = RoundedCornerShape(50), color = if (signal.faixa == SignalBand.NOTICIA) SoftBlue else AmberSoft) {
                            Text(t.termo, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                                color = if (signal.faixa == SignalBand.NOTICIA) InkBlue else Amber)
                        }
                    }
                }
            }
            Collapsible("Como ler este sinal", key = signal.posicao) {
                Text("O modelo aprendeu com cerca de 12 mil textos do FakeRecogna (boatos e checagens de um lado, notícias do outro). " +
                    "Ele reconhece estilo e vocabulário, por isso pode errar com notícias em tom de rede social e com boatos bem escritos. " +
                    "Em um teste com 81 notícias reais do dia, 2 foram marcadas como estilo de boato; entre textos de boato conhecidos, " +
                    "cerca de 7 em cada 10. Use como pista, ao lado das fontes, nunca como resposta.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
}

@Composable
private fun StyleScale(signal: TextSignal, accent: Color) {
    val motion = LocalLumeMotion.current
    var shown by remember(signal) { mutableStateOf(!motion) }
    LaunchedEffect(signal) { shown = true }
    val pos by animateFloatAsState(if (shown) signal.posicao else 0.5f, tween(if (motion) 750 else 0, easing = FastOutSlowInEasing), label = "estilo")
    val label = when (signal.faixa) {
        SignalBand.DESINFORMACAO -> "estilo de boato"; SignalBand.NOTICIA -> "estilo de notícia comum"; else -> "sem sinal claro"
    }
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Escala de estilo do texto: $label" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp)) {
            Row(Modifier.align(Alignment.Center).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.weight(signal.limiteNoticia.coerceAtLeast(0.08f)).fillMaxHeight().background(ZoneNews))
                Box(Modifier.weight((signal.limiteBoato - signal.limiteNoticia).coerceAtLeast(0.08f)).fillMaxHeight().background(ZoneNeutral))
                Box(Modifier.weight((1f - signal.limiteBoato).coerceAtLeast(0.08f)).fillMaxHeight().background(ZoneHoax))
            }
            val size = 22.dp
            val x = (maxWidth * pos - size / 2).coerceIn(0.dp, maxWidth - size)
            Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(size).shadow(3.dp, CircleShape).clip(CircleShape)
                .background(Color.White).border(BorderStroke(4.dp, accent), CircleShape))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Estilo de notícia", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Text("Estilo de boato", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}

/** Resumo de uma linha do sinal do texto, logo abaixo do resumo da pesquisa (o cartão completo fica depois das fontes). */
@Composable
private fun SignalPill(signal: TextSignal) {
    val (text, accent) = when (signal.faixa) {
        SignalBand.DESINFORMACAO -> "Sinal do texto: o estilo tende a lembrar desinformação" to Amber
        SignalBand.NOTICIA -> "Sinal do texto: o estilo tende a ser de notícia comum" to InkBlue
        else -> "Sinal do texto: sem sinal claro de estilo" to InkSoft
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Sheet, border = BorderStroke(1.dp, Hairline)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
            Text("veja abaixo", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}
