package br.com.trilha.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val PT: Locale = Locale.forLanguageTag("pt-BR")
private val inteiro: NumberFormat = NumberFormat.getIntegerInstance(PT)
private val decimal: NumberFormat = NumberFormat.getNumberInstance(PT).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}

/** R$ arredondado — em painel, centavo é ruído. */
fun brl(v: Double): String {
    val sinal = if (v < 0) "-" else ""
    return "${sinal}R$ ${inteiro.format(abs(v).roundToLong())}"
}

fun brl2(v: Double): String = "R$ ${decimal.format(v)}"

fun pct(v: Double): String = String.format(PT, "%.1f%%", v * 100)

fun fmt1(v: Double): String = String.format(PT, "%.1f", v)

/** Texto do campo → número, aceitando "1.234,56" e "1234.56". */
fun paraValor(texto: String): Double {
    var s = texto.trim().filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
    if (s.isEmpty()) return 0.0
    s = when {
        s.contains(',') -> s.replace(".", "").replace(',', '.')
        Regex("^-?\\d{1,3}(\\.\\d{3})+$").matches(s) -> s.replace(".", "")
        else -> s
    }
    return s.toDoubleOrNull() ?: 0.0
}

/** Número → texto do campo, sem casas quando redondo. */
fun paraTexto(v: Double): String = when {
    v == 0.0 -> ""
    v == v.roundToLong().toDouble() -> v.roundToLong().toString()
    else -> String.format(PT, "%.2f", v)
}

private val MESES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
)
private val SEMANA = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

fun nomeMes(ym: YearMonth): String = "${MESES[ym.monthValue - 1]} ${ym.year}"
fun nomeMesCurto(ym: YearMonth): String = "${MESES[ym.monthValue - 1].take(3)}/${ym.year}"
fun diaSemana(data: LocalDate): String = SEMANA[data.dayOfWeek.value - 1]
fun ddMM(data: LocalDate): String = String.format("%02d/%02d", data.dayOfMonth, data.monthValue)
