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
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen

private val WARN = Color(0xFFB26A00)
private val FALSE_RED = Color(0xFFC62828)
private val NEUTRAL = Color(0xFF6B7B72)

private fun indicationColor(rotulo: String?) = when (rotulo) {
    "tende_verdadeira", "resposta" -> LumeDarkGreen
    "tende_falsa" -> FALSE_RED
    "falha_tecnica" -> WARN
    else -> NEUTRAL
}

@Composable
private fun ResearchLink(title: String, url: String, small: Boolean = false) {
    val context = LocalContext.current
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri?.scheme in listOf("https", "http") && !uri?.host.isNullOrBlank()) {
        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } },
            contentPadding = if (small) PaddingValues(horizontal = 4.dp, vertical = 0.dp) else ButtonDefaults.TextButtonContentPadding,
            modifier = if (small) Modifier.heightIn(min = 48.dp) else Modifier) {
            Text(title.ifBlank { url }, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
        }
    }
}

/** "O que você quer saber?" com opções tocáveis que iniciam a pesquisa. */
@Composable
fun ClarificationChooser(options: List<Option>, onOption: (String) -> Unit, onNotThis: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(QUESTION, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            options.forEach { o ->
                Surface(onClick = { onOption(o.texto) }, shape = RoundedCornerShape(12.dp), color = LumeLightGreen,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(o.texto, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold, color = Color(0xFF12301F))
                }
            }
            if (onNotThis != null) OutlinedButton(onClick = onNotThis, modifier = Modifier.fillMaxWidth()) { Text(NOT_THIS) }
        }
    }
}

@Composable
private fun IndicationCard(s: Synthesis, title: String = "Indicação provisória", observations: List<String> = emptyList()) {
    val ind = s.indicacao
    val color = indicationColor(ind?.rotulo)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ind?.texto ?: s.frase, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                ind?.motivo?.let {
                    Text(if(s.situacao=="sem_comparacao") "As fontes encontradas ainda não permitem esclarecer este detalhe." else it,
                        style = MaterialTheme.typography.bodyMedium)
                }
                s.detalhe?.let { Text("Detalhe comparado: $it", style = MaterialTheme.typography.bodySmall) }
                if (s.explicacao.isNotEmpty()) {
                    var showAll by remember(s) { mutableStateOf(false) }
                    Text("O que encontramos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    val visible = if (showAll) s.explicacao else s.explicacao.take(4)
                    visible.forEach { e ->
                        Text("• " + e.texto, style = MaterialTheme.typography.bodyMedium)
                        e.fontes.take(2).forEach { ResearchLink("Abrir: ${it.veiculo}", it.url, small = true) }
                    }
                    if (s.explicacao.size > 4) TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Ver menos" else "Ver mais (${s.explicacao.size - 4})")
                    }
                }
                observations.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall) }
                Text(ind?.aviso ?: AVISO_INDICACAO, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceCard(c: ResultCard) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.titulo, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(c.veiculo, c.data?.let { Dates.format(it) }, c.autor?.let { "Autor: $it" }).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall)
            Text("${c.relacaoTexto} · ${c.conteudoTexto}", style = MaterialTheme.typography.labelSmall, color = LumeDarkGreen)
            if (c.trechosPagina.isNotEmpty()) {
                Text("Trechos da página:", style = MaterialTheme.typography.labelSmall)
                c.trechosPagina.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
            } else if (c.trecho != null) {
                Text(if (c.conteudo == "resumo") "Resumo da Wikipédia:" else "Trecho do buscador:", style = MaterialTheme.typography.labelSmall)
                Text("“${c.trecho}”", style = MaterialTheme.typography.bodyMedium)
            }
            if (c.conteudo == "titulo") Text("O Lume não leu o conteúdo desta página; abra o link para conferir.", style = MaterialTheme.typography.bodySmall)
            c.alertas.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = WARN) }
            if (c.republicacoes.isNotEmpty()) {
                Text("Mesmo conteúdo em mais ${c.republicacoes.size} veículo(s): ${c.republicacoes.joinToString(", ") { it.veiculo }}. " +
                    "Republicações não são confirmações independentes.", style = MaterialTheme.typography.bodySmall)
            }
            c.observacaoLink?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            ResearchLink("Abrir fonte", c.url)
        }
    }
}

