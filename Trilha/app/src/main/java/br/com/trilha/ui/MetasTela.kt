package br.com.trilha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.data.Meta
import br.com.trilha.data.Perfil
import br.com.trilha.domain.*
import java.time.YearMonth
import kotlin.math.pow

@Composable
fun MetasTela(vm: TrilhaViewModel, p: Perfil, padding: PaddingValues) {
    val r = resumo(p)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {

        item {
            Bloco("Metas", "Cada meta vira número: aporte necessário, prazo e três cenários.") {
                BotaoAdicionar("+ criar meta") { vm.addMeta() }
                if (p.metas.isEmpty()) {
                    ListaVazia(
                        "Nenhuma meta ainda. Comece pela reserva: " +
                            if (r.custoBase > 0) brl(r.custoBase * 6) else "6× seu custo essencial."
                    )
                }
            }
        }

        for (meta in p.metas) {
            item(key = meta.id) { CartaoMeta(vm, p, r, meta) }
        }

        item { BlocoPremissas(vm, p) }
    }
}

@Composable
private fun CartaoMeta(vm: TrilhaViewModel, p: Perfil, r: Resumo, meta: Meta) {
    var cenario by remember { mutableIntStateOf(1) } // 0 conservador, 1 base, 2 otimista
    var mesTocado by remember { mutableStateOf<Int?>(null) }
    var editando by remember { mutableStateOf(false) }

    val meses = meta.prazoMeses.coerceAtLeast(1)
    val taxas = listOf(
        "Conservador" to p.premissas.conservador,
        "Base" to p.premissas.base,
        "Otimista" to p.premissas.otimista
    )
    val necessarios = taxas.map { aporteNecessario(meta.valor, meta.jaTenho, taxaMensal(it.second), meses) }
    val prazos = taxas.map { prazoParaMeta(meta.valor, meta.jaTenho, taxaMensal(it.second), meta.aporte) }
    val aporteUsado = if (meta.aporte > 0) meta.aporte else necessarios[cenario]
    val series = taxas.map { projecao(meta.jaTenho, aporteUsado, taxaMensal(it.second), meses) }
    val falta = necessarios[cenario] - meta.aporte
    val progresso = if (meta.valor > 0) (meta.jaTenho / meta.valor).toFloat() else 0f
    val cores = listOf(Ambar, Verde, Azul)

    Bloco {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                meta.nome.ifBlank { "Meta" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = Tinta, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { editando = !editando }) { Text(if (editando) "pronto" else "editar", fontSize = 12.5.sp) }
            BotaoRemover { vm.delMeta(meta.id) }
        }

        AnimatedVisibility(editando) {
            Column {
                CampoTexto("Nome", meta.nome, Modifier.fillMaxWidth()) { vm.setMeta(meta.copy(nome = it)) }
                Spacer(Modifier.height(6.dp))
                Row {
                    CampoNumero("Valor da meta", meta.valor, meta.id + "v", Modifier.weight(1f)) {
                        vm.setMeta(meta.copy(valor = it))
                    }
                    Spacer(Modifier.width(8.dp))
                    CampoNumero("Prazo (meses)", meta.prazoMeses.toDouble(), meta.id + "p", Modifier.width(118.dp)) {
                        vm.setMeta(meta.copy(prazoMeses = it.toInt().coerceAtLeast(1)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    CampoNumero("Já tenho", meta.jaTenho, meta.id + "j", Modifier.weight(1f)) {
                        vm.setMeta(meta.copy(jaTenho = it))
                    }
                    Spacer(Modifier.width(8.dp))
                    CampoNumero("Aporte mensal", meta.aporte, meta.id + "a", Modifier.weight(1f)) {
                        vm.setMeta(meta.copy(aporte = it))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        TrilhaAnimada(progresso)
        Spacer(Modifier.height(10.dp))

        // toque no gráfico mostra o mês; sem toque, mostra o resumo do cenário
        val tocado = mesTocado
        if (tocado != null && tocado < series[cenario].size) {
            val data = YearMonth.now().plusMonths(tocado.toLong())
            Text("em ${nomeMes(data)} · mês $tocado", fontSize = 11.sp, color = Tinta2)
            NumeroAnimado(series[cenario][tocado], tamanho = 24.sp, peso = FontWeight.Bold, cor = cores[cenario])
            val restante = meta.valor - series[cenario][tocado]
            Text(
                if (restante > 0) "faltam ${brl(restante)} para a meta" else "meta alcançada aqui",
                fontSize = 11.5.sp, color = if (restante > 0) Tinta2 else Verde
            )
        } else {
            Text("aporte necessário no cenário ${taxas[cenario].first.lowercase()}", fontSize = 11.sp, color = Tinta2)
            NumeroAnimado(necessarios[cenario], tamanho = 24.sp, peso = FontWeight.Bold, cor = cores[cenario])
            Text(
                "arraste no gráfico para ver mês a mês",
                fontSize = 11.5.sp, color = Tinta2
            )
        }

        Spacer(Modifier.height(8.dp))
        GraficoCenarios(series, cores, meta.valor, cenario, mesTocado, { mesTocado = it })
        RotulosEixo((0..meses).map { "$it" }, mesTocado, 6)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            taxas.forEachIndexed { i, (nome, taxa) ->
                FilterChip(
                    selected = cenario == i,
                    onClick = { cenario = i },
                    label = { Text("$nome ${fmt1(taxa)}%", fontSize = 11.5.sp) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            ColunaResumo("Aporte p/ prazo", brl(necessarios[cenario]), Modifier.weight(1f))
            ColunaResumo(
                "Com seu aporte",
                if (meta.aporte > 0) (prazos[cenario]?.let { "$it m" } ?: "+100 anos") else "—",
                Modifier.weight(1f)
            )
            ColunaResumo("Data alvo", nomeMesCurto(YearMonth.now().plusMonths(meses.toLong())), Modifier.weight(1f))
        }

        Nota(
            buildString {
                append(
                    when {
                        meta.aporte <= 0 ->
                            "Defina um aporte mensal para ver o prazo real. No cenário ${taxas[cenario].first.lowercase()} " +
                                "você precisa de ${brl(necessarios[cenario])}/mês."
                        falta > 1 ->
                            "Faltam ${brl(falta)}/mês para bater o prazo. Três saídas: cortar ${brl(falta)} de gastos, " +
                                "aumentar a renda em ${brl(falta)} líquidos, ou esticar o prazo para " +
                                "${prazos[cenario]?.let { "$it meses" } ?: "mais tempo"}."
                        else ->
                            "Seu aporte de ${brl(meta.aporte)} cobre a meta neste cenário, com folga de ${brl(-falta)}/mês."
                    }
                )
                if (r.sobra > 0 && meta.aporte > r.sobra) {
                    append("\n\nAtenção: o aporte de ${brl(meta.aporte)} é maior que sua sobra de ${brl(r.sobra)}. ")
                    append("O plano não cabe no orçamento atual.")
                }
                val corroido = meta.valor / (1 + p.premissas.inflacao / 100).pow(meses / 12.0)
                append("\n\nA ${fmt1(p.premissas.inflacao)}% de inflação ao ano, ${brl(meta.valor)} daqui a $meses ")
                append("meses compram o que ${brl(corroido)} compram hoje.")
            }
        )
    }
}

@Composable
private fun ColunaResumo(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 6.dp)) {
        Text(rotulo, fontSize = 10.5.sp, color = Tinta2)
        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
    }
}

@Composable
private fun BlocoPremissas(vm: TrilhaViewModel, p: Perfil) {
    val pr = p.premissas
    Bloco(
        "Premissas de rendimento",
        "Taxas nominais ao ano, líquidas de imposto. Em 31/08/2026 a Selic estava em 14% a.a., com projeção de queda."
    ) {
        Row {
            CampoNumero("Conservador % a.a.", pr.conservador, "prc", Modifier.weight(1f)) {
                vm.setPremissas(pr.copy(conservador = it))
            }
            Spacer(Modifier.width(8.dp))
            CampoNumero("Base % a.a.", pr.base, "prb", Modifier.weight(1f)) { vm.setPremissas(pr.copy(base = it)) }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            CampoNumero("Otimista % a.a.", pr.otimista, "pro", Modifier.weight(1f)) {
                vm.setPremissas(pr.copy(otimista = it))
            }
            Spacer(Modifier.width(8.dp))
            CampoNumero("Inflação % a.a.", pr.inflacao, "pri", Modifier.weight(1f)) {
                vm.setPremissas(pr.copy(inflacao = it))
            }
        }
        Nota(
            "Rendimento projetado não é promessa. O cenário otimista serve para dimensionar o teto — nunca para " +
                "planejar em cima dele."
        )
        Text(
            "Este app organiza seus números e mostra cenários. Não é recomendação de investimento nem substitui um " +
                "profissional certificado.",
            fontSize = 11.5.sp, color = Tinta2, lineHeight = 16.sp
        )
    }
}
