package br.com.trilha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Gráficos tocáveis. O padrão é o mesmo em todos: a seleção é estado do
 * chamador, o toque devolve o índice, e quem desenha nunca formata texto —
 * rótulo e valor ficam em Composables comuns, fora do Canvas.
 */

private fun DrawScope.pontoEm(
    valores: List<Double>, i: Int, minimo: Double, maximo: Double, pad: Float
): Offset {
    val faixa = (maximo - minimo).takeIf { it > 0 } ?: 1.0
    val x = if (valores.size <= 1) pad else pad + i.toFloat() / (valores.size - 1) * (size.width - 2 * pad)
    val y = size.height - pad - ((valores[i] - minimo) / faixa).toFloat() * (size.height - 2 * pad)
    return Offset(x, y)
}

/**
 * Linha única com área preenchida, linha do zero e marcador arrastável.
 * Usada para o saldo diário: arrastar o dedo percorre os dias do mês.
 */
@Composable
fun GraficoLinhaInterativo(
    valores: List<Double>,
    selecionado: Int?,
    onSelecionar: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    altura: Int = 150,
    cor: Color = Verde,
    corNegativa: Color = Vermelho
) {
    if (valores.isEmpty()) return
    val minimo = minOf(valores.min(), 0.0)
    val maximo = maxOf(valores.max(), 0.0)
    val revelar by animateFloatAsState(1f, tween(700), label = "revelar")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura.dp)
            .pointerInput(valores.size) {
                detectTapGestures { pos ->
                    val i = ((pos.x / size.width) * (valores.size - 1)).roundToInt().coerceIn(0, valores.size - 1)
                    onSelecionar(if (i == selecionado) null else i)
                }
            }
            .pointerInput(valores.size) {
                detectHorizontalDragGestures { mudanca, _ ->
                    val i = ((mudanca.position.x / size.width) * (valores.size - 1))
                        .roundToInt().coerceIn(0, valores.size - 1)
                    onSelecionar(i)
                }
            }
    ) {
        val pad = 8f
        val zeroY = pontoEm(listOf(0.0), 0, minimo, maximo, pad).y

        // faixa negativa destacada, para o vermelho não depender só da linha
        if (minimo < 0) {
            drawRect(
                color = Vermelho.copy(alpha = 0.06f),
                topLeft = Offset(0f, zeroY),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - zeroY)
            )
        }
        drawLine(Linha, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)

        val quantos = (valores.size * revelar).roundToInt().coerceAtLeast(2)
        val pontos = (0 until quantos).map { pontoEm(valores, it, minimo, maximo, pad) }

        val area = Path().apply {
            moveTo(pontos.first().x, zeroY)
            pontos.forEach { lineTo(it.x, it.y) }
            lineTo(pontos.last().x, zeroY)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(cor.copy(alpha = 0.22f), cor.copy(alpha = 0.02f)))
        )

        val linha = Path().apply {
            moveTo(pontos.first().x, pontos.first().y)
            pontos.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linha, cor, style = Stroke(width = 3f))

        // trecho negativo redesenhado por cima, em vermelho
        var i = 0
        while (i < quantos - 1) {
            if (valores[i] < 0 || valores[i + 1] < 0) {
                drawLine(corNegativa, pontos[i], pontos[i + 1], strokeWidth = 3f)
            }
            i++
        }

        selecionado?.let { sel ->
            if (sel in valores.indices) {
                val pt = pontoEm(valores, sel, minimo, maximo, pad)
                drawLine(Tinta2.copy(alpha = 0.4f), Offset(pt.x, 0f), Offset(pt.x, size.height), strokeWidth = 1.5f)
                drawCircle(if (valores[sel] < 0) corNegativa else cor, radius = 7f, center = pt)
                drawCircle(Superficie, radius = 3.5f, center = pt)
            }
        }
    }
}

/**
 * Barras comparativas com toque. Usada para comparar meses:
 * cada barra é um mês, tocar seleciona e o detalhe aparece fora do gráfico.
 */
@Composable
fun GraficoBarrasInterativo(
    valores: List<Double>,
    selecionado: Int?,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    altura: Int = 120,
    cor: Color = Verde,
    corNegativa: Color = Vermelho
) {
    if (valores.isEmpty()) return
    val maximo = maxOf(valores.maxOf { abs(it) }, 1.0)
    val crescer by animateFloatAsState(1f, tween(700), label = "barras")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura.dp)
            .pointerInput(valores.size) {
                detectTapGestures { pos ->
                    val i = ((pos.x / size.width) * valores.size).toInt().coerceIn(0, valores.size - 1)
                    onSelecionar(i)
                }
            }
    ) {
        val vao = size.width / valores.size
        val larguraBarra = vao * 0.56f
        val temNegativo = valores.any { it < 0 }
        val zeroY = if (temNegativo) size.height / 2 else size.height

        if (temNegativo) drawLine(Linha, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)

        valores.forEachIndexed { i, v ->
            val disponivel = if (temNegativo) size.height / 2 else size.height
            val h = (abs(v) / maximo).toFloat() * disponivel * 0.92f * crescer
            val x = vao * i + (vao - larguraBarra) / 2
            val destacada = i == selecionado
            val corBarra = when {
                v < 0 -> corNegativa
                destacada -> cor
                else -> cor.copy(alpha = 0.42f)
            }
            drawRoundRect(
                color = corBarra,
                topLeft = Offset(x, if (v >= 0) zeroY - h else zeroY),
                size = androidx.compose.ui.geometry.Size(larguraBarra, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
            )
        }
    }
}

