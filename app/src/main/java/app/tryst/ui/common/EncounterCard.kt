package app.tryst.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.relation.EncounterWithDetails

/** Thumbnail decode size for the card's photo corner. */
const val CARD_THUMB_PX = 140

/**
 * One tryst as a list row — date badge, headline act + partners, pills, note preview, photo thumb.
 *
 * Shared by the Trysts list, the calendar's selected-day list, and Search. [sharedScope]/[animatedScope]
 * are nullable (mirroring `EncounterEditScreen`): pass them to get the card→editor container transform,
 * or null to render a plain card.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun EncounterCard(
    item: EncounterWithDetails,
    onLoadThumb: suspend (MediaEntity) -> ImageBitmap?,
    onClick: () -> Unit,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val e = item.encounter
    Card(
        onClick = onClick,
        modifier = if (sharedScope != null && animatedScope != null) {
            with(sharedScope) {
                modifier
                    .fillMaxWidth()
                    // This card morphs into the editor when opened (container transform).
                    .sharedBounds(
                        rememberSharedContentState(encounterSharedKey(e.id)),
                        animatedVisibilityScope = animatedScope,
                    )
            }
        } else {
            modifier.fillMaxWidth()
        },
    ) {
        // Merge the whole card into a single TalkBack stop so it reads as one item with one action.
        Row(modifier = Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
            DateBadge(e.startAt)
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val primaryActId = ActVisuals.primaryAct(e.practicesPerformed, e.practicesReceived)
                    val primaryActLabel = primaryActId
                        ?.let { id -> runCatching { Act.valueOf(id) }.getOrNull() }
                        ?.let { Format.enumLabel(it) }
                    ActBadge(
                        icon = ActVisuals.icon(primaryActId),
                        contentDescription = primaryActLabel,
                    )
                    Text(
                        text = if (item.partners.isEmpty()) stringResource(R.string.history_solo) else item.partners.joinToString(", ") { Format.partnerName(it) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = Format.time(e.startAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Each pill carries an explicit spoken label so the ★ / ✨ glyphs read meaningfully.
                val pills = buildList {
                    e.satisfactionRating?.let { add("★ $it" to stringResource(R.string.history_rating_cd, it)) }
                    e.durationMin?.let { add(stringResource(R.string.history_pill_minutes, it) to null) }
                    e.mood?.let { add(it.label to null) }
                    val orgasms = (e.orgasmCountSelf ?: 0) +
                        (e.partnerOrgasms?.values?.sum() ?: 0) +
                        (e.orgasmCountPartner ?: 0)
                    if (orgasms > 0) {
                        add("✨ $orgasms" to pluralStringResource(R.plurals.cd_orgasm_count, orgasms, orgasms))
                    }
                }
                if (pills.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pills.forEach { (text, cd) -> Pill(text, cd) }
                    }
                }

                e.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item.media.firstOrNull()?.let { media ->
                DecodedImage(
                    model = media.id,
                    contentDescription = stringResource(R.string.cd_photo),
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    load = { onLoadThumb(media) },
                )
            }
        }
    }
}

/** Sticky-looking day separator above a run of cards ("Today", "Monday, Jun 12", …). */
@Composable
fun DateHeader(modifier: Modifier = Modifier, label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun DateBadge(epochMillis: Long) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(56.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Format.dayOfMonth(epochMillis), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(Format.monthShort(epochMillis), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActBadge(@DrawableRes icon: Int, contentDescription: String?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Pill(text: String, contentDescription: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
