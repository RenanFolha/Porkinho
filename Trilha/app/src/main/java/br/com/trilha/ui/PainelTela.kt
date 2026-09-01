package br.com.trilha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.Perfil
import br.com.trilha.domain.*
import java.time.YearMonth
import kotlin.math.ceil

/**
 * Painel adaptativo: a ordem das seções vem de `painelAdaptativo`, que pontua
 * cada uma pela situação atual. Nada aqui é ordem fixa.
 */
@Composable
fun PainelTela(
    p: Perfil,
    mes: YearMonth,
    padding: PaddingValues,
    onIrPara: (Secao) -> Unit
) {
    val r = resumo(p)
    val fluxo = fluxoDiario(p, mes)
    val secoes = painelAdaptativo(p, r, fluxo)
    val acao = acaoDoMomento(p, r, fluxo)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {

        item { CartaoAcao(acao, onIrPara) }

        items(secoes.size, key = { secoes[it].secao.name }) { indice ->
            val sp = secoes[indice]
            Column {
                when (sp.secao) {
                    Secao.CAIXA -> SecaoCaixa(p, r, fluxo, mes, sp)
                    Secao.PROXIMOS -> SecaoProximos(p, sp)
                    Secao.DIVIDAS -> SecaoDividas(p, r, sp, onIrPara)
                    Secao.RESERVA -> SecaoReserva(r, sp)
                    Secao.CATEGORIAS -> SecaoCategorias(p, r, mes, sp)
                    Secao.FLUXO -> SecaoComposicao(r, sp)
                    Secao.METAS -> SecaoMetas(p, r, sp, onIrPara)
                    Secao.PONTUACAO -> SecaoPontuacao(p, r, sp)
                    Secao.EVOLUCAO -> SecaoEvolucao(p, mes, sp)
                    Secao.DIAGNOSTICO -> SecaoDiagnostico(p, r, fluxo, sp)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------- cabeçalho de seção com a razão de estar ali ---------- */

@Composable
private fun Cabecalho(sp: SecaoPainel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(sp.secao.titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tinta, modifier = Modifier.weight(1f))
        Text(sp.motivo, fontSize = 10.5.sp, color = Tinta2)
    }
    Spacer(Modifier.height(8.dp))
}

/* ---------- ação do momento ---------- */

@Composable
private fun CartaoAcao(a: AcaoMomento, onIrPara: (Secao) -> Unit) {
    val (fundo, borda, tinta) = when (a.severidade) {
        2 -> Triple(Color(0xFFFBEFEB), Color(0xFFE3C1B6), Color(0xFF6E2513))
        1 -> Triple(Color(0xFFFBF4E9), Color(0xFFE7D2B0), Color(0xFF7A4A0E))
        else -> Triple(Color(0xFFEDF5F0), Color(0xFFB8D6C8), Color(0xFF0A4C38))
    }
    Surface(
        color = fundo,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borda),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (a.severidade == 2) "AGORA" else if (a.severidade == 1) "PRÓXIMO PASSO" else "TUDO EM ORDEM",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tinta.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(3.dp))
            Text(a.titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tinta, lineHeight = 23.sp)
            Spacer(Modifier.height(5.dp))
            Text(a.texto, fontSize = 13.5.sp, color = tinta.copy(alpha = 0.9f), lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onIrPara(a.destino) },
                colors = ButtonDefaults.buttonColors(containerColor = tinta, contentColor = Color.White)
            ) { Text(a.rotuloBotao, fontSize = 13.sp) }
        }
    }
}

/* ---------- seções ---------- */

