package br.com.trilha.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.domain.brl
import br.com.trilha.domain.pct

/**
 * Número que percorre a distância entre o valor antigo e o novo.
 * Serve para o usuário perceber *que* mudou e *para onde* — um valor que
 * simplesmente troca não comunica direção.
 */
@Composable
fun NumeroAnimado(
    valor: Double,
    modifier: Modifier = Modifier,
    formato: (Double) -> String = { brl(it) },
    tamanho: TextUnit = 17.sp,
    peso: FontWeight = FontWeight.SemiBold,
    cor: Color = Tinta
) {
    val alvo by animateFloatAsState(
        targetValue = valor.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "numero"
    )
    Text(formato(alvo.toDouble()), modifier = modifier, fontSize = tamanho, fontWeight = peso, color = cor)
}

@Composable
fun PorcentagemAnimada(
    valor: Double,
    modifier: Modifier = Modifier,
    tamanho: TextUnit = 17.sp,
    cor: Color = Tinta
) = NumeroAnimado(valor, modifier, { pct(it) }, tamanho, FontWeight.SemiBold, cor)

/** Mostrador circular da pontuação, com o arco crescendo até o valor. */
@Composable
fun MostradorAnimado(valor: Int, modifier: Modifier = Modifier, diametro: Int = 96) {
    val progresso by animateFloatAsState(
        targetValue = valor / 100f,
        animationSpec = tween(900),
        label = "mostrador"
    )
    val cor = when {
        valor >= 70 -> Verde
        valor >= 45 -> Ambar
        else -> Vermelho
    }
    val corAnimada by androidx.compose.animation.animateColorAsState(cor, tween(600), label = "corMostrador")

    Box(modifier.size(diametro.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val traco = 11.dp.toPx()
            val inset = traco / 2
            val tamanho = Size(size.width - traco, size.height - traco)
            drawArc(
                color = Lavado, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = tamanho, style = Stroke(traco)
            )
            drawArc(
                color = corAnimada, startAngle = -90f, sweepAngle = 360f * progresso, useCenter = false,
                topLeft = Offset(inset, inset), size = tamanho,
                style = Stroke(traco, cap = StrokeCap.Round)
            )
        }
        NumeroAnimado(
            valor.toDouble(), formato = { it.toInt().toString() },
            tamanho = 27.sp, peso = FontWeight.Bold
        )
    }
}

/** Barra de progresso com preenchimento animado e cor por faixa. */
@Composable
fun BarraAnimada(
    progresso: Float,
    modifier: Modifier = Modifier,
    altura: Int = 6,
    cor: Color = Verde,
    fundo: Color = Lavado
) {
    val largura by animateFloatAsState(
        targetValue = progresso.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "barra"
    )
    Box(
        modifier.fillMaxWidth().height(altura.dp).clip(RoundedCornerShape(altura.dp)).background(fundo)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(largura).background(cor))
    }
}

@Composable
fun BarraPontosAnimada(nome: String, pontos: Double, maximo: Double, dica: String? = null) {
    var expandido by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(if (dica != null) Modifier.clickableSemRipple { expandido = !expandido } else Modifier)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nome, fontSize = 12.5.sp, color = Tinta2)
            NumeroAnimado(
                pontos, formato = { "${it.toInt()}/${maximo.toInt()}" },
                tamanho = 12.5.sp, peso = FontWeight.Medium, cor = Tinta2
            )
        }
        Spacer(Modifier.height(3.dp))
        BarraAnimada(
            (pontos / maximo).toFloat(), altura = 5,
            cor = if (pontos / maximo >= 0.7) Verde else if (pontos / maximo >= 0.4) Ambar else Vermelho
        )
        androidx.compose.animation.AnimatedVisibility(expandido && dica != null) {
            Text(dica ?: "", fontSize = 11.5.sp, color = Tinta2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Trilha da meta: barra larga com marcas de quarto e rótulo animado. */
@Composable
fun TrilhaAnimada(progresso: Float, modifier: Modifier = Modifier) {
    val largura by animateFloatAsState(progresso.coerceIn(0f, 1f), tween(800), label = "trilha")
    Box(
        modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(Lavado)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(largura).background(Verde))
        Canvas(Modifier.fillMaxSize()) {
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                drawLine(
                    Color.Black.copy(alpha = 0.13f),
                    Offset(size.width * f, 0f), Offset(size.width * f, size.height)
                )
            }
        }
        NumeroAnimado(
            (progresso * 100).toDouble(), formato = { "${it.toInt()}%" },
            tamanho = 13.sp, peso = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
        )
    }
}

/** Clique sem o realce circular, para áreas grandes onde o ripple distrai. */
fun Modifier.clickableSemRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
)
