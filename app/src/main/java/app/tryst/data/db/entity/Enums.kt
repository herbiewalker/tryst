package app.tryst.data.db.entity

/** Implemented by every category enum so the UI can show a curated, human-written label. */
interface DisplayLabel {
    val label: String
}

/** Who initiated the encounter. */
enum class Initiator(override val label: String) : DisplayLabel {
    ME("Me"),
    PARTNER("Partner"),
    MUTUAL("Mutual"),
}

/** Mood / vibe of the encounter. Order here is the display order. */
enum class Mood(override val label: String) : DisplayLabel {
    AMAZING("Amazing"),
    EUPHORIC("Euphoric"),
    PASSIONATE("Passionate"),
    HORNY("Horny"),
    SENSUAL("Sensual"),
    WILD("Wild"),
    ADVENTUROUS("Adventurous"),
    KINKY("Kinky"),
    NAUGHTY("Naughty"),
    PLAYFUL("Playful"),
    ROMANTIC("Romantic"),
    INTIMATE("Intimate"),
    TENDER("Tender"),
    CONNECTED("Connected"),
    LOVED("Loved"),
    DESIRED("Desired"),
    CONFIDENT("Confident"),
    SAFE("Safe"),
    VULNERABLE("Vulnerable"),
    EMOTIONAL("Emotional"),
    CURIOUS("Curious"),
    SPONTANEOUS("Spontaneous"),
    TIPSY("Tipsy / high"),
    SATISFIED("Satisfied"),
    RELAXED("Relaxed"),
    SLEEPY("Sleepy"),
    GOOD("Good"),
    NEUTRAL("Neutral"),
    BORED("Bored"),
    AWKWARD("Awkward"),
    MEH("Meh"),
    FRUSTRATED("Frustrated"),
    DISAPPOINTED("Disappointed"),
    BAD("Bad"),
}

/** Legacy "who orgasmed" marker (M3). Superseded by per-person orgasm counts; kept for migration. */
enum class Orgasm { NONE, SELF, PARTNER, BOTH }

/** Partner biological sex. */
enum class Sex(override val label: String) : DisplayLabel {
    MALE("Male"),
    FEMALE("Female"),
    INTERSEX("Intersex"),
}

/** Partner gender identity. */
enum class Gender(override val label: String) : DisplayLabel {
    MAN("Man"),
    WOMAN("Woman"),
    NON_BINARY("Non-binary"),
    OTHER("Other"),
}

/** Partner / self ethnicity (single-select demographic). */
enum class Ethnicity(override val label: String) : DisplayLabel {
    WHITE("White"),
    BLACK("Black"),
    LATINO("Latino / Hispanic"),
    EAST_ASIAN("East Asian"),
    SOUTH_ASIAN("South Asian"),
    SOUTHEAST_ASIAN("Southeast Asian"),
    MIDDLE_EASTERN("Middle Eastern"),
    NATIVE_AMERICAN("Native American"),
    PACIFIC_ISLANDER("Pacific Islander"),
    MIXED("Mixed"),
    OTHER("Other"),
}

/** Partner / self body type (single-select demographic). */
enum class BodyType(override val label: String) : DisplayLabel {
    SLIM("Slim"),
    PETITE("Petite"),
    ATHLETIC("Athletic / fit"),
    AVERAGE("Average"),
    CURVY("Curvy"),
    MUSCULAR("Muscular"),
    STOCKY("Stocky"),
    PLUS_SIZE("Plus-size"),
    TALL("Tall"),
    OTHER("Other"),
}

/** How the user relates to a partner. */
enum class RelationshipType(override val label: String) : DisplayLabel {
    SPOUSE("Spouse"),
    PARTNER("Long-term partner"),
    GIRLFRIEND("Girlfriend"),
    BOYFRIEND("Boyfriend"),
    DATING("Dating"),
    FWB("Friends with benefits"),
    CASUAL("Casual / hookup"),
    ONE_NIGHT_STAND("One-night stand"),
    OPEN_POLY("Open / poly partner"),
    EX("Ex"),
    FRIEND("Friend"),
    ACQUAINTANCE("Acquaintance"),
    STRANGER("Stranger"),
    SEX_WORKER("Sex worker"),
    OTHER("Other"),
}