@Composable
private fun SecaoCaixa(p: Perfil, r: Resumo, f: Fluxo, mes: YearMonth, sp: SecaoPainel) {
    var diaSelecionado by remember(mes) { mutableStateOf<Int?>(null) }
    Bloco {
        Cabecalho(sp)
        if (f.dias.isEmpty()) {
            ListaVazia("Sem datas cadastradas. Lance a renda com o dia de recebimento e os gastos fixos com o dia de vencimento.")
            return@Bloco
        }
        val sel = diaSelecionado
        if (sel != null) {
            val dia = sel + 1
            Text("dia $dia de ${nomeMes(mes)}", fontSize = 11.sp, color = Tinta2)
            NumeroAnimado(
                f.serieSaldo[sel], tamanho = 26.sp, peso = FontWeight.Bold,
                cor = if (f.serieSaldo[sel] < 0) Vermelho else Tinta
            )
            val doDia = f.diaDe(dia)
            if (doDia == null) {
                Text("sem movimento neste dia", fontSize = 12.sp, color = Tinta2)
            } else {
                doDia.eventos.forEach { ev ->
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ev.nome, fontSize = 12.5.sp, color = Tinta2, modifier = Modifier.weight(1f))
                        Text(
                            (if (ev.entrada) "+" else "−") + brl(ev.valor), fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold, color = if (ev.entrada) Verde else Vermelho
                        )
                    }
                }
            }
        } else {
            Text("menor saldo do mês", fontSize = 11.sp, color = Tinta2)
            NumeroAnimado(
                f.menorSaldo, tamanho = 26.sp, peso = FontWeight.Bold,
                cor = if (f.menorSaldo < 0) Vermelho else Tinta
            )
            Text("no dia ${f.diaMenorSaldo} · toque ou arraste no gráfico", fontSize = 11.5.sp, color = Tinta2)
        }
        Spacer(Modifier.height(8.dp))
        GraficoLinhaInterativo(f.serieSaldo, diaSelecionado, { diaSelecionado = it })
        RotulosEixo((1..f.serieSaldo.size).map { it.toString() }, diaSelecionado, 6)

        if (f.menorSaldo < 0) {
            Spacer(Modifier.height(8.dp))
            Nota(
                "A conta fecha no mês — entram ${brl(f.entradas)} e saem ${brl(f.saidas)} — mas o dinheiro não está " +
                    "lá no dia ${f.diaMenorSaldo}. Mudar a data de um vencimento costuma resolver mais rápido do que cortar gasto."
            )
        }
    }
}

