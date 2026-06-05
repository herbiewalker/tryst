package app.tryst.data.db.entity

/** Who initiated the encounter. */
enum class Initiator { ME, PARTNER, MUTUAL }

/** Mood / vibe of the encounter. Order here is the display order. */
enum class Mood {
    AMAZING,
    PASSIONATE,
    PLAYFUL,
    ROMANTIC,
    CONNECTED,
    ADVENTUROUS,
    RELAXED,
    GOOD,
    NEUTRAL,
    MEH,
    BAD,
}

/** Legacy "who orgasmed" marker (M3). Superseded by per-person orgasm counts on the encounter;
 *  retained so the v1 column survives migration. Not shown in the UI. */
enum class Orgasm { NONE, SELF, PARTNER, BOTH }

/** Protection / contraception used (multi-select). */
enum class Protection {
    NONE,
    CONDOM,
    INTERNAL_CONDOM,
    DENTAL_DAM,
    BIRTH_CONTROL,
    IUD,
    IMPLANT,
    PREP,
    WITHDRAWAL,
    EMERGENCY_CONTRACEPTION,
    OTHER,
}

/** Where ejaculation happened (multi-select; an encounter can have more than one). */
enum class EjaculationLocation {
    NONE,
    IN_CONDOM,
    VAGINAL,
    ANAL,
    ORAL,
    ON_BODY,
    ON_CHEST,
    ON_FACE,
    ON_BACK,
    OTHER,
}

/** A sex type / practice. Tracked as two sets per encounter: performed (gave) and received (got). */
enum class Practice {
    KISSING,
    ORAL,
    MANUAL,
    VAGINAL,
    ANAL,
    TOYS,
    MASSAGE,
    MASTURBATION,
    ROLEPLAY,
    BONDAGE,
    OTHER,
}
