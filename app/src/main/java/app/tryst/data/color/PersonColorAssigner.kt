// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.color

import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Makes sure everyone has a stored color of their own (D-70). Runs after unlock, after a partner or the profile is
 * saved, and after a restore; only fills people with no color, so nothing anyone has already got ever changes here.
 * Archived partners keep their colors but only count once current people have used every color, so someone new
 * can get a color from a partner who left years ago before two current people ever share.
 */
@Singleton
class PersonColorAssigner @Inject constructor(
    private val profiles: ProfileRepository,
    private val partners: PartnerRepository,
) {
    private val lock = Mutex()

    suspend fun fillMissing() = lock.withLock {
        val profile = profiles.get()
        val all = partners.getAll().sortedWith(compareBy({ it.createdAt }, { it.id }))
        val people = listOf(ColorAssignment.Person(ProfileEntity.SELF_ID, profile?.color)) +
            all.map { ColorAssignment.Person(it.id, it.color, active = it.archivedAt == null) }
        val assigned = ColorAssignment.fill(people)
        if (assigned.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        assigned[ProfileEntity.SELF_ID]?.let { hex ->
            profiles.upsert((profile ?: ProfileEntity(updatedAt = now)).copy(color = hex, updatedAt = now))
        }
        all.forEach { p -> assigned[p.id]?.let { hex -> partners.upsert(p.copy(color = hex, updatedAt = now)) } }
    }

    /**
     * Who holds which color right now, as normalized "#RRGGBB" to the people using it, so the pickers can mark
     * taken swatches. Includes archived partners, since their color stays theirs.
     */
    fun observeOwners(): Flow<Map<String, List<ColorOwner>>> = combine(profiles.observe(), partners.observeAll()) { profile, all ->
        val self = listOf(ColorOwner(ProfileEntity.SELF_ID, profile?.displayName, profile?.color))
        val others = all.map { ColorOwner(it.id, if (it.isAnonymous) null else it.displayName, it.color) }
        (self + others)
            .mapNotNull { o -> PersonPalette.normalize(o.hex)?.let { it to o } }
            .groupBy({ it.first }, { it.second })
    }.catch { emit(emptyMap()) }

    /** The color a new partner would get, so the editor can show it picked before they're saved. */
    suspend fun suggestionForNew(): String {
        val existing = listOf(ColorAssignment.Person(ProfileEntity.SELF_ID, profiles.get()?.color)) +
            partners.getAll().map { ColorAssignment.Person(it.id, it.color, active = it.archivedAt == null) }
        return ColorAssignment.next(existing)
    }
}

/** One person holding a color. A null [name] shows as "You" for the self profile and "Anonymous" for a partner. */
data class ColorOwner(val id: String, val name: String?, val hex: String?) {
    val isSelf: Boolean get() = id == ProfileEntity.SELF_ID
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
