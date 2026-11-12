package com.vivificavit.shortstack.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.ui.icons.EntryIcons
import com.vivificavit.shortstack.ui.viewmodel.KidTileState
import com.vivificavit.shortstack.ui.viewmodel.LedgerState
import com.vivificavit.shortstack.ui.theme.CategoryTagBackground
import com.vivificavit.shortstack.ui.theme.CategoryTagText
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.theme.DebitRed
import com.vivificavit.shortstack.ui.theme.kidColor
import com.vivificavit.shortstack.ui.components.formatMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Above this system font scale, rows stack the amount under the note. */
private const val LargeTextScale = 1.3f

/** "Oct 30" this year, "Dec 28, 2025" for older dates, in the device's locale order. */
internal fun formatShortDate(timestamp: Long): String {
    val locale = Locale.getDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val skeleton = if (date.year == LocalDate.now().year) "MMMd" else "yMMMd"
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/** Shows nothing until [ledger] has loaded, so an empty ledger never flashes up. */
@Composable
fun RunningTallyTabContent(
    kidName: String,
    ledger: LedgerState?,
    categoryIcons: Map<String, String>,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onShowGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ledger == null) return
    val transactions = ledger.transactions

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header_card") {
            BalanceHeaderCard(
                kidName = kidName,
                ledger = ledger,
                onShowGoals = onShowGoals
            )
        }

        if (transactions.isEmpty()) {
            item(key = "empty_state") {
                EmptyTransactionsCard()
            }
        } else {
            items(
                items = transactions,
                key = { transaction -> transaction.id }
            ) { transaction ->
                SwipeableTransactionItem(
                    transaction = transaction,
                    icon = remember(transaction, categoryIcons) {
                        EntryIcons.forEntry(transaction, categoryIcons).vector
                    },
                    onClick = { onEditTransaction(transaction) },
                    onDelete = { onDeleteTransaction(transaction) }
                )
            }
        }
    }
}

/**
 * With goals, pocket money (what's free to spend) leads and the total comes
 * second; without any, it's just the balance, as the two would be the same.
 */
