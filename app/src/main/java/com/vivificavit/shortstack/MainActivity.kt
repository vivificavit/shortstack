package com.vivificavit.shortstack

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivificavit.shortstack.data.local.AppDatabase
import com.vivificavit.shortstack.data.local.DataStoreSelectedKidStore
import com.vivificavit.shortstack.repository.TransactionRepository
import com.vivificavit.shortstack.ui.components.LocalToday
import com.vivificavit.shortstack.ui.screens.TrackerMainScreen
import com.vivificavit.shortstack.ui.theme.AllowanceTrackerTheme
import com.vivificavit.shortstack.ui.viewmodel.TrackerViewModel

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
            DataStoreSelectedKidStore(applicationContext)
        )

        setContent {
            AllowanceTrackerTheme {
                val trackerViewModel: TrackerViewModel = viewModel(factory = viewModelFactory)
                val today by trackerViewModel.today.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalToday provides today) {
                    TrackerMainScreen(viewModel = trackerViewModel)
                }
            }
        }
    }
}
