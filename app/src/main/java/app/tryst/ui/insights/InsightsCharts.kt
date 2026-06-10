package app.tryst.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

/**
 * A 0→1 "grow-in" factor that plays once when a chart first appears, then stays at 1. The played
 * flag is [rememberSaveable] so scrolling a chart out of and back into the LazyColumn doesn't replay
 * the reveal — it animates on genuine first sight only.
 */
@Composable
private fun rememberChartReveal(): Float {
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            anim.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            played = true
        }
    }
    return anim.value
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
    val reveal = rememberChartReveal()
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
                // Bars grow up from the baseline on first reveal.
                val fraction = bucket.count.toFloat() / max * reveal
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

/** Ranked horizontal bars (color dot · label · bar · count), longest first, colored by type. */
@Composable
fun RankedBars(
    items: List<Tally>,
    modifier: Modifier = Modifier,
    max: Int = 8,
) {
    if (items.isEmpty()) return
    val shown = items.take(max)
    val top = (shown.maxOf { it.count }).coerceAtLeast(1)
    val reveal = rememberChartReveal()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { item ->
            val color = TypeColors.colorFor(item.label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).width(112.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Bars sweep out from the left on first reveal.
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((item.count.toFloat() / top).coerceIn(0.04f, 1f) * reveal)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
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

/** Smooth filled-area line for a trend series, with the count labelled above each point. */
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
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val w = size.width
            val h = size.height
            val n = data.size
            if (n == 0) return@Canvas
            // Inset so the line and its value labels stay inside the canvas.
            val padTop = 22f
            val padBottom = 8f
            val padX = 10f
            val stepX = if (n == 1) 0f else (w - padX * 2) / (n - 1)
            fun pt(i: Int): Offset {
                val x = if (n == 1) w / 2 else padX + i * stepX
                val frac = data[i].count.toFloat() / max
                val y = padTop + (1f - frac) * (h - padTop - padBottom)
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
            pts.forEachIndexed { i, p ->
                drawCircle(dot, radius = 7f, center = p)
                val layout = measurer.measure(data[i].count.toString(), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        (p.x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width),
                        (p.y - layout.size.height - 6f).coerceAtLeast(0f),
                    ),
                )
            }
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
    // Collapse the long tail into "Other" so the donut stays readable.
    val head = items.take(max)
    val tail = items.drop(max).sumOf { it.count }
    val slices = buildList {
        addAll(head)
        if (tail > 0) add(Tally("Other", tail))
    }
    val colors = slices.map { TypeColors.colorFor(it.label) }
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    val reveal = rememberChartReveal()

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 26f
                val inset = stroke / 2
                // Sweep the ring open clockwise from the top as it reveals.
                var start = -90f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                slices.forEachIndexed { i, slice ->
                    val sweep = 360f * slice.count / total * reveal
                    drawArc(
                        color = colors[i],
                        startAngle = start,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0f), // tiny gap between slices
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
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
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
