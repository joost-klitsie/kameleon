package dev.klitsie.kameleon.sample.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.klitsie.kameleon.sample.app.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.i("MainActivity", "Read from config: ${KameleonConfig.TEST}")
        setContent {
            App()
        }
    }
}
