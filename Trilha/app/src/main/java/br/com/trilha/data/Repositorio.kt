package br.com.trilha.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Persistência local em arquivo JSON no diretório privado do app.
 * O volume é pequeno (um documento por aparelho), então um banco relacional
 * traria complexidade sem ganho. Se o histórico crescer a ponto de exigir
 * consulta por período, o caminho é migrar esta classe para Room — o pacote
 * `domain` não precisa mudar.
 */
class Repositorio(context: Context) {

    private val pasta = context.filesDir
    private val arquivo = AtomicFile(File(pasta, "trilha.json"))
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Arquivo ilegível não é tratado como "sem dados": antes de seguir com um banco
     * vazio, uma cópia é guardada ao lado, senão a próxima gravação a apagaria.
     */
    suspend fun carregar(): Carga = withContext(Dispatchers.IO) {
        val lido = try {
            json.decodeFromString<Banco>(arquivo.readFully().decodeToString())
        } catch (e: FileNotFoundException) {
            Banco()
        } catch (e: Exception) {
            runCatching {
                arquivo.baseFile.copyTo(File(pasta, "trilha-corrompido-${System.currentTimeMillis()}.json"))
            }
            return@withContext Carga(migrar(Banco()), corrompido = true)
        }
        Carga(migrar(lido), corrompido = false)
    }

    suspend fun salvar(banco: Banco): Boolean = withContext(Dispatchers.IO) { gravar(banco) }

    /**
     * Grava num arquivo novo e só então troca pelo antigo, então uma interrupção no meio
     * nunca deixa o JSON pela metade. Bloqueante: chame fora da main, exceto no flush final.
     */
    fun gravar(banco: Banco): Boolean = synchronized(trava) {
        val saida = try { arquivo.startWrite() } catch (e: IOException) { return false }
        try {
            saida.write(json.encodeToString(banco).encodeToByteArray())
            arquivo.finishWrite(saida)
            true
        } catch (e: Exception) {
            arquivo.failWrite(saida)
            false
        }
    }

    fun exportarJson(banco: Banco): String = json.encodeToString(banco)

    suspend fun importarJson(texto: String): Banco? = withContext(Dispatchers.IO) {
        runCatching { migrar(json.decodeFromString<Banco>(texto)) }.getOrNull()
    }

    private companion object {
        /** Um só trilha.json por processo, mesmo que exista mais de um Repositorio. */
        val trava = Any()
    }
}

class Carga(val banco: Banco, val corrompido: Boolean)

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
