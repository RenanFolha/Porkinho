package br.com.trilha.domain

import br.com.trilha.data.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/* ===================== resultados ===================== */

data class Resumo(
    val renda: Double, val despesas: Double,
    val essenciais: Double, val importantes: Double, val superfluos: Double,
    val parcelas: Double, val saldoDividas: Double,
    val liquido: Double, val investido: Double, val bens: Double,
    val sobra: Double, val custoBase: Double,
    val taxaPoupanca: Double, val comprometimentoDividas: Double, val pctSuperfluos: Double,
    val patrimonioLiquido: Double, val reservaAtual: Double, val mesesReserva: Double
)

data class ItemPontuacao(val nome: String, val pontos: Double, val maximo: Double, val dica: String)
data class Pontuacao(val total: Int, val itens: List<ItemPontuacao>)

data class Evento(
    val nome: String, val valor: Double, val entrada: Boolean,
    val contaId: String = "", val categoriaId: String = "", val fatura: Boolean = false
)
data class DiaFluxo(val dia: Int, val eventos: List<Evento>, val saldo: Double)
data class Fatura(val conta: Conta, val total: Double, val itens: Int, val diaVencimento: Int)

data class Fluxo(
    val dias: List<DiaFluxo>,
    val serieSaldo: List<Double>,
    val menorSaldo: Double, val diaMenorSaldo: Int,
    val saldoInicial: Double, val saldoFinal: Double,
    val entradas: Double, val saidas: Double,
    val faturas: List<Fatura>
) {
    val totalFaturas: Double get() = faturas.sumOf { it.total }
    fun diaDe(dia: Int): DiaFluxo? = dias.firstOrNull { it.dia == dia }
}

data class PlanoDivida(val meses: Int, val juros: Double, val quitou: Boolean, val ordem: List<String>)

data class Diagnostico(
    val fortes: List<String>, val atencao: List<String>,
    val problemas: List<String>, val prioridades: List<String>
)

data class GastoCategoria(
    val categoria: Categoria, val valor: Double, val meta: Double, val pctRenda: Double
)

data class ComparacaoMes(
    val ref: YearMonth, val entradas: Double, val saidas: Double,
    val saldoFinal: Double, val menorSaldo: Double
)

/* ===================== cálculos base ===================== */

/** Média mensal dos gastos avulsos nos últimos 3 meses com lançamentos. */
fun mediaAvulsos(p: Perfil, classe: Classe? = null): Double {
    val meses = p.lancamentos.mapNotNull { it.data.takeIf { d -> d.length >= 7 }?.substring(0, 7) }
        .distinct().sorted().takeLast(3)
    if (meses.isEmpty()) return 0.0
    val total = p.lancamentos
        .filter { it.data.length >= 7 && it.data.substring(0, 7) in meses }
        .filter { classe == null || p.classeDe(it.categoriaId, it.classe) == classe }
        .sumOf { it.valor }
    return total / meses.size
}

fun resumo(p: Perfil): Resumo {
    val renda = p.rendas.sumOf { it.valor }
    fun fixos(c: Classe) = p.fixos.filter { p.classeDe(it.categoriaId, it.classe) == c }.sumOf { it.valor }

    val essenciais = fixos(Classe.ESSENCIAL) + mediaAvulsos(p, Classe.ESSENCIAL)
    val importantes = fixos(Classe.IMPORTANTE) + mediaAvulsos(p, Classe.IMPORTANTE)
    val superfluos = fixos(Classe.SUPERFLUO) + mediaAvulsos(p, Classe.SUPERFLUO)
    val despesas = essenciais + importantes + superfluos

    val parcelas = p.dividas.sumOf { it.parcela }
    val saldoDividas = p.dividas.sumOf { it.saldo }
    val liquido = p.ativos.filter { it.tipo == TipoAtivo.LIQUIDO }.sumOf { it.valor }
    val investido = p.ativos.filter { it.tipo == TipoAtivo.INVESTIMENTO }.sumOf { it.valor }
    val bens = p.ativos.filter { it.tipo == TipoAtivo.BEM }.sumOf { it.valor }

    val sobra = renda - despesas - parcelas
    val custoBase = essenciais + parcelas
    val reserva = liquido + investido

    return Resumo(
        renda = renda, despesas = despesas,
        essenciais = essenciais, importantes = importantes, superfluos = superfluos,
        parcelas = parcelas, saldoDividas = saldoDividas,
        liquido = liquido, investido = investido, bens = bens,
        sobra = sobra, custoBase = custoBase,
        taxaPoupanca = if (renda > 0) sobra / renda else 0.0,
        comprometimentoDividas = if (renda > 0) parcelas / renda else 0.0,
        pctSuperfluos = if (renda > 0) superfluos / renda else 0.0,
        patrimonioLiquido = liquido + investido + bens - saldoDividas,
        reservaAtual = reserva,
        mesesReserva = if (custoBase > 0) reserva / custoBase else 0.0
    )
}

