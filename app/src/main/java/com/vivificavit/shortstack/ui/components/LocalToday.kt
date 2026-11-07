package com.vivificavit.shortstack.ui.components

import androidx.compose.runtime.compositionLocalOf
import java.time.LocalDate

/**
 * Today's date for anything on screen that depends on it ("by Friday", quick
 * ranges, which days can be picked). Provided from TrackerViewModel.today, so it
 * moves on at midnight while the app is open.
 */
val LocalToday = compositionLocalOf { LocalDate.now() }
