package com.vivificavit.shortstack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.local.CategorySum
import com.vivificavit.shortstack.ui.theme.CategoryPalette
import com.vivificavit.shortstack.ui.theme.OtherCategoryColor

/**
 * Gives each category a steady color from its place in [allCategories], so it
 * looks the same in both bars and whatever the date range. Categories past the
 * palette (or not in the list) share [OtherCategoryColor] rather than repeating one.
 */
fun categoryColor(category: String, allCategories: List<String>): Color {
    val index = allCategories.indexOfFirst { it.equals(category, ignoreCase = true) }
    return CategoryPalette.getOrNull(index) ?: OtherCategoryColor
}

/** One bar of [StackedBars]: its label and the categories in it, largest first. */
data class StackedBar(val label: String, val parts: List<CategorySum>)

/** Square at the start, rounded at the end where the bar stops. */
private val BarShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)

/**
 * Bars stacked one above the other on one shared scale, so the longer one is
 * the bigger total, whatever the amounts; the chart's height never changes. Each
 * bar is split into its categories, largest first, with its label and total above it.
 */
@Composable
fun StackedBars(
    bars: List<StackedBar>,
    colorFor: (String) -> Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 28.dp
) {
    val maxTotal = bars.maxOfOrNull { bar -> bar.parts.sumOf { it.totalCents } }?.coerceAtLeast(1L) ?: 1L
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        bars.forEach { bar ->
            StackedRow(
                bar = bar,
                maxTotal = maxTotal,
                colorFor = colorFor,
                barHeight = barHeight
            )
        }
    }
}

@Composable
private fun StackedRow(
    bar: StackedBar,
    maxTotal: Long,
    colorFor: (String) -> Color,
    barHeight: Dp
) {
    val resources = LocalResources.current
    val moneyFormat = LocalMoneyFormat.current
    val parts = bar.parts.filter { it.totalCents > 0L }
    val total = parts.sumOf { it.totalCents }
    val description = if (parts.isEmpty()) {
        stringResource(R.string.cd_stacked_bar, bar.label, formatMoney(total))
    } else {
        stringResource(
            R.string.cd_stacked_bar_parts,
            bar.label,
            formatMoney(total),
            parts.joinToString { resources.getString(R.string.cd_stacked_bar_part, it.category, moneyFormat.format(it.totalCents)) }
        )
    }

    // Grows from the start on first show, then eases to each new total.
    val lengthFraction = remember { Animatable(0f) }
    LaunchedEffect(total, maxTotal) {
        lengthFraction.animateTo(
            targetValue = total.toFloat() / maxTotal,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = bar.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatMoney(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // The full-width track is the shared scale: the bigger total fills it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(BarShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(lengthFraction.value)
                    .fillMaxHeight()
                    .clip(BarShape)
            ) {
                if (total <= 0L) return@Canvas
                // A 2dp gap between segments lets the card show through, unless the bar is too short for gaps.
                val gap = 2.dp.toPx()
                val gaps = gap * (parts.size - 1)
                val withGaps = size.width > gaps + parts.size
                val usable = if (withGaps) size.width - gaps else size.width
                var start = 0f
                parts.forEach { part ->
                    val segment = usable * part.totalCents / total
                    drawRect(
                        color = colorFor(part.category),
                        topLeft = Offset(start, 0f),
                        size = Size(segment, size.height)
                    )
                    start += segment + if (withGaps) gap else 0f
                }
            }
        }
    }
}

/** Every category in either list once, by name: income ones first, then the rest, each largest first. */
fun legendCategories(incomeSums: List<CategorySum>, spendingSums: List<CategorySum>): List<String> =
    (incomeSums + spendingSums).map { it.category }.distinctBy { it.lowercase() }

/**
 * Lists every category in either bar once, by name. Tapping one hides it from
 * both bars (or shows it again); hidden ones stay listed, struck through.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryLegend(
    incomeSums: List<CategorySum>,
    spendingSums: List<CategorySum>,
    hiddenCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    colorFor: (String) -> Color,
    modifier: Modifier = Modifier
) {
    val categories = remember(incomeSums, spendingSums) { legendCategories(incomeSums, spendingSums) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val shown = category.lowercase() !in hiddenCategories
            val dotColor = colorFor(category)

            Surface(
                checked = shown,
                onCheckedChange = { onToggleCategory(category) },
                shape = RoundedCornerShape(12.dp),
                color = if (shown) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
                border = if (shown) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .then(
                                if (shown) {
                                    Modifier.background(dotColor)
                                } else {
                                    Modifier.border(1.5.dp, dotColor, CircleShape)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (shown) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textDecoration = if (shown) null else TextDecoration.LineThrough
                    )
                }
            }
        }
    }
}
