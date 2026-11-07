package com.vivificavit.shortstack.ui.viewmodel

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** A snackbar message; the UI resolves it to text so it can be translated. */
data class UiMessage(
    /** The message text; unused when [pluralsRes] is set. */
    @param:StringRes val resId: Int = 0,
    val args: List<String> = emptyList(),
    /** When set, the snackbar shows an Undo button that runs this. */
    val onUndo: (() -> Unit)? = null,
    /** A message that depends on a count ("1 entry", "3 entries"), picked for [quantity]. */
    @param:PluralsRes val pluralsRes: Int = 0,
    val quantity: Int = 0
)
