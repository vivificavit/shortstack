package com.vivificavit.shortstack.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.ui.icons.EntryIcon
import com.vivificavit.shortstack.ui.icons.EntryIcons
import com.vivificavit.shortstack.util.AppCurrency

@Composable
fun ManageCategoriesDialog(
    categories: List<String>,
    usedCategories: Set<String>,
    categoryIcons: Map<String, String>,
    notesByCategory: Map<String, List<String>>,
    onAddCategory: (String) -> Unit,
    onSetCategoryIcon: (category: String, iconKey: String?) -> Unit,
    onRenameCategory: (oldName: String, newName: String) -> Unit,
    onDeleteUnusedCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryInput by rememberSaveable { mutableStateOf("") }
    var pickingIconFor by rememberSaveable { mutableStateOf<String?>(null) }

    pickingIconFor?.let { category ->
        CategoryIconDialog(
            category = category,
            chosenKey = categoryIcons[category.lowercase()],
            notes = notesByCategory[category.lowercase()].orEmpty(),
            onPick = { key ->
                onSetCategoryIcon(category, key)
                pickingIconFor = null
            },
            onDismiss = { pickingIconFor = null }
        )
    }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    renaming?.let { category ->
        RenameDialog(
            currentName = category,
            allNames = categories,
            labelRes = R.string.rename_category_label,
            takenErrorRes = R.string.rename_category_name_taken,
            onRename = { newName ->
                onRenameCategory(category, newName)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    fun addCategory() {
        if (newCategoryInput.isNotBlank()) {
            onAddCategory(newCategoryInput.trim())
            newCategoryInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_categories_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text(stringResource(R.string.manage_categories_new)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { addCategory() }),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { addCategory() }) {
                        Text(stringResource(R.string.action_add))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories, key = { it }) { catName ->
                        val isUsed = usedCategories.contains(catName.lowercase())
                        val icon = EntryIcons.forKey(categoryIcons[catName.lowercase()])
                            ?: EntryIcons.automaticForCategory(catName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { pickingIconFor = catName }) {
                                Icon(
                                    imageVector = icon?.vector ?: EntryIcons.noMatch,
                                    contentDescription = stringResource(R.string.cd_change_icon, catName),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = catName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isUsed) {
                                        stringResource(R.string.category_in_use)
                                    } else {
                                        stringResource(R.string.category_unused)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = catName }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_rename_item, catName)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteUnusedCategory(catName) },
                                enabled = !isUsed
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_item, catName)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@Composable
fun AddKidDialog(
    onDismiss: () -> Unit,
    onConfirmKid: (String) -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<Int?>(null) }
    fun confirm() {
        if (nameInput.isBlank()) {
            nameError = R.string.add_kid_name_error
        } else {
            onConfirmKid(nameInput.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_kid_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.add_kid_name_label)) },
                    placeholder = { Text(stringResource(R.string.add_kid_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth()
                )
                nameError?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { confirm() }) {
                Text(stringResource(R.string.add_kid_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Add, rename or remove kids; removing a kid who has entries asks for confirmation first. */
@Composable
fun EditKidsDialog(
    kids: List<String>,
    kidsWithEntries: Set<String>,
    onAddKid: (String) -> Unit,
    onRenameKid: (oldName: String, newName: String) -> Unit,
    onRemoveKid: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newKidInput by rememberSaveable { mutableStateOf("") }
    fun addKid() {
        if (newKidInput.isNotBlank()) {
            onAddKid(newKidInput.trim())
            newKidInput = ""
        }
    }
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }

    renaming?.let { kidName ->
        RenameDialog(
            currentName = kidName,
            allNames = kids,
            labelRes = R.string.add_kid_name_label,
            takenErrorRes = R.string.rename_kid_name_taken,
            onRename = { newName ->
                onRenameKid(kidName, newName)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    pendingRemoval?.let { kidName ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.remove_kid_title, kidName)) },
            text = { Text(stringResource(R.string.remove_kid_body, kidName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveKid(kidName)
                        pendingRemoval = null
                    }
                ) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_kids_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKidInput,
                        onValueChange = { newKidInput = it },
                        label = { Text(stringResource(R.string.add_kid_name_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { addKid() }),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { addKid() }) {
                        Text(stringResource(R.string.action_add))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(kids, key = { it }) { kidName ->
                        val hasEntries = kidName in kidsWithEntries
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = kidName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasEntries) {
                                        stringResource(R.string.kid_has_entries)
                                    } else {
                                        stringResource(R.string.kid_no_entries)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = kidName }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_rename_item, kidName)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (hasEntries) pendingRemoval = kidName else onRemoveKid(kidName)
                                }
                            ) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

/**
 * Asks for a new name for the kid or category called [currentName]. A name another
 * one in [allNames] has, in any capitalization, is refused here rather than after saving.
 */
@Composable
private fun RenameDialog(
    currentName: String,
    allNames: List<String>,
    @StringRes labelRes: Int,
    @StringRes takenErrorRes: Int,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf(currentName) }
    var nameError by remember { mutableStateOf<Int?>(null) }
    fun confirm() {
        val newName = nameInput.trim()
        val taken = allNames.any {
            !it.equals(currentName, ignoreCase = true) && it.equals(newName, ignoreCase = true)
        }
        when {
            newName.isBlank() -> nameError = R.string.add_kid_name_error
            taken -> nameError = takenErrorRes
            newName == currentName -> onDismiss()
            else -> onRename(newName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title, currentName), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        nameError = null
                    },
                    label = { Text(stringResource(labelRes)) },
                    singleLine = true,
                    isError = nameError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth()
                )
                nameError?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes, nameInput.trim()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { confirm() }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Restoring replaces everything, so say so before the file picker opens. */
@Composable
fun RestoreBackupDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = { Text(stringResource(R.string.restore_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restore_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@StringRes
private fun currencyNameRes(currency: AppCurrency): Int = when (currency) {
    AppCurrency.USD -> R.string.currency_usd
    AppCurrency.CAD -> R.string.currency_cad
    AppCurrency.GBP -> R.string.currency_gbp
    AppCurrency.EUR -> R.string.currency_eur
    AppCurrency.AUD -> R.string.currency_aud
    AppCurrency.NZD -> R.string.currency_nzd
}

/**
 * Picks the currency amounts are shown in. A pick applies right away, so the
 * amounts behind the dialog show what it does before the dialog is closed.
 */
@Composable
fun CurrencyDialog(
    current: AppCurrency,
    onPick: (AppCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.currency_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(modifier = Modifier.selectableGroup()) {
                    AppCurrency.entries.forEach { currency ->
                        // The whole row is the radio button, so it's an easy target and reads as one item.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currency == current,
                                    role = Role.RadioButton,
                                    onClick = { onPick(currency) }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currency == current, onClick = null)
                            Text(
                                text = stringResource(
                                    R.string.currency_option,
                                    stringResource(currencyNameRes(currency)),
                                    currency.symbol
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

/**
 * Picks the icon for [category], or Automatic (null) to let each entry's note and the
 * category's name choose. Icons related to the category's name and its entries' [notes]
 * come first, then every icon grouped by theme. Picking anything closes the dialog.
 */
@Composable
private fun CategoryIconDialog(
    category: String,
    chosenKey: String?,
    notes: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    IconPickerDialog(
        title = stringResource(R.string.category_icon_title, category),
        hint = stringResource(R.string.category_icon_hint),
        chosenKey = chosenKey,
        suggested = remember(category, notes) { EntryIcons.suggestedFor(category, notes) },
        onPick = onPick,
        onDismiss = onDismiss
    )
}

/**
 * Picks one of the [EntryIcons], or Automatic (null). The [suggested] icons
 * come first, then every icon grouped by theme. Picking anything closes the dialog.
 */
@Composable
fun IconPickerDialog(
    title: String,
    hint: String,
    chosenKey: String?,
    suggested: List<EntryIcon>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = remember { EntryIcons.all.groupBy { it.theme } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = chosenKey == null,
                    onClick = { onPick(null) },
                    label = { Text(stringResource(R.string.category_icon_automatic)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    if (suggested.isNotEmpty()) {
                        iconGroupHeading(R.string.category_icon_suggested)
                        // Suggested icons also appear under their theme, so their keys need a prefix.
                        items(suggested, key = { "suggested_${it.key}" }) { option ->
                            IconChoice(option, checked = option.key == chosenKey, onPick = onPick)
                        }
                    }
                    themes.forEach { (theme, icons) ->
                        iconGroupHeading(theme.labelRes)
                        items(icons, key = { it.key }) { option ->
                            IconChoice(option, checked = option.key == chosenKey, onPick = onPick)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** A heading that spans the whole row of the icon grid; screen readers can jump between them. */
private fun LazyGridScope.iconGroupHeading(@StringRes labelRes: Int) {
    item(key = "heading_$labelRes", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .semantics { heading() }
        )
    }
}

@Composable
private fun IconChoice(option: EntryIcon, checked: Boolean, onPick: (String?) -> Unit) {
    // Plain until chosen, so the chosen icon is the one that stands out.
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onPick(option.key) },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(
            imageVector = option.vector,
            contentDescription = stringResource(option.labelRes)
        )
    }
}
