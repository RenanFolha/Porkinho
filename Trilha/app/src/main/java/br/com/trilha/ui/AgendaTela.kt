package br.com.trilha.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.*
import br.com.trilha.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AgendaTela(vm: TrilhaViewModel, p: Perfil, mes: YearMonth, padding: PaddingValues) {
    val ref = mes.toString()
    val fluxo = fluxoDiario(p, mes)
    val r = resumo(p)

    var filtroClasse by remember { mutableStateOf<Classe?>(null) }
    var filtroConta by remember { mutableStateOf<String?>(null) }
    var diaSelecionado by remember(mes) { mutableStateOf<Int?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {

        item {
            Bloco {
                SeletorMes(mes, { vm.mesAnterior() }, { vm.mesSeguinte() })
                Spacer(Modifier.height(10.dp))
                // O conteúdo do mês desliza no sentido da navegação.
                AnimatedContent(
                    targetState = mes,
                    transitionSpec = {
                        val indo = targetState > initialState
                        val d = if (indo) 1 else -1
                        (slideInHorizontally(tween(280)) { it / 4 * d } + fadeIn(tween(280)))
                            .togetherWith(slideOutHorizontally(tween(280)) { -it / 4 * d } + fadeOut(tween(200)))
                    },
                    label = "mes"
                ) { alvo ->
                    val f = if (alvo == mes) fluxo else fluxoDiario(p, alvo)
                    Column {
                        LinhaValorAnimada("Entradas", f.entradas, Verde)
                        LinhaValorAnimada("Saídas", f.saidas, Vermelho)
                        f.faturas.forEach {
                            LinhaValor(
                                "${it.conta.nome} · fatura", brl(it.total), Tinta,
                                "vence dia ${it.diaVencimento} · ${it.itens} itens"
                            )
                        }
                        LinhaValorAnimada("Saldo no fim do mês", f.saldoFinal, if (f.saldoFinal >= 0) Verde else Vermelho)
                    }
                }
                Spacer(Modifier.height(10.dp))
                CampoNumero(
                    "Saldo em conta no início do mês (R$)",
                    p.saldos[ref] ?: 0.0, "saldo-$ref", Modifier.fillMaxWidth()
                ) { vm.setSaldoInicial(ref, it) }
            }
        }

        item {
            Bloco("Saldo dia a dia", "Toque ou arraste para percorrer o mês.") {
                val sel = diaSelecionado
                if (sel != null) {
                    val dia = sel + 1
                    Text("dia $dia", fontSize = 11.sp, color = Tinta2)
                    NumeroAnimado(
                        fluxo.serieSaldo[sel], tamanho = 24.sp, peso = FontWeight.Bold,
                        cor = if (fluxo.serieSaldo[sel] < 0) Vermelho else Tinta
                    )
                } else {
                    Text("menor saldo do mês", fontSize = 11.sp, color = Tinta2)
                    NumeroAnimado(
                        fluxo.menorSaldo, tamanho = 24.sp, peso = FontWeight.Bold,
                        cor = if (fluxo.menorSaldo < 0) Vermelho else Tinta
                    )
                    Text("no dia ${fluxo.diaMenorSaldo}", fontSize = 11.5.sp, color = Tinta2)
                }
                Spacer(Modifier.height(8.dp))
                GraficoLinhaInterativo(fluxo.serieSaldo, diaSelecionado, { diaSelecionado = it })
                RotulosEixo((1..fluxo.serieSaldo.size).map { it.toString() }, diaSelecionado, 6)
            }
        }

        item {
            Bloco("Movimentos", "Filtre por classe ou conta para ver só o que interessa.") {
                FiltroClasses(filtroClasse) { filtroClasse = it }
                Spacer(Modifier.height(6.dp))
                FiltroContas(p, filtroConta) { filtroConta = it }
                Spacer(Modifier.height(10.dp))

                val dias = fluxo.dias.mapNotNull { d ->
                    val evs = d.eventos.filter { ev ->
                        val okClasse = filtroClasse == null || ev.entrada ||
                            p.categoria(ev.categoriaId)?.classe == filtroClasse
                        val okConta = filtroConta == null || ev.contaId == filtroConta
                        okClasse && okConta
                    }
                    if (evs.isEmpty()) null else d.copy(eventos = evs)
                }

                if (dias.isEmpty()) ListaVazia("Nenhum movimento com esses filtros.")
                else dias.forEach { d -> LinhaDia(p, mes, d) }
            }
        }

        item {
            Bloco("Contas fixas do mês", "Marque conforme pagar.") {
                if (p.fixos.isEmpty()) ListaVazia("Nenhum gasto fixo cadastrado.")
                else {
                    val pagos = p.fixos.count { ref in it.pagos }
                    BarraAnimada(pagos.toFloat() / p.fixos.size, altura = 6)
                    Text(
                        "$pagos de ${p.fixos.size} pagas · ${brl(p.fixos.filter { ref in it.pagos }.sumOf { it.valor })} quitados",
                        fontSize = 11.5.sp, color = Tinta2, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    p.fixos.sortedBy { it.dia }.forEach { f ->
                        val pago = ref in f.pagos
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pago, onCheckedChange = { vm.marcarPago(f.id, ref, it) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.nome.ifBlank { "sem nome" }, fontSize = 13.5.sp,
                                    color = if (pago) Tinta2 else Tinta
                                )
                                Text(
                                    "dia ${f.dia} · ${p.conta(f.contaId)?.nome ?: "sem conta"}",
                                    fontSize = 11.sp, color = Tinta2
                                )
                            }
                            NumeroAnimado(f.valor, tamanho = 14.sp, cor = if (pago) Tinta2 else Tinta)
                        }
                    }
                }
            }
        }

        item { NovoLancamento(vm, p, mes) }

        item {
            val doMes = p.lancamentos
                .filter { it.data.length >= 7 && it.data.substring(0, 7) == ref }
                .sortedByDescending { it.data }
            Bloco("Gastos lançados") {
                if (doMes.isEmpty()) ListaVazia("Nenhum gasto avulso lançado neste mês.")
                else {
                    doMes.forEach { l ->
                        val cat = p.categoria(l.categoriaId)
                        val conta = p.conta(l.contaId)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.nome.ifBlank { "—" }, fontSize = 13.5.sp, color = Tinta)
                                val detalhe = buildString {
                                    append(l.data.substring(8, 10) + "/" + l.data.substring(5, 7))
                                    cat?.let { append(" · ${it.nome}") }
                                    conta?.let { append(" · ${it.nome}") }
                                    if (conta?.ehCartao == true) {
                                        val v = vencimentoFatura(LocalDate.parse(l.data), conta)
                                        append(" · cobra ${ddMM(v)}")
                                    }
                                }
                                Text(detalhe, fontSize = 11.sp, color = Tinta2)
                            }
                            Text(brl(l.valor), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
                            BotaoRemover { vm.delLancamento(l.id) }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                    LinhaValorAnimada("Total do mês", doMes.sumOf { it.valor })
                }
            }
        }

        item { FecharMes(vm, p, mes, fluxo, r) }
    }
}

