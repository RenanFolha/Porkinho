package br.com.trilha.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.*
import br.com.trilha.domain.brl
import br.com.trilha.domain.resumoLocal
import kotlinx.coroutines.launch

@Composable
fun LocaisTela(vm: TrilhaViewModel, locais: List<Local>, padding: PaddingValues) {
    var criandoNome by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (locais.isEmpty()) {
            item {
                Bloco("Locais") {
                    ListaVazia("Crie um local para organizar contas e dívidas de um lugar onde você mora com outras pessoas, e convide quem mora com você por link.")
                }
            }
        }
        locais.forEach { local ->
            item(key = local.id) { BlocoLocal(vm, local) }
        }
        item {
            Bloco {
                CampoTexto("Nome do novo local", criandoNome, Modifier.fillMaxWidth()) { criandoNome = it }
                Spacer(Modifier.height(6.dp))
                BotaoAdicionar("+ adicionar local") {
                    vm.criarLocalNuvem(criandoNome)
                    criandoNome = ""
                }
            }
        }
    }
}

@Composable
private fun BlocoLocal(vm: TrilhaViewModel, local: Local) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    Bloco {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoTexto("Nome do local", local.nome, Modifier.weight(1f)) { vm.salvarLocalNuvem(local.copy(nome = it)) }
        }
        Spacer(Modifier.height(6.dp))
        BotaoAdicionar("convidar por link") {
            escopo.launch {
                val link = vm.gerarLinkConvite(local.id) ?: return@launch
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Entra no Local \"${local.nome}\" no Trilha: $link")
                }
                contexto.startActivity(Intent.createChooser(intent, "Convidar por"))
            }
        }

        TituloSecao("Pessoas")
        if (local.pessoas.isEmpty()) ListaVazia("Convide ou adicione quem mora aqui.")
        local.pessoas.forEach { p ->
            key(p.id) {
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    CampoTexto("Nome", p.nome, Modifier.weight(1f)) {
                        vm.salvarLocalNuvem(local.copy(pessoas = local.pessoas.map { x -> if (x.id == p.id) x.copy(nome = it) else x }))
                    }
                    BotaoRemover {
                        vm.salvarLocalNuvem(
                            local.copy(
                                pessoas = local.pessoas.filterNot { it.id == p.id },
                                membros = local.membros.filterNot { it == p.id },
                                contas = local.contas.map { it.copy(divisao = it.divisao.filterNot { d -> d.pessoaId == p.id }) },
                                dividas = local.dividas.map { it.copy(divisao = it.divisao.filterNot { d -> d.pessoaId == p.id }) }
                            )
                        )
                    }
                }
            }
        }
        BotaoAdicionar("+ pessoa (sem app)") { vm.salvarLocalNuvem(local.copy(pessoas = local.pessoas + Pessoa())) }

        TituloSecao("Contas compartilhadas")
        if (local.contas.isEmpty()) ListaVazia("Nada aqui ainda.")
        local.contas.forEach { c ->
            key(c.id) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoTexto("Conta", c.nome, Modifier.weight(1f)) {
                            vm.salvarLocalNuvem(local.copy(contas = local.contas.map { x -> if (x.id == c.id) x.copy(nome = it) else x }))
                        }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("R$", c.valor, c.id, Modifier.width(94.dp)) {
                            vm.salvarLocalNuvem(local.copy(contas = local.contas.map { x -> if (x.id == c.id) x.copy(valor = it) else x }))
                        }
                        Spacer(Modifier.width(6.dp))
                        CampoDia("Dia", c.dia, c.id, Modifier.width(64.dp)) {
                            vm.salvarLocalNuvem(local.copy(contas = local.contas.map { x -> if (x.id == c.id) x.copy(dia = it) else x }))
                        }
                        BotaoRemover { vm.salvarLocalNuvem(local.copy(contas = local.contas.filterNot { it.id == c.id })) }
                    }
                    EditorDivisao(local, c.divisao) { nova ->
                        vm.salvarLocalNuvem(local.copy(contas = local.contas.map { x -> if (x.id == c.id) x.copy(divisao = nova) else x }))
                    }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }
        BotaoAdicionar("+ conta compartilhada") { vm.salvarLocalNuvem(local.copy(contas = local.contas + ContaCompartilhada())) }

        TituloSecao("Dívidas compartilhadas")
        if (local.dividas.isEmpty()) ListaVazia("Nada aqui ainda.")
        local.dividas.forEach { d ->
            key(d.id) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoTexto("Dívida", d.nome, Modifier.weight(1f)) {
                            vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(nome = it) else x }))
                        }
                        BotaoRemover { vm.salvarLocalNuvem(local.copy(dividas = local.dividas.filterNot { it.id == d.id })) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoNumero("Saldo", d.saldo, d.id, Modifier.weight(1f)) {
                            vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(saldo = it) else x }))
                        }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("% a.m.", d.taxaMes, "${d.id}-tx", Modifier.weight(1f)) {
                            vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(taxaMes = it) else x }))
                        }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("Parcela", d.parcela, "${d.id}-pc", Modifier.weight(1f)) {
                            vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(parcela = it) else x }))
                        }
                        Spacer(Modifier.width(6.dp))
                        CampoDia("Dia", d.dia, d.id, Modifier.width(64.dp)) {
                            vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(dia = it) else x }))
                        }
                    }
                    EditorDivisao(local, d.divisao) { nova ->
                        vm.salvarLocalNuvem(local.copy(dividas = local.dividas.map { x -> if (x.id == d.id) x.copy(divisao = nova) else x }))
                    }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }
        BotaoAdicionar("+ dívida compartilhada") { vm.salvarLocalNuvem(local.copy(dividas = local.dividas + DividaCompartilhada())) }

        if (local.pessoas.isNotEmpty()) {
            TituloSecao("Resumo por pessoa")
            val r = resumoLocal(local)
            local.pessoas.forEach { p ->
                key(p.id) {
                    LinhaValor(
                        p.nome.ifBlank { "Sem nome" },
                        brl(r.porPessoaMensal[p.id] ?: 0.0),
                        detalhe = "dívida: ${brl(r.porPessoaDivida[p.id] ?: 0.0)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorDivisao(local: Local, divisao: List<Divisao>, onDivisao: (List<Divisao>) -> Unit) {
    var expandido by remember(local.id) { mutableStateOf(divisao.isNotEmpty()) }

    if (!expandido) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dividido igualmente entre ${local.pessoas.size} pessoas",
                fontSize = 12.sp, color = Tinta2, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                if (divisao.isEmpty() && local.pessoas.isNotEmpty()) {
                    onDivisao(local.pessoas.map { Divisao(it.id, 100.0 / local.pessoas.size) })
                }
                expandido = true
            }) { Text("ajustar %") }
        }
    } else {
        Column {
            local.pessoas.forEach { p ->
                key(p.id) {
                    val atual = divisao.firstOrNull { it.pessoaId == p.id }?.percentual ?: 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.nome.ifBlank { "Sem nome" }, fontSize = 12.sp, color = Tinta, modifier = Modifier.weight(1f))
                        CampoNumero("%", atual, "${p.id}-div", Modifier.width(80.dp)) { novo ->
                            val resto = divisao.filterNot { it.pessoaId == p.id }
                            onDivisao(resto + Divisao(p.id, novo))
                        }
                    }
                }
            }
            TextButton(onClick = { onDivisao(emptyList()); expandido = false }) { Text("usar partes iguais") }
        }
    }
}
