package br.com.trilha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EntrarTela(vm: TrilhaViewModel) {
    val contexto = LocalContext.current

    Box(Modifier.fillMaxSize().background(Papel), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Trilha", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text(
                "Entre com sua conta Google para continuar. Seus dados pessoais ficam só no aparelho — " +
                    "o login é usado apenas para os Locais compartilhados com outras pessoas.",
                fontSize = 13.5.sp, color = Tinta2, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val activity = contexto as? android.app.Activity ?: return@Button
                vm.entrarComGoogle(activity)
            }) {
                Text("Entrar com Google")
            }
        }
    }
}