/** Protection / contraception / STI prevention used (multi-select). */
enum class Protection(override val label: String) : DisplayLabel {
    NONE("None"),
    CONDOM("Condom"),
    INTERNAL_CONDOM("Internal condom"),
    DENTAL_DAM("Dental dam"),
    PILL("Pill"),
    IUD("IUD"),
    IMPLANT("Implant"),
    PATCH("Patch"),
    VAGINAL_RING("Vaginal ring"),
    INJECTION("Injection"),
    DIAPHRAGM("Diaphragm"),
    CERVICAL_CAP("Cervical cap"),
    SPERMICIDE("Spermicide"),
    SPONGE("Sponge"),
    FERTILITY_AWARENESS("Fertility awareness"),
    WITHDRAWAL("Withdrawal (pull-out)"),
    VASECTOMY("Vasectomy"),
    TUBAL_LIGATION("Tubal ligation"),
    PREP("PrEP"),
    PEP("PEP"),
    DOXY_PEP("DoxyPEP"),
    EMERGENCY_CONTRACEPTION("Emergency contraception"),
    OTHER("Other"),
}

/**
 * Where ejaculation happened (per-orgasm multi-select). User-configurable & id-based (FDP-5) —
 * **intentionally empty** enum; every finish location, including the two neutral starters, is a
 * user-owned [EjaculationLocationEntity] row seeded by [CatalogSeeds] and fully editable/removable.
 */
enum class EjaculationLocation(override val label: String) : DisplayLabel

/**
 * Physical sex acts. Tracked as two sets per encounter: gave and received; every act is a string id.
 *
 * **Intentionally empty** — Tryst ships **no compiled-in acts** (F-Droid content policy, D-41 / FDP-5).
 * Every act, including the couple of neutral starters, is a user-owned [ActEntity] row (`custom:<id>`)
 * seeded by [CatalogSeeds] and fully editable/removable. The enum type is kept only as the (now empty)
 * built-in id namespace so `.entries` and [app.tryst.data.db.CatalogAdoption] keep compiling.
 */
enum class Act(override val label: String) : DisplayLabel

/**
 * Kink / BDSM (single multi-select; gave/received doesn't meaningfully apply).
 *
 * Like [Act], **intentionally empty** — Tryst ships **no predefined kinks** (F-Droid content policy,
 * D-41 / FDP-5). Every kink is user data via Settings → Manage kinks (a [KinkEntity] custom row);
 * previously-built-in ids a user logged are adopted as custom rows by `MIGRATION_11_12`.
 */
enum class Kink(override val label: String) : DisplayLabel

/** Place where the encounter happened (single multi-select). */
enum class Place(override val label: String) : DisplayLabel {
    HOME("Home"),
    BEDROOM("Bedroom"),
    LIVING_ROOM("Living room"),
    KITCHEN("Kitchen"),
    BATHROOM("Bathroom"),
    SHOWER("Shower"),
    BATH("Bath"),
    BALCONY("Balcony"),
    BACKYARD("Backyard"),
    ROOFTOP("Rooftop"),
    CAR("Car"),
    HOTEL("Hotel"),
    AIRBNB("Airbnb"),
    FRIENDS_FAMILY("Friend / family's place"),
    VACATION("Vacation"),
    OUTDOORS("Outdoors"),
    NATURE("Woods / nature"),
    BEACH("Beach"),
    CAMPING("Tent / camping"),
    BOAT("Boat"),
    POOL("Pool"),
    HOT_TUB("Hot tub / sauna"),
    GYM("Gym"),
    OFFICE("Office / work"),
    CINEMA("Cinema"),
    CHANGING_ROOM("Changing room"),
    PUBLIC_RESTROOM("Public restroom"),
    PARKING_LOT("Parking lot"),
    ELEVATOR("Elevator"),
    BAR_CLUB("Bar / club"),
    SEX_CLUB("Sex club"),
    PARTY("Party"),
    PUBLIC("Public"),
    SEMI_PUBLIC("Semi-public"),
    OTHER("Other"),
}

/**
 * Occasion / context of the encounter (multi-select). User-configurable & id-based (FDP-5) —
 * **intentionally empty** enum; every occasion, including the two neutral calendar starters, is a
 * user-owned [OccasionEntity] row seeded by [CatalogSeeds] and fully editable/removable.
 */
enum class Occasion(override val label: String) : DisplayLabel

/**
 * Toys used (multi-select). Like [Act]/[Kink], **intentionally empty** — Tryst ships **no predefined
 * toys** (F-Droid content policy, D-41 / FDP-5). Every toy is user data via Settings → Manage toys (a
 * [ToyEntity] custom row); previously-built-in ids a user logged are adopted by `MIGRATION_11_12`.
 */
enum class ToyType(override val label: String) : DisplayLabel

/**
 * Sex positions. **Intentionally empty** — Tryst ships **no predefined positions** (F-Droid content
 * policy, D-41 / FDP-5). Every position is user data via Settings → Manage positions (a
 * [PositionEntity] custom row); previously-built-in ids a user logged are adopted by `MIGRATION_11_12`.
 */
enum class Position(override val label: String) : DisplayLabel
