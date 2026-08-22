package com.example.nino_home

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nino_home.ui.HomeScreen
import com.example.nino_home.ui.theme.NinoHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NinoHomeTheme(darkTheme = false) {
                HomeScreen()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (VolumeKeyDispatcher.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
