package com.vivificavit.shortstack

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivificavit.shortstack.data.local.AppDatabase
import com.vivificavit.shortstack.data.local.DataStoreSettingsStore
import com.vivificavit.shortstack.repository.TransactionRepository
import com.vivificavit.shortstack.ui.components.LocalMoneyFormat
import com.vivificavit.shortstack.ui.components.LocalToday
import com.vivificavit.shortstack.ui.screens.TrackerMainScreen
import com.vivificavit.shortstack.ui.theme.AllowanceTrackerTheme
import com.vivificavit.shortstack.ui.viewmodel.TrackerViewModel
import com.vivificavit.shortstack.util.MoneyFormat

/** Translucent white behind three-button navigation, the same as Android's light default. */
private val LightNavScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light, so keep dark system bar icons even when the phone is
        // in dark mode; otherwise the clock and battery turn white on the white top bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LightNavScrim, LightNavScrim)
        )

        val database = AppDatabase.getInstance(applicationContext)
        val repository = TransactionRepository(database)
        val viewModelFactory = TrackerViewModel.Factory(
            repository,
            DataStoreSettingsStore(applicationContext)
        )

        setContent {
            AllowanceTrackerTheme {
                val trackerViewModel: TrackerViewModel = viewModel(factory = viewModelFactory)
                val today by trackerViewModel.today.collectAsStateWithLifecycle()
                val currency by trackerViewModel.currency.collectAsStateWithLifecycle()
                // The phone's first language decides how numbers are written (1,234.56 or 1.234,56).
                val locale = LocalConfiguration.current.locales[0]
                val moneyFormat = remember(currency, locale) { MoneyFormat(currency, locale) }
                CompositionLocalProvider(LocalToday provides today, LocalMoneyFormat provides moneyFormat) {
                    TrackerMainScreen(viewModel = trackerViewModel)
                }
            }
        }
    }
}