/**
 * Pontuação 0–100. Os pesos são escolha explícita: caixa e reserva pesam mais
 * porque são o que impede uma dívida nova.
 */
fun pontuacao(p: Perfil, r: Resumo): Pontuacao {
    fun limita(x: Double, maximo: Double) = max(0.0, min(maximo, x))
    // Sem nenhum dado lançado, não há o que pontuar. Dar 20 pontos por "não ter
    // dívidas" quando o usuário simplesmente não cadastrou nada seria mentira.
    val semDados = r.renda <= 0 && p.fixos.isEmpty() && p.dividas.isEmpty() && p.ativos.isEmpty()
    if (semDados) return Pontuacao(
        0,
        listOf(
            ItemPontuacao("Sobra mensal", 0.0, 25.0, "Cadastre renda e gastos."),
            ItemPontuacao("Reserva de emergência", 0.0, 20.0, "Cadastre seu patrimônio."),
            ItemPontuacao("Endividamento", 0.0, 20.0, "Cadastre suas dívidas, se houver."),
            ItemPontuacao("Patrimônio investido", 0.0, 15.0, "Cadastre seus investimentos."),
            ItemPontuacao("Controle de supérfluos", 0.0, 10.0, "Classifique seus gastos."),
            ItemPontuacao("Disciplina de registro", 0.0, 10.0, "Feche o primeiro mês.")
        )
    )
    val itens = listOf(
        ItemPontuacao(
            "Sobra mensal", limita(r.taxaPoupanca / 0.20 * 25, 25.0), 25.0,
            if (r.taxaPoupanca >= 0.20) "No alvo." else "Guardar 20% da renda vale os 25 pontos."
        ),
        ItemPontuacao(
            "Reserva de emergência", limita(r.mesesReserva / 6 * 20, 20.0), 20.0,
            if (r.mesesReserva >= 6) "Reserva completa."
            else "Faltam ${fmt1(max(0.0, 6 - r.mesesReserva))} meses de custo para chegar a 6."
        ),
        ItemPontuacao(
            "Endividamento",
            if (r.saldoDividas <= 0) 20.0 else limita(20 * (1 - r.comprometimentoDividas / 0.30), 20.0),
            20.0,
            when {
                r.saldoDividas <= 0 -> "Sem dívidas."
                r.comprometimentoDividas > 0.30 -> "Mais de 30% da renda em parcelas — zona de risco."
                else -> "Comprometimento sob controle."
            }
        ),
        ItemPontuacao(
            "Patrimônio investido",
            if (r.custoBase > 0) limita(r.investido / (r.custoBase * 12) * 15, 15.0) else 0.0, 15.0,
            "12 meses de custo investidos valem os 15 pontos."
        ),
        ItemPontuacao(
            "Controle de supérfluos",
            if (r.renda > 0) limita(10 * (1 - r.pctSuperfluos / 0.15), 10.0) else 0.0, 10.0,
            if (r.pctSuperfluos > 0.15) "Supérfluos acima de 15% da renda." else "Dentro de um limite saudável."
        ),
        ItemPontuacao(
            "Disciplina de registro", limita(p.fechamentos.size / 6.0 * 10, 10.0), 10.0,
            "${p.fechamentos.size} de 6 fechamentos mensais registrados."
        )
    )
    return Pontuacao(itens.sumOf { it.pontos }.toInt(), itens)
}

/* ===================== cartão de crédito ===================== */

/**
 * Data em que uma compra é efetivamente cobrada. Compra após o fechamento cai
 * na fatura seguinte; se o vencimento é anterior ao fechamento, ele pertence ao
 * mês posterior.
 */
fun vencimentoFatura(compra: LocalDate, diaFecha: Int, diaVence: Int): LocalDate {
    var fechamento = YearMonth.from(compra)
    if (compra.dayOfMonth > diaFecha) fechamento = fechamento.plusMonths(1)
    val mesVenc = if (diaVence < diaFecha) fechamento.plusMonths(1) else fechamento
    return mesVenc.atDay(min(diaVence, mesVenc.lengthOfMonth()))
}

