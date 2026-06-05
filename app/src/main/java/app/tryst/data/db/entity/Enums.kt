package app.tryst.data.db.entity

/** Who initiated the encounter. */
enum class Initiator { ME, PARTNER, MUTUAL }

/** Coarse mood/quality marker. Curated small set; refine during UI design (M3). */
enum class Mood { AMAZING, GOOD, NEUTRAL, MEH, BAD }

/** Orgasm outcome. */
enum class Orgasm { NONE, SELF, PARTNER, BOTH }

/** Protection used. Stored as a set on an encounter. */
enum class Protection { NONE, CONDOM, BIRTH_CONTROL, PREP, OTHER }
