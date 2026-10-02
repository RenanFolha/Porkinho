package br.com.trilha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.*
import br.com.trilha.domain.brl
import br.com.trilha.domain.resumoLocal

@Composable
fun LocaisTela(vm: TrilhaViewModel, banco: Banco, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (banco.locais.isEmpty()) {
            item {
                Bloco("Locais") {
                    ListaVazia("Crie um local para organizar contas e dívidas de um lugar onde você mora com outras pessoas.")
                }
            }
        }
        banco.locais.forEach { local ->
            item(key = local.id) { BlocoLocal(vm, local) }
        }
        item {
            Bloco {
                BotaoAdicionar("+ adicionar local") { vm.addLocal() }
            }
        }
    }
}

@Composable
private fun BlocoLocal(vm: TrilhaViewModel, local: Local) {
    Bloco {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoTexto("Nome do local", local.nome, Modifier.weight(1f)) { vm.setLocal(local.copy(nome = it)) }
            BotaoRemover { vm.delLocal(local.id) }
        }

        TituloSecao("Pessoas")
        if (local.pessoas.isEmpty()) ListaVazia("Adicione quem mora aqui.")
        local.pessoas.forEach { p ->
            key(p.id) {
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    CampoTexto("Nome", p.nome, Modifier.weight(1f)) { vm.setPessoa(local.id, p.copy(nome = it)) }
                    BotaoRemover { vm.delPessoa(local.id, p.id) }
                }
            }
        }
        BotaoAdicionar("+ pessoa") { vm.addPessoa(local.id) }

        TituloSecao("Contas compartilhadas")
        if (local.contas.isEmpty()) ListaVazia("Nada aqui ainda.")
        local.contas.forEach { c ->
            key(c.id) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoTexto("Conta", c.nome, Modifier.weight(1f)) { vm.setContaLocal(local.id, c.copy(nome = it)) }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("R$", c.valor, c.id, Modifier.width(94.dp)) { vm.setContaLocal(local.id, c.copy(valor = it)) }
                        Spacer(Modifier.width(6.dp))
                        CampoDia("Dia", c.dia, c.id, Modifier.width(64.dp)) { vm.setContaLocal(local.id, c.copy(dia = it)) }
                        BotaoRemover { vm.delContaLocal(local.id, c.id) }
                    }
                    EditorDivisao(local, c.divisao) { vm.setContaLocal(local.id, c.copy(divisao = it)) }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }
        BotaoAdicionar("+ conta compartilhada") { vm.addContaLocal(local.id) }

        TituloSecao("Dívidas compartilhadas")
        if (local.dividas.isEmpty()) ListaVazia("Nada aqui ainda.")
        local.dividas.forEach { d ->
            key(d.id) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoTexto("Dívida", d.nome, Modifier.weight(1f)) { vm.setDividaLocal(local.id, d.copy(nome = it)) }
                        BotaoRemover { vm.delDividaLocal(local.id, d.id) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampoNumero("Saldo", d.saldo, d.id, Modifier.weight(1f)) { vm.setDividaLocal(local.id, d.copy(saldo = it)) }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("% a.m.", d.taxaMes, "${d.id}-tx", Modifier.weight(1f)) { vm.setDividaLocal(local.id, d.copy(taxaMes = it)) }
                        Spacer(Modifier.width(6.dp))
                        CampoNumero("Parcela", d.parcela, "${d.id}-pc", Modifier.weight(1f)) { vm.setDividaLocal(local.id, d.copy(parcela = it)) }
                        Spacer(Modifier.width(6.dp))
                        CampoDia("Dia", d.dia, d.id, Modifier.width(64.dp)) { vm.setDividaLocal(local.id, d.copy(dia = it)) }
                    }
                    EditorDivisao(local, d.divisao) { vm.setDividaLocal(local.id, d.copy(divisao = it)) }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }
        BotaoAdicionar("+ dívida compartilhada") { vm.addDividaLocal(local.id) }

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
