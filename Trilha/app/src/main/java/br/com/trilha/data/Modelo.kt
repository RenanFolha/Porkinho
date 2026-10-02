package br.com.trilha.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun novoId(): String = UUID.randomUUID().toString().take(8)

enum class Classe(val rotulo: String) {
    ESSENCIAL("Essencial"), IMPORTANTE("Importante"), SUPERFLUO("Supérfluo")
}

enum class TipoConta(val rotulo: String) {
    CORRENTE("Conta / Pix / débito"), DINHEIRO("Dinheiro"), CARTAO("Cartão de crédito")
}

enum class TipoAtivo(val rotulo: String) {
    LIQUIDO("Conta / liquidez"), INVESTIMENTO("Investimento"), BEM("Bem de uso")
}

/** Mantido só para ler dados gravados antes das contas existirem. */
enum class Forma { CONTA, CARTAO }

/**
 * Meio de pagamento criado pelo usuário. Cartões têm fechamento e vencimento
 * próprios, então dois cartões diferentes produzem duas faturas diferentes.
 */
@Serializable
data class Conta(
    val id: String = novoId(),
    val nome: String = "",
    val tipo: TipoConta = TipoConta.CORRENTE,
    val diaFechamento: Int = 25,
    val diaVencimento: Int = 5
) {
    val ehCartao: Boolean get() = tipo == TipoConta.CARTAO
}

/** Categoria livre. O usuário cria as que quiser e define a classe de cada uma. */
@Serializable
data class Categoria(
    val id: String = novoId(),
    val nome: String = "",
    val classe: Classe = Classe.IMPORTANTE,
    val emoji: String = "",
    /** Teto mensal opcional definido pelo usuário. 0 = sem teto. */
    val teto: Double = 0.0
)

@Serializable
data class Renda(
    val id: String = novoId(),
    val nome: String = "",
    val valor: Double = 0.0,
    val dia: Int = 5,
    val contaId: String = ""
)

@Serializable
data class Fixo(
    val id: String = novoId(),
    val nome: String = "",
    val valor: Double = 0.0,
    /** Dia do vencimento / cobrança. */
    val dia: Int = 10,
    val categoriaId: String = "",
    val contaId: String = "",
    /** Meses "AAAA-MM" já marcados como pagos. */
    val pagos: List<String> = emptyList(),
    // legado
    val classe: Classe = Classe.ESSENCIAL,
    val forma: Forma = Forma.CONTA
)

@Serializable
data class Lancamento(
    val id: String = novoId(),
    val nome: String = "",
    val valor: Double = 0.0,
    /** Data da compra, ISO "AAAA-MM-DD". */
    val data: String = "",
    val categoriaId: String = "",
    val contaId: String = "",
    // legado
    val classe: Classe = Classe.IMPORTANTE,
    val forma: Forma = Forma.CONTA
)

@Serializable
data class Divida(
    val id: String = novoId(),
    val nome: String = "",
    val saldo: Double = 0.0,
    /** Juros em % ao mês. */
    val taxaMes: Double = 0.0,
    val parcela: Double = 0.0,
    val dia: Int = 10
)

@Serializable
data class Ativo(
    val id: String = novoId(),
    val nome: String = "",
    val valor: Double = 0.0,
    val tipo: TipoAtivo = TipoAtivo.LIQUIDO
)

@Serializable
data class Meta(
    val id: String = novoId(),
    val nome: String = "Nova meta",
    val valor: Double = 0.0,
    val prazoMeses: Int = 12,
    val jaTenho: Double = 0.0,
    val aporte: Double = 0.0
)

@Serializable
data class Fechamento(
    val id: String = novoId(),
    val ref: String = "",
    val renda: Double = 0.0,
    val gastos: Double = 0.0,
    val investido: Double = 0.0,
    val dividaAbatida: Double = 0.0,
    val patrimonio: Double = 0.0
)

@Serializable
data class Cartao(val fecha: Int = 25, val vence: Int = 5)

@Serializable
data class Premissas(
    val conservador: Double = 7.0,
    val base: Double = 11.5,
    val otimista: Double = 15.0,
    val inflacao: Double = 5.0
)

