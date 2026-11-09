package com.harsh.extensionrenamer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.harsh.extensionrenamer.ui.AppRoot
import com.harsh.extensionrenamer.ui.theme.ExtensionRenamerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExtensionRenamerTheme {
                AppRoot()
            }
        }
    }
}
