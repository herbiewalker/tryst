package app.tryst.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tryst.core.prefs.ChartStyle
import app.tryst.data.stats.Bucket
import app.tryst.data.stats.Tally

// ---------------------------------------------------------------------------------------
// Style dispatchers — one user-chosen ChartStyle drives every chart, with graceful fallback
// where a style doesn't fit a chart's data shape (donut can't show an ordered time series;
// a line can't show categories).
// ---------------------------------------------------------------------------------------

/** Ordered time/ordinal series (months, weekdays, ratings): bars or line; donut → bars. */
@Composable
fun TrendChart(style: ChartStyle, data: List<Bucket>, modifier: Modifier = Modifier) {
    when (style) {
        ChartStyle.LINE -> LineAreaChart(data, modifier)
        else -> VerticalBarChart(data, modifier)
    }
}

/** Categorical breakdown (partners, acts, …): ranked bars or donut; line → bars. */
@Composable
fun BreakdownChart(
    style: ChartStyle,
    items: List<Tally>,
    modifier: Modifier = Modifier,
    max: Int = 8,
) {
    when (style) {
        ChartStyle.DONUT -> DonutChart(items, modifier, max)
        else -> RankedBars(items, modifier, max)
    }
}

// ---------------------------------------------------------------------------------------
// Bars
// ---------------------------------------------------------------------------------------

/** Vertical bar chart in plain Compose layout (no chart dependency). */
@Composable
fun VerticalBarChart(
    data: List<Bucket>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) return
    val max = (data.maxOf { it.count }).coerceAtLeast(1)
    val barBrush = Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.55f)))
    Row(
        modifier = modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { bucket ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (bucket.count > 0) {
                    Text(
                        bucket.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val fraction = bucket.count.toFloat() / max
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth(0.68f)
                        .weight(fraction.coerceAtLeast(0.012f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (bucket.count > 0) barBrush
                            else Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    bucket.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Ranked horizontal bars (label · bar · count), longest first. */
@Composable
fun RankedBars(
    items: List<Tally>,
    modifier: Modifier = Modifier,
    max: Int = 8,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (items.isEmpty()) return
    val shown = items.take(max)
    val top = (shown.maxOf { it.count }).coerceAtLeast(1)
    val barBrush = Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.75f), barColor))
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((item.count.toFloat() / top).coerceIn(0.04f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(barBrush),
                    )
                }
                Text(
                    item.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).width(28.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Line / area
// ---------------------------------------------------------------------------------------

/** Smooth filled-area line for a trend series. */
@Composable
fun LineAreaChart(
    data: List<Bucket>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) return
    val max = (data.maxOf { it.count }).coerceAtLeast(1)
    val fill = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f)))
    val dot = lineColor
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            val n = data.size
            if (n == 0) return@Canvas
            val stepX = if (n == 1) 0f else w / (n - 1)
            val pad = 6f
            fun pt(i: Int): Offset {
                val x = if (n == 1) w / 2 else i * stepX
                val frac = data[i].count.toFloat() / max
                val y = pad + (1f - frac) * (h - pad * 2)
                return Offset(x, y)
            }
            val pts = (0 until n).map(::pt)

            // Smooth line via quadratic segments through midpoints.
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until n) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    val midX = (prev.x + cur.x) / 2
                    quadraticTo(prev.x, prev.y, midX, (prev.y + cur.y) / 2)
                    quadraticTo(cur.x, cur.y, cur.x, cur.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, h)
                lineTo(pts.first().x, h)
                close()
            }
            drawPath(area, brush = fill)
            drawPath(line, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
            pts.forEach { drawCircle(dot, radius = 7f, center = it) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach {
                Text(
                    it.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Donut
// ---------------------------------------------------------------------------------------

/** Donut for share-of-total of a categorical breakdown, with a legend. */
@Composable
fun DonutChart(
    items: List<Tally>,
    modifier: Modifier = Modifier,
    max: Int = 6,
) {
    if (items.isEmpty()) return
    val palette = sliceColors()
    // Collapse the long tail into "Other" so the donut stays readable.
    val head = items.take(max)
    val tail = items.drop(max).sumOf { it.count }
    val slices = buildList {
        addAll(head)
        if (tail > 0) add(Tally("Other", tail))
    }
    val total = slices.sumOf { it.count }.coerceAtLeast(1)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 26f
                val inset = stroke / 2
                var start = -90f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                slices.forEachIndexed { i, slice ->
                    val sweep = 360f * slice.count / total
                    drawArc(
                        color = palette[i % palette.size],
                        startAngle = start,
                        sweepAngle = sweep - 1.5f, // tiny gap between slices
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Text(
                total.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slices.forEachIndexed { i, slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape).background(palette[i % palette.size]),
                    )
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        slice.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun sliceColors(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, primary.copy(alpha = 0.6f), tertiary.copy(alpha = 0.6f), secondary.copy(alpha = 0.6f), outline)
}
