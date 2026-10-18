package br.com.trilha.domain

import br.com.trilha.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinancasTest {

    private val corrente = Conta(id = "cc", nome = "Conta", tipo = TipoConta.CORRENTE)
    private val cartao = Conta(id = "cartao", nome = "Cartão", tipo = TipoConta.CARTAO, diaFechamento = 25, diaVencimento = 5)
    private val essencial = Categoria(id = "ess", nome = "Moradia", classe = Classe.ESSENCIAL)

    private fun perfil(
        rendas: List<Renda> = emptyList(),
        fixos: List<Fixo> = emptyList(),
        lancamentos: List<Lancamento> = emptyList(),
        dividas: List<Divida> = emptyList()
    ) = Perfil(
        contas = listOf(corrente, cartao), categorias = listOf(essencial),
        rendas = rendas, fixos = fixos, lancamentos = lancamentos, dividas = dividas
    )

    @Test
    fun `compra apos o fechamento com vencimento antes do fechamento cai dois meses depois`() {
        assertEquals(LocalDate.of(2026, 12, 5), vencimentoFatura(LocalDate.of(2026, 10, 28), 25, 5))
        assertEquals(LocalDate.of(2026, 11, 5), vencimentoFatura(LocalDate.of(2026, 10, 20), 25, 5))
    }

    @Test
    fun `fixo no cartao depois do fechamento entra em toda fatura`() {
        val p = perfil(fixos = listOf(Fixo(nome = "Seguro", valor = 90.0, dia = 28, categoriaId = "ess", contaId = "cartao")))
        listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2027, 1)).forEach { ym ->
            val f = fluxoDiario(p, ym)
            assertEquals("fatura de $ym", 90.0, f.totalFaturas, 0.001)
            assertEquals(1, f.faturas.single().itens)
        }
    }

    @Test
    fun `fixo no cartao nao conta meses antes de existir`() {
        val p = perfil(fixos = listOf(Fixo(valor = 90.0, dia = 28, contaId = "cartao", desde = "2026-10")))
        // Gasto de 28/10 só é cobrado em 05/12.
        assertEquals(0.0, fluxoDiario(p, YearMonth.of(2026, 11)).totalFaturas, 0.001)
        assertEquals(90.0, fluxoDiario(p, YearMonth.of(2026, 12)).totalFaturas, 0.001)
    }

    @Test
    fun `lancamento com data malformada e ignorado sem quebrar`() {
        val p = perfil(
            lancamentos = listOf(
                Lancamento(valor = 10.0, data = "2026-10-xx", contaId = "cc"),
                Lancamento(valor = 10.0, data = "lixo", contaId = "cartao"),
                Lancamento(valor = 25.0, data = "2026-10-03", contaId = "cc")
            )
        )
        val f = fluxoDiario(p, YearMonth.of(2026, 10))
        assertEquals(25.0, f.saidas, 0.001)
        assertNotNull(f.diaDe(3))
    }

    @Test
    fun `renda so aparece a partir do mes em que foi criada`() {
        val p = perfil(rendas = listOf(Renda(valor = 1000.0, dia = 5, contaId = "cc", desde = "2026-10")))
        assertEquals(0.0, fluxoDiario(p, YearMonth.of(2026, 9)).entradas, 0.001)
        assertEquals(1000.0, fluxoDiario(p, YearMonth.of(2026, 10)).entradas, 0.001)
    }

    @Test
    fun `parcela some do fluxo depois da quitacao projetada`() {
        val hoje = YearMonth.of(2026, 10)
        val p = perfil(dividas = listOf(Divida(saldo = 1000.0, taxaMes = 0.0, parcela = 500.0, dia = 10)))
        assertEquals(500.0, fluxoDiario(p, YearMonth.of(2026, 9), hoje).saidas, 0.001)
        assertEquals(500.0, fluxoDiario(p, YearMonth.of(2026, 10), hoje).saidas, 0.001)
        assertEquals(500.0, fluxoDiario(p, YearMonth.of(2026, 11), hoje).saidas, 0.001)
        assertEquals(0.0, fluxoDiario(p, YearMonth.of(2026, 12), hoje).saidas, 0.001)
    }

    @Test
    fun `parcela que nao cobre os juros continua indefinidamente`() {
        val d = Divida(saldo = 10000.0, taxaMes = 10.0, parcela = 500.0)
        assertNull(mesesRestantes(d))
        val p = perfil(dividas = listOf(d))
        assertEquals(500.0, fluxoDiario(p, YearMonth.of(2030, 1), YearMonth.of(2026, 10)).saidas, 0.001)
    }

    @Test
    fun `sem gastos essenciais o app nao pede reserva de zero reais`() {
        val p = perfil(rendas = listOf(Renda(valor = 3000.0, contaId = "cc")))
        val r = resumo(p)
        val acao = acaoDoMomento(p, r, fluxoDiario(p, YearMonth.of(2026, 10)))
        assertEquals("Cadastre seus gastos essenciais", acao.titulo)
        assertFalse(diagnostico(p, r, fluxoDiario(p, YearMonth.of(2026, 10))).prioridades.first().contains("R$ 0"))
    }

    @Test
    fun `menor saldo e um dia real do mes`() {
        val p = perfil(rendas = listOf(Renda(valor = 1000.0, dia = 5, contaId = "cc")))
        val f = fluxoDiario(p, YearMonth.of(2026, 10))
        assertEquals(1, f.diaMenorSaldo)
    }
}