/**
 * Várias séries no mesmo eixo com linha de alvo — os três cenários da meta.
 * Arrastar percorre os meses; a série em destaque fica opaca e as outras recuam.
 */
@Composable
fun GraficoCenarios(
    series: List<List<Double>>,
    cores: List<Color>,
    alvo: Double,
    destaque: Int,
    selecionado: Int?,
    onSelecionar: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    altura: Int = 150
) {
    if (series.isEmpty() || series.all { it.size < 2 }) return
    val todos = series.flatten() + alvo + 0.0
    val minimo = 0.0
    val maximo = todos.max().takeIf { it > 0 } ?: 1.0
    val n = series.maxOf { it.size }
    val revelar by animateFloatAsState(1f, tween(700), label = "cenarios")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura.dp)
            .pointerInput(n) {
                detectTapGestures { pos ->
                    val i = ((pos.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    onSelecionar(if (i == selecionado) null else i)
                }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { mudanca, _ ->
                    val i = ((mudanca.position.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    onSelecionar(i)
                }
            }
    ) {
        val pad = 8f
        val yAlvo = pontoEm(listOf(alvo), 0, minimo, maximo, pad).y
        drawLine(
            Ouro, Offset(pad, yAlvo), Offset(size.width - pad, yAlvo),
            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f))
        )

        series.forEachIndexed { idx, serie ->
            if (serie.size < 2) return@forEachIndexed
            val quantos = (serie.size * revelar).roundToInt().coerceAtLeast(2)
            val pontos = (0 until quantos).map { pontoEm(serie, it, minimo, maximo, pad) }
            val caminho = Path().apply {
                moveTo(pontos.first().x, pontos.first().y)
                pontos.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val ehDestaque = idx == destaque
            drawPath(
                caminho,
                cores[idx].copy(alpha = if (ehDestaque) 1f else 0.38f),
                style = Stroke(width = if (ehDestaque) 3.5f else 2f)
            )
        }

        selecionado?.let { sel ->
            val serie = series[destaque]
            if (sel in serie.indices) {
                val pt = pontoEm(serie, sel, minimo, maximo, pad)
                drawLine(Tinta2.copy(alpha = 0.4f), Offset(pt.x, 0f), Offset(pt.x, size.height), strokeWidth = 1.5f)
                drawCircle(cores[destaque], radius = 7f, center = pt)
                drawCircle(Superficie, radius = 3.5f, center = pt)
            }
        }
    }
}

/**
 * Barra horizontal empilhada: a divisão da renda entre as classes de gasto.
 * Tocar cada faixa seleciona a classe correspondente.
 */
@Composable
fun BarraComposicao(
    partes: List<Triple<String, Double, Color>>,
    selecionado: Int?,
    modifier: Modifier = Modifier,
    onSelecionar: (Int?) -> Unit
) {
    val total = partes.sumOf { it.second }.takeIf { it > 0 } ?: return
    val animado by animateFloatAsState(1f, tween(700), label = "composicao")

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(30.dp).pointerInput(partes.size) {
                detectTapGestures { pos ->
                    var acumulado = 0.0
                    var achado = -1
                    partes.forEachIndexed { i, parte ->
                        val ini = (acumulado / total).toFloat() * size.width
                        acumulado += parte.second
                        val fim = (acumulado / total).toFloat() * size.width
                        if (pos.x in ini..fim) achado = i
                    }
                    if (achado >= 0) onSelecionar(if (achado == selecionado) null else achado)
                }
            }
        ) {
            var x = 0f
            partes.forEachIndexed { i, (_, valor, cor) ->
                val largura = (valor / total).toFloat() * size.width * animado
                val destacada = selecionado == null || selecionado == i
                drawRoundRect(
                    color = if (destacada) cor else cor.copy(alpha = 0.3f),
                    topLeft = Offset(x + 1f, 0f),
                    size = androidx.compose.ui.geometry.Size((largura - 2f).coerceAtLeast(0f), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                x += largura
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            partes.forEachIndexed { i, (nome, valor, cor) ->
                Row(
                    Modifier.weight(1f).clickableSemRipple { onSelecionar(if (i == selecionado) null else i) },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(cor) }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(nome, fontSize = 10.5.sp, color = Tinta2, maxLines = 1)
                        Text(
                            "${(valor / total * 100).roundToInt()}%",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selecionado == null || selecionado == i) Tinta else Tinta2
                        )
                    }
                }
            }
        }
    }
}

/** Rótulos sob um gráfico, mostrando só alguns para não embolar. */
@Composable
fun RotulosEixo(rotulos: List<String>, selecionado: Int?, maximo: Int = 5) {
    if (rotulos.isEmpty()) return
    val passo = (rotulos.size / maximo).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        rotulos.forEachIndexed { i, r ->
            if (i % passo == 0 || i == rotulos.lastIndex) {
                Text(
                    r, fontSize = 10.sp,
                    color = if (i == selecionado) Destaque else Tinta2,
                    fontWeight = if (i == selecionado) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
