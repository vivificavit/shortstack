package com.vivificavit.shortstack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.theme.EntryButtonNavy
import com.vivificavit.shortstack.util.BalancePoint
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** The kid's whole balance: the app's money green, as on the ledger. */
private val BalanceColor = CreditGreen

/** Money set aside for goals: navy, so it stays apart from the green and the category colors below. */
private val SavedColor = EntryButtonNavy

private val ChartDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * Total balance and money saved for goals over time, as two areas on one
 * axis. The labels above name each area and give its value on the last day,
 * or on the day being touched: touching or dragging across the chart reads
 * out that day.
 */
@Composable
fun BalanceChart(
    points: List<BalancePoint>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 168.dp
) {
    if (points.isEmpty()) return
    var touched by remember(points) { mutableStateOf<Int?>(null) }
    val shown = points[touched ?: points.lastIndex]
    val first = points.first()
    val last = points.last()
    val description = stringResource(
        R.string.cd_balance_chart,
        formatMoney(first.balanceCents),
        formatMoney(last.balanceCents),
        formatMoney(first.savedCents),
        formatMoney(last.savedCents),
        first.day.format(ChartDate),
        last.day.format(ChartDate)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SeriesLabel(BalanceColor, stringResource(R.string.chart_total_balance), shown.balanceCents)
            SeriesLabel(SavedColor, stringResource(R.string.chart_saved_for_goals), shown.savedCents)
        }
        Text(
            text = shown.day.format(ChartDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BalancePlot(
            days = points,
            touched = touched,
            onTouch = { touched = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = first.day.format(ChartDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = last.day.format(ChartDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A colored key, the series' name, and its value: the label for one area. */
@Composable
private fun SeriesLabel(color: Color, name: String, cents: Long) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatMoney(cents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BalancePlot(
    days: List<BalancePoint>,
    touched: Int?,
    onTouch: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    // A one-day range is drawn as a flat stretch across the plot rather than a single dot.
    val points = remember(days) { if (days.size == 1) days + days[0].copy(day = days[0].day.plusDays(1)) else days }
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val scale = remember(points) { AxisScale.of(points) }
    val firstDay = points.first().day
    val spanDays = ChronoUnit.DAYS.between(firstDay, points.last().day).coerceAtLeast(1L)
    val moneyFormat = LocalMoneyFormat.current
    val tickLabels = remember(scale, moneyFormat) { scale.ticks.map(moneyFormat::formatShort) }

    // The plot starts after the widest axis label, so the labels never overlap it.
    val labelWidth = remember(tickLabels, axisStyle) {
        tickLabels.maxOf { textMeasurer.measure(it, axisStyle).size.width }
    }

    fun DrawScope.plotLeft() = labelWidth + 8.dp.toPx()
    fun DrawScope.xOf(point: BalancePoint) =
        plotLeft() + (size.width - plotLeft()) * ChronoUnit.DAYS.between(firstDay, point.day) / spanDays.toFloat()
    fun DrawScope.yOf(cents: Long) =
        size.height - size.height * (cents - scale.min).toFloat() / (scale.max - scale.min).toFloat()

    // The point nearest a touch, by day.
    fun nearest(x: Float, width: Float, left: Float): Int {
        val fraction = ((x - left) / (width - left)).coerceIn(0f, 1f)
        val day = (fraction * spanDays).roundToInt()
        return points.indices.minBy { abs(ChronoUnit.DAYS.between(firstDay, points[it].day) - day) }
            .coerceAtMost(days.lastIndex)
    }

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures { onTouch(nearest(it.x, size.width.toFloat(), labelWidth + 8.dp.toPx())) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { onTouch(nearest(it.x, size.width.toFloat(), labelWidth + 8.dp.toPx())) },
                    onHorizontalDrag = { change, _ ->
                        onTouch(nearest(change.position.x, size.width.toFloat(), labelWidth + 8.dp.toPx()))
                    }
                )
            }
    ) {
        // Hairline gridlines with their labels, recessive behind the data.
        scale.ticks.forEachIndexed { index, tick ->
            val y = yOf(tick)
            drawLine(gridColor, Offset(plotLeft(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure(tickLabels[index], axisStyle)
            val top = (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
            drawText(label, topLeft = Offset(0f, top))
        }

        fun area(color: Color, valueOf: (BalancePoint) -> Long) {
            val baseline = yOf(0L.coerceIn(scale.min, scale.max))
            val line = Path()
            points.forEachIndexed { i, point ->
                if (i == 0) line.moveTo(xOf(point), yOf(valueOf(point))) else line.lineTo(xOf(point), yOf(valueOf(point)))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(xOf(points.last()), baseline)
                lineTo(xOf(points.first()), baseline)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.12f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        area(BalanceColor) { it.balanceCents }
        area(SavedColor) { it.savedCents }

        touched?.let { index ->
            val point = points[index]
            val x = xOf(point)
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            listOf(BalanceColor to point.balanceCents, SavedColor to point.savedCents).forEach { (color, cents) ->
                drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x, yOf(cents)))
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, yOf(cents)))
            }
        }
    }
}

/** The y axis: rounded ends that take in every value and zero, with a gridline at each tick. */
private data class AxisScale(val min: Long, val max: Long, val ticks: List<Long>) {
    companion object {
        fun of(points: List<BalancePoint>): AxisScale {
            val high = points.maxOf { maxOf(it.balanceCents, it.savedCents) }.coerceAtLeast(0L)
            val low = points.minOf { minOf(it.balanceCents, it.savedCents) }.coerceAtMost(0L)
            val max = if (high > 0L) niceCeil(high) else if (low < 0L) 0L else 100L
            val min = if (low < 0L) -niceCeil(-low) else 0L
            val ticks = if (min < 0L) listOf(min, 0L, max).distinct() else listOf(0L, max / 2, max)
            return AxisScale(min, max, ticks)
        }

        /** Rounds [cents] up to 1, 2 or 5 times a power of ten dollars, so axis labels read cleanly. */
        private fun niceCeil(cents: Long): Long {
            val dollars = ceil(cents / 100.0)
            val magnitude = 10.0.pow(floor(log10(dollars)))
            val nice = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= dollars } * magnitude
            return (nice * 100).toLong()
        }
    }
}