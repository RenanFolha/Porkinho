package br.com.trilha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.*
import br.com.trilha.domain.*
import java.time.YearMonth

@Composable
fun DadosTela(vm: TrilhaViewModel, p: Perfil, mes: YearMonth, padding: PaddingValues) {
    val r = resumo(p)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {

        item { BlocoCategorias(vm, p, r, mes) }
        item { BlocoContas(vm, p, mes) }

        item {
            Bloco("Renda", "Valor líquido e o dia em que cai na conta.") {
                if (p.rendas.isEmpty()) ListaVazia("Nada aqui ainda.")
                p.rendas.forEach { renda ->
                    key(renda.id) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CampoTexto("Fonte", renda.nome, Modifier.weight(1f)) { vm.setRenda(renda.copy(nome = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoNumero("R$/mês", renda.valor, renda.id, Modifier.width(100.dp)) {
                                    vm.setRenda(renda.copy(valor = it))
                                }
                                Spacer(Modifier.width(6.dp))
                                CampoDia("Dia", renda.dia, renda.id, Modifier.width(64.dp)) {
                                    vm.setRenda(renda.copy(dia = it))
                                }
                                BotaoRemover { vm.delRenda(renda.id) }
                            }
                            EscolhaConta(p, renda.contaId) { vm.setRenda(renda.copy(contaId = it)) }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                }
                BotaoAdicionar("+ adicionar fonte de renda") { vm.addRenda() }
                Spacer(Modifier.height(8.dp))
                LinhaValorAnimada("Total que entra", r.renda, Verde)
            }
        }

        item {
            Bloco("Gastos fixos", "O dia é o do vencimento — é ele que monta o fluxo de caixa.") {
                if (p.fixos.isEmpty()) ListaVazia("Nada aqui ainda.")
                p.fixos.forEach { f ->
                    key(f.id) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CampoTexto("Gasto", f.nome, Modifier.weight(1f)) { vm.setFixo(f.copy(nome = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoNumero("R$", f.valor, f.id, Modifier.width(94.dp)) { vm.setFixo(f.copy(valor = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoDia("Dia", f.dia, f.id, Modifier.width(64.dp)) { vm.setFixo(f.copy(dia = it)) }
                                BotaoRemover { vm.delFixo(f.id) }
                            }
                            EscolhaCategoria(p, f.categoriaId) { vm.setFixo(f.copy(categoriaId = it)) }
                            EscolhaConta(p, f.contaId) { vm.setFixo(f.copy(contaId = it)) }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                }
                BotaoAdicionar("+ adicionar gasto fixo") { vm.addFixo() }
                Spacer(Modifier.height(8.dp))
                LinhaValorAnimada("Essenciais", r.essenciais)
                LinhaValorAnimada("Importantes", r.importantes)
                LinhaValorAnimada("Supérfluos", r.superfluos, if (r.pctSuperfluos > 0.15) Ambar else Tinta)
            }
        }

        item {
            Bloco("Patrimônio", "Separe o líquido do bem de uso — muda o cálculo da reserva.") {
                if (p.ativos.isEmpty()) ListaVazia("Nada aqui ainda.")
                p.ativos.forEach { a ->
                    key(a.id) {
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CampoTexto("Ativo", a.nome, Modifier.weight(1f)) { vm.setAtivo(a.copy(nome = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoNumero("Valor R$", a.valor, a.id, Modifier.width(110.dp)) {
                                    vm.setAtivo(a.copy(valor = it))
                                }
                                BotaoRemover { vm.delAtivo(a.id) }
                            }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TipoAtivo.entries.forEach { t ->
                                    FilterChip(
                                        selected = a.tipo == t,
                                        onClick = { vm.setAtivo(a.copy(tipo = t)) },
                                        label = { Text(t.rotulo, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                }
                BotaoAdicionar("+ adicionar ativo") { vm.addAtivo() }
                Spacer(Modifier.height(8.dp))
                LinhaValorAnimada("Líquido + investido", r.liquido + r.investido)
                LinhaValorAnimada("Bens de uso", r.bens)
                LinhaValorAnimada("Dívidas", -r.saldoDividas, Vermelho)
                LinhaValorAnimada("Patrimônio líquido", r.patrimonioLiquido, if (r.patrimonioLiquido >= 0) Verde else Vermelho)
            }
        }
    }
}

/* ---------- categorias criadas pelo usuário ---------- */

@Composable
private fun BlocoCategorias(vm: TrilhaViewModel, p: Perfil, r: Resumo, mes: YearMonth) {
    var criando by remember { mutableStateOf(false) }
    var nome by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var classe by remember { mutableStateOf(Classe.IMPORTANTE) }
    var editando by remember { mutableStateOf<String?>(null) }

    val gastos = remember(p, mes) { gastosPorCategoria(p, r, mes).associateBy { it.categoria.id } }

    Bloco("Minhas categorias", "Você cria as suas. A classe define como o gasto pesa no diagnóstico.") {
        p.categorias.forEach { c ->
            key(c.id) {
                val usada = p.fixos.count { it.categoriaId == c.id } + p.lancamentos.count { it.categoriaId == c.id }
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickableSemRipple { editando = if (editando == c.id) null else c.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.emoji.ifBlank { "•" }, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nome, fontSize = 14.sp, color = Tinta)
                            Text(
                                "${c.classe.rotulo} · $usada ${if (usada == 1) "lançamento" else "lançamentos"}" +
                                    if (c.teto > 0) " · teto ${brl(c.teto)}" else "",
                                fontSize = 11.sp, color = Tinta2
                            )
                        }
                        gastos[c.id]?.let {
                            NumeroAnimado(it.valor, tamanho = 13.5.sp, cor = if (it.valor > it.meta + 1) Ambar else Tinta2)
                        }
                        Text(if (editando == c.id) "  ▴" else "  ▾", fontSize = 13.sp, color = Tinta2)
                    }

                    AnimatedVisibility(editando == c.id) {
                        Column(Modifier.padding(top = 6.dp)) {
                            Row {
                                CampoTexto("Nome", c.nome, Modifier.weight(1f)) { vm.setCategoria(c.copy(nome = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoTexto("Ícone", c.emoji, Modifier.width(78.dp)) {
                                    vm.setCategoria(c.copy(emoji = it.take(2)))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Classe.entries.forEach { cl ->
                                    FilterChip(
                                        selected = c.classe == cl,
                                        onClick = { vm.setCategoria(c.copy(classe = cl)) },
                                        label = { Text(cl.rotulo, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            CampoNumero("Teto mensal (R$, 0 = automático)", c.teto, "teto-${c.id}", Modifier.fillMaxWidth()) {
                                vm.setCategoria(c.copy(teto = it))
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = { vm.delCategoria(c.id); editando = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = Vermelho)
                            ) {
                                Text(
                                    if (usada > 0) "Apagar (os $usada lançamentos ficam sem categoria)"
                                    else "Apagar categoria",
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }

        AnimatedVisibility(criando) {
            Column(Modifier.padding(top = 8.dp)) {
                Row {
                    CampoTexto("Nome da categoria", nome, Modifier.weight(1f)) { nome = it }
                    Spacer(Modifier.width(6.dp))
                    CampoTexto("Ícone", emoji, Modifier.width(78.dp)) { emoji = it.take(2) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Classe.entries.forEach { cl ->
                        FilterChip(
                            selected = classe == cl,
                            onClick = { classe = cl },
                            label = { Text(cl.rotulo, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.addCategoria(nome, classe, emoji)
                        nome = ""; emoji = ""; criando = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Criar categoria") }
            }
        }

        if (!criando) BotaoAdicionar("+ nova categoria") { criando = true }
    }
}

/* ---------- contas e cartões criados pelo usuário ---------- */

@Composable
private fun BlocoContas(vm: TrilhaViewModel, p: Perfil, mes: YearMonth) {
    var criando by remember { mutableStateOf(false) }
    var nome by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TipoConta.CORRENTE) }
    var editando by remember { mutableStateOf<String?>(null) }

    Bloco("Minhas contas e cartões", "Cada cartão tem fechamento e vencimento próprios — e gera a própria fatura.") {
        p.contas.forEach { c ->
            key(c.id) {
                val usos = p.rendas.count { it.contaId == c.id } +
                    p.fixos.count { it.contaId == c.id } + p.lancamentos.count { it.contaId == c.id }
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickableSemRipple { editando = if (editando == c.id) null else c.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.nome, fontSize = 14.sp, color = Tinta)
                            Text(
                                buildString {
                                    append(c.tipo.rotulo)
                                    if (c.ehCartao) append(" · fecha ${c.diaFechamento}, vence ${c.diaVencimento}")
                                    append(" · $usos em uso")
                                },
                                fontSize = 11.sp, color = Tinta2
                            )
                        }
                        if (c.ehCartao) Etiqueta("cartão", Azul, Lavado)
                        Text(if (editando == c.id) "  ▴" else "  ▾", fontSize = 13.sp, color = Tinta2)
                    }

                    AnimatedVisibility(editando == c.id) {
                        Column(Modifier.padding(top = 6.dp)) {
                            CampoTexto("Nome", c.nome, Modifier.fillMaxWidth()) { vm.setConta(c.copy(nome = it)) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TipoConta.entries.forEach { t ->
                                    FilterChip(
                                        selected = c.tipo == t,
                                        onClick = { vm.setConta(c.copy(tipo = t)) },
                                        label = { Text(t.rotulo, fontSize = 11.5.sp) }
                                    )
                                }
                            }
                            AnimatedVisibility(c.ehCartao) {
                                Column {
                                    Spacer(Modifier.height(6.dp))
                                    Row {
                                        CampoDia("Fecha dia", c.diaFechamento, "cf-${c.id}", Modifier.weight(1f)) {
                                            vm.setConta(c.copy(diaFechamento = it))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        CampoDia("Vence dia", c.diaVencimento, "cv-${c.id}", Modifier.weight(1f)) {
                                            vm.setConta(c.copy(diaVencimento = it))
                                        }
                                    }
                                    val exemplo = mes.atDay(minOf(c.diaFechamento + 1, mes.lengthOfMonth()))
                                    val cobranca = vencimentoFatura(exemplo, c)
                                    val dias = java.time.temporal.ChronoUnit.DAYS.between(exemplo, cobranca)
                                    Nota(
                                        "Uma compra em ${ddMM(exemplo)} — um dia depois do fechamento — só é cobrada " +
                                            "em ${ddMM(cobranca)}. São $dias dias entre gastar e pagar."
                                    )
                                }
                            }
                            if (p.contas.size > 1) {
                                TextButton(
                                    onClick = { vm.delConta(c.id); editando = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Vermelho)
                                ) { Text("Apagar conta", fontSize = 12.5.sp) }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Linha.copy(alpha = 0.6f))
            }
        }

        AnimatedVisibility(criando) {
            Column(Modifier.padding(top = 8.dp)) {
                CampoTexto("Nome da conta", nome, Modifier.fillMaxWidth()) { nome = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoConta.entries.forEach { t ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = { tipo = t },
                            label = { Text(t.rotulo, fontSize = 11.5.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.addConta(nome, tipo); nome = ""; criando = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Criar conta") }
            }
        }

        if (!criando) BotaoAdicionar("+ nova conta ou cartão") { criando = true }
    }
}

/* ---------- escolhas rápidas ---------- */

@Composable
private fun EscolhaCategoria(p: Perfil, selecionada: String, onEscolher: (String) -> Unit) {
    if (p.categorias.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        p.categorias.forEach { c ->
            FilterChip(
                selected = selecionada == c.id,
                onClick = { onEscolher(c.id) },
                label = { Text((c.emoji.takeIf { it.isNotBlank() }?.plus(" ") ?: "") + c.nome, fontSize = 11.5.sp) }
            )
        }
    }
}

@Composable
private fun EscolhaConta(p: Perfil, selecionada: String, onEscolher: (String) -> Unit) {
    if (p.contas.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        p.contas.forEach { c ->
            FilterChip(
                selected = selecionada == c.id,
                onClick = { onEscolher(c.id) },
                label = { Text(c.nome, fontSize = 11.5.sp) }
            )
        }
    }
}

/* ---------- dívidas ---------- */

@Composable
fun DividasTela(vm: TrilhaViewModel, p: Perfil, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {

        item {
            Bloco("Suas dívidas", "Taxa em % ao mês. Só tem a anual? Divida por 12 e corrija depois.") {
                if (p.dividas.isEmpty()) ListaVazia("Nada aqui ainda.")
                p.dividas.forEach { d ->
                    key(d.id) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CampoTexto("Dívida", d.nome, Modifier.weight(1f)) { vm.setDivida(d.copy(nome = it)) }
                                BotaoRemover { vm.delDivida(d.id) }
                            }
                            Row(Modifier.padding(top = 4.dp)) {
                                CampoNumero("Saldo", d.saldo, d.id, Modifier.weight(1f)) { vm.setDivida(d.copy(saldo = it)) }
                                Spacer(Modifier.width(6.dp))
                                CampoNumero("% a.m.", d.taxaMes, d.id + "t", Modifier.width(80.dp)) {
                                    vm.setDivida(d.copy(taxaMes = it))
                                }
                                Spacer(Modifier.width(6.dp))
                                CampoNumero("Parcela", d.parcela, d.id + "p", Modifier.width(92.dp)) {
                                    vm.setDivida(d.copy(parcela = it))
                                }
                                Spacer(Modifier.width(6.dp))
                                CampoDia("Dia", d.dia, d.id + "d", Modifier.width(62.dp)) { vm.setDivida(d.copy(dia = it)) }
                            }
                            AnimatedVisibility(d.taxaMes >= 4) {
                                Nota(
                                    "A ${fmt1(d.taxaMes)}% ao mês, esta dívida custa " +
                                        "${((Math.pow(1 + d.taxaMes / 100, 12.0) - 1) * 100).toInt()}% ao ano."
                                )
                            }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                }
                BotaoAdicionar("+ adicionar dívida") { vm.addDivida() }
            }
        }

        item { PlanoQuitacao(vm, p) }
    }
}

@Composable
private fun PlanoQuitacao(vm: TrilhaViewModel, p: Perfil) {
    var estrategiaVista by remember { mutableIntStateOf(0) } // 0 = avalanche, 1 = bola

    Bloco("Plano de quitação", "Quanto você consegue pôr por mês além das parcelas mínimas?") {
        CampoNumero("Valor extra mensal (R$)", p.extraDivida, "extra", Modifier.fillMaxWidth()) {
            vm.setExtraDivida(it)
        }
        Spacer(Modifier.height(10.dp))

        val ativas = p.dividas.filter { it.saldo > 0 }
        if (ativas.isEmpty()) {
            ListaVazia("Sem dívidas cadastradas. Se você não tem dívidas, essa tela fica em branco mesmo — é bom sinal.")
            return@Bloco
        }

        val av = simularDividas(ativas, p.extraDivida, true)!!
        val bn = simularDividas(ativas, p.extraDivida, false)!!
        val parcelaTotal = ativas.sumOf { it.parcela }

        if (!av.quitou || !bn.quitou) {
            Aviso(
                TomAviso.ALERTA,
                "As parcelas não cobrem os juros. Com ${brl(parcelaTotal + p.extraDivida)} por mês o saldo não fecha " +
                    "nem em 50 anos. Isso é sinal de renegociação ou portabilidade, não de esforço maior."
            )
            return@Bloco
        }

        val difJuros = bn.juros - av.juros
        val recAvalanche = difJuros > bn.juros * 0.06 || (bn.meses - av.meses) > 2
        val vista = if (estrategiaVista == 0) av else bn

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = estrategiaVista == 0, onClick = { estrategiaVista = 0 },
                label = { Text("Avalanche", fontSize = 12.sp) }
            )
            FilterChip(
                selected = estrategiaVista == 1, onClick = { estrategiaVista = 1 },
                label = { Text("Bola de neve", fontSize = 12.sp) }
            )
        }
        Spacer(Modifier.height(10.dp))

        Text(if (estrategiaVista == 0) "maior juro primeiro" else "menor saldo primeiro", fontSize = 11.sp, color = Tinta2)
        NumeroAnimado(vista.meses.toDouble(), formato = { "${it.toInt()} meses" }, tamanho = 26.sp, peso = FontWeight.Bold)
        NumeroAnimado(vista.juros, formato = { "${brl(it)} em juros" }, tamanho = 13.sp, peso = FontWeight.Normal, cor = Tinta2)

        Spacer(Modifier.height(10.dp))
        TituloSecao("Ordem de ataque")
        vista.ordem.forEachIndexed { i, nome -> Marcador("${i + 1}. $nome") }

        Spacer(Modifier.height(8.dp))
        Nota(
            if (recAvalanche)
                "Recomendação: avalanche. Economiza ${brl(difJuros)} em juros" +
                    (if (bn.meses - av.meses > 0) " e antecipa a quitação em ${bn.meses - av.meses} meses" else "") + "."
            else
                "Recomendação: bola de neve. A diferença de custo entre as duas é de só ${brl(kotlin.math.abs(difJuros))}. " +
                    "Vale começar pelas menores: quitar contratos cedo libera parcela e sustenta o hábito, que é o que " +
                    "costuma quebrar o plano."
        )
        LinhaValorAnimada("Parcela liberada ao final", parcelaTotal + p.extraDivida, Verde)
    }
}
