package com.example.ekh2026

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ekh2026.ui.JurorAppScreen
import com.example.ekh2026.ui.theme.EKH2026Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EKH2026Theme(dynamicColor = false) {
                JurorAppScreen()
            }
        }
    }
}
