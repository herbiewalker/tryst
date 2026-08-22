// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.gallery.GalleryGroup
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.gallery.GallerySection
import app.tryst.data.gallery.GridSpacing
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val TARGET_ROW_HEIGHT_DP = 132f
private const val COMPACT_GAP_DP = 3f
private const val NORMAL_GAP_DP = 8f
private const val THUMB_PX = 500

// Aspect ratios are clamped so a lone panorama/strip can't blow out a row's height.
private const val MIN_ASPECT = 0.4f
private const val MAX_ASPECT = 2.6f

private data class MosaicCell(val photo: GalleryPhoto, val widthDp: Float)
private data class MosaicRow(val heightDp: Float, val cells: List<MosaicCell>)
private sealed interface MosaicItem {
    data class Header(val group: GalleryGroup) : MosaicItem
    data class PhotoRow(val row: MosaicRow) : MosaicItem
}

/**
 * Aspect-preserving justified rows (GAL-1b): each row is scaled so its photos fill the width at their
 * true aspect ratios, Google-Photos style. Ratios are decoded lazily (default square until known), so the
 * grid settles as they load. Row packing is a pure function ([justify]); the LazyColumn stays lazy per row.
 */
@Composable
fun GalleryMosaic(
    sections: List<GallerySection>,
    spacing: GridSpacing,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    aspectOf: suspend (blobId: String) -> Float,
    interaction: TileInteraction,
) {
    val gapDp = when (spacing) {
        GridSpacing.COMPACT -> COMPACT_GAP_DP
        GridSpacing.NORMAL -> NORMAL_GAP_DP
    }
    val flat = remember(sections) { sections.flatMap { it.photos } }
    val aspects = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(flat) {
        for (p in flat) {
            if (p.id !in aspects) aspects[p.id] = aspectOf(p.blobId).coerceIn(MIN_ASPECT, MAX_ASPECT)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableDp = maxWidth.value - 2 * gapDp
        val items = remember(sections, aspects.toMap(), availableDp, gapDp) {
            buildItems(sections, availableDp, gapDp) { id -> aspects[id] ?: 1f }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(gapDp.dp),
            verticalArrangement = Arrangement.spacedBy(gapDp.dp),
        ) {
            items(items, key = { it.key() }) { item ->
                when (item) {
                    is MosaicItem.Header -> MonthHeader(item.group)
                    is MosaicItem.PhotoRow -> MosaicRowView(item.row, gapDp, onLoad, interaction)
                }
            }
        }
    }
}

@Composable
private fun MosaicRowView(
    row: MosaicRow,
    gapDp: Float,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    interaction: TileInteraction,
) {
    Row(
        Modifier.fillMaxWidth().height(row.heightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
    ) {
        row.cells.forEach { cell ->
            SelectablePhotoTile(
                photo = cell.photo,
                reqPx = THUMB_PX,
                onLoad = onLoad,
                interaction = interaction,
                modifier = Modifier.width(cell.widthDp.dp).height(row.heightDp.dp),
            )
        }
    }
}

@Composable
private fun MonthHeader(group: GalleryGroup) {
    val label = when (group) {
        is GalleryGroup.Month -> {
            val locale = LocalConfiguration.current.locales[0]
            YearMonth.of(group.year, group.month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        }
        GalleryGroup.Solo -> stringResource(R.string.gallery_group_solo)
        else -> ""
    }
    if (label.isNotEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        )
    }
}

private fun MosaicItem.key(): String = when (this) {
    is MosaicItem.Header -> "hdr:" + when (val g = group) {
        is GalleryGroup.Month -> "m${g.year}-${g.month}"
        GalleryGroup.Solo -> "solo"
        else -> "all"
    }
    is MosaicItem.PhotoRow -> "row:" + row.cells.first().photo.id
}

private fun buildItems(sections: List<GallerySection>, availableDp: Float, gapDp: Float, aspectOf: (String) -> Float): List<MosaicItem> {
    val out = mutableListOf<MosaicItem>()
    for (section in sections) {
        if (section.group != GalleryGroup.Ungrouped) out += MosaicItem.Header(section.group)
        justify(section.photos, availableDp, gapDp, aspectOf).forEach { out += MosaicItem.PhotoRow(it) }
    }
    return out
}

/**
 * Packs [photos] into justified rows: fill a row at the target height, then scale the row's height so its
 * photos exactly span [availableDp]. The trailing partial row keeps the target height (not upscaled). Pure.
 */
private fun justify(photos: List<GalleryPhoto>, availableDp: Float, gapDp: Float, aspectOf: (String) -> Float): List<MosaicRow> {
    if (photos.isEmpty() || availableDp <= 0f) return emptyList()
    val rows = mutableListOf<MosaicRow>()
    var current = mutableListOf<GalleryPhoto>()
    var sumWidthsAtTarget = 0f

    fun aspect(p: GalleryPhoto) = aspectOf(p.id).coerceIn(MIN_ASPECT, MAX_ASPECT)

    for (p in photos) {
        current.add(p)
        sumWidthsAtTarget += aspect(p) * TARGET_ROW_HEIGHT_DP
        val gaps = gapDp * (current.size - 1)
        if (sumWidthsAtTarget + gaps >= availableDp) {
            val scale = (availableDp - gaps) / sumWidthsAtTarget
            val h = TARGET_ROW_HEIGHT_DP * scale
            rows += MosaicRow(h, current.map { MosaicCell(it, aspect(it) * h) })
            current = mutableListOf()
            sumWidthsAtTarget = 0f
        }
    }
    if (current.isNotEmpty()) {
        rows += MosaicRow(TARGET_ROW_HEIGHT_DP, current.map { MosaicCell(it, aspect(it) * TARGET_ROW_HEIGHT_DP) })
    }
    return rows
}
