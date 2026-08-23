package com.watson.nutrilog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watson.nutrilog.data.DarkModePreference
import com.watson.nutrilog.ui.NutriLogApp
import com.watson.nutrilog.ui.NutriViewModel
import com.watson.nutrilog.ui.theme.NutriLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Activity-scoped：整個 app 只有這一個 ViewModel，
            // 各畫面之間的狀態（草稿、選到的日期）就靠它串起來。
            val viewModel: NutriViewModel = viewModel()
            val darkTheme = when (viewModel.settings.darkMode) {
                DarkModePreference.SYSTEM -> isSystemInDarkTheme()
                DarkModePreference.LIGHT -> false
                DarkModePreference.DARK -> true
            }
            NutriLogTheme(darkTheme = darkTheme) {
                NutriLogApp(viewModel)
            }
        }
    }
}
