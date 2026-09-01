package br.com.trilha.domain

import br.com.trilha.data.Perfil
import java.time.LocalDate
import java.time.YearMonth

/**
 * Painel adaptativo: em vez de uma ordem fixa, cada seção recebe uma nota de
 * relevância calculada a partir da situação atual. Quem está com o caixa
 * negativo vê o caixa primeiro; quem já estabilizou vê as metas.
 */
enum class Secao(val titulo: String) {
    CAIXA("Caixa do mês"),
    PROXIMOS("Próximos 7 dias"),
    DIVIDAS("Dívidas"),
    RESERVA("Reserva de emergência"),
    CATEGORIAS("Para onde vai o dinheiro"),
    FLUXO("Fluxo do mês"),
    METAS("Metas"),
    PONTUACAO("Pontuação financeira"),
    EVOLUCAO("Evolução dos meses"),
    DIAGNOSTICO("Diagnóstico")
}

data class SecaoPainel(val secao: Secao, val relevancia: Int, val motivo: String)

/** A única coisa que mais importa hoje, com destino de navegação. */
data class AcaoMomento(
    val titulo: String,
    val texto: String,
    val rotuloBotao: String,
    val destino: Secao,
    val severidade: Int // 2 = grave, 1 = atenção, 0 = tudo certo
)

data class Vencimento(val data: LocalDate, val evento: Evento)

fun proximosVencimentos(p: Perfil, dias: Long = 7, hoje: LocalDate = LocalDate.now()): List<Vencimento> {
    val limite = hoje.plusDays(dias)
    val resultado = mutableListOf<Vencimento>()
    listOf(0L, 1L).forEach { off ->
        val ym = YearMonth.from(hoje).plusMonths(off)
        fluxoDiario(p, ym).dias.forEach { d ->
            val data = ym.atDay(d.dia)
            if (!data.isBefore(hoje) && !data.isAfter(limite))
                d.eventos.forEach { resultado.add(Vencimento(data, it)) }
        }
    }
    return resultado.sortedBy { it.data }
}

fun painelAdaptativo(p: Perfil, r: Resumo, f: Fluxo): List<SecaoPainel> {
    val temDados = r.renda > 0 || p.fixos.isNotEmpty()
    val notas = mutableListOf<SecaoPainel>()

    fun nota(s: Secao, n: Int, motivo: String) { notas.add(SecaoPainel(s, n, motivo)) }

    // Caixa: sobe quando o mês tem um dia no vermelho.
    nota(
        Secao.CAIXA,
        when {
            f.dias.isEmpty() -> 30
            f.menorSaldo < 0 -> 100
            r.custoBase > 0 && f.menorSaldo < r.custoBase * 0.25 -> 80
            else -> 45
        },
        if (f.menorSaldo < 0) "saldo negativo no dia ${f.diaMenorSaldo}" else "caixa acompanhado"
    )

    // Próximos vencimentos: relevante quando há algo grande chegando.
    val proximos = proximosVencimentos(p)
    val saidaProxima = proximos.filter { !it.evento.entrada }.sumOf { it.evento.valor }
    nota(
        Secao.PROXIMOS,
        when {
            proximos.isEmpty() -> 10
            r.renda > 0 && saidaProxima > r.renda * 0.3 -> 85
            else -> 55
        },
        "${proximos.size} eventos até sete dias"
    )

    // Dívidas: sobem com juro alto e com comprometimento da renda.
    val maiorTaxa = p.dividas.maxOfOrNull { it.taxaMes } ?: 0.0
    nota(
        Secao.DIVIDAS,
        when {
            p.dividas.none { it.saldo > 0 } -> 0
            maiorTaxa >= 8 -> 95
            r.comprometimentoDividas > 0.30 -> 90
            maiorTaxa >= 4 -> 75
            else -> 50
        },
        if (maiorTaxa > 0) "maior juro ${fmt1(maiorTaxa)}% a.m." else "sem dívidas"
    )

    // Reserva: prioridade máxima quando quase não existe, cai quando completa.
    nota(
        Secao.RESERVA,
        when {
            !temDados -> 20
            r.mesesReserva < 1 -> 88
            r.mesesReserva < 3 -> 70
            r.mesesReserva < 6 -> 50
            else -> 25
        },
        "${fmt1(r.mesesReserva)} meses de custo cobertos"
    )

    // Categorias: sobem quando há excesso concentrado em algum lugar.
    val categorias = gastosPorCategoria(p, r)
    val excesso = categorias.sumOf { maxOf(0.0, it.valor - it.meta) }
    nota(
        Secao.CATEGORIAS,
        when {
            categorias.isEmpty() -> 5
            r.renda > 0 && excesso > r.renda * 0.10 -> 80
            excesso > 0 -> 60
            else -> 40
        },
        if (excesso > 0) "${brl(excesso)} acima das metas" else "dentro das metas"
    )

    nota(Secao.FLUXO, if (temDados) 42 else 15, "composição do mês")

    // Metas: sobem quando já há sobra para direcionar.
    nota(
        Secao.METAS,
        when {
            p.metas.isEmpty() && r.sobra > 0 -> 65
            p.metas.isEmpty() -> 15
            r.sobra > 0 && r.mesesReserva >= 3 -> 72
            else -> 35
        },
        "${p.metas.size} metas ativas"
    )

    nota(Secao.PONTUACAO, if (temDados) 38 else 60, "visão geral")
    nota(Secao.EVOLUCAO, if (p.fechamentos.size >= 2) 58 else 8, "${p.fechamentos.size} meses fechados")
    nota(Secao.DIAGNOSTICO, if (temDados) 44 else 12, "leitura completa")

    return notas.filter { it.relevancia > 12 }.sortedByDescending { it.relevancia }
}

