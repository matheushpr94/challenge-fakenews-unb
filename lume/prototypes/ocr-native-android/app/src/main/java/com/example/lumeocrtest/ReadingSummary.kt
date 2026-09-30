package com.example.lumeocrtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lumeocrtest.ocr.ArticleReading
import com.example.lumeocrtest.ocr.BlockRole
import com.example.lumeocrtest.ocr.MetadataOrigin
import com.example.lumeocrtest.research.Evaluation
import com.example.lumeocrtest.research.RelationKind
import com.example.lumeocrtest.research.jaccard
import com.example.lumeocrtest.research.titleKey
import com.example.lumeocrtest.ui.theme.LumeDarkGreen

/** Uma linha "rótulo: valor" com a origem da informação logo abaixo, em letra menor. */
@Composable
private fun Field(label: String, value: String, origin: String? = null, missing: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text("$label ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
        origin?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * "O que conseguimos ler nesta imagem?": cada campo diz de onde veio (linha da imagem, link consultado) ou
 * que não foi identificado com segurança. Nada é completado por suposição.
 */
@Composable
fun ReadingSummary(r: ArticleReading, evaluation: Evaluation?) {
    val m = r.metadata
    // Informação sugerida pela página consultada: a matéria encontrada com o mesmo título.
    val original = evaluation?.partes?.firstOrNull()?.resultados?.values?.flatten()?.firstOrNull { c ->
        c.relacaoTipo == RelationKind.MESMO_FATO && r.title != null && jaccard(titleKey(c.titulo), titleKey(r.title)) >= 0.8
    }
    Text("O que conseguimos ler nesta imagem?", style = MaterialTheme.typography.titleMedium, color = LumeDarkGreen,
        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                r.title != null -> Field("Título:", r.title)
                r.partialTitle != null -> Field("Título:", "aparece cortado na imagem (“…${r.partialTitle}”)", missing = true)
                else -> Field("Título:", "não identificado na imagem", missing = true)
            }
            r.subtitle?.let { Field("Subtítulo:", it) }
            // Veículo
            when {
                m.source != null && m.sourceOrigin == MetadataOrigin.HEADER_CANDIDATE ->
                    Field("Veículo:", "possivelmente ${m.source}", "Nome no cabeçalho da página; a matéria não traz assinatura com o veículo.")
                m.source != null && m.sourceOrigin == MetadataOrigin.DOMAIN -> Field("Veículo:", m.source, "Endereço visível na imagem.")
                m.source != null -> Field("Veículo:", m.source, m.sourceEvidence?.let { "Lido na imagem: “$it”" })
                original != null -> Field("Veículo:", "não aparece na imagem", "Sugerido pela publicação encontrada com o mesmo título: ${original.veiculo}.", missing = true)
                else -> Field("Veículo:", "não identificado na imagem", missing = true)
            }
            // Autoria: pessoa, assinatura institucional ou ausência
            when {
                m.author != null -> Field("Autoria:", m.author, m.authorEvidence?.let { "Lido na imagem: $it" })
                m.institutionalByline != null && m.institutionalByline == m.source ->
                    Field("Autoria:", "publicado por ${m.institutionalByline}", "Assinatura institucional; não há autor individual na imagem.")
                m.institutionalByline != null -> Field("Autoria:", "assinatura institucional: ${m.institutionalByline}", "Não há autor individual na imagem.")
                original?.autor != null -> Field("Autoria:", "não aparece na imagem", "Segundo a página consultada (${original.veiculo}): ${original.autor}.", missing = true)
                else -> Field("Autoria:", "autor não identificado na imagem", missing = true)
            }
            if (m.publishedAt != null) Field("Publicado em:", m.publishedAt + (m.updated?.let { " · atualizado $it" } ?: ""),
                m.dateEvidence?.let { "Lido na imagem: “$it”" })
            else Field("Publicado em:", "data não identificada na imagem", missing = true)
            m.place?.let { Field("Local:", it) }
            if (m.imageCredits.isNotEmpty()) Field("Crédito da imagem:", m.imageCredits.joinToString("; "),
                "Crédito de foto ou arte, não é a autoria da matéria.")
        }
    }
    // O que ficou de fora do texto principal, com o motivo (sem termos técnicos).
    val ignored = r.decisions.filter { it.role in setOf(BlockRole.LATERAL, BlockRole.LEGENDA, BlockRole.CREDITO_IMAGEM,
        BlockRole.TEXTO_NA_IMAGEM, BlockRole.CABECALHO, BlockRole.INTERFACE, BlockRole.RECOMENDACAO, BlockRole.ANUNCIO) }
    if (ignored.isNotEmpty()) {
        var open by remember(r) { mutableStateOf(false) }
        TextButton(onClick = { open = !open }) {
            Text(if (open) "Esconder o que não entrou na matéria" else "O que não entrou na matéria (${ignored.size})")
        }
        LumeVisibility(open) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ignored.groupBy { it.role }.forEach { (role, items) ->
                        Text(role.label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                        items.take(4).forEach { d ->
                            Row { Text("• ", style = MaterialTheme.typography.bodySmall); Text("“${d.text.take(90)}”", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