@Serializable
data class Perfil(
    val id: String = novoId(),
    val nome: String = "Meu orçamento",
    val contas: List<Conta> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val rendas: List<Renda> = emptyList(),
    val fixos: List<Fixo> = emptyList(),
    val lancamentos: List<Lancamento> = emptyList(),
    val dividas: List<Divida> = emptyList(),
    val ativos: List<Ativo> = emptyList(),
    val metas: List<Meta> = emptyList(),
    val fechamentos: List<Fechamento> = emptyList(),
    val saldos: Map<String, Double> = emptyMap(),
    val extraDivida: Double = 0.0,
    val cartao: Cartao = Cartao(),
    val premissas: Premissas = Premissas()
) {
    fun conta(id: String): Conta? = contas.firstOrNull { it.id == id }
    fun categoria(id: String): Categoria? = categorias.firstOrNull { it.id == id }

    fun classeDe(categoriaId: String, padrao: Classe): Classe = categoria(categoriaId)?.classe ?: padrao
    fun ehCartao(contaId: String, legado: Forma): Boolean =
        conta(contaId)?.ehCartao ?: (legado == Forma.CARTAO)

    val contaPadrao: Conta? get() = contas.firstOrNull { !it.ehCartao } ?: contas.firstOrNull()
}

@Serializable
data class Banco(
    val perfis: List<Perfil> = listOf(Perfil()),
    val ativoId: String = perfis.first().id,
    val locais: List<Local> = emptyList()
) {
    val ativo: Perfil get() = perfis.firstOrNull { it.id == ativoId } ?: perfis.first()
}

/* ===================== locais compartilhados ===================== */

@Serializable
data class Pessoa(
    val id: String = novoId(),
    val nome: String = ""
)

/** % que uma pessoa paga de um item específico. Lista vazia no item = dividir igualmente. */
@Serializable
data class Divisao(
    val pessoaId: String = "",
    val percentual: Double = 0.0
)

@Serializable
data class ContaCompartilhada(
    val id: String = novoId(),
    val nome: String = "",
    val valor: Double = 0.0,
    val dia: Int = 10,
    val divisao: List<Divisao> = emptyList()
)

@Serializable
data class DividaCompartilhada(
    val id: String = novoId(),
    val nome: String = "",
    val saldo: Double = 0.0,
    val taxaMes: Double = 0.0,
    val parcela: Double = 0.0,
    val dia: Int = 10,
    val divisao: List<Divisao> = emptyList()
)

/** Um lugar (ex.: a casa onde mora) com contas e dívidas divididas entre as pessoas que moram lá. */
@Serializable
data class Local(
    val id: String = novoId(),
    val nome: String = "Novo local",
    val pessoas: List<Pessoa> = emptyList(),
    val contas: List<ContaCompartilhada> = emptyList(),
    val dividas: List<DividaCompartilhada> = emptyList()
)

/* ===================== categorias e contas iniciais ===================== */

fun categoriasPadrao(): List<Categoria> = listOf(
    Categoria(nome = "Moradia", classe = Classe.ESSENCIAL, emoji = "🏠"),
    Categoria(nome = "Alimentação", classe = Classe.ESSENCIAL, emoji = "🛒"),
    Categoria(nome = "Transporte", classe = Classe.ESSENCIAL, emoji = "🚌"),
    Categoria(nome = "Saúde", classe = Classe.ESSENCIAL, emoji = "💊"),
    Categoria(nome = "Contas e serviços", classe = Classe.ESSENCIAL, emoji = "💡"),
    Categoria(nome = "Educação", classe = Classe.IMPORTANTE, emoji = "📚"),
    Categoria(nome = "Cuidados pessoais", classe = Classe.IMPORTANTE, emoji = "✂️"),
    Categoria(nome = "Lazer", classe = Classe.SUPERFLUO, emoji = "🎬"),
    Categoria(nome = "Assinaturas", classe = Classe.SUPERFLUO, emoji = "📺")
)

fun contasPadrao(cartao: Cartao = Cartao()): List<Conta> = listOf(
    Conta(nome = "Conta corrente", tipo = TipoConta.CORRENTE),
    Conta(nome = "Dinheiro", tipo = TipoConta.DINHEIRO),
    Conta(
        nome = "Cartão de crédito", tipo = TipoConta.CARTAO,
        diaFechamento = cartao.fecha, diaVencimento = cartao.vence
    )
)
