package com.vivificavit.shortstack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.vivificavit.shortstack.util.AppCurrency
import com.vivificavit.shortstack.util.MoneyFormat
import java.util.Locale

/**
 * How amounts are written everywhere on screen: the currency chosen in the
 * menu, in the phone's number style. Provided from MainActivity, so every
 * amount changes together when either one does.
 */
val LocalMoneyFormat = staticCompositionLocalOf { MoneyFormat(AppCurrency.USD, Locale.US) }

/** [cents] as money, such as "$1,234.56". */
@Composable
@ReadOnlyComposable
fun formatMoney(cents: Long): String = LocalMoneyFormat.current.format(cents)