fun vencimentoFatura(compra: LocalDate, conta: Conta): LocalDate =
    vencimentoFatura(compra, conta.diaFechamento.coerceIn(1, 31), conta.diaVencimento.coerceIn(1, 31))

/* ===================== fluxo de caixa diário ===================== */

fun fluxoDiario(p: Perfil, ym: YearMonth): Fluxo {
    val ultimoDia = ym.lengthOfMonth()
    val eventos = HashMap<Int, MutableList<Evento>>()
    fun lancar(dia: Int, ev: Evento) {
        eventos.getOrPut(dia.coerceIn(1, ultimoDia)) { mutableListOf() }.add(ev)
    }

    p.rendas.filter { it.valor > 0 }.forEach {
        lancar(it.dia, Evento(it.nome.ifBlank { "Renda" }, it.valor, true, it.contaId))
    }
    p.fixos.filter { it.valor > 0 && !p.ehCartao(it.contaId, it.forma) }.forEach {
        lancar(it.dia, Evento(it.nome.ifBlank { "Gasto fixo" }, it.valor, false, it.contaId, it.categoriaId))
    }
    p.dividas.filter { it.parcela > 0 }.forEach {
        lancar(it.dia, Evento("${it.nome.ifBlank { "Dívida" }} (parcela)", it.parcela, false))
    }
    p.lancamentos
        .filter { it.valor > 0 && it.data.length >= 10 && !p.ehCartao(it.contaId, it.forma) }
        .filter { it.data.substring(0, 7) == ym.toString() }
        .forEach {
            lancar(
                it.data.substring(8, 10).toInt(),
                Evento(it.nome.ifBlank { "Gasto" }, it.valor, false, it.contaId, it.categoriaId)
            )
        }

    // Uma fatura por cartão, cada um com seu próprio fechamento e vencimento.
    val faturas = mutableListOf<Fatura>()
    p.contas.filter { it.ehCartao }.forEach { cartao ->
        var total = 0.0
        var itens = 0
        p.lancamentos.filter { it.valor > 0 && it.contaId == cartao.id && it.data.length >= 10 }.forEach {
            if (YearMonth.from(vencimentoFatura(LocalDate.parse(it.data), cartao)) == ym) {
                total += it.valor; itens++
            }
        }
        p.fixos.filter { it.valor > 0 && it.contaId == cartao.id }.forEach { f ->
            listOf(-1L, 0L).forEach { off ->
                val mes = ym.plusMonths(off)
                val data = mes.atDay(min(f.dia.coerceAtLeast(1), mes.lengthOfMonth()))
                if (YearMonth.from(vencimentoFatura(data, cartao)) == ym) { total += f.valor; itens++ }
            }
        }
        if (total > 0) {
            val diaVenc = min(cartao.diaVencimento.coerceIn(1, 31), ultimoDia)
            faturas.add(Fatura(cartao, total, itens, diaVenc))
            val plural = if (itens == 1) "lançamento" else "lançamentos"
            lancar(diaVenc, Evento("${cartao.nome} ($itens $plural)", total, false, cartao.id, fatura = true))
        }
    }

    val inicial = p.saldos[ym.toString()] ?: 0.0
    var saldo = inicial
    val dias = mutableListOf<DiaFluxo>()
    val serie = ArrayList<Double>(ultimoDia)
    for (d in 1..ultimoDia) {
        eventos[d]?.let { lista ->
            lista.forEach { saldo += if (it.entrada) it.valor else -it.valor }
            dias.add(DiaFluxo(d, lista.toList(), saldo))
        }
        serie.add(saldo)
    }
    // O menor saldo sai da série completa, não só dos dias com evento: assim o
    // dia informado é sempre um dia real do mês, mesmo quando nada piora o saldo.
    val idxMenor = serie.indices.minByOrNull { serie[it] } ?: 0
    val menor = serie.getOrElse(idxMenor) { inicial }
    val diaMenor = idxMenor + 1

    return Fluxo(
        dias = dias, serieSaldo = serie,
        menorSaldo = menor, diaMenorSaldo = diaMenor,
        saldoInicial = inicial, saldoFinal = saldo,
        entradas = dias.sumOf { d -> d.eventos.filter { it.entrada }.sumOf { it.valor } },
        saidas = dias.sumOf { d -> d.eventos.filter { !it.entrada }.sumOf { it.valor } },
        faturas = faturas
    )
}

