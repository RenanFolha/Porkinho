package br.com.trilha.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Paleta: papel-contábil claro, tinta verde-escura, sinais reservados para status. */
val Papel = Color(0xFFF1F3EF)
val Superficie = Color(0xFFFFFFFF)
val Lavado = Color(0xFFE7EDE7)
val Linha = Color(0xFFD7DDD5)
val Tinta = Color(0xFF16211C)
val Tinta2 = Color(0xFF5A6A61)
val Verde = Color(0xFF0B6B4F)
val VerdeEscuro = Color(0xFF08543E)
val Ouro = Color(0xFFB98D14)
val Ambar = Color(0xFFC0741A)
val Vermelho = Color(0xFFA3381B)
val Azul = Color(0xFF2A4E7A)
/** Cor de destaque da interface (cabeçalho, aba selecionada) — separada do
 *  verde, que continua marcando "valor positivo" nos números financeiros. */
val Destaque = Color(0xFFD9711A)

private val esquema = lightColorScheme(
    primary = Destaque,
    onPrimary = Color.White,
    primaryContainer = Lavado,
    onPrimaryContainer = VerdeEscuro,
    secondary = Ouro,
    background = Papel,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = Lavado,
    onSurfaceVariant = Tinta2,
    outline = Linha,
    error = Vermelho
)

@Composable
fun TemaTrilha(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = Typography(), content = content)
}
