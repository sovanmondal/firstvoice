package com.firstvoice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.firstvoice.app.ui.FirstVoiceNavHost
import com.firstvoice.app.ui.theme.FirstVoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirstVoiceTheme {
                FirstVoiceNavHost()
            }
        }
    }
}