/** Resumo de vários meses, para o gráfico comparativo. */
fun compararMeses(p: Perfil, ate: YearMonth, quantidade: Int = 6): List<ComparacaoMes> =
    (quantidade - 1 downTo 0).map { off ->
        val ym = ate.minusMonths(off.toLong())
        val f = fluxoDiario(p, ym)
        ComparacaoMes(ym, f.entradas, f.saidas, f.saldoFinal, f.menorSaldo)
    }

/* ===================== orçamento por categoria ===================== */

fun gastosPorCategoria(p: Perfil, r: Resumo, ym: YearMonth? = null): List<GastoCategoria> {
    val totais = HashMap<String, Double>()

    p.fixos.filter { it.valor > 0 }.forEach {
        totais[it.categoriaId] = (totais[it.categoriaId] ?: 0.0) + it.valor
    }
    if (ym != null) {
        p.lancamentos.filter { it.valor > 0 && it.data.length >= 7 && it.data.substring(0, 7) == ym.toString() }
            .forEach { totais[it.categoriaId] = (totais[it.categoriaId] ?: 0.0) + it.valor }
    } else {
        val meses = max(
            1,
            p.lancamentos.mapNotNull { it.data.takeIf { d -> d.length >= 7 }?.substring(0, 7) }.distinct().size
        )
        p.lancamentos.filter { it.valor > 0 }
            .forEach { totais[it.categoriaId] = (totais[it.categoriaId] ?: 0.0) + it.valor / meses }
    }

    return totais.mapNotNull { (id, valor) ->
        val cat = p.categoria(id) ?: Categoria(id = id, nome = "Sem categoria")
        var meta = if (cat.teto > 0) cat.teto else valor
        if (cat.teto <= 0 && r.renda > 0) {
            if (cat.classe == Classe.SUPERFLUO && valor > r.renda * 0.07) meta = r.renda * 0.07
            else if (cat.classe == Classe.IMPORTANTE && valor > r.renda * 0.15) meta = r.renda * 0.15
        }
        if (r.sobra < 0 && cat.classe != Classe.ESSENCIAL) meta = min(meta, valor * 0.75)
        GastoCategoria(cat, valor, meta, if (r.renda > 0) valor / r.renda else 0.0)
    }.sortedByDescending { it.valor }
}

/* ===================== dívidas ===================== */

private class SaldoDivida(var s: Double, val i: Double, val p: Double, val nome: String)

fun simularDividas(dividas: List<Divida>, extraMensal: Double, avalanche: Boolean): PlanoDivida? {
    val lista = dividas.filter { it.saldo > 0 }
        .map { SaldoDivida(it.saldo, it.taxaMes / 100.0, it.parcela, it.nome.ifBlank { "sem nome" }) }
        .sortedWith(if (avalanche) compareByDescending { it.i } else compareBy { it.s })
    if (lista.isEmpty()) return null

    val ordem = lista.map { it.nome }
    var mes = 0
    var juros = 0.0
    var quitou = true

    while (lista.any { it.s > 0.01 }) {
        mes++
        if (mes > 600) { quitou = false; break }
        var pote = extraMensal
        lista.filter { it.s > 0.01 }.forEach { it.s *= (1 + it.i) }
        lista.filter { it.s > 0.01 }.forEach { d ->
            juros += d.s - d.s / (1 + d.i)
            d.s -= min(d.p, d.s)
            if (d.s <= 0.01) { pote += d.p; d.s = 0.0 }
        }
        for (d in lista) {
            if (pote <= 0) break
            if (d.s <= 0.01) continue
            val pg = min(pote, d.s)
            d.s -= pg; pote -= pg
            if (d.s <= 0.01) d.s = 0.0
        }
    }
    return PlanoDivida(mes, juros, quitou, ordem)
}

/* ===================== metas ===================== */

fun taxaMensal(taxaAnualPct: Double): Double = (1 + taxaAnualPct / 100).pow(1.0 / 12) - 1

fun aporteNecessario(fv: Double, pv: Double, i: Double, meses: Int): Double {
    if (meses <= 0) return max(0.0, fv - pv)
    if (i == 0.0) return max(0.0, (fv - pv) / meses)
    val f = (1 + i).pow(meses)
    return max(0.0, (fv - pv * f) * i / (f - 1))
}

