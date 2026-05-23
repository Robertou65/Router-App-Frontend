package com.example.router_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.example.router_app.ui.theme.Router_AppTheme
import com.example.router_app.ui.navigation.RouterNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Router_AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RouterNavHost()
                }
            }
        }
    }
}