package br.com.trilha.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.trilha.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class TrilhaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    private val _banco = MutableStateFlow(Banco())
    val banco: StateFlow<Banco> = _banco.asStateFlow()

    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    val perfil: Perfil get() = _banco.value.ativo

    init {
        viewModelScope.launch { _banco.value = repo.carregar() }
    }

    private fun persistir(novo: Banco) {
        _banco.value = novo
        viewModelScope.launch { repo.salvar(novo) }
    }

    /** Aplica uma transformação no perfil ativo e salva. */
    fun editar(bloco: (Perfil) -> Perfil) {
        val b = _banco.value
        persistir(b.copy(perfis = b.perfis.map { if (it.id == b.ativoId) bloco(it) else it }))
    }

    fun avisar(msg: String?) { _aviso.value = msg }

    /* ---------- mês em foco ---------- */
    fun mesAnterior() { _mes.value = _mes.value.minusMonths(1) }
    fun mesSeguinte() { _mes.value = _mes.value.plusMonths(1) }

    /* ---------- perfis ---------- */
    fun criarPerfil(nome: String) {
        val novo = Perfil(
            nome = nome.ifBlank { "Novo perfil" },
            contas = contasPadrao(), categorias = categoriasPadrao()
        )
        val b = _banco.value
        persistir(b.copy(perfis = b.perfis + novo, ativoId = novo.id))
        avisar("Perfil criado")
    }

    fun trocarPerfil(id: String) {
        persistir(_banco.value.copy(ativoId = id))
    }

    fun renomearPerfil(id: String, nome: String) {
        val b = _banco.value
        persistir(b.copy(perfis = b.perfis.map { if (it.id == id) it.copy(nome = nome) else it }))
    }

    fun apagarPerfil(id: String) {
        val b = _banco.value
        if (b.perfis.size <= 1) { avisar("É preciso ter ao menos um perfil"); return }
        val restantes = b.perfis.filterNot { it.id == id }
        persistir(b.copy(perfis = restantes, ativoId = if (b.ativoId == id) restantes.first().id else b.ativoId))
    }

    /* ---------- renda ---------- */
    fun addRenda() = editar { it.copy(rendas = it.rendas + Renda()) }
    fun setRenda(r: Renda) = editar { p -> p.copy(rendas = p.rendas.map { if (it.id == r.id) r else it }) }
    fun delRenda(id: String) = editar { p -> p.copy(rendas = p.rendas.filterNot { it.id == id }) }

    /* ---------- gastos fixos ---------- */
    fun addFixo() = editar { it.copy(fixos = it.fixos + Fixo()) }
    fun setFixo(f: Fixo) = editar { p -> p.copy(fixos = p.fixos.map { if (it.id == f.id) f else it }) }
    fun delFixo(id: String) = editar { p -> p.copy(fixos = p.fixos.filterNot { it.id == id }) }

    fun marcarPago(id: String, ref: String, pago: Boolean) = editar { p ->
        p.copy(fixos = p.fixos.map {
            if (it.id != id) it
            else it.copy(pagos = if (pago) (it.pagos + ref).distinct() else it.pagos - ref)
        })
    }

    /* ---------- lançamentos ---------- */
    fun addLancamento(l: Lancamento) = editar { it.copy(lancamentos = it.lancamentos + l) }
    fun delLancamento(id: String) = editar { p -> p.copy(lancamentos = p.lancamentos.filterNot { it.id == id }) }

    /* ---------- dívidas ---------- */
    fun addDivida() = editar { it.copy(dividas = it.dividas + Divida()) }
    fun setDivida(d: Divida) = editar { p -> p.copy(dividas = p.dividas.map { if (it.id == d.id) d else it }) }
    fun delDivida(id: String) = editar { p -> p.copy(dividas = p.dividas.filterNot { it.id == id }) }
    fun setExtraDivida(v: Double) = editar { it.copy(extraDivida = v) }

    /* ---------- ativos ---------- */
    fun addAtivo() = editar { it.copy(ativos = it.ativos + Ativo()) }
    fun setAtivo(a: Ativo) = editar { p -> p.copy(ativos = p.ativos.map { if (it.id == a.id) a else it }) }
    fun delAtivo(id: String) = editar { p -> p.copy(ativos = p.ativos.filterNot { it.id == id }) }

    /* ---------- metas ---------- */
    fun addMeta() = editar { it.copy(metas = it.metas + Meta()) }
    fun setMeta(m: Meta) = editar { p -> p.copy(metas = p.metas.map { if (it.id == m.id) m else it }) }
    fun delMeta(id: String) = editar { p -> p.copy(metas = p.metas.filterNot { it.id == id }) }


    /* ---------- categorias livres ---------- */
    fun addCategoria(nome: String, classe: Classe, emoji: String = "") = editar {
        it.copy(categorias = it.categorias + Categoria(nome = nome.ifBlank { "Nova categoria" }, classe = classe, emoji = emoji))
    }
    fun setCategoria(c: Categoria) = editar { p -> p.copy(categorias = p.categorias.map { if (it.id == c.id) c else it }) }

    /**
     * Apagar categoria reatribui os gastos em vez de perdê-los. Sem destino
     * informado, eles ficam sem categoria e reaparecem para reclassificação.
     */
    fun delCategoria(id: String, destinoId: String = "") = editar { p ->
        p.copy(
            categorias = p.categorias.filterNot { it.id == id },
            fixos = p.fixos.map { if (it.categoriaId == id) it.copy(categoriaId = destinoId) else it },
            lancamentos = p.lancamentos.map { if (it.categoriaId == id) it.copy(categoriaId = destinoId) else it }
        )
    }

    /* ---------- contas e cartões ---------- */
    fun addConta(nome: String, tipo: TipoConta) = editar {
        it.copy(contas = it.contas + Conta(nome = nome.ifBlank { "Nova conta" }, tipo = tipo))
    }
    fun setConta(c: Conta) = editar { p -> p.copy(contas = p.contas.map { if (it.id == c.id) c else it }) }

    fun delConta(id: String) = editar { p ->
        if (p.contas.size <= 1) return@editar p
        val destino = p.contas.firstOrNull { it.id != id }?.id ?: ""
        p.copy(
            contas = p.contas.filterNot { it.id == id },
            rendas = p.rendas.map { if (it.contaId == id) it.copy(contaId = destino) else it },
            fixos = p.fixos.map { if (it.contaId == id) it.copy(contaId = destino) else it },
            lancamentos = p.lancamentos.map { if (it.contaId == id) it.copy(contaId = destino) else it }
        )
    }

    /** Garante que um perfil novo já nasça utilizável. */
    fun semearSeVazio() {
        val p = perfil
        if (p.contas.isEmpty() || p.categorias.isEmpty()) {
            editar {
                it.copy(
                    contas = it.contas.ifEmpty { contasPadrao() },
                    categorias = it.categorias.ifEmpty { categoriasPadrao() }
                )
            }
        }
    }

    /* ---------- configuração ---------- */
    fun setCartao(c: Cartao) = editar { it.copy(cartao = c) }
    fun setPremissas(pr: Premissas) = editar { it.copy(premissas = pr) }
    fun setSaldoInicial(ref: String, v: Double) = editar { it.copy(saldos = it.saldos + (ref to v)) }

    /* ---------- fechamento ---------- */
    fun fecharMes(f: Fechamento) = editar { p ->
        p.copy(fechamentos = p.fechamentos.filterNot { it.ref == f.ref } + f)
    }
    fun delFechamento(id: String) = editar { p ->
        p.copy(fechamentos = p.fechamentos.filterNot { it.id == id })
    }

    /* ---------- backup ---------- */
    fun exportar(): String = repo.exportarJson(_banco.value)

    fun importar(texto: String) {
        viewModelScope.launch {
            val b = repo.importarJson(texto)
            if (b == null) avisar("Arquivo inválido")
            else { persistir(b); avisar("Backup importado") }
        }
    }

    fun apagarTudo() {
        persistir(Banco())
        avisar("Tudo apagado")
    }

    /* ---------- locais compartilhados ---------- */
    fun addLocal() = persistir(_banco.value.let { it.copy(locais = it.locais + Local()) })
    fun setLocal(l: Local) = persistir(_banco.value.let { b -> b.copy(locais = b.locais.map { if (it.id == l.id) l else it }) })
    fun delLocal(id: String) = persistir(_banco.value.let { b -> b.copy(locais = b.locais.filterNot { it.id == id }) })

    private fun editarLocal(localId: String, bloco: (Local) -> Local) {
        val b = _banco.value
        persistir(b.copy(locais = b.locais.map { if (it.id == localId) bloco(it) else it }))
    }

    fun addPessoa(localId: String) = editarLocal(localId) { it.copy(pessoas = it.pessoas + Pessoa()) }
    fun setPessoa(localId: String, p: Pessoa) = editarLocal(localId) { l -> l.copy(pessoas = l.pessoas.map { if (it.id == p.id) p else it }) }

    /** Remover pessoa também limpa as divisões dela nos itens já personalizados. */
    fun delPessoa(localId: String, id: String) = editarLocal(localId) { l ->
        l.copy(
            pessoas = l.pessoas.filterNot { it.id == id },
            contas = l.contas.map { it.copy(divisao = it.divisao.filterNot { d -> d.pessoaId == id }) },
            dividas = l.dividas.map { it.copy(divisao = it.divisao.filterNot { d -> d.pessoaId == id }) }
        )
    }

    fun addContaLocal(localId: String) = editarLocal(localId) { it.copy(contas = it.contas + ContaCompartilhada()) }
    fun setContaLocal(localId: String, c: ContaCompartilhada) = editarLocal(localId) { l -> l.copy(contas = l.contas.map { if (it.id == c.id) c else it }) }
    fun delContaLocal(localId: String, id: String) = editarLocal(localId) { l -> l.copy(contas = l.contas.filterNot { it.id == id }) }

    fun addDividaLocal(localId: String) = editarLocal(localId) { it.copy(dividas = it.dividas + DividaCompartilhada()) }
    fun setDividaLocal(localId: String, d: DividaCompartilhada) = editarLocal(localId) { l -> l.copy(dividas = l.dividas.map { if (it.id == d.id) d else it }) }
    fun delDividaLocal(localId: String, id: String) = editarLocal(localId) { l -> l.copy(dividas = l.dividas.filterNot { it.id == id }) }

    /** Preenche o perfil ativo com dados de demonstração. */
    fun carregarExemplo() {
        val ym = _mes.value
        fun dia(d: Int) = ym.atDay(minOf(d, ym.lengthOfMonth())).toString()

        val corrente = Conta(nome = "Conta corrente", tipo = TipoConta.CORRENTE)
        val dinheiro = Conta(nome = "Dinheiro", tipo = TipoConta.DINHEIRO)
        val cartao = Conta(nome = "Cartão principal", tipo = TipoConta.CARTAO, diaFechamento = 25, diaVencimento = 5)

        val moradia = Categoria(nome = "Moradia", classe = Classe.ESSENCIAL, emoji = "🏠")
        val comida = Categoria(nome = "Alimentação", classe = Classe.ESSENCIAL, emoji = "🛒")
        val transporte = Categoria(nome = "Transporte", classe = Classe.ESSENCIAL, emoji = "🚌")
        val contas = Categoria(nome = "Contas e serviços", classe = Classe.ESSENCIAL, emoji = "💡")
        val saude = Categoria(nome = "Saúde", classe = Classe.ESSENCIAL, emoji = "💊")
        val academia = Categoria(nome = "Cuidados pessoais", classe = Classe.IMPORTANTE, emoji = "🏋️")
        val lazer = Categoria(nome = "Lazer", classe = Classe.SUPERFLUO, emoji = "🎬")
        val assinaturas = Categoria(nome = "Assinaturas", classe = Classe.SUPERFLUO, emoji = "📺")

        editar {
            it.copy(
                nome = if (it.nome == "Meu orçamento") "Exemplo" else it.nome,
                contas = listOf(corrente, dinheiro, cartao),
                categorias = listOf(moradia, comida, transporte, contas, saude, academia, lazer, assinaturas),
                rendas = listOf(
                    Renda(nome = "Salário líquido", valor = 4200.0, dia = 5, contaId = corrente.id),
                    Renda(nome = "Freelas", valor = 600.0, dia = 20, contaId = corrente.id)
                ),
                fixos = listOf(
                    Fixo(nome = "Aluguel", valor = 1400.0, dia = 10, categoriaId = moradia.id, contaId = corrente.id),
                    Fixo(nome = "Luz e água", valor = 280.0, dia = 15, categoriaId = contas.id, contaId = corrente.id),
                    Fixo(nome = "Internet", valor = 110.0, dia = 8, categoriaId = contas.id, contaId = corrente.id),
                    Fixo(nome = "Academia", valor = 120.0, dia = 5, categoriaId = academia.id, contaId = cartao.id),
                    Fixo(nome = "Streaming", valor = 180.0, dia = 12, categoriaId = assinaturas.id, contaId = cartao.id)
                ),
                lancamentos = listOf(
                    Lancamento(nome = "Mercado", valor = 640.0, data = dia(6), categoriaId = comida.id, contaId = corrente.id),
                    Lancamento(nome = "Ônibus e app", valor = 320.0, data = dia(3), categoriaId = transporte.id, contaId = dinheiro.id),
                    Lancamento(nome = "Cinema", valor = 180.0, data = dia(14), categoriaId = lazer.id, contaId = cartao.id),
                    Lancamento(nome = "Farmácia", valor = 95.0, data = dia(27), categoriaId = saude.id, contaId = cartao.id)
                ),
                dividas = listOf(
                    Divida(nome = "Cartão parcelado", saldo = 3800.0, taxaMes = 12.0, parcela = 600.0, dia = 5),
                    Divida(nome = "Empréstimo pessoal", saldo = 6200.0, taxaMes = 3.2, parcela = 520.0, dia = 18)
                ),
                ativos = listOf(
                    Ativo(nome = "Conta corrente", valor = 900.0, tipo = TipoAtivo.LIQUIDO),
                    Ativo(nome = "Tesouro Selic", valor = 2100.0, tipo = TipoAtivo.INVESTIMENTO),
                    Ativo(nome = "Moto", valor = 9000.0, tipo = TipoAtivo.BEM)
                ),
                metas = listOf(
                    Meta(nome = "Reserva de emergência", valor = 18000.0, prazoMeses = 18, jaTenho = 3000.0, aporte = 600.0)
                ),
                saldos = it.saldos + (ym.toString() to 1200.0),
                extraDivida = 200.0
            )
        }
        avisar("Exemplo preenchido")
    }

    fun hojeIso(): String = LocalDate.now().toString()
}
