package com.vivificavit.shortstack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.ui.components.GoalActions
import com.vivificavit.shortstack.ui.components.GoalIcon
import com.vivificavit.shortstack.ui.components.GoalProgressBar
import com.vivificavit.shortstack.ui.components.goalSaveMoreText
import com.vivificavit.shortstack.ui.icons.EntryIcons
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.viewmodel.GoalProgress
import com.vivificavit.shortstack.ui.viewmodel.GoalsState
import com.vivificavit.shortstack.ui.components.formatMoney

/** Shows nothing until [goals] has loaded, so the empty state never flashes up. */
@Composable
fun GoalsTabContent(
    goals: GoalsState?,
    actions: GoalActions,
    onAddGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (goals == null) return
    var showDone by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "pocket") {
            PocketMoneyStrip(goals.pocketCents)
        }

        if (goals.active.isEmpty()) {
            item(key = "empty_state") {
                EmptyGoalsCard(firstGoal = goals.done.isEmpty(), onAddGoal = onAddGoal)
            }
        } else {
            items(goals.active, key = { it.goal.id }) { progress ->
                GoalCard(progress, actions)
            }
        }

        if (goals.done.isNotEmpty()) {
            item(key = "done_heading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDone = !showDone }
                        .padding(vertical = 8.dp)
                        .semantics { heading() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.goals_done_heading, goals.done.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showDone) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (showDone) R.string.cd_hide_done_goals else R.string.cd_show_done_goals
                        )
                    )
                }
            }
            if (showDone) {
                items(goals.done, key = { it.goal.id }) { progress ->
                    DoneGoalCard(progress, actions)
                }
            }
        }
    }
}

@Composable
private fun PocketMoneyStrip(pocketCents: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.goals_pocket_money),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatMoney(pocketCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Invites a first goal, or, once every goal has been bought, the next one. */
@Composable
private fun EmptyGoalsCard(firstGoal: Boolean, onAddGoal: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(if (firstGoal) R.string.goals_empty_title else R.string.goals_empty_title_next),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.goals_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddGoal) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.goals_add))
            }
        }
    }
}

/** Tap opens the goal's details; long press opens a menu of the same actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(progress: GoalProgress, actions: GoalActions) {
    val goal = progress.goal
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.cd_open_goal),
                    onLongClickLabel = stringResource(R.string.cd_goal_options),
                    onLongClick = { menuOpen = true },
                    onClick = { actions.open(goal) }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = if (progress.isReached) BorderStroke(1.5.dp, CreditGreen) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalIcon(remember(goal.icon, goal.name) { EntryIcons.forGoal(goal) })
                    Spacer(modifier = Modifier.width(12.dp))
                    // The amount sits under the name, so long names and large text still fit.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.goal_progress_amount,
                                formatMoney(progress.savedCents),
                                formatMoney(goal.targetCents)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                GoalProgressBar(progress.fraction)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress.isReached) {
                        Text(
                            text = stringResource(R.string.goal_reached),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CreditGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { actions.buy(goal) },
                            colors = ButtonDefaults.buttonColors(containerColor = CreditGreen)
                        ) {
                            Text(stringResource(R.string.goal_mark_bought))
                        }
                    } else {
                        Text(
                            text = goalSaveMoreText(progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { actions.addMoney(goal) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.goal_add_short))
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            GoalMenuItem(R.string.goal_action_add_money, Icons.Default.Add) {
                menuOpen = false
                actions.addMoney(goal)
            }
            GoalMenuItem(R.string.goal_action_move_money, Icons.Default.SwapHoriz) {
                menuOpen = false
                actions.moveMoney(goal)
            }
            GoalMenuItem(R.string.goal_action_buy, Icons.Default.ShoppingBag) {
                menuOpen = false
                actions.buy(goal)
            }
            GoalMenuItem(R.string.goal_action_edit, Icons.Default.Edit) {
                menuOpen = false
                actions.edit(goal)
            }
            GoalMenuItem(R.string.goal_action_delete, Icons.Default.Delete) {
                menuOpen = false
                actions.delete(goal)
            }
        }
    }
}

@Composable
private fun GoalMenuItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick
    )
}

/** A goal that's been bought: quieter, with what it cost and when. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoneGoalCard(progress: GoalProgress, actions: GoalActions) {
    val goal = progress.goal
    var menuOpen by remember { mutableStateOf(false) }
    val boughtOn = remember(goal.completedAt) { goal.completedAt?.let(::formatShortDate).orEmpty() }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.cd_open_goal),
                    onLongClickLabel = stringResource(R.string.cd_goal_options),
                    onLongClick = { menuOpen = true },
                    onClick = { actions.open(goal) }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoalIcon(remember(goal.icon, goal.name) { EntryIcons.forGoal(goal) }, muted = true)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = progress.paidCents?.let {
                            stringResource(R.string.goal_bought_for, formatMoney(it), boughtOn)
                        } ?: stringResource(R.string.goal_bought_on, boughtOn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            GoalMenuItem(R.string.goal_action_delete, Icons.Default.Delete) {
                menuOpen = false
                actions.delete(goal)
            }
        }
    }
}
