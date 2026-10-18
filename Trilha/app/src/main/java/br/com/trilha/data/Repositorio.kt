package br.com.trilha.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
    private val temporario = File(context.filesDir, "trilha.json.tmp")
    private val copia = File(context.filesDir, "trilha.json.bak")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Gravações em fila: a última chamada é sempre a última a chegar ao disco. */
    private val trava = Mutex()

    /**
     * Lê o arquivo principal e, se ele estiver ausente ou ilegível, a cópia da
     * gravação anterior. Se nada puder ser lido, o arquivo danificado é guardado
     * à parte antes de começar do zero — nunca é sobrescrito em silêncio.
     */
    suspend fun carregar(): Banco = trava.withLock {
        withContext(Dispatchers.IO) {
            val candidatos = listOf(arquivo, copia).filter { it.exists() }
            val lido = candidatos.firstNotNullOfOrNull { f ->
                runCatching { migrar(json.decodeFromString<Banco>(f.readText())) }.getOrNull()
            }
            if (lido == null) {
                candidatos.forEach { f ->
                    runCatching { f.renameTo(File(f.parentFile, "${f.name}.danificado-${System.currentTimeMillis()}")) }
                }
            }
            lido ?: migrar(Banco())
        }
    }

    /**
     * Grava em arquivo temporário e só então substitui o principal, mantendo a
     * versão anterior como cópia. Uma interrupção no meio da escrita deixa o
     * arquivo antigo intacto.
     */
    suspend fun salvar(banco: Banco) {
        val texto = json.encodeToString(banco)
        trava.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    temporario.writeText(texto)
                    if (arquivo.exists()) {
                        copia.delete()
                        arquivo.renameTo(copia)
                    }
                    if (!temporario.renameTo(arquivo)) temporario.copyTo(arquivo, overwrite = true)
                }
            }
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

    // O usuário pode ter apagado o cartão ou a conta corrente; nesse caso a
    // conta que faltar é criada só se algum item antigo precisar dela.
    val contas = p.contas.ifEmpty { contasPadrao(p.cartao) }.toMutableList()
    val idCorrente by lazy {
        contas.firstOrNull { !it.ehCartao }?.id
            ?: Conta(nome = "Conta corrente", tipo = TipoConta.CORRENTE).also { contas.add(it) }.id
    }
    val idCartao by lazy {
        contas.firstOrNull { it.ehCartao }?.id
            ?: Conta(
                nome = "Cartão de crédito", tipo = TipoConta.CARTAO,
                diaFechamento = p.cartao.fecha, diaVencimento = p.cartao.vence
            ).also { contas.add(it) }.id
    }

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
        contas = contas.toList(), categorias = categorias,
        rendas = rendas, fixos = fixos, lancamentos = lancamentos
    )
}
