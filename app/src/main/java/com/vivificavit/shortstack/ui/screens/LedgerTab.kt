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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.ui.icons.EntryIcons
import com.vivificavit.shortstack.ui.viewmodel.LedgerState
import com.vivificavit.shortstack.ui.theme.CategoryTagBackground
import com.vivificavit.shortstack.ui.theme.CategoryTagText
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.theme.DebitRed
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