fun prazoParaMeta(fv: Double, pv: Double, i: Double, aporte: Double): Int? {
    var s = pv
    for (m in 1..1200) {
        s = s * (1 + i) + aporte
        if (s >= fv) return m
    }
    return null
}

fun projecao(pv: Double, aporte: Double, i: Double, meses: Int): List<Double> {
    val serie = mutableListOf(pv)
    var s = pv
    repeat(meses) { s = s * (1 + i) + aporte; serie.add(s) }
    return serie
}

/* ===================== diagnóstico ===================== */

fun diagnostico(p: Perfil, r: Resumo, f: Fluxo): Diagnostico {
    val fortes = mutableListOf<String>()
    val atencao = mutableListOf<String>()
    val problemas = mutableListOf<String>()
    val prioridades = mutableListOf<String>()
    if (r.renda <= 0) return Diagnostico(fortes, atencao, problemas, prioridades)

    if (r.taxaPoupanca >= 0.20) fortes += "Você guarda ${pct(r.taxaPoupanca)} da renda — acima do patamar de 20%."
    if (r.mesesReserva >= 6) fortes += "Reserva cobre ${fmt1(r.mesesReserva)} meses de custo essencial."
    if (p.dividas.isEmpty()) fortes += "Sem dívidas registradas: renda inteira disponível para decisão."
    if (r.investido > 0) fortes += "Patrimônio investido de ${brl(r.investido)}."
    if (f.dias.isNotEmpty() && r.custoBase > 0 && f.menorSaldo >= r.custoBase * 0.5)
        fortes += "O caixa não aperta em nenhum dia do mês."

    if (r.sobra > 0 && r.taxaPoupanca < 0.10)
        atencao += "Sobra de apenas ${pct(r.taxaPoupanca)} da renda — margem fina para imprevistos."
    if (r.pctSuperfluos > 0.15)
        atencao += "Supérfluos em ${pct(r.pctSuperfluos)} da renda (${brl(r.superfluos)}). " +
            "Cortando para 15%, sobram ${brl(r.superfluos - r.renda * 0.15)} por mês."
    if (r.mesesReserva in 0.01..2.99)
        atencao += "Reserva cobre menos de 3 meses: um imprevisto vira dívida nova."
    if (f.totalFaturas > r.renda * 0.3)
        atencao += "As faturas de cartão consomem ${pct(f.totalFaturas / r.renda)} da renda deste mês."

    if (r.sobra < 0)
        problemas += "Você gasta ${brl(-r.sobra)} a mais do que ganha por mês. Sem corrigir isso, qualquer plano falha."
    if (r.comprometimentoDividas > 0.30)
        problemas += "Parcelas em ${pct(r.comprometimentoDividas)} da renda — acima de 30% o orçamento trava."
    if (f.dias.isNotEmpty() && f.menorSaldo < 0)
        problemas += "O caixa fica negativo no dia ${f.diaMenorSaldo}, mesmo com a conta fechando no mês."
    val caras = p.dividas.filter { it.taxaMes >= 4 }
    if (caras.isNotEmpty())
        problemas += "Dívida a ${caras.joinToString(", ") { fmt1(it.taxaMes) + "% a.m." }} — " +
            "nenhum investimento acessível compete com isso."

    prioridades += when {
        r.sobra < 0 -> "1. Fechar o buraco mensal de ${brl(-r.sobra)} cortando supérfluos e renegociando parcelas."
        f.dias.isNotEmpty() && f.menorSaldo < 0 ->
            "1. Reorganizar datas de vencimento: a conta fecha no mês, mas o dinheiro não está lá no dia da cobrança."
        caras.isNotEmpty() ->
            "1. Quitar a dívida de maior juro. Cada real abatido rende o próprio juro, sem risco e sem imposto."
        r.mesesReserva < 3 -> "1. Formar 3 meses de reserva (${brl(r.custoBase * 3)}) em liquidez diária."
        r.mesesReserva < 6 -> "1. Completar a reserva até 6 meses (${brl(r.custoBase * 6)})."
        else -> "1. Direcionar a sobra de ${brl(r.sobra)}/mês para as metas."
    }
    prioridades += if (r.sobra < r.renda * 0.1)
        "2. Aumentar renda: com sobra abaixo de 10%, cortar gasto tem teto e renda não tem."
    else "2. Dar prazo e valor a cada meta para transformar a sobra em aporte com destino."

    return Diagnostico(fortes, atencao, problemas, prioridades)
}
