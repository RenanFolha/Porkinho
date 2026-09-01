package br.com.trilha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import br.com.trilha.ui.AppTrilha
import br.com.trilha.ui.TemaTrilha
import br.com.trilha.ui.TrilhaViewModel

class MainActivity : ComponentActivity() {

    private val vm: TrilhaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaTrilha { AppTrilha(vm) }
        }
    }
}