fun acaoDoMomento(p: Perfil, r: Resumo, f: Fluxo): AcaoMomento {
    val caras = p.dividas.filter { it.taxaMes >= 4 && it.saldo > 0 }
    return when {
        r.renda <= 0 -> AcaoMomento(
            "Comece pela renda",
            "Sem renda cadastrada não há o que calcular. Lance quanto entra e em que dia cai na conta.",
            "Preencher dados", Secao.FLUXO, 1
        )
        f.dias.isNotEmpty() && f.menorSaldo < 0 -> AcaoMomento(
            "Seu caixa fura no dia ${f.diaMenorSaldo}",
            "A projeção chega a ${brl(f.menorSaldo)}. A conta fecha no mês, mas o dinheiro não está lá no dia da " +
                "cobrança. Mudar a data de um vencimento resolve mais rápido do que cortar gasto.",
            "Ver o fluxo diário", Secao.CAIXA, 2
        )
        r.sobra < 0 -> AcaoMomento(
            "Você gasta ${brl(-r.sobra)} a mais do que ganha",
            "Nenhum plano se sustenta sobre um mês negativo. Comece pelas categorias acima da meta.",
            "Ver categorias", Secao.CATEGORIAS, 2
        )
        caras.isNotEmpty() -> {
            val pior = caras.maxBy { it.taxaMes }
            AcaoMomento(
                "Ataque ${pior.nome.ifBlank { "a dívida mais cara" }} primeiro",
                "A ${fmt1(pior.taxaMes)}% ao mês, cada real abatido rende o próprio juro — sem risco e sem imposto. " +
                    "Não existe investimento acessível que compita com isso.",
                "Abrir plano de quitação", Secao.DIVIDAS, 2
            )
        }
        r.mesesReserva < 3 -> AcaoMomento(
            "Forme 3 meses de reserva",
            "São ${brl(r.custoBase * 3)} em liquidez diária. É o que separa um imprevisto de uma dívida nova.",
            "Ver reserva", Secao.RESERVA, 1
        )
        r.mesesReserva < 6 -> AcaoMomento(
            "Complete a reserva até 6 meses",
            "Faltam ${brl(maxOf(0.0, r.custoBase * 6 - r.reservaAtual))}. Com a sobra atual de ${brl(r.sobra)}/mês, " +
                "isso leva ${if (r.sobra > 0) "${Math.ceil((r.custoBase * 6 - r.reservaAtual) / r.sobra).toInt()} meses" else "tempo indefinido"}.",
            "Ver reserva", Secao.RESERVA, 1
        )
        p.metas.isEmpty() && r.sobra > 0 -> AcaoMomento(
            "Dê destino à sobra de ${brl(r.sobra)}",
            "Reserva feita e caixa positivo. Sobra sem meta vira gasto — defina valor e prazo do próximo objetivo.",
            "Criar uma meta", Secao.METAS, 0
        )
        else -> AcaoMomento(
            "Situação sob controle",
            "Caixa positivo, reserva formada e metas definidas. O trabalho agora é manter o registro em dia e " +
                "revisar as premissas quando os juros mudarem.",
            "Ver metas", Secao.METAS, 0
        )
    }
}
