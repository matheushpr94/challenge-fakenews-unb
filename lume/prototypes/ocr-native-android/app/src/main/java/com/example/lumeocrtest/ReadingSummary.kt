package com.example.lumeocrtest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lumeocrtest.ocr.ArticleReading
import com.example.lumeocrtest.ocr.BlockRole
import com.example.lumeocrtest.ocr.MetadataOrigin
import com.example.lumeocrtest.research.Evaluation
import com.example.lumeocrtest.research.RelationKind
import com.example.lumeocrtest.research.jaccard
import com.example.lumeocrtest.research.titleKey
import com.example.lumeocrtest.ui.theme.Hairline
import com.example.lumeocrtest.ui.theme.InkFaint
import com.example.lumeocrtest.ui.theme.InkSoft

private class ReadField(val label: String, val value: String, val origin: String? = null)

/** Linha "rótulo | valor" com colunas alinhadas; a origem da informação vem logo abaixo, menor. */
@Composable
private fun FieldRow(f: ReadField) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(f.label, Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium, color = InkFaint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.value, style = MaterialTheme.typography.bodyMedium)
            f.origin?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkFaint) }
        }
    }
}

/** Prévia da captura: recorte do topo (onde fica a manchete), bem enquadrado; toque para ver inteira. */
@Composable
private fun CaptureThumb(preview: ImageBitmap, onOpen: () -> Unit) {
    Image(bitmap = preview, contentDescription = "Captura enviada. Toque para ampliar.",
        contentScale = ContentScale.Crop, alignment = Alignment.TopStart, // começo da manchete sempre visível
        modifier = Modifier.width(96.dp).height(128.dp).clip(MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, Hairline), MaterialTheme.shapes.small)
            .clickable(role = Role.Image, onClickLabel = "Ampliar a captura", onClick = onOpen))
}

@Composable
private fun CaptureDialog(preview: ImageBitmap, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Captura enviada", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text("Fechar") }
                }
                Image(bitmap = preview, contentDescription = "Captura enviada, em tamanho maior", contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
            }
        }
    }
}

/**
 * A captura e o que foi lido nela. Primeiro a prévia com manchete, veículo e data; depois os demais dados,
 * alinhados em duas colunas. Cada dado diz de onde veio; o que não aparece na imagem fica numa linha só.
 * Nada é completado por suposição.
 */
