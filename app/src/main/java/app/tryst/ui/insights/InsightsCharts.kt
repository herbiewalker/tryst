package app.tryst.ui.insights

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tryst.data.stats.Bucket
import app.tryst.data.stats.Tally

/**
 * A vertical bar chart drawn with plain Compose layout (no chart dependency — keeps the
 * project dependency-light and FOSS, consistent with the hand-rolled visuals elsewhere).
 */
@Composable
fun VerticalBarChart(
    data: List<Bucket>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) return
    val max = (data.maxOf { it.count }).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth().height(132.dp),
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
                // The bar grows from the baseline; an empty bucket shows a faint stub.
                val fraction = bucket.count.toFloat() / max
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth(0.7f)
                        .weight(fraction.coerceAtLeast(0.01f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (bucket.count > 0) barColor else MaterialTheme.colorScheme.surfaceVariant),
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

/**
 * A ranked horizontal bar list (label · bar · count), longest first. Used for the
 * per-partner / per-attribute breakdowns.
 */
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
                        .height(18.dp)
                        .clip(RoundedCornerShape(50)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((item.count.toFloat() / top).coerceIn(0.04f, 1f))
                            .background(barColor, RoundedCornerShape(50)),
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
