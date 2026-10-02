package br.com.trilha.domain

import br.com.trilha.data.Divisao
import br.com.trilha.data.Local

data class ResumoLocal(
    val totalMensal: Double,
    val totalDividas: Double,
    val porPessoaMensal: Map<String, Double>,
    val porPessoaDivida: Map<String, Double>
)

private fun distribuir(valor: Double, divisao: List<Divisao>, ids: List<String>, acc: MutableMap<String, Double>) {
    if (valor == 0.0 || ids.isEmpty()) return
    if (divisao.isEmpty()) {
        val fatia = valor / ids.size
        ids.forEach { acc[it] = (acc[it] ?: 0.0) + fatia }
    } else {
        divisao.forEach { d -> acc[d.pessoaId] = (acc[d.pessoaId] ?: 0.0) + valor * d.percentual / 100.0 }
    }
}

/** Quanto cada pessoa do local paga por mês (contas + parcelas) e deve no total (saldo das dívidas). */
fun resumoLocal(local: Local): ResumoLocal {
    val ids = local.pessoas.map { it.id }
    val mensal = HashMap<String, Double>()
    local.contas.forEach { distribuir(it.valor, it.divisao, ids, mensal) }
    local.dividas.forEach { distribuir(it.parcela, it.divisao, ids, mensal) }
    val divida = HashMap<String, Double>()
    local.dividas.forEach { distribuir(it.saldo, it.divisao, ids, divida) }
    return ResumoLocal(mensal.values.sum(), divida.values.sum(), mensal, divida)
}
