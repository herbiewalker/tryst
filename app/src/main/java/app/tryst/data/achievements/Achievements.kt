package app.tryst.data.achievements

import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.Setting
import app.tryst.data.db.relation.EncounterWithDetails

/**
 * The curated achievement catalog. Ids are stable (don't rename). Keys for [Distinct] rules use raw
 * stored ids/enum names — distinctness only needs identity, not display labels. Emoji badges keep it
 * dependency-free and on-theme (swappable for custom art later, like the act icons).
 */
object Achievements {

    private fun acts(e: EncounterWithDetails): Set<String> = (e.encounter.practicesPerformed ?: emptySet()) + (e.encounter.practicesReceived ?: emptySet())

    private fun partnerOrgasms(e: EncounterWithDetails): Int = (e.encounter.partnerOrgasms?.values?.sum() ?: 0) + (e.encounter.orgasmCountPartner ?: 0)

    private fun hasOccasion(e: EncounterWithDetails, vararg occasions: Occasion): Boolean = e.encounter.occasions?.any { it in occasions } == true

    private fun hasContext(e: EncounterWithDetails, vararg settings: Setting): Boolean = e.encounter.contexts?.any { it in settings } == true

    val catalog: List<AchievementDef> = listOf(
        // --- Milestones: total trysts logged ---
        AchievementDef("m_first", "First Time", "Log your very first tryst.", AchievementCategory.MILESTONES, 1, "🌱", Count { true }),
        AchievementDef("m_5", "Warming Up", "Log 5 trysts.", AchievementCategory.MILESTONES, 5, "🌶️", Count { true }),
        AchievementDef("m_10", "Getting Started", "Log 10 trysts.", AchievementCategory.MILESTONES, 10, "🔥", Count { true }),
        AchievementDef("m_25", "Regular", "Log 25 trysts.", AchievementCategory.MILESTONES, 25, "✨", Count { true }),
        AchievementDef("m_50", "Half Century", "Log 50 trysts.", AchievementCategory.MILESTONES, 50, "🏅", Count { true }),
        AchievementDef("m_100", "Centurion", "Log 100 trysts.", AchievementCategory.MILESTONES, 100, "💯", Count { true }),
        AchievementDef("m_250", "Devoted", "Log 250 trysts.", AchievementCategory.MILESTONES, 250, "🏆", Count { true }),
        AchievementDef("m_500", "Legend", "Log 500 trysts.", AchievementCategory.MILESTONES, 500, "👑", Count { true }),
        AchievementDef("m_1000", "Hall of Fame", "Log 1,000 trysts.", AchievementCategory.MILESTONES, 1000, "🗿", Count { true }),

        // --- Streaks: consecutive active weeks ---
        AchievementDef("s_2", "On a Roll", "Active 2 weeks in a row.", AchievementCategory.STREAKS, 2, "📅", Streak),
        AchievementDef("s_4", "Consistent", "Active 4 weeks in a row.", AchievementCategory.STREAKS, 4, "🔥", Streak),
        AchievementDef("s_8", "Dedicated", "Active 8 weeks in a row.", AchievementCategory.STREAKS, 8, "💪", Streak),
        AchievementDef("s_12", "Unstoppable", "Active 12 weeks in a row.", AchievementCategory.STREAKS, 12, "🚀", Streak),
        AchievementDef("s_26", "Half-Year Hero", "Active 26 weeks in a row.", AchievementCategory.STREAKS, 26, "🗓️", Streak),
        AchievementDef("s_52", "Year of Yes", "Active 52 weeks in a row.", AchievementCategory.STREAKS, 52, "🎍", Streak),

        // --- Variety: distinct things tried ---
        AchievementDef("v_acts5", "Explorer", "Try 5 different acts.", AchievementCategory.VARIETY, 5, "🧭", Distinct { e, _ -> acts(e) }),
        AchievementDef("v_acts15", "Adventurer", "Try 15 different acts.", AchievementCategory.VARIETY, 15, "🧗", Distinct { e, _ -> acts(e) }),
        AchievementDef("v_acts30", "Connoisseur", "Try 30 different acts.", AchievementCategory.VARIETY, 30, "🎓", Distinct { e, _ -> acts(e) }),
        AchievementDef("v_pos5", "Flexible", "Try 5 different positions.", AchievementCategory.VARIETY, 5, "🤸", Distinct { e, _ -> e.encounter.positions ?: emptySet() }),
        AchievementDef("v_pos15", "Contortionist", "Try 15 different positions.", AchievementCategory.VARIETY, 15, "🌀", Distinct { e, _ -> e.encounter.positions ?: emptySet() }),
        AchievementDef("v_pos25", "Acrobat", "Try 25 different positions.", AchievementCategory.VARIETY, 25, "🎪", Distinct { e, _ -> e.encounter.positions ?: emptySet() }),
        AchievementDef("v_partners3", "Social", "Be with 3 different partners.", AchievementCategory.VARIETY, 3, "👥", Distinct { e, _ -> e.partners.map { it.id }.toSet() }),
        AchievementDef("v_partners5", "Popular", "Be with 5 different partners.", AchievementCategory.VARIETY, 5, "💞", Distinct { e, _ -> e.partners.map { it.id }.toSet() }),
        AchievementDef("v_partners10", "Casanova", "Be with 10 different partners.", AchievementCategory.VARIETY, 10, "🕶️", Distinct { e, _ -> e.partners.map { it.id }.toSet() }),
        AchievementDef("v_places5", "Globetrotter", "Get busy in 5 different places.", AchievementCategory.VARIETY, 5, "🌍", Distinct { e, _ -> e.encounter.contexts?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_places10", "Anywhere & Everywhere", "10 different places.", AchievementCategory.VARIETY, 10, "🌎", Distinct { e, _ -> e.encounter.contexts?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_kinks5", "Curious", "Explore 5 different kinks.", AchievementCategory.VARIETY, 5, "😈", Distinct { e, _ -> e.encounter.kinks?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_kinks10", "Kinkster", "Explore 10 different kinks.", AchievementCategory.VARIETY, 10, "🖤", Distinct { e, _ -> e.encounter.kinks?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_kinks25", "Deviant", "Explore 25 different kinks.", AchievementCategory.VARIETY, 25, "🔥", Distinct { e, _ -> e.encounter.kinks?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_toys5", "Toy Box", "Use 5 different toys.", AchievementCategory.VARIETY, 5, "🧸", Distinct { e, _ -> e.encounter.toys?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_toys10", "Toy Collector", "Use 10 different toys.", AchievementCategory.VARIETY, 10, "🪀", Distinct { e, _ -> e.encounter.toys?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_moods10", "Mood Ring", "Log 10 different moods.", AchievementCategory.VARIETY, 10, "🎭", Distinct { e, _ -> e.encounter.mood?.let { setOf(it.name) } ?: emptySet() }),
        AchievementDef("v_protection4", "Safety First Class", "Use 4 different kinds of protection.", AchievementCategory.VARIETY, 4, "🛡️", Distinct { e, _ -> e.encounter.protectionUsed.filter { it != Protection.NONE }.map { it.name }.toSet() }),
        AchievementDef("v_finish6", "Sharpshooter", "Finish in 6 different places.", AchievementCategory.VARIETY, 6, "🎯", Distinct { e, _ -> e.encounter.ejaculationLocations?.values?.filter { it != EjaculationLocation.NONE }?.map { it.name }?.toSet() ?: emptySet() }),
        AchievementDef("v_week", "Week Complete", "Log on all 7 days of the week.", AchievementCategory.VARIETY, 7, "🗓️", Distinct { _, d -> setOf(d.dayOfWeek.name) }),
        AchievementDef("v_year", "All Year Round", "Log in all 12 months.", AchievementCategory.VARIETY, 12, "🎆", Distinct { _, d -> setOf(d.month.name) }),

        // --- Pleasure ---
        AchievementDef("p_self10", "Good Vibes", "Reach 10 of your own orgasms.", AchievementCategory.PLEASURE, 10, "💫", Sum { it.encounter.orgasmCountSelf ?: 0 }),
        AchievementDef("p_self50", "Cloud Nine", "Reach 50 of your own orgasms.", AchievementCategory.PLEASURE, 50, "☁️", Sum { it.encounter.orgasmCountSelf ?: 0 }),
        AchievementDef("p_self100", "Bliss Centurion", "Reach 100 of your own orgasms.", AchievementCategory.PLEASURE, 100, "🌟", Sum { it.encounter.orgasmCountSelf ?: 0 }),
        AchievementDef("p_self250", "Ecstasy", "Reach 250 of your own orgasms.", AchievementCategory.PLEASURE, 250, "🌈", Sum { it.encounter.orgasmCountSelf ?: 0 }),
        AchievementDef("p_partner10", "Giver", "Give 10 partner orgasms.", AchievementCategory.PLEASURE, 10, "🎁", Sum { partnerOrgasms(it) }),
        AchievementDef("p_partner50", "Generous Lover", "Give 50 partner orgasms.", AchievementCategory.PLEASURE, 50, "💝", Sum { partnerOrgasms(it) }),
        AchievementDef("p_partner100", "Pleasure Giver", "Give 100 partner orgasms.", AchievementCategory.PLEASURE, 100, "🍯", Sum { partnerOrgasms(it) }),
        AchievementDef("p_combo", "Hat Trick", "Have 3 orgasms in one session.", AchievementCategory.PLEASURE, 1, "🎩", Count { (it.encounter.orgasmCountSelf ?: 0) >= 3 }),
        AchievementDef("p_multi", "Fireworks", "Have 5 orgasms in one session.", AchievementCategory.PLEASURE, 1, "🌋", Count { (it.encounter.orgasmCountSelf ?: 0) >= 5 }),
        AchievementDef("p_5star1", "Perfect Night", "Rate a tryst 5 stars.", AchievementCategory.PLEASURE, 1, "⭐", Count { it.encounter.satisfactionRating == 5 }),
        AchievementDef("p_5star10", "Five-Star Lover", "Rate 10 trysts 5 stars.", AchievementCategory.PLEASURE, 10, "🌠", Count { it.encounter.satisfactionRating == 5 }),
        AchievementDef("p_5star25", "Critic's Choice", "Rate 25 trysts 5 stars.", AchievementCategory.PLEASURE, 25, "🏵️", Count { it.encounter.satisfactionRating == 5 }),

        // --- Occasions & places ---
        AchievementDef("o_morning", "Early Bird", "Log morning or wake-up sex.", AchievementCategory.OCCASIONS, 1, "🌅", Count { hasOccasion(it, Occasion.MORNING_SEX, Occasion.WAKE_UP_SEX) }),
        AchievementDef("o_makeup", "Kiss & Makeup", "Log makeup sex.", AchievementCategory.OCCASIONS, 1, "💋", Count { hasOccasion(it, Occasion.MAKEUP_SEX) }),
        AchievementDef("o_quickie", "In a Hurry", "Log a quickie.", AchievementCategory.OCCASIONS, 1, "⚡", Count { hasOccasion(it, Occasion.QUICKIE) }),
        AchievementDef("o_datenight", "Date Night", "Log a date-night tryst.", AchievementCategory.OCCASIONS, 1, "🍷", Count { hasOccasion(it, Occasion.DATE_NIGHT) }),
        AchievementDef("o_special", "Special Occasion", "Log an anniversary or birthday tryst.", AchievementCategory.OCCASIONS, 1, "🎉", Count { hasOccasion(it, Occasion.ANNIVERSARY, Occasion.BIRTHDAY) }),
        AchievementDef("o_vacation", "Vacation Mode", "Get busy on vacation.", AchievementCategory.OCCASIONS, 1, "🏝️", Count { hasContext(it, Setting.VACATION) }),
        AchievementDef("o_public", "Thrill Seeker", "Log a public or semi-public tryst.", AchievementCategory.OCCASIONS, 1, "😳", Count { hasContext(it, Setting.PUBLIC, Setting.SEMI_PUBLIC) }),
        AchievementDef("o_outdoors", "Wild Side", "Get busy outdoors.", AchievementCategory.OCCASIONS, 1, "🌲", Count { hasContext(it, Setting.OUTDOORS, Setting.NATURE, Setting.BEACH) }),
        AchievementDef("o_car", "Backseat", "Log a tryst in a car.", AchievementCategory.OCCASIONS, 1, "🚗", Count { hasContext(it, Setting.CAR) }),

        // --- Odds and ends ---
        AchievementDef("x_photo", "Picture Perfect", "Attach a photo to a tryst.", AchievementCategory.MISC, 1, "📷", Count { it.media.isNotEmpty() }),
        AchievementDef("x_photos10", "Shutterbug", "Attach photos to 10 trysts.", AchievementCategory.MISC, 10, "📸", Count { it.media.isNotEmpty() }),
        AchievementDef("x_marathon", "Marathon", "Log a 60-minute-plus session.", AchievementCategory.MISC, 1, "⏱️", Count { (it.encounter.durationMin ?: 0) >= 60 }),
        AchievementDef("x_solo", "Me Time", "Log a solo session.", AchievementCategory.MISC, 1, "🧘", Count { it.partners.isEmpty() }),
        AchievementDef("x_solo25", "Self-Love", "Log 25 solo sessions.", AchievementCategory.MISC, 25, "💖", Count { it.partners.isEmpty() }),
        AchievementDef("x_note", "Dear Diary", "Write a note on a tryst.", AchievementCategory.MISC, 1, "📔", Count { !it.encounter.note.isNullOrBlank() }),
        AchievementDef("x_storyteller", "Storyteller", "Write notes on 25 trysts.", AchievementCategory.MISC, 25, "✍️", Count { !it.encounter.note.isNullOrBlank() }),
        AchievementDef("x_safe", "Play It Safe", "Use protection on 25 trysts.", AchievementCategory.MISC, 25, "🦺", Count { e -> e.encounter.protectionUsed.any { it != Protection.NONE } }),
        AchievementDef("x_initme", "Go-Getter", "Initiate 25 trysts.", AchievementCategory.MISC, 25, "🏹", Count { it.encounter.initiator == Initiator.ME }),
        AchievementDef("x_mutual", "In Sync", "Log 10 mutually-initiated trysts.", AchievementCategory.MISC, 10, "🤝", Count { it.encounter.initiator == Initiator.MUTUAL }),
    )
}
