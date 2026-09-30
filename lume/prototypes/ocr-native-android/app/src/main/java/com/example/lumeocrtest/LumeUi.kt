package com.example.lumeocrtest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lumeocrtest.ui.theme.Amber
import com.example.lumeocrtest.ui.theme.AmberSoft
import com.example.lumeocrtest.ui.theme.Hairline
import com.example.lumeocrtest.ui.theme.InkFaint
import com.example.lumeocrtest.ui.theme.InkSoft
import com.example.lumeocrtest.ui.theme.LumeDarkGreen
import com.example.lumeocrtest.ui.theme.LumeLightGreen
import com.example.lumeocrtest.ui.theme.LumeMist
import com.example.lumeocrtest.ui.theme.QuoteStyle
import com.example.lumeocrtest.ui.theme.Sheet
import kotlinx.coroutines.delay

/*
 * Peças visuais do Lume: superfícies claras com borda fina, rótulos discretos, citações em serifa,
 * seções expansíveis e movimentos curtos. Toda animação respeita LocalLumeMotion (preferência do app
 * + escala de animação do Android): com movimento reduzido, o conteúdo aparece direto.
 */

/** Resposta imediata ao toque: o elemento recua 2% enquanto pressionado. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalLumeMotion.current
    val scale by animateFloatAsState(if (pressed && motion) 0.98f else 1f, if (motion) tween(90) else snap(), label = "Toque")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Cartão claro com borda fina. Com `onClick`, responde ao toque. */
@Composable
fun LumeCard(modifier: Modifier = Modifier, color: Color = Sheet, border: Color = Hairline,
             onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth().pressScale(source), shape = shape, color = color,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, border), interactionSource = source) { Column(content = content) }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color,
            contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, border)) {
            Column(content = content)
        }
    }
}

/** Rótulo pequeno em caixa alta, acima de um bloco. */
@Composable
fun Overline(text: String, color: Color = InkFaint, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = LumeDarkGreen,
            modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }
    }
}

enum class Tone { Green, Amber, Neutral }

@Composable
fun Tag(text: String, tone: Tone = Tone.Neutral) {
    val (bg, fg) = when (tone) {
        Tone.Green -> LumeLightGreen to LumeDarkGreen
        Tone.Amber -> AmberSoft to Amber
        Tone.Neutral -> Color(0xFFEDEFE8) to InkSoft
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Trecho de evidência: trilho verde à esquerda e serifa, para se distinguir do texto da interface. */
@Composable
fun QuoteBlock(text: String, caption: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(LumeLightGreen))
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("“$text”", style = QuoteStyle, color = MaterialTheme.colorScheme.onSurface)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkFaint) }
        }
    }
}

/** Linha que abre/fecha um conteúdo: rótulo + seta que gira. Área de toque de 48dp. */
@Composable
fun ExpanderRow(label: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false) {
    val motion = LocalLumeMotion.current
    val rotation by animateFloatAsState(if (open) 180f else 0f, if (motion) tween(200) else snap(), label = "Seta")
    val source = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (open) "Aberto" else "Fechado" }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = LumeDarkGreen)
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = LumeDarkGreen,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
    }
}

/** Seção recolhível completa (rótulo + conteúdo animado). */
@Composable
fun Collapsible(label: String, key: Any?, initiallyOpen: Boolean = false, strong: Boolean = false,
                content: @Composable () -> Unit) {
    var open by remember(key) { mutableStateOf(initiallyOpen) }
    Column {
        ExpanderRow(label, open, { open = !open }, strong = strong)
        LumeVisibility(open) { Box(Modifier.padding(top = 4.dp)) { content() } }
    }
}

/**
 * Entrada suave de um item (opacidade + 10dp de subida), escalonada pelo índice. O espaço do item é
 * reservado desde o início, então a lista não "pula" enquanto os itens aparecem.
 */
@Composable
fun Reveal(index: Int = 0, key: Any? = Unit, content: @Composable () -> Unit) {
    val motion = LocalLumeMotion.current
    val progress = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key, motion) {
        if (motion) progress.animateTo(1f, tween(260, delayMillis = 45 * index.coerceAtMost(6), easing = FastOutSlowInEasing))
        else progress.snapTo(1f)
    }
    val offset = with(LocalDensity.current) { 10.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * offset }) { content() }
}

/** O Lume como presença discreta: respira de leve e pisca de vez em quando. Decorativo (sem leitura por TalkBack). */
@Composable
fun LumePresence(size: Dp, animate: Boolean = true) {
    val motion = LocalLumeMotion.current && animate
    val bob = if (motion) {
        rememberInfiniteTransition(label = "Presença").animateFloat(0f, 1f,
            infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Respiração").value
    } else 0f
    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion) {
        blink = 0f
        while (motion) {
            delay(kotlin.random.Random.nextLong(2800, 6500))
            blink = 1f; delay(120); blink = 0f
        }
    }
    val lift = with(LocalDensity.current) { 2.dp.toPx() }
    Box(Modifier.size(size).graphicsLayer { translationY = -bob * lift }) {
        Image(painterResource(R.drawable.lume_mascot), contentDescription = null, modifier = Modifier.size(size))
        if (blink > 0f) Image(painterResource(R.drawable.lume_mascot_blink), contentDescription = null, modifier = Modifier.size(size))
    }
}

/** Etapa em andamento: o Lume ao lado do texto da etapa (que troca com transição) e uma barra fina. */
@Composable
fun StageProgress(text: String, modifier: Modifier = Modifier) {
    val motion = LocalLumeMotion.current
    LumeCard(modifier, color = LumeMist, border = Color.Transparent) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LumePresence(34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(text, label = "Etapa", transitionSpec = {
                    if (motion) fadeIn(tween(180)) togetherWith fadeOut(tween(120)) else fadeIn(snap()) togetherWith fadeOut(snap())
                }) { Text(it, style = MaterialTheme.typography.titleSmall, color = LumeDarkGreen, fontWeight = FontWeight.SemiBold) }
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = LumeDarkGreen, trackColor = LumeLightGreen)
            }
        }
    }
}

/** Fonte do sistema ampliada (acessibilidade): layouts lado a lado passam a empilhar. */
@Composable
fun largeText(): Boolean = LocalDensity.current.fontScale >= 1.3f