@Composable
private fun SecaoProximos(p: Perfil, sp: SecaoPainel) {
    val proximos = remember(p) { proximosVencimentos(p) }
    Bloco {
        Cabecalho(sp)
        if (proximos.isEmpty()) ListaVazia("Nada nos próximos 7 dias — ou faltam datas nos seus lançamentos.")
        else proximos.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.width(42.dp).clip(RoundedCornerShape(6.dp)).background(Lavado).padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${v.data.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tinta)
                    Text(diaSemana(v.data), fontSize = 9.sp, color = Tinta2)
                }
                Spacer(Modifier.width(10.dp))
                Text(v.evento.nome, fontSize = 13.5.sp, color = Tinta, modifier = Modifier.weight(1f))
                Text(
                    (if (v.evento.entrada) "+" else "−") + brl(v.evento.valor),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (v.evento.entrada) Verde else Tinta
                )
            }
            HorizontalDivider(color = Linha.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SecaoDividas(p: Perfil, r: Resumo, sp: SecaoPainel, onIrPara: (Secao) -> Unit) {
    val ativas = p.dividas.filter { it.saldo > 0 }
    if (ativas.isEmpty()) return
    Bloco {
        Cabecalho(sp)
        LinhaValorAnimada("Saldo devedor", r.saldoDividas, Vermelho)
        LinhaValorAnimada("Parcelas por mês", r.parcelas, if (r.comprometimentoDividas > 0.3) Vermelho else Tinta)
        val plano = remember(ativas, p.extraDivida) { simularDividas(ativas, p.extraDivida, true) }
        if (plano != null && plano.quitou) {
            LinhaValor("Livre de dívidas em", "${plano.meses} meses", Tinta, "juros de ${brl(plano.juros)}")
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { onIrPara(Secao.DIVIDAS) }) { Text("Ver plano de quitação →") }
    }
}

@Composable
private fun SecaoReserva(r: Resumo, sp: SecaoPainel) {
    Bloco {
        Cabecalho(sp)
        Row(verticalAlignment = Alignment.Bottom) {
            NumeroAnimado(r.reservaAtual, tamanho = 26.sp, peso = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            NumeroAnimado(
                r.mesesReserva, formato = { "${fmt1(it)} meses" },
                tamanho = 13.sp, peso = FontWeight.Normal, cor = Tinta2,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        BarraAnimada((r.mesesReserva / 6).toFloat(), altura = 8)
        Spacer(Modifier.height(10.dp))
        listOf(3, 6, 12).forEach { alvoMeses ->
            val alvo = r.custoBase * alvoMeses
            val falta = (alvo - r.reservaAtual).coerceAtLeast(0.0)
            LinhaValor(
                "$alvoMeses meses = ${brl(alvo)}",
                if (falta <= 0) "completo" else "falta ${brl(falta)}",
                if (falta <= 0) Verde else Tinta,
                if (falta > 0 && r.sobra > 0) "${ceil(falta / r.sobra).toInt()} meses guardando a sobra" else null
            )
        }
        Nota(
            "Para reserva a ordem é liquidez → segurança → rendimento, nessa sequência. Resgate em D+0 com FGC ou " +
                "lastro do Tesouro cumpre esse papel; ações, cripto e fundos com carência não cumprem."
        )
    }
}

@Composable
private fun SecaoCategorias(p: Perfil, r: Resumo, mes: YearMonth, sp: SecaoPainel) {
    val gastos = remember(p, mes) { gastosPorCategoria(p, r, mes) }
    var expandido by remember { mutableStateOf(false) }
    Bloco {
        Cabecalho(sp)
        if (gastos.isEmpty()) { ListaVazia("Nenhum gasto categorizado ainda."); return@Bloco }
        val visiveis = if (expandido) gastos else gastos.take(4)
        visiveis.forEach { g ->
            val excedeu = g.valor > g.meta + 1
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (g.categoria.emoji.takeIf { it.isNotBlank() }?.plus(" ") ?: "") + g.categoria.nome,
                        fontSize = 13.5.sp, color = Tinta, modifier = Modifier.weight(1f)
                    )
                    NumeroAnimado(g.valor, tamanho = 14.sp, cor = if (excedeu) Ambar else Tinta)
                }
                Spacer(Modifier.height(3.dp))
                BarraAnimada(
                    if (g.meta > 0) (g.valor / g.meta).toFloat().coerceAtMost(1f) else 0f,
                    altura = 4, cor = if (excedeu) Ambar else Verde
                )
                if (excedeu) Text(
                    "meta ${brl(g.meta)} · ${brl(g.valor - g.meta)} acima",
                    fontSize = 11.sp, color = Ambar, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (gastos.size > 4) {
            TextButton(onClick = { expandido = !expandido }) {
                Text(if (expandido) "mostrar menos" else "mostrar todas as ${gastos.size}")
            }
        }
        val excesso = gastos.sumOf { maxOf(0.0, it.valor - it.meta) }
        AnimatedVisibility(excesso > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Nota(
                "Ajustando as categorias acima da meta você libera ${brl(excesso)} por mês — " +
                    "${brl(excesso * 12)} em um ano — sem tocar em nada essencial."
            )
        }
    }
}

@Composable
private fun SecaoComposicao(r: Resumo, sp: SecaoPainel) {
    var faixa by remember { mutableStateOf<Int?>(null) }
    Bloco {
        Cabecalho(sp)
        val partes = listOf(
            Triple("Essencial", r.essenciais, Verde),
            Triple("Importante", r.importantes, Azul),
            Triple("Supérfluo", r.superfluos, Ambar),
            Triple("Dívida", r.parcelas, Vermelho),
            Triple("Sobra", maxOf(0.0, r.sobra), Ouro)
        ).filter { it.second > 0 }
        if (partes.isEmpty()) { ListaVazia("Sem gastos lançados."); return@Bloco }

        BarraComposicao(partes, faixa) { faixa = it }
        Spacer(Modifier.height(10.dp))
        faixa?.let { i ->
            val (nome, valor, _) = partes[i]
            LinhaValor(nome, brl(valor), Tinta, if (r.renda > 0) "${pct(valor / r.renda)} da renda" else null)
        }
        LinhaValorAnimada("Renda líquida", r.renda)
        LinhaValorAnimada("Sobra", r.sobra, if (r.sobra > 0) Verde else Vermelho)
    }
}

@Composable
private fun SecaoMetas(p: Perfil, r: Resumo, sp: SecaoPainel, onIrPara: (Secao) -> Unit) {
    Bloco {
        Cabecalho(sp)
        if (p.metas.isEmpty()) {
            ListaVazia(
                if (r.sobra > 0) "Sobra de ${brl(r.sobra)} por mês sem destino definido."
                else "Nenhuma meta criada."
            )
            TextButton(onClick = { onIrPara(Secao.METAS) }) { Text("Criar uma meta →") }
            return@Bloco
        }
        p.metas.take(3).forEach { m ->
            val progresso = if (m.valor > 0) (m.jaTenho / m.valor).toFloat() else 0f
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(m.nome, fontSize = 13.5.sp, color = Tinta, modifier = Modifier.weight(1f))
                    Text("${brl(m.jaTenho)} de ${brl(m.valor)}", fontSize = 12.sp, color = Tinta2)
                }
                Spacer(Modifier.height(4.dp))
                BarraAnimada(progresso, altura = 6)
            }
        }
        TextButton(onClick = { onIrPara(Secao.METAS) }) { Text("Ver todas as metas →") }
    }
}

@Composable
private fun SecaoPontuacao(p: Perfil, r: Resumo, sp: SecaoPainel) {
    val pontos = pontuacao(p, r)
    Bloco {
        Cabecalho(sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MostradorAnimado(pontos.total)
            Spacer(Modifier.width(14.dp))
            Column {
                val (titulo, msg) = when {
                    pontos.total == 0 -> "Preencha seus dados" to "Comece pela aba Dados."
                    pontos.total >= 80 -> "Situação sólida" to "Foco em crescer patrimônio e renda."
                    pontos.total >= 60 -> "Caminho certo" to "Fechar as lacunas abaixo destrava os próximos pontos."
                    pontos.total >= 35 -> "Base em construção" to "Prioridade é sobra mensal e reserva, nessa ordem."
                    else -> "Zona de risco" to "Estabilizar o caixa antes de qualquer investimento de risco."
                }
                Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
                Text(msg, fontSize = 12.5.sp, color = Tinta2, lineHeight = 17.sp)
                Text("toque numa barra para ver o que falta", fontSize = 10.5.sp, color = Tinta2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        pontos.itens.forEach { BarraPontosAnimada(it.nome, it.pontos, it.maximo, it.dica) }
    }
}

@Composable
private fun SecaoEvolucao(p: Perfil, mes: YearMonth, sp: SecaoPainel) {
    val comparacao = remember(p, mes) { compararMeses(p, mes, 6) }
    var selecionado by remember { mutableStateOf<Int?>(comparacao.lastIndex) }
    Bloco {
        Cabecalho(sp)
        val saldos = comparacao.map { it.entradas - it.saidas }
        selecionado?.let { i ->
            val c = comparacao[i]
            Text(nomeMes(c.ref), fontSize = 11.sp, color = Tinta2)
            NumeroAnimado(
                c.entradas - c.saidas, tamanho = 24.sp, peso = FontWeight.Bold,
                cor = if (c.entradas >= c.saidas) Verde else Vermelho
            )
            Text("entradas ${brl(c.entradas)} · saídas ${brl(c.saidas)}", fontSize = 11.5.sp, color = Tinta2)
        }
        Spacer(Modifier.height(8.dp))
        GraficoBarrasInterativo(saldos, selecionado, { selecionado = it })
        RotulosEixo(comparacao.map { nomeMesCurto(it.ref) }, selecionado, 6)
        Nota("Meses anteriores usam os gastos fixos e as dívidas de hoje. Os avulsos entram só onde você lançou.")
    }
}

@Composable
private fun SecaoDiagnostico(p: Perfil, r: Resumo, f: Fluxo, sp: SecaoPainel) {
    val d = remember(p, r, f) { diagnostico(p, r, f) }
    var aberto by remember { mutableStateOf(false) }
    Bloco {
        Row(
            Modifier.fillMaxWidth().clickableSemRipple { aberto = !aberto },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sp.secao.titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tinta, modifier = Modifier.weight(1f))
            Text(if (aberto) "▴" else "▾", fontSize = 14.sp, color = Tinta2)
        }
        if (!aberto) {
            Text(
                "${d.fortes.size} fortes · ${d.atencao.size} em atenção · ${d.problemas.size} problemas",
                fontSize = 12.sp, color = Tinta2, modifier = Modifier.padding(top = 4.dp)
            )
        }
        AnimatedVisibility(aberto, enter = fadeIn() + expandVertically(tween(250)), exit = fadeOut() + shrinkVertically(tween(200))) {
            Column {
                if (d.fortes.isNotEmpty()) { TituloSecao("🟢 Pontos fortes"); d.fortes.forEach { Marcador(it) } }
                if (d.atencao.isNotEmpty()) { TituloSecao("🟡 Atenção"); d.atencao.forEach { Marcador(it) } }
                if (d.problemas.isNotEmpty()) { TituloSecao("🔴 Problemas"); d.problemas.forEach { Marcador(it) } }
                if (d.prioridades.isNotEmpty()) { TituloSecao("🎯 Prioridades"); d.prioridades.forEach { Marcador(it) } }
            }
        }
    }
}

/** Linha de valor cujo número anima ao mudar. */
@Composable
fun LinhaValorAnimada(rotulo: String, valor: Double, cor: Color = Tinta) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, fontSize = 13.5.sp, color = Tinta2, modifier = Modifier.weight(1f))
        NumeroAnimado(valor, cor = cor)
    }
    HorizontalDivider(color = Linha.copy(alpha = 0.6f))
}
