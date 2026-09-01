package br.com.trilha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.trilha.domain.paraTexto
import br.com.trilha.domain.paraValor

/* ---------- contêineres ---------- */

@Composable
fun Bloco(
    titulo: String? = null,
    subtitulo: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Superficie),
        border = androidx.compose.foundation.BorderStroke(1.dp, Linha),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (titulo != null) {
                Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
            }
            if (subtitulo != null) {
                Text(subtitulo, fontSize = 12.5.sp, color = Tinta2, modifier = Modifier.padding(top = 2.dp))
            }
            if (titulo != null || subtitulo != null) Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun LinhaValor(
    rotulo: String,
    valor: String,
    cor: Color = Tinta,
    detalhe: String? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, fontSize = 13.5.sp, color = Tinta2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(valor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cor)
            if (detalhe != null) Text(detalhe, fontSize = 11.sp, color = Tinta2)
        }
    }
    HorizontalDivider(color = Linha.copy(alpha = 0.6f))
}

@Composable
fun Nota(texto: String) {
    Surface(
        color = Lavado, shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(texto, fontSize = 12.5.sp, color = Tinta2, modifier = Modifier.padding(10.dp), lineHeight = 17.sp)
    }
}

enum class TomAviso { OK, ATENCAO, ALERTA }

@Composable
fun Aviso(tom: TomAviso, texto: String) {
    val (fundo, borda, tinta) = when (tom) {
        TomAviso.OK -> Triple(Color(0xFFEDF5F0), Color(0xFFB8D6C8), Color(0xFF0A4C38))
        TomAviso.ATENCAO -> Triple(Color(0xFFFBF4E9), Color(0xFFE7D2B0), Color(0xFF7A4A0E))
        TomAviso.ALERTA -> Triple(Color(0xFFFBEFEB), Color(0xFFE3C1B6), Color(0xFF6E2513))
    }
    Surface(
        color = fundo, shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borda),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(texto, fontSize = 13.5.sp, color = tinta, lineHeight = 19.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun Etiqueta(texto: String, cor: Color = Verde, fundo: Color = Color(0xFFEDF5F0)) {
    Text(
        texto, fontSize = 10.5.sp, color = cor,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fundo)
            .border(1.dp, cor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    )
}

@Composable
fun ListaVazia(texto: String) {
    Text(texto, fontSize = 13.5.sp, color = Tinta2, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
fun BotaoAdicionar(texto: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VerdeEscuro, containerColor = Lavado)
    ) { Text(texto, fontSize = 13.5.sp) }
}

@Composable
fun BotaoRemover(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Filled.Close, contentDescription = "remover", tint = Tinta2, modifier = Modifier.size(18.dp))
    }
}

/* ---------- campos ---------- */

@Composable
fun CampoTexto(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    onValor: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(rotulo, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

/**
 * Mantém o texto digitado em estado local para não brigar com o cursor,
 * e devolve o valor já convertido. A chave [id] garante que a linha certa
 * receba o texto certo quando a lista muda.
 */
@Composable
fun CampoNumero(
    rotulo: String,
    valor: Double,
    id: String,
    modifier: Modifier = Modifier,
    onValor: (Double) -> Unit
) {
    var texto by remember(id) { mutableStateOf(paraTexto(valor)) }
    OutlinedTextField(
        value = texto,
        onValueChange = { novo ->
            texto = novo.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
            onValor(paraValor(texto))
        },
        label = { Text(rotulo, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun CampoDia(
    rotulo: String,
    valor: Int,
    id: String,
    modifier: Modifier = Modifier,
    onValor: (Int) -> Unit
) {
    var texto by remember(id) { mutableStateOf(if (valor > 0) valor.toString() else "") }
    OutlinedTextField(
        value = texto,
        onValueChange = { novo ->
            texto = novo.filter { it.isDigit() }.take(2)
            onValor(texto.toIntOrNull()?.coerceIn(1, 31) ?: 1)
        },
        label = { Text(rotulo, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

/** Dropdown simples, sem APIs experimentais. */



/** Barra de progresso da meta, com marcas em 25/50/75%. */

/**
 * Três curvas de acúmulo (conservador, base, otimista) e a linha da meta.
 */

@Composable
fun TituloSecao(texto: String) {
    Text(
        texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tinta,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun Marcador(texto: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", fontSize = 13.sp, color = Tinta2, modifier = Modifier.width(14.dp), textAlign = TextAlign.Center)
        Text(texto, fontSize = 13.sp, color = Tinta, lineHeight = 18.sp)
    }
}
