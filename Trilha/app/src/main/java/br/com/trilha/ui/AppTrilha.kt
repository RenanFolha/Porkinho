package br.com.trilha.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.trilha.data.Perfil
import br.com.trilha.domain.*
import kotlinx.coroutines.launch

private enum class Aba(val titulo: String, val icone: ImageVector) {
    PAINEL("Painel", Icons.Filled.AccountBalanceWallet),
    AGENDA("Agenda", Icons.Filled.CalendarMonth),
    DADOS("Dados", Icons.Filled.ListAlt),
    DIVIDAS("Dívidas", Icons.Filled.CreditCard),
    METAS("Metas", Icons.Filled.Flag)
}

/** Para onde cada seção do painel adaptativo leva ao ser tocada. */
private fun Secao.aba(): Aba = when (this) {
    Secao.DIVIDAS -> Aba.DIVIDAS
    Secao.METAS -> Aba.METAS
    Secao.CAIXA, Secao.PROXIMOS, Secao.EVOLUCAO -> Aba.AGENDA
    Secao.CATEGORIAS, Secao.FLUXO, Secao.RESERVA -> Aba.DADOS
    else -> Aba.PAINEL
}

@Composable
fun AppTrilha(vm: TrilhaViewModel) {
    val banco by vm.banco.collectAsStateWithLifecycle()
    val mes by vm.mes.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()

    val perfil = banco.ativo
    var aba by rememberSaveable { mutableStateOf(Aba.PAINEL) }
    var mostrarPerfis by remember { mutableStateOf(false) }
    var mostrarAjustes by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.semearSeVazio() }
    LaunchedEffect(aviso) {
        aviso?.let {
            escopo.launch { snackbar.showSnackbar(it) }
            vm.avisar(null)
        }
    }

    val r = resumo(perfil)
    val fluxo = fluxoDiario(perfil, mes)

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(Verde).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Trilha", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEAF2ED))
                        Text(perfil.nome, fontSize = 12.sp, color = Color(0xFFA9C9BB), maxLines = 1)
                    }
                    IconButton(onClick = { mostrarPerfis = true }) {
                        Icon(Icons.Filled.Person, "perfis", tint = Color(0xFFEAF2ED))
                    }
                    IconButton(onClick = { mostrarAjustes = true }) {
                        Icon(Icons.Filled.Settings, "ajustes", tint = Color(0xFFEAF2ED))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    ResumoTopo("Sobra do mês", r.sobra, r.renda > 0, Modifier.weight(1f))
                    Spacer(Modifier.width(7.dp))
                    ResumoTopoTexto(
                        "Guardando",
                        if (r.renda > 0) pct(r.taxaPoupanca) else "—",
                        Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(7.dp))
                    ResumoTopo(
                        "Menor saldo", fluxo.menorSaldo, fluxo.dias.isNotEmpty(), Modifier.weight(1f),
                        alerta = fluxo.menorSaldo < 0
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Superficie) {
                Aba.entries.forEach { item ->
                    NavigationBarItem(
                        selected = aba == item,
                        onClick = { aba = item },
                        icon = { Icon(item.icone, item.titulo, modifier = Modifier.size(20.dp)) },
                        label = { Text(item.titulo, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Verde,
                            selectedTextColor = Verde,
                            indicatorColor = Lavado,
                            unselectedIconColor = Tinta2,
                            unselectedTextColor = Tinta2
                        )
                    )
                }
            }
        }
    ) { padding ->
        val interno = PaddingValues(
            top = padding.calculateTopPadding() + 6.dp,
            bottom = padding.calculateBottomPadding() + 16.dp
        )
        // Troca de aba desliza no sentido da navegação, dando noção de posição.
        AnimatedContent(
            targetState = aba,
            transitionSpec = {
                val d = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(260)) { it / 5 * d } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(260)) { -it / 5 * d } + fadeOut(tween(160)))
            },
            label = "aba"
        ) { alvo ->
            when (alvo) {
                Aba.PAINEL -> PainelTela(perfil, mes, interno) { aba = it.aba() }
                Aba.AGENDA -> AgendaTela(vm, perfil, mes, interno)
                Aba.DADOS -> DadosTela(vm, perfil, mes, interno)
                Aba.DIVIDAS -> DividasTela(vm, perfil, interno)
                Aba.METAS -> MetasTela(vm, perfil, interno)
            }
        }
    }

    if (mostrarPerfis) DialogoPerfis(vm, banco.perfis, banco.ativoId) { mostrarPerfis = false }
    if (mostrarAjustes) DialogoAjustes(vm) { mostrarAjustes = false }
}

