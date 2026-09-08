package com.folzi.astrachat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.folzi.astrachat.ui.AstraRoot
import com.folzi.astrachat.ui.AstraViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: AstraViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The window starts secured so nothing is capturable before settings load; AstraRoot then
        // applies the stored preference, which allows screenshots by default since 1.0.3.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { AstraRoot(vm) }
    }
}
