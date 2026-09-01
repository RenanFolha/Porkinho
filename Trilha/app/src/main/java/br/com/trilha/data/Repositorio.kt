package br.com.trilha.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistência local em arquivo JSON no diretório privado do app.
 * O volume é pequeno (um documento por aparelho), então um banco relacional
 * traria complexidade sem ganho. Se o histórico crescer a ponto de exigir
 * consulta por período, o caminho é migrar esta classe para Room — o pacote
 * `domain` não precisa mudar.
 */
class Repositorio(context: Context) {

    private val arquivo = File(context.filesDir, "trilha.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun carregar(): Banco = withContext(Dispatchers.IO) {
        val lido = runCatching {
            if (arquivo.exists()) json.decodeFromString<Banco>(arquivo.readText()) else Banco()
        }.getOrElse { Banco() }
        migrar(lido)
    }

    suspend fun salvar(banco: Banco) {
        withContext(Dispatchers.IO) {
            runCatching { arquivo.writeText(json.encodeToString(banco)) }
        }
    }

    fun exportarJson(banco: Banco): String = json.encodeToString(banco)

    suspend fun importarJson(texto: String): Banco? = withContext(Dispatchers.IO) {
        runCatching { migrar(json.decodeFromString<Banco>(texto)) }.getOrNull()
    }
}

/**
 * Traz dados antigos para o formato com contas e categorias.
 * Antes existiam apenas `forma` (conta ou cartão) e `classe` solta em cada
 * gasto; agora cada gasto aponta para uma conta e uma categoria criadas pelo
 * usuário. A conversão é feita uma vez, sem perder nada.
 */
fun migrar(banco: Banco): Banco = banco.copy(perfis = banco.perfis.map { migrarPerfil(it) })

private fun migrarPerfil(p: Perfil): Perfil {
    if (p.contas.isNotEmpty() && p.categorias.isNotEmpty()) return p

    val contas = p.contas.ifEmpty { contasPadrao(p.cartao) }
    val idCorrente = contas.first { !it.ehCartao }.id
    val idCartao = contas.first { it.ehCartao }.id

    // Cada nome de gasto já usado vira uma categoria, preservando a classe.
    val existentes = p.categorias.toMutableList()
    fun garantirCategoria(nome: String, classe: Classe): String {
        val limpo = nome.trim().ifBlank { "Sem categoria" }
        existentes.firstOrNull { it.nome.equals(limpo, ignoreCase = true) }?.let { return it.id }
        val nova = Categoria(nome = limpo, classe = classe)
        existentes.add(nova)
        return nova.id
    }

    val fixos = p.fixos.map {
        it.copy(
            categoriaId = it.categoriaId.ifBlank { garantirCategoria(it.nome, it.classe) },
            contaId = it.contaId.ifBlank { if (it.forma == Forma.CARTAO) idCartao else idCorrente }
        )
    }
    val lancamentos = p.lancamentos.map {
        it.copy(
            categoriaId = it.categoriaId.ifBlank { garantirCategoria(it.nome, it.classe) },
            contaId = it.contaId.ifBlank { if (it.forma == Forma.CARTAO) idCartao else idCorrente }
        )
    }
    val rendas = p.rendas.map { it.copy(contaId = it.contaId.ifBlank { idCorrente }) }

    val categorias = if (existentes.isEmpty()) categoriasPadrao() else existentes

    return p.copy(
        contas = contas, categorias = categorias,
        rendas = rendas, fixos = fixos, lancamentos = lancamentos
    )
}
