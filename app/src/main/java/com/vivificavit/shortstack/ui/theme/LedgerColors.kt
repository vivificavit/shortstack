package com.vivificavit.shortstack.ui.theme

import androidx.compose.ui.graphics.Color

// Palette from the original AI Studio design (Tailwind teal, slate, emerald and red).
internal val Teal50 = Color(0xFFF0FDFA)
internal val Teal100 = Color(0xFFCCFBF1)
internal val Teal700 = Color(0xFF0F766E)
internal val Teal800 = Color(0xFF115E59)
internal val Teal900 = Color(0xFF134E4A)
internal val Slate50 = Color(0xFFF8FAFC)
internal val Slate100 = Color(0xFFF1F5F9)
internal val Slate200 = Color(0xFFE2E8F0)
internal val Slate300 = Color(0xFFCBD5E1)
internal val Slate500 = Color(0xFF64748B)
internal val Slate700 = Color(0xFF334155)
internal val Slate900 = Color(0xFF0F172A)
internal val Emerald100 = Color(0xFFD1FAE5)
internal val Emerald900 = Color(0xFF064E3B)
internal val Red100 = Color(0xFFFEE2E2)
internal val Red800 = Color(0xFF991B1B)
internal val Red900 = Color(0xFF7F1D1D)

/** Amount colors for money coming in and going out. */
val CreditGreen = Color(0xFF047857) // emerald-700
val DebitRed = Color(0xFFB91C1C) // red-700

/** The floating "+ Entry" button. */
val EntryButtonNavy = Color(0xFF1E3A8A) // blue-900

/** Category tags on ledger entries: soft gray with black text. */
val CategoryTagBackground = Slate200
val CategoryTagText = Color.Black

/** Light backgrounds behind the selected Income / Expense choice. */
val CreditContainer = Emerald100
val OnCreditContainer = Emerald900
val DebitContainer = Red100
val OnDebitContainer = Red900

/**
 * Chart colors, handed out to categories in list order. Any two are easy to
 * tell apart, with red-green color blindness too (OKLab distance at least 15,
 * and 8 when simulated), since both bars put every category on screen at once.
 * None is close to the income green or expense red. A ninth color would sit
 * too close to one of these, so later categories get [OtherCategoryColor].
 */
val CategoryPalette = listOf(
    Color(0xFF14B8A6), // teal-500
    Color(0xFFD97706), // amber-600
    Color(0xFF0284C7), // sky-600
    Color(0xFFDB2777), // pink-600
    Color(0xFFA78BFA), // violet-400
    Color(0xFF84CC16), // lime-500
    Color(0xFF86198F), // fuchsia-800
    Color(0xFF4338CA)  // indigo-700
)

/** Categories past [CategoryPalette]: plain gray, so it reads as "other" rather than another color. */
val OtherCategoryColor = Slate500

/**
 * Kid colors for the all-kids overview, handed out in kid-list order and reused
 * from the start for a seventh kid. Each is dark enough for a white initial on
 * top, and none is close to the income green or expense red.
 */
val KidPalette = listOf(
    Color(0xFF4338CA), // indigo-700
    Color(0xFFB45309), // amber-700
    Color(0xFF86198F), // fuchsia-800
    Color(0xFF0369A1), // sky-700
    Color(0xFFBE185D), // pink-700
    Color(0xFF475569)  // slate-600
)

fun kidColor(index: Int): Color = KidPalette[index.mod(KidPalette.size)]
