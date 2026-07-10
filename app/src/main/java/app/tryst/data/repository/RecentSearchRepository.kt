package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.RecentSearchEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The user's recent search queries, stored **in the encrypted DB** (D-42). Only queries the user
 * actually submits are recorded — not every keystroke — and the table is capped at [MAX_RECENTS].
 */
@Singleton
class RecentSearchRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().recentSearchDao()

    fun observeRecent(): Flow<List<RecentSearchEntity>> = dao.observeRecent(MAX_RECENTS)

    /** Records a submitted query (blank is ignored) and prunes the tail. */
    suspend fun record(query: String, now: Long = System.currentTimeMillis()) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(RecentSearchEntity(query = trimmed, lastUsedAt = now))
        dao.trimTo(MAX_RECENTS)
    }

    suspend fun delete(query: String) = dao.delete(query)

    suspend fun clear() = dao.clear()

    companion object {
        /** How many recent queries to keep (and show). */
        const val MAX_RECENTS = 8
    }
}
