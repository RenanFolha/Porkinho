package br.com.trilha.data

import org.junit.Assert.*
import org.junit.Test

class MigracaoTest {

    @Test
    fun `perfil sem cartao e sem categorias nao quebra`() {
        val p = Perfil(
            contas = listOf(Conta(id = "cc", nome = "Conta", tipo = TipoConta.CORRENTE)),
            fixos = listOf(Fixo(nome = "Aluguel", valor = 1000.0, classe = Classe.ESSENCIAL))
        )
        val m = migrar(Banco(perfis = listOf(p))).ativo
        assertEquals("cc", m.fixos.single().contaId)
        assertEquals(1, m.contas.size)
        assertEquals("Aluguel", m.categoria(m.fixos.single().categoriaId)?.nome)
    }

    @Test
    fun `item legado no cartao cria o cartao que falta`() {
        val p = Perfil(
            contas = listOf(Conta(id = "cc", tipo = TipoConta.CORRENTE)),
            lancamentos = listOf(Lancamento(nome = "Cinema", valor = 50.0, data = "2026-10-01", forma = Forma.CARTAO))
        )
        val m = migrar(Banco(perfis = listOf(p))).ativo
        val conta = m.conta(m.lancamentos.single().contaId)
        assertTrue(conta!!.ehCartao)
        assertEquals(2, m.contas.size)
    }

    @Test
    fun `perfil so com cartao nao quebra`() {
        val p = Perfil(
            contas = listOf(Conta(id = "c", tipo = TipoConta.CARTAO)),
            rendas = listOf(Renda(valor = 100.0))
        )
        val m = migrar(Banco(perfis = listOf(p))).ativo
        val conta = m.conta(m.rendas.single().contaId)
        assertFalse(conta!!.ehCartao)
    }

    @Test
    fun `migrar duas vezes nao duplica`() {
        val p = Perfil(fixos = listOf(Fixo(nome = "Luz", valor = 100.0)))
        val uma = migrar(Banco(perfis = listOf(p)))
        val duas = migrar(uma)
        assertEquals(uma, duas)
    }

    @Test
    fun `banco novo nasce com contas e categorias`() {
        val m = migrar(Banco()).ativo
        assertTrue(m.contas.isNotEmpty())
        assertTrue(m.categorias.isNotEmpty())
    }
}