@Composable
fun ReadingSummary(r: ArticleReading, evaluation: Evaluation?, preview: ImageBitmap? = null, recognizedText: String = "") {
    val m = r.metadata
    // Informação sugerida pela página consultada: a matéria encontrada com o mesmo título.
    val original = evaluation?.partes?.firstOrNull()?.resultados?.values?.flatten()?.firstOrNull { c ->
        c.relacaoTipo == RelationKind.MESMO_FATO && r.title != null && jaccard(titleKey(c.titulo), titleKey(r.title)) >= 0.8
    }
    val fields = mutableListOf<ReadField>()
    val missing = mutableListOf<String>()
    // Post de rede social: quem publicou é o perfil; não há veículo nem assinatura de matéria a procurar.
    val post = r.socialPost && r.title == null
    if (post) r.postProfile?.let { fields += ReadField("Perfil", it, "Nome do perfil que aparece acima do texto do post.") }
    // Veículo
    if (!post) when {
        m.source != null && m.sourceOrigin == MetadataOrigin.HEADER_CANDIDATE ->
            fields += ReadField("Veículo", "possivelmente ${m.source}", "Nome no cabeçalho da página; a matéria não traz assinatura com o veículo.")
        m.source != null && m.sourceOrigin == MetadataOrigin.DOMAIN -> fields += ReadField("Veículo", m.source, "Endereço visível na imagem.")
        m.source != null -> fields += ReadField("Veículo", m.source, m.sourceEvidence?.let { "Lido na imagem: “$it”" })
        original != null -> fields += ReadField("Veículo", "não aparece na imagem", "Sugerido pela publicação encontrada com o mesmo título: ${original.veiculo}.")
        else -> missing += "veículo"
    }
    // Autoria: pessoa, assinatura institucional ou ausência
    if (!post) when {
        m.author != null -> fields += ReadField("Autoria", m.author, m.authorEvidence?.let { "Lido na imagem: $it" })
        m.institutionalByline != null && m.institutionalByline == m.source ->
            fields += ReadField("Autoria", "publicado por ${m.institutionalByline}", "Assinatura institucional; não há autor individual na imagem.")
        m.institutionalByline != null -> fields += ReadField("Autoria", "assinatura institucional: ${m.institutionalByline}", "Não há autor individual na imagem.")
        original?.autor != null -> fields += ReadField("Autoria", "não aparece na imagem", "Segundo a página consultada (${original.veiculo}): ${original.autor}.")
        else -> missing += "autor"
    }
    if (m.publishedAt != null) fields += ReadField("Publicado em", m.publishedAt + (m.updated?.let { " · atualizado $it" } ?: ""),
        // Mesma linha já citada no veículo: não repete.
        m.dateEvidence?.takeIf { it != m.sourceEvidence || m.source == null }?.let { "Lido na imagem: “$it”" })
    else missing += "data"
    m.place?.let { fields += ReadField("Local", it) }
    // Num post, os rótulos do aplicativo acima do texto não são crédito de foto.
    if (m.imageCredits.isNotEmpty() && !post) fields += ReadField("Crédito da imagem", m.imageCredits.joinToString("; "),
        "Crédito de foto ou arte, não é a autoria da matéria.")

    var enlarged by remember(preview) { mutableStateOf(false) }
    if (enlarged && preview != null) CaptureDialog(preview) { enlarged = false }

    LumeCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                preview?.let { CaptureThumb(it) { enlarged = true } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Overline(if (post) "Post lido na imagem" else "Lido na imagem", modifier = Modifier.semantics { heading() })
                    when {
                        // Post não tem título: mostra o começo do texto lido.
                        post && r.body.isNotBlank() -> Text(r.body.substringBefore("\n\n").replace("\n", " "),
                            style = MaterialTheme.typography.bodyLarge, maxLines = 7, overflow = TextOverflow.Ellipsis)
                        r.title != null -> Text(r.title, style = MaterialTheme.typography.titleLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        r.partialTitle != null -> Text("Título cortado na imagem: “…${r.partialTitle}”", style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                        else -> Text("Título não identificado na imagem", style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                    }
                }
            }
            r.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = InkSoft) }
            if (fields.isNotEmpty()) {
                Column {
                    fields.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = Hairline)
                        FieldRow(f)
                    }
                }
            }
            if (missing.isNotEmpty()) Text("Não identificado na imagem: ${missing.joinToString(", ")}.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
    }

    // Detalhes secundários: a um toque, fora do caminho.
    val ignored = r.decisions.filter { it.role in setOf(BlockRole.LATERAL, BlockRole.LEGENDA, BlockRole.CREDITO_IMAGEM,
        BlockRole.TEXTO_NA_IMAGEM, BlockRole.CABECALHO, BlockRole.INTERFACE, BlockRole.RECOMENDACAO, BlockRole.ANUNCIO) }
    if (r.body.isNotBlank() || recognizedText.isNotBlank() || ignored.isNotEmpty()) {
        Collapsible("Ver o texto lido na imagem", key = r) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r.body.isNotBlank()) Collapsible("Texto da matéria", key = r.body, initiallyOpen = true) {
                    LumeCard { SelectionContainer { Text(r.body, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) } }
                }
                if (ignored.isNotEmpty()) Collapsible("O que não entrou na matéria (${ignored.size})", key = ignored.size) {
                    LumeCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ignored.groupBy { it.role }.forEach { (role, items) ->
                                Overline(role.label)
                                items.take(4).forEach { d -> Text("“${d.text.take(90)}”", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
                            }
                        }
                    }
                }
                if (recognizedText.isNotBlank()) Collapsible("Tudo o que foi reconhecido", key = recognizedText) {
                    LumeCard { SelectionContainer { Text(recognizedText, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = InkSoft) } }
                }
            }
        }
    }
}