@Composable
private fun ContextSection(items: List<ContextItem>) {
    if (items.isEmpty()) return
    Text("Contexto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    items.forEach { c ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("“${c.texto}”", style = MaterialTheme.typography.bodyMedium)
                Text("${c.motivo.replaceFirstChar { it.uppercase() }} — ${c.veiculo}", style = MaterialTheme.typography.bodySmall)
                ResearchLink(c.titulo.ifBlank { c.veiculo }, c.url)
            }
        }
    }
}

@Composable
private fun ResultBlock(r: ResearchResult, title: String?, onOption: (String) -> Unit) {
    var expanded by remember(r) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        if (r.status == "ambigua" && r.sugestoes.isNotEmpty()) ClarificationChooser(r.sugestoes, onOption, null)
        r.sintese?.let { IndicationCard(it, observations = r.observacoes) }
        r.detalhesAdicionais.forEach { IndicationCard(it, "Indicação provisória — outro detalhe da mesma frase") }
        r.avisos.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = WARN) }
        ContextSection(r.contexto)
        val answers = r.resultados["responde"].orEmpty()
        if (answers.isNotEmpty()) {
            Text("Fontes que esclarecem o detalhe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            answers.forEach { SourceCard(it) }
        }
        val related = (r.resultados["direto"].orEmpty() + r.resultados["anterior"].orEmpty()).distinctBy { it.url }.take(6)
        if (related.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text("${if (expanded) "Ocultar" else "Ver"} outras publicações relacionadas (${related.size})")
            }
            LumeVisibility(expanded) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Mencionam o tema, mas não necessariamente respondem ao detalhe.", style = MaterialTheme.typography.bodySmall)
                    related.forEach { SourceCard(it) }
                }
            }
        }
        if (answers.isEmpty() && related.isEmpty() && r.status == "insuficiente") Text(r.mensagem, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ResearchResults(e: Evaluation, onOption: (String) -> Unit) {
    var detailsOpen by remember(e) { mutableStateOf(false) }
    var relatedOpen by remember(e) { mutableStateOf(false) }
    val parts = e.partes
    Column(if(LocalLumeMotion.current) Modifier.animateContentSize() else Modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (parts.size <= 1) {
            parts.firstOrNull()?.let { ResultBlock(it, null, onOption) }
        } else {
            Text("O que encontramos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            Text(AVISO_INDICACAO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            parts.filter { it.status == "ambigua" }.flatMap { it.sugestoes }.distinctBy { it.texto }.takeIf { it.isNotEmpty() }
                ?.let { ClarificationChooser(it, onOption, null) }
            ContextSection(parts.flatMap { it.contexto }.distinctBy { it.texto }.take(2))
            val answers = parts.flatMap { it.resultados["responde"].orEmpty() }.distinctBy { it.url }
            if (answers.isNotEmpty()) {
                Text("Fontes que esclarecem os detalhes", style = MaterialTheme.typography.titleMedium)
                answers.forEach { SourceCard(it) }
            }
            val related = parts.flatMap { it.resultados["direto"].orEmpty() + it.resultados["anterior"].orEmpty() }
                .distinctBy { it.url }.filterNot { candidate -> answers.any { it.url == candidate.url } }.take(8)
            if (related.isNotEmpty()) {
                TextButton(onClick = { relatedOpen = !relatedOpen }) { Text("${if(relatedOpen) "Ocultar" else "Ver"} outras publicações relacionadas (${related.size})") }
                LumeVisibility(relatedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mencionam o tema, mas não necessariamente respondem a todos os detalhes.", style = MaterialTheme.typography.bodySmall)
                        related.forEach { SourceCard(it) }
                    }
                }
            }
            TextButton(onClick = { detailsOpen = !detailsOpen }) { Text(if(detailsOpen) "Ocultar detalhes da análise" else "Sobre esta análise") }
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