@Composable
private fun ResumoTopo(
    rotulo: String,
    valor: Double,
    temDados: Boolean,
    modifier: Modifier,
    alerta: Boolean = false
) {
    Surface(
        color = Color.Black.copy(alpha = 0.17f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(rotulo, fontSize = 10.5.sp, color = Color(0xFFA9C9BB))
            if (temDados) {
                NumeroAnimado(
                    valor, tamanho = 17.sp, peso = FontWeight.SemiBold,
                    cor = if (alerta) Color(0xFFFFC9BB) else Color.White
                )
            } else {
                Text("—", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ResumoTopoTexto(rotulo: String, valor: String, modifier: Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.17f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(rotulo, fontSize = 10.5.sp, color = Color(0xFFA9C9BB))
            Text(valor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun DialogoPerfis(
    vm: TrilhaViewModel,
    perfis: List<Perfil>,
    ativoId: String,
    onFechar: () -> Unit
) {
    var novoNome by remember { mutableStateOf("") }
    var renomeando by remember { mutableStateOf<String?>(null) }
    var nomeEditado by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onFechar,
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
        title = { Text("Perfis") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Cada perfil guarda contas, categorias, gastos, dívidas e metas separados. Serve para você e " +
                        "outra pessoa da casa, ou para separar pessoa física de MEI.",
                    fontSize = 12.5.sp, color = Tinta2
                )
                Spacer(Modifier.height(10.dp))
                perfis.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (renomeando == p.id) {
                            CampoTexto("Nome", nomeEditado, Modifier.weight(1f)) { nomeEditado = it }
                            TextButton(onClick = {
                                vm.renomearPerfil(p.id, nomeEditado.ifBlank { p.nome }); renomeando = null
                            }) { Text("ok") }
                        } else {
                            Column(Modifier.weight(1f)) {
                                Text(p.nome, fontSize = 14.5.sp, color = Tinta)
                                if (p.id == ativoId) Text("ativo", fontSize = 11.sp, color = Verde)
                            }
                            if (p.id != ativoId) {
                                TextButton(onClick = { vm.trocarPerfil(p.id); onFechar() }) { Text("usar") }
                            }
                            TextButton(onClick = { renomeando = p.id; nomeEditado = p.nome }) { Text("renomear") }
                            if (perfis.size > 1) BotaoRemover { vm.apagarPerfil(p.id) }
                        }
                    }
                    HorizontalDivider(color = Linha.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(10.dp))
                CampoTexto("Nome do novo perfil", novoNome, Modifier.fillMaxWidth()) { novoNome = it }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.criarPerfil(novoNome); novoNome = ""; onFechar() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Criar perfil") }
            }
        }
    )
}

@Composable
private fun DialogoAjustes(vm: TrilhaViewModel, onFechar: () -> Unit) {
    var textoImportacao by remember { mutableStateOf("") }
    var mostrarExport by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
        title = { Text("Ajustes e dados") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tudo fica salvo neste aparelho, no armazenamento privado do app. Nada é enviado para nenhum servidor.",
                    fontSize = 12.5.sp, color = Tinta2
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.carregarExemplo(); onFechar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Preencher perfil de exemplo")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { mostrarExport = !mostrarExport }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (mostrarExport) "Ocultar backup" else "Mostrar backup (JSON)")
                }
                if (mostrarExport) {
                    OutlinedTextField(
                        value = vm.exportar(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Copie e guarde este texto") },
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = textoImportacao,
                    onValueChange = { textoImportacao = it },
                    label = { Text("Colar backup para importar") },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { vm.importar(textoImportacao); onFechar() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = textoImportacao.isNotBlank()
                ) { Text("Importar backup") }
                Spacer(Modifier.height(10.dp))
                if (!confirmarApagar) {
                    OutlinedButton(
                        onClick = { confirmarApagar = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Vermelho)
                    ) { Text("Apagar tudo") }
                } else {
                    Button(
                        onClick = { vm.apagarTudo(); onFechar() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Vermelho)
                    ) { Text("Confirmar: apagar todos os perfis") }
                }
            }
        }
    )
}