@Composable
fun BalanceHeaderCard(
    kidName: String,
    ledger: LedgerState,
    onShowGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasGoals = ledger.activeGoalCount > 0
    val headlineCents = if (hasGoals) ledger.pocketCents else ledger.balanceCents
    // Outline and text follow the headline's sign; zero counts as not negative.
    val balanceColor = if (headlineCents < 0L) DebitRed else CreditGreen

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, balanceColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(
                    if (hasGoals) R.string.pocket_money_title else R.string.balance_title,
                    kidName
                ),
                style = MaterialTheme.typography.labelLarge,
                color = balanceColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatMoney(headlineCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = balanceColor
            )
            if (hasGoals) {
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.total_savings_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMoney(ledger.balanceCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.cd_show_goals),
                            onClick = onShowGoals
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.saved_for_goals,
                            ledger.activeGoalCount,
                            ledger.activeGoalCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMoney(ledger.savedCents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionItem(
    transaction: Transaction,
    icon: ImageVector,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        onDismiss = {
            // Deleting offers Undo; reset so the row is back in place if it's restored.
            onDelete()
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_entry),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            TransactionRowCard(
                transaction = transaction,
                icon = icon,
                onClick = onClick,
                onDeleteClick = onDelete
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRowCard(
    transaction: Transaction,
    icon: ImageVector,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shortDate = remember(transaction.timestamp) { formatShortDate(transaction.timestamp) }
    // With large system text, the amount moves under the note so the row isn't squeezed.
    val largeText = LocalDensity.current.fontScale > LargeTextScale

    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) CreditGreen else DebitRed
    val formattedSignedAmount = if (isCredit) {
        "+${formatMoney(transaction.amountCents)}"
    } else {
        "-${formatMoney(transaction.amountCents)}"
    }
    val amount = @Composable {
        Text(
            text = formattedSignedAmount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(amountColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Decorative: the category and signed amount beside it say the same thing.
                    // Green or red keeps income and expenses easy to tell apart at a glance.
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (largeText) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // The date drops to its own line when it doesn't fit beside the category.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CategoryTagBackground
                        ) {
                            Text(
                                text = transaction.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = CategoryTagText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = shortDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        // A small mark, not a label, for entries a repeating entry added.
                        if (transaction.recurringId != null) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.cd_repeats),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (largeText) {
                        Spacer(modifier = Modifier.height(4.dp))
                        amount()
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!largeText) amount()
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_item, transaction.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTransactionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.empty_ledger_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.empty_ledger_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Every kid at once: a tile each, with their headline amount and newest entries.
 * Shows nothing until [tiles] has loaded.
 */
@Composable
fun AllKidsTabContent(
    tiles: List<KidTileState>?,
    categoryIcons: Map<String, String>,
    onOpenKid: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tiles == null) return

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = tiles,
            key = { tile -> tile.kidName }
        ) { tile ->
            KidTile(
                tile = tile,
                categoryIcons = categoryIcons,
                onClick = { onOpenKid(tile.kidName) }
            )
        }
    }
}

/** The first letter of a kid's name, for their badge. */
internal fun kidInitial(kidName: String): String {
    val trimmed = kidName.trim()
    if (trimmed.isEmpty()) return ""
    // By code point, so a name that starts with an emoji doesn't get half of it.
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

/** A kid's initial on their color. Decorative: it always sits beside the name. */
@Composable
fun KidBadge(
    kidName: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = kidInitial(kidName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * One kid in the all-kids overview. The outline and badge take the kid's color;
 * the amount stays green or red. The whole tile opens the kid's own ledger, which
 * is where entries are edited and deleted.
 */
@Composable
fun KidTile(
    tile: KidTileState,
    categoryIcons: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = kidColor(tile.colorIndex)
    val shape = RoundedCornerShape(24.dp)
    // With large system text, the amount moves under the name so neither is squeezed.
    val largeText = LocalDensity.current.fontScale > LargeTextScale
    val headline = @Composable {
        Column(horizontalAlignment = if (largeText) Alignment.Start else Alignment.End) {
            Text(
                text = stringResource(
                    if (tile.hasGoals) R.string.tile_pocket_money_label else R.string.tile_balance_label
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMoney(tile.headlineCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (tile.headlineCents < 0L) DebitRed else CreditGreen
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                onClickLabel = stringResource(R.string.cd_open_kid_ledger, tile.kidName),
                role = Role.Button,
                onClick = onClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KidBadge(kidName = tile.kidName, color = color)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tile.kidName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!largeText) {
                    Spacer(modifier = Modifier.width(8.dp))
                    headline()
                }
            }
            if (largeText) {
                Spacer(modifier = Modifier.height(8.dp))
                headline()
            }
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            if (tile.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_ledger_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                tile.recent.forEach { transaction ->
                    TileEntryRow(
                        transaction = transaction,
                        icon = remember(transaction, categoryIcons) {
                            EntryIcons.forEntry(transaction, categoryIcons).vector
                        },
                        largeText = largeText
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tile.entryCount > tile.recent.size) {
                        stringResource(R.string.tile_see_all, tile.entryCount)
                    } else {
                        stringResource(R.string.tile_open_ledger)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** An entry inside a [KidTile]: a slimmer [TransactionRowCard] with nothing to tap. */
@Composable
private fun TileEntryRow(
    transaction: Transaction,
    icon: ImageVector,
    largeText: Boolean
) {
    val shortDate = remember(transaction.timestamp) { formatShortDate(transaction.timestamp) }
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) CreditGreen else DebitRed
    val amount = @Composable {
        Text(
            text = (if (isCredit) "+" else "-") + formatMoney(transaction.amountCents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(amountColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = amountColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (largeText) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shortDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (largeText) amount()
        }
        if (!largeText) {
            Spacer(modifier = Modifier.width(8.dp))
            amount()
        }
    }
}

/** Asks which kid a new entry is for, when it's added from the all-kids overview. */
@Composable
fun EntryKidDialog(
    kids: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_kid_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                kids.forEachIndexed { index, kidName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onPick(kidName) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KidBadge(kidName = kidName, color = kidColor(index))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = kidName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
