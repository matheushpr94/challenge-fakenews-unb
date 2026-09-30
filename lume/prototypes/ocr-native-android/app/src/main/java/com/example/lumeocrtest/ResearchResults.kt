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
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen

private val WARN = Color(0xFFB26A00)
private val NEUTRAL = Color(0xFF6B7B72)

private fun indicationColor(rotulo: String?) = when (rotulo) {
    "dados_compativeis", "resposta" -> LumeDarkGreen
    "dados_diferentes" -> WARN
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
private fun IndicationCard(s: Synthesis, title: String = "O que as fontes mostram", observations: List<String> = emptyList()) {
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
private fun RelationChip(c: ResultCard) {
    val (text, color) = when {
        c.relacaoTipo == Independence.PROPRIA -> "A própria matéria" to NEUTRAL
        c.relacaoTipo == Independence.MESMO_VEICULO -> "Mesmo veículo" to NEUTRAL
        c.relacaoTipo == Independence.REPUBLICACAO -> "Republicação" to NEUTRAL
        c.relacaoTipo == RelationKind.DIFERENTE -> "Detalhe divergente" to WARN
        c.relacaoTipo == RelationKind.INDEFINIDO -> "Relação incerta" to WARN
        c.detalheStatus == DetailStatus.CITA -> "Cita o mesmo detalhe" to LumeDarkGreen
        c.relacaoTipo == RelationKind.MESMO_FATO -> "Mesmo acontecimento" to LumeDarkGreen
        c.relacaoTipo == RelationKind.ANTERIOR -> "Fato anterior" to NEUTRAL
        c.relacaoTipo == RelationKind.OUTRO -> "Outro acontecimento" to NEUTRAL
        c.relacao == "responde" -> "Informa o detalhe pesquisado" to LumeDarkGreen
        else -> "Contexto relacionado" to NEUTRAL
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun readLabel(base: String) = when (base) {
    "pagina" -> "Lemos a página"
    "trecho" -> "Lemos só o resumo do buscador"
    else -> "Lemos só o título"
}

@Composable
private fun SourceCard(c: ResultCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.veiculo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = LumeDarkGreen, modifier = Modifier.weight(1f))
                RelationChip(c)
            }
            Text(c.titulo, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(c.data?.let { Dates.format(it) }, c.autor?.takeIf { norm(it) != norm(c.veiculo) }?.let { "por $it (segundo a página)" }, readLabel(c.baseLeitura))
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.resumoRelacao.isNotBlank()) Text(c.resumoRelacao, style = MaterialTheme.typography.bodyMedium)
            val reasons = c.motivos.orEmpty()
            val compared = c.trechosComparacao.orEmpty()
            if (reasons.isNotEmpty()) ComparisonDetails(reasons, compared)
            val quote = c.trechoRelacao ?: c.trechosPagina.firstOrNull()
            if (compared.isNotEmpty()) {
                // Os trechos já aparecem na comparação acima.
            } else if (quote != null) {
                Text(if (c.baseLeitura == "pagina") "Trecho da página:" else "Trecho do resumo do buscador:",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“$quote”", style = MaterialTheme.typography.bodyMedium)
            } else if (c.trecho != null && c.relacaoTipo != RelationKind.MESMO_FATO) {
                Text(if (c.conteudo == "resumo") "Resumo da Wikipédia:" else "Resumo do buscador:",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“${c.trecho.take(300)}”", style = MaterialTheme.typography.bodyMedium)
            }
            c.alertas.filterNot { it.startsWith("Lemos apenas") }.forEach {
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = WARN)
            }
            if (c.republicacoes.isNotEmpty()) {
                Text("Mesmo texto também publicado por:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.republicacoes.take(3).forEach { ResearchLink(it.veiculo, it.url, small = true) }
                }
            }
            ResearchLink("Abrir fonte original", c.url)
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
    var open by remember(reasons) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
        Text(if (open) "Ocultar a comparação" else "Por que esta relação?", style = MaterialTheme.typography.labelLarge)
    }
    LumeVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            compared.forEach { e ->
                Text("${whereLabel(e.onde)}:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“${e.texto}”", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    // O trecho mais forte da fonte fica sempre visível.
    compared.lastOrNull { it.onde != "captura" && it.onde != "titulo" }?.let { e ->
        if (!open) {
            Text("${whereLabel(e.onde)}:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("“${e.texto}”", style = MaterialTheme.typography.bodyMedium)
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
private fun SectionTitle(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = LumeDarkGreen, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Lista com os primeiros itens visíveis e o restante atrás de "Ver mais". */
@Composable
private fun CardList(items: List<ResultCard>, visible: Int, moreLabel: String) {
    var expanded by remember(items) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(visible).forEach { SourceCard(it) }
        val rest = items.drop(visible)
        if (rest.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "$moreLabel (${rest.size})") }
            LumeVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rest.forEach { SourceCard(it) } } }
        }
    }
}

@Composable
private fun ResultBlock(r: ResearchResult, title: String?, onOption: (String) -> Unit) {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        if (r.status == "ambigua" && r.sugestoes.isNotEmpty()) ClarificationChooser(r.sugestoes, onOption, null)

        // Resumo: contagens do que foi publicado, sem veredito.
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(if (same.isNotEmpty()) LumeDarkGreen else NEUTRAL))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resumo da pesquisa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when {
                        same.isEmpty() && different.isEmpty() && uncertain.isNotEmpty() ->
                            "Encontramos publicações parecidas, mas não deu para confirmar que tratam do mesmo acontecimento."
                        same.isEmpty() && different.isEmpty() -> "Não encontramos outros veículos relatando este mesmo acontecimento."
                        else -> "${outlets.size} outro(s) veículo(s) relataram este mesmo acontecimento."
                    }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uncertain.isNotEmpty() && (same.isNotEmpty() || different.isNotEmpty()))
                        Text("Em ${uncertain.size} outra(s), a relação ficou incerta.", style = MaterialTheme.typography.bodyMedium)
                    notIndependent.firstOrNull { it.relacaoTipo == Independence.PROPRIA }?.let {
                        Text("A própria matéria importada (${it.veiculo}) apareceu na busca e não foi contada como outra fonte.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (detail != null && (same.isNotEmpty() || different.isNotEmpty())) {
                        val cites = same.count { it.detalheStatus == DetailStatus.CITA }
                        Text("Sobre o detalhe “$detail”: $cites citam o mesmo detalhe; ${different.size} trazem informação diferente; " +
                            "${same.size - cites} não o mencionam no que foi lido.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Isso mostra o que foi publicado. Não confirma nem desmente a notícia.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Quantidades comparáveis (ex.: "tem 5 títulos"): síntese própria por regras.
        if (r.interpretacao.quantity != null) r.sintese?.let { IndicationCard(it, observations = r.observacoes) }
        r.detalhesAdicionais.forEach { IndicationCard(it, "Outro detalhe da mesma frase") }
        r.avisos.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = WARN) }

        if (same.isNotEmpty()) {
            SectionTitle("Quais publicações falam deste mesmo acontecimento?",
                "Outros veículos, com o mesmo envolvido, a mesma ação, o mesmo assunto e data compatível.")
            val ordered = same.sortedBy { when (it.detalheStatus) { DetailStatus.CITA -> 0; DetailStatus.NAO_CITA -> 1; else -> 2 } }
            CardList(ordered, 4, "Ver mais publicações do mesmo acontecimento")
        }
        if (different.isNotEmpty()) {
            SectionTitle("Mesmo acontecimento, com detalhe divergente", "Confira o trecho: outro número, outro prazo ou o sentido oposto (negação).")
            CardList(different, 3, "Ver mais")
        }
        if (uncertain.isNotEmpty()) {
            SectionTitle("Relação incerta", "Parecidas, mas o que foi lido não basta para dizer se é o mesmo fato.")
            CardList(uncertain, 2, "Ver mais publicações com relação incerta")
        }
        if (context.isNotEmpty()) {
            SectionTitle("Contexto relacionado", "Outros fatos sobre o mesmo assunto: outra ação, outro envolvido ou outra etapa.")
            CardList(context, 2, "Ver mais publicações de contexto")
        }
        if (older.isNotEmpty()) {
            SectionTitle("Fatos parecidos em datas anteriores", "Não mostram que o fato ocorreu agora.")
            CardList(older, 2, "Ver mais publicações anteriores")
        }
        if (notIndependent.isNotEmpty()) {
            SectionTitle("Não contam como fontes independentes", "A própria matéria importada, republicações dela e textos do mesmo veículo.")
            CardList(notIndependent, 1, "Ver as demais")
        }
        ContextSection(r.contexto)
        UnknownsSection(r, same, different, uncertain)
        AboutSearch(r)
    }
}

/** "O que ainda não sabemos?": limites reais desta pesquisa. */
@Composable
private fun UnknownsSection(r: ResearchResult, same: List<ResultCard>, different: List<ResultCard>, uncertain: List<ResultCard>) {
    val detail = r.interpretacao.prazos.firstOrNull() ?: r.interpretacao.numeros.firstOrNull()?.texto
    val items = buildList {
        if (r.status == "insuficiente") add(r.mensagem)
        if (uncertain.isNotEmpty()) add("${uncertain.size} publicação(ões) parecida(s) não puderam ser confirmadas como o mesmo fato com o que foi lido.")
        if (detail != null && same.none { it.detalheStatus == DetailStatus.CITA && it.baseLeitura == "pagina" })
            add("Nenhuma página lida confirmou o detalhe “$detail”.")
        val titleOnly = (same + different).count { it.baseLeitura != "pagina" }
        if (titleOnly > 0) add("$titleOnly publicação(ões) só puderam ser avaliadas pelo título ou pelo resumo do buscador; abra a fonte para conferir.")
        if (different.isNotEmpty()) add("Há fontes com informação diferente sobre o detalhe; o Lume não decide qual está certa.")
        r.fontesConsultadas.filter { it.status == "erro" }.map { it.nome }.distinct().takeIf { it.isNotEmpty() }
            ?.let { add("Não responderam nesta pesquisa: ${it.joinToString(", ")}.") }
        add("O Lume não avalia se a notícia é verdadeira. A classificação de veracidade está sendo desenvolvida separadamente.")
    }
    SectionTitle("O que ainda não sabemos?")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Como a pesquisa foi feita: consultas, respostas de cada buscador e o que foi deixado de fora. */
@Composable
private fun AboutSearch(r: ResearchResult) {
    var open by remember(r) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar como a pesquisa foi feita" else "Como a pesquisa foi feita") }
    LumeVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Buscas feitas:", style = MaterialTheme.typography.labelLarge)
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
