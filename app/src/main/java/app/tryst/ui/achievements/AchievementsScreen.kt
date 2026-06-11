package app.tryst.ui.achievements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import app.tryst.ui.common.adaptiveContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.achievements.AchievementCategory
import app.tryst.data.achievements.AchievementStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val unlockDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val unlocked = achievements.count { it.unlocked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            // Cap + centre on wide windows so rows don't stretch (Pass 5); no-op on phones.
            modifier = Modifier.fillMaxSize().padding(padding).wrapContentWidth().adaptiveContentWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { OverallProgress(unlocked, achievements.size) }

            AchievementCategory.entries.forEach { category ->
                val items = achievements.filter { it.def.category == category }
                if (items.isEmpty()) return@forEach
                item(key = "cat-${category.name}") {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items, key = { it.def.id }) { status ->
                    AchievementRow(status, today, Modifier.animateItem())
                }
            }
            item(key = "footer") { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun OverallProgress(unlocked: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$unlocked / $total unlocked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            val target = if (total == 0) 0f else unlocked.toFloat() / total
            val animated by animateFloatAsState(target, label = "overallProgress")
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AchievementRow(status: AchievementStatus, today: LocalDate, modifier: Modifier = Modifier) {
    val unlocked = status.unlocked
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(status.def.emoji, unlocked)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.def.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.isNew(today)) {
                        Box(Modifier.padding(start = 6.dp)) { NewRibbon() }
                    }
                }
                Text(
                    status.def.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unlocked) {
                    status.unlockedAt?.let {
                        Text(
                            "Unlocked ${it.format(unlockDateFormat)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    val animated by animateFloatAsState(status.progress, label = "achievementProgress")
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                    Text(
                        "${status.current.coerceAtMost(status.def.target)} / ${status.def.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(emoji: String, unlocked: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Emoji can't be tinted; fade locked badges so unlocked ones pop.
            Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alpha(if (unlocked) 1f else 0.35f))
        }
    }
}

@Composable
private fun NewRibbon() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            "NEW",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/**
 * Compact teaser shown inside the Insights screen: unlocked count, recent unlocks, the nearest
 * in-progress achievement, and a "See all" link. Uses its own [AchievementsViewModel] instance.
 */
@Composable
fun AchievementsTeaser(
    onSeeAll: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    if (summary.total == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏆 Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${summary.unlockedCount} / ${summary.total}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (summary.recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Recently unlocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.recent.take(6).forEach { Badge(it.def.emoji, unlocked = true) }
                    }
                }
            }

            summary.nearest.firstOrNull()?.let { next ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Almost there: ${next.def.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val animated by animateFloatAsState(next.progress, label = "teaserProgress")
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${next.current.coerceAtMost(next.def.target)} / ${next.def.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(onClick = onSeeAll, modifier = Modifier.align(Alignment.End)) {
                Text("See all")
            }
        }
    }
}