/* ---------- peças ---------- */

@Composable
private fun SeletorMes(mes: YearMonth, onAnterior: () -> Unit, onProximo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onAnterior) { Text("‹", fontSize = 18.sp) }
        AnimatedContent(
            targetState = mes,
            transitionSpec = {
                val indo = targetState > initialState
                val d = if (indo) 1 else -1
                (slideInHorizontally(tween(250)) { it / 2 * d } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally(tween(250)) { -it / 2 * d } + fadeOut(tween(150)))
            },
            label = "rotuloMes"
        ) { alvo ->
            Text(nomeMes(alvo), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
        }
        FilledTonalIconButton(onClick = onProximo) { Text("›", fontSize = 18.sp) }
    }
}

@Composable
private fun FiltroClasses(selecionada: Classe?, onSelecionar: (Classe?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selecionada == null,
            onClick = { onSelecionar(null) },
            label = { Text("Todas", fontSize = 12.sp) }
        )
        Classe.entries.forEach { c ->
            FilterChip(
                selected = selecionada == c,
                onClick = { onSelecionar(if (selecionada == c) null else c) },
                label = { Text(c.rotulo, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun FiltroContas(p: Perfil, selecionada: String?, onSelecionar: (String?) -> Unit) {
    if (p.contas.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selecionada == null,
            onClick = { onSelecionar(null) },
            label = { Text("Todas as contas", fontSize = 12.sp) }
        )
        p.contas.forEach { c ->
            FilterChip(
                selected = selecionada == c.id,
                onClick = { onSelecionar(if (selecionada == c.id) null else c.id) },
                label = { Text(c.nome, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun LinhaDia(p: Perfil, mes: YearMonth, d: DiaFluxo) {
    val data = mes.atDay(d.dia)
    val hoje = data == LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(
            Modifier.width(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hoje) Destaque.copy(alpha = 0.12f) else Lavado)
                .padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${d.dia}", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (hoje) Destaque else Tinta
            )
            Text(diaSemana(data), fontSize = 9.sp, color = Tinta2)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            d.eventos.forEach { ev ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(ev.nome, fontSize = 13.5.sp, color = Tinta)
                        if (ev.fatura) Etiqueta("fatura", Azul, Lavado)
                    }
                    Text(
                        (if (ev.entrada) "+" else "−") + brl(ev.valor),
                        fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (ev.entrada) Verde else Vermelho
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            brl(d.saldo), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (d.saldo < 0) Vermelho else Tinta2,
            textAlign = TextAlign.End, modifier = Modifier.width(74.dp)
        )
    }
    HorizontalDivider(color = Linha.copy(alpha = 0.6f))
}

@Composable
private fun NovoLancamento(vm: TrilhaViewModel, p: Perfil, mes: YearMonth) {
    var desc by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(0.0) }
    var chave by remember { mutableIntStateOf(0) }
    var dia by remember { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var categoriaId by remember { mutableStateOf(p.categorias.firstOrNull()?.id ?: "") }
    var contaId by remember { mutableStateOf(p.contaPadrao?.id ?: "") }

    Bloco("Novo gasto", "No cartão, o valor cai na fatura certa automaticamente.") {
        CampoTexto("Descrição", desc, Modifier.fillMaxWidth()) { desc = it }
        Spacer(Modifier.height(6.dp))
        Row {
            CampoNumero("Valor (R$)", valor, "novo-$chave", Modifier.weight(1f)) { valor = it }
            Spacer(Modifier.width(8.dp))
            CampoDia("Dia", dia, "novoDia-$chave", Modifier.width(88.dp)) { dia = it }
        }
        Spacer(Modifier.height(10.dp))

        Text("Categoria", fontSize = 11.sp, color = Tinta2)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            p.categorias.forEach { c ->
                FilterChip(
                    selected = categoriaId == c.id,
                    onClick = { categoriaId = c.id },
                    label = { Text((c.emoji.takeIf { it.isNotBlank() }?.plus(" ") ?: "") + c.nome, fontSize = 12.sp) }
                )
            }
        }

        Text("Conta", fontSize = 11.sp, color = Tinta2, modifier = Modifier.padding(top = 6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            p.contas.forEach { c ->
                FilterChip(
                    selected = contaId == c.id,
                    onClick = { contaId = c.id },
                    label = { Text(c.nome, fontSize = 12.sp) }
                )
            }
        }

        val conta = p.conta(contaId)
        AnimatedVisibility(conta?.ehCartao == true && valor > 0) {
            val data = mes.atDay(minOf(dia, mes.lengthOfMonth()))
            val venc = conta?.let { vencimentoFatura(data, it) }
            Nota(
                "Compra em ${ddMM(data)} no ${conta?.nome}: o dinheiro só sai da conta em " +
                    "${venc?.let { ddMM(it) } ?: "—"}. Até lá o saldo continua parecendo maior do que é."
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (valor <= 0) { vm.avisar("Informe o valor"); return@Button }
                vm.addLancamento(
                    Lancamento(
                        nome = desc.ifBlank { p.categoria(categoriaId)?.nome ?: "Gasto" },
                        valor = valor,
                        data = mes.atDay(minOf(dia, mes.lengthOfMonth())).toString(),
                        categoriaId = categoriaId,
                        contaId = contaId
                    )
                )
                desc = ""; valor = 0.0; chave++
                vm.avisar("Gasto lançado")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lançar gasto") }
    }
}

@Composable
private fun FecharMes(vm: TrilhaViewModel, p: Perfil, mes: YearMonth, fluxo: Fluxo, r: Resumo) {
    val ref = mes.toString()
    val existente = p.fechamentos.firstOrNull { it.ref == ref }
    var aberto by remember { mutableStateOf(false) }
    var renda by remember(ref) { mutableStateOf(existente?.renda ?: fluxo.entradas) }
    var gastos by remember(ref) { mutableStateOf(existente?.gastos ?: fluxo.saidas) }
    var investido by remember(ref) { mutableStateOf(existente?.investido ?: 0.0) }
    var abatida by remember(ref) { mutableStateOf(existente?.dividaAbatida ?: 0.0) }
    var patrimonio by remember(ref) { mutableStateOf(existente?.patrimonio ?: r.patrimonioLiquido) }

    Bloco {
        Row(
            Modifier.fillMaxWidth().clickableSemRipple { aberto = !aberto },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Fechar o mês", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
                Text(
                    if (existente != null) "já fechado · toque para revisar" else "${p.fechamentos.size} meses no histórico",
                    fontSize = 11.5.sp, color = Tinta2
                )
            }
            Text(if (aberto) "▴" else "▾", fontSize = 14.sp, color = Tinta2)
        }

        AnimatedVisibility(aberto) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row {
                    CampoNumero("Renda recebida", renda, "fr-$ref", Modifier.weight(1f)) { renda = it }
                    Spacer(Modifier.width(8.dp))
                    CampoNumero("Gastos totais", gastos, "fg-$ref", Modifier.weight(1f)) { gastos = it }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    CampoNumero("Investido", investido, "fi-$ref", Modifier.weight(1f)) { investido = it }
                    Spacer(Modifier.width(8.dp))
                    CampoNumero("Dívida abatida", abatida, "fd-$ref", Modifier.weight(1f)) { abatida = it }
                }
                Spacer(Modifier.height(8.dp))
                CampoNumero("Patrimônio no fim", patrimonio, "fp-$ref", Modifier.fillMaxWidth()) { patrimonio = it }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        vm.fecharMes(
                            Fechamento(
                                ref = ref, renda = renda, gastos = gastos,
                                investido = investido, dividaAbatida = abatida, patrimonio = patrimonio
                            )
                        )
                        vm.avisar("Mês $ref salvo")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar fechamento") }

                val hist = p.fechamentos.sortedByDescending { it.ref }
                if (hist.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Histórico", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
                    hist.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.ref, fontSize = 13.sp, color = Tinta, modifier = Modifier.width(72.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "sobrou ${brl(f.renda - f.gastos)}", fontSize = 13.sp,
                                    color = if (f.renda - f.gastos >= 0) Verde else Vermelho
                                )
                                Text("patrimônio ${brl(f.patrimonio)}", fontSize = 11.sp, color = Tinta2)
                            }
                            BotaoRemover { vm.delFechamento(f.id) }
                        }
                        HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
