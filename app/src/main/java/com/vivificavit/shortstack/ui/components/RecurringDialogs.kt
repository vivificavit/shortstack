package com.vivificavit.shortstack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.theme.DebitRed
import com.vivificavit.shortstack.util.RecurringMath
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A kid's repeating entries: what each adds, how often, when next, and what it
 * sets aside. Tapping one edits it; Stop ends it, keeping what it already added.
 * [goalNames] holds the goals still being saved for, by id.
 */
@Composable
fun RepeatingEntriesDialog(
    kidName: String,
    entries: List<RecurringEntry>,
    goalNames: Map<Long, String>,
    onEdit: (RecurringEntry) -> Unit,
    onStop: (RecurringEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_title, kidName), fontWeight = FontWeight.Bold) },
        text = {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.recurring_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        RecurringRow(
                            entry = entry,
                            goalName = entry.goalId?.let(goalNames::get).takeIf { entry.type == TransactionType.CREDIT },
                            zone = zone,
                            onEdit = { onEdit(entry) },
                            onStop = { onStop(entry) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun RecurringRow(
    entry: RecurringEntry,
    goalName: String?,
    zone: ZoneId,
    onEdit: () -> Unit,
    onStop: () -> Unit
) {
    val isCredit = entry.type == TransactionType.CREDIT
    val amount = (if (isCredit) "+" else "-") + formatMoney(entry.amountCents)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_edit_recurring, entry.title),
                    onClick = onEdit
                )
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // The dialog's text slot defaults to a muted color; the name should stand out.
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.recurring_amount, amount, stringResource(repeatsRes(entry.frequency))),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCredit) CreditGreen else DebitRed
            )
            Text(
                text = stringResource(
                    R.string.recurring_next,
                    RecurringMath.nextDate(entry, zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (goalName != null && entry.goalCents != null) {
                Text(
                    text = stringResource(R.string.recurring_goal, formatMoney(entry.goalCents), goalName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onStop) {
            Text(stringResource(R.string.action_stop), color = MaterialTheme.colorScheme.error)
        }
    }
}
