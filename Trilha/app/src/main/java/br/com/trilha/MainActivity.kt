package br.com.trilha

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import br.com.trilha.ui.AppTrilha
import br.com.trilha.ui.TemaTrilha
import br.com.trilha.ui.TrilhaViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: TrilhaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaTrilha { AppTrilha(vm) }
        }
        tratarConvite(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratarConvite(intent)
    }

    /** Link do tipo https://<host>/convite/<token> — espera o login terminar antes de processar. */
    private fun tratarConvite(intent: Intent?) {
        val token = intent?.data?.tokenDeConvite() ?: return
        lifecycleScope.launch {
            vm.usuario.filterNotNull().first()
            vm.processarConvite(token)
        }
    }

    private fun Uri.tokenDeConvite(): String? =
        pathSegments.takeIf { it.size >= 2 && it[0] == "convite" }?.get(1)
}
