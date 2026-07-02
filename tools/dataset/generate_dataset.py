#!/usr/bin/env python3
"""
Tryst test-dataset generator.

Emits the *plaintext* contents of a Tryst backup container:

  <out>/data.json        — the generic table dump (matches schema v6, see docs/DATA_MODEL.md)
  <out>/media/<id>       — encrypted-at-rest-bound photo blobs, here just raw PNG bytes
                           (the Tink container re-encrypts them on the way in)

The encrypted `.tryst` file is produced from these by `pack_backup.java` (real Tink, so the
output is byte-for-byte what the app's BackupManager.export would write).

Run:  python3 generate_dataset.py <out-dir>

Everything is seeded, so re-running reproduces the same dataset.
"""
import json
import os
import random
import struct
import sys
import uuid
import zlib
from datetime import datetime, timedelta, timezone

SEED = 20260613
random.seed(SEED)

# Build identifiers deterministically so re-runs are stable.
_uuid_counter = [0]


def uid() -> str:
    _uuid_counter[0] += 1
    return str(uuid.UUID(int=(_uuid_counter[0] * 0x9E3779B97F4A7C15) & ((1 << 128) - 1)))


# --------------------------------------------------------------------------------------
# Category enum values — names copied verbatim from data/db/entity/Enums.kt.
# Parsing in the app skips unknown names, so these must match exactly.
# --------------------------------------------------------------------------------------
INITIATOR = ["ME", "PARTNER", "MUTUAL"]
MOOD = ["AMAZING", "EUPHORIC", "PASSIONATE", "HORNY", "SENSUAL", "WILD", "ADVENTUROUS",
        "KINKY", "NAUGHTY", "PLAYFUL", "ROMANTIC", "INTIMATE", "TENDER", "CONNECTED",
        "LOVED", "DESIRED", "CONFIDENT", "SAFE", "VULNERABLE", "EMOTIONAL", "CURIOUS",
        "SPONTANEOUS", "TIPSY", "SATISFIED", "RELAXED", "SLEEPY", "GOOD", "NEUTRAL",
        "BORED", "AWKWARD", "MEH", "FRUSTRATED", "DISAPPOINTED", "BAD"]
PROTECTION = ["NONE", "CONDOM", "INTERNAL_CONDOM", "DENTAL_DAM", "PILL", "IUD", "IMPLANT",
              "PATCH", "VAGINAL_RING", "INJECTION", "DIAPHRAGM", "CERVICAL_CAP",
              "SPERMICIDE", "SPONGE", "FERTILITY_AWARENESS", "WITHDRAWAL", "VASECTOMY",
              "TUBAL_LIGATION", "PREP", "PEP", "DOXY_PEP", "EMERGENCY_CONTRACEPTION", "OTHER"]
EJAC = ["NONE", "IN_CONDOM", "VAGINAL", "ON_VAGINA", "ANAL", "ORAL", "SWALLOWED", "ON_FACE",
        "ON_CHEST", "ON_BREASTS", "ON_STOMACH", "ON_BACK", "ON_BUTT", "ON_THIGHS",
        "ON_HANDS", "ON_HAIR", "ON_SELF", "ON_SHEETS", "ON_FLOOR", "ON_BODY", "IN_TOY", "OTHER"]
# v10 trimmed the shipped Act/Kink catalogs to a non-explicit starter set (FDP-2 / D-41);
# these pools mirror the surviving built-ins. Explicit entries are user-custom now.
PRACTICE = ["KISSING", "MAKING_OUT", "ORAL", "SIXTY_NINE", "MANUAL",
            "FINGERING", "VAGINAL", "ANAL", "PROSTATE_MASSAGE",
            "NIPPLE_PLAY", "BREAST_PLAY",
            "MASSAGE", "MUTUAL_MASTURBATION", "MASTURBATION", "CUDDLING", "OTHER"]
KINK = ["DOMINATION", "SUBMISSION", "BONDAGE", "RESTRAINTS",
        "SPANKING", "HAIR_PULLING",
        "BITING", "BLINDFOLD",
        "SENSORY_PLAY", "TEMPERATURE_PLAY",
        "EDGING", "PRAISE",
        "ROLEPLAY", "COSTUME_PLAY", "DIRTY_TALK", "AFTERCARE"]
SETTING = ["HOME", "BEDROOM", "LIVING_ROOM", "KITCHEN", "BATHROOM", "SHOWER", "BATH",
           "BALCONY", "BACKYARD", "ROOFTOP", "CAR", "HOTEL", "AIRBNB", "FRIENDS_FAMILY",
           "VACATION", "OUTDOORS",
           "NATURE", "BEACH", "CAMPING", "BOAT", "POOL", "HOT_TUB", "GYM", "OFFICE", "CINEMA",
           "CHANGING_ROOM", "PUBLIC_RESTROOM", "PARKING_LOT", "ELEVATOR", "BAR_CLUB",
           "SEX_CLUB", "PARTY", "PUBLIC", "SEMI_PUBLIC", "OTHER"]
OCCASION = ["NONE", "REGULAR", "QUICKIE", "MORNING_SEX", "WAKE_UP_SEX", "MAKEUP_SEX",
            "ANGRY_SEX", "PERIOD_SEX", "DRUNK_HIGH", "SPONTANEOUS", "DATE_NIGHT",
            "ANNIVERSARY", "BIRTHDAY", "REUNION", "PHONE_SEX", "SEXTING", "VIDEO_SEX", "OTHER"]
TOY = ["NONE", "VIBRATOR", "WAND", "AIR_PULSE", "DILDO", "DOUBLE_DILDO", "STROKER",
       "LOVE_EGG", "REMOTE_VIBE", "KEGEL_BALLS", "BUTT_PLUG", "ANAL_BEADS",
       "PROSTATE_MASSAGER", "COCK_RING", "PENIS_PUMP", "CHASTITY_CAGE", "STRAP_ON",
       "HARNESS", "NIPPLE_CLAMPS", "RESTRAINTS", "SPREADER_BAR", "BLINDFOLD", "GAG",
       "PADDLE", "FLOGGER", "FEATHER", "MASSAGE_CANDLE", "OTHER"]
POSITION = ["MISSIONARY", "DOGGY_STYLE", "COWGIRL", "REVERSE_COWGIRL", "SPOONING",
            "SIXTY_NINE", "STANDING", "STANDING_DOGGY", "SIDE_BY_SIDE", "SEATED", "STRADDLE",
            "LAP", "EDGE_OF_BED", "AGAINST_WALL", "BENT_OVER", "PRONE_BONE",
            "LEGS_ON_SHOULDERS", "COITAL_ALIGNMENT", "ANKLES_BACK", "PRETZEL", "ANVIL",
            "ANAL_MISSIONARY", "JOCKEY", "LOTUS", "BUTTERFLY", "BRIDGE", "EAGLE", "AMAZON",
            "CRADLE", "CRAB", "FROG", "FLATIRON", "PILEDRIVER", "WHEELBARROW", "SCISSORING",
            "FACE_SITTING", "KNEELING_ORAL", "STANDING_ORAL", "LYING_ORAL", "ORAL_EDGE_OF_BED",
            "ORAL_THRONE", "SPIT_ROAST", "SANDWICH", "DAISY_CHAIN", "EIFFEL_TOWER",
            "TABLE_TOP", "CHAIR", "MODIFIED_MISSIONARY", "MISSIONARY_STANDING_EDGE",
            "REVERSE_COWGIRL_MODIFIED", "ANAL_TOY", "OTHER"]

# "Spice tier" classification (1 vanilla .. 3 heavy). Used to keep combos plausible: a value
# is only placed on an encounter whose tier >= its own. Anything unclassified defaults to 1.
KINK_TIER = {k: 3 for k in KINK}
for k in ["PRAISE", "AFTERCARE", "DIRTY_TALK", "ROLEPLAY", "BITING",
          "HAIR_PULLING", "BLINDFOLD", "SENSORY_PLAY",
          "COSTUME_PLAY", "EDGING", "TEMPERATURE_PLAY"]:
    KINK_TIER[k] = 2
for k in ["SUBMISSION", "DOMINATION", "SPANKING", "RESTRAINTS", "BONDAGE"]:
    KINK_TIER[k] = 2  # common-enough kinks live at tier 2

PRACTICE_TIER = {p: 1 for p in PRACTICE}
for p in ["ANAL", "PROSTATE_MASSAGE"]:
    PRACTICE_TIER[p] = 3
SOLO_PRACTICE = ["MASTURBATION", "OTHER", "NIPPLE_PLAY"]

# Toys that only make sense with no condom/penetration distinctions aside, all fine solo or not.
SOLO_TOY = ["VIBRATOR", "WAND", "AIR_PULSE", "DILDO", "STROKER", "LOVE_EGG", "REMOTE_VIBE",
            "KEGEL_BALLS", "BUTT_PLUG", "ANAL_BEADS", "PROSTATE_MASSAGER", "COCK_RING",
            "PENIS_PUMP", "CHASTITY_CAGE", "FEATHER", "MASSAGE_CANDLE", "NONE", "OTHER"]

EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def millis(dt: datetime) -> int:
    return int((dt - EPOCH).total_seconds() * 1000)


# --------------------------------------------------------------------------------------
# Minimal PNG encoder (RGB, no dependencies) — generates a small, visually-distinct tile.
# --------------------------------------------------------------------------------------
def _chunk(typ: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)


def make_png(size: int, top: tuple, bottom: tuple, accent: tuple | None = None) -> bytes:
    """A vertical gradient from `top` to `bottom`, with an optional diagonal accent band."""
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type 0
        t = y / max(1, size - 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        for x in range(size):
            if accent and abs((x + y) % size - size // 2) < 4:
                raw += bytes(accent)
            else:
                raw += bytes((r, g, b))
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    return sig + _chunk(b"IHDR", ihdr) + _chunk(b"IDAT", idat) + _chunk(b"IEND", b"")


def rand_color() -> tuple:
    return (random.randint(40, 220), random.randint(40, 220), random.randint(40, 220))


# --------------------------------------------------------------------------------------
# Partners — one spouse plus a sensible spread (4-8 total). Each gets a photo (avatar).
# --------------------------------------------------------------------------------------
now = datetime(2026, 6, 13, tzinfo=timezone.utc)
base_created = millis(datetime(2025, 11, 1, tzinfo=timezone.utc))

PARTNER_DEFS = [
    # name, sex, gender, relationship, tier-bias, note, avatar colors
    ("Jordan", "FEMALE", "WOMAN", "SPOUSE", [1, 1, 2, 2, 3], "My wife. Together 9 years, married 5.",
     ((232, 120, 150), (150, 40, 90))),
    ("Riley", "FEMALE", "WOMAN", "OPEN_POLY", [2, 2, 3, 3], "Poly partner — we opened things up last year.",
     ((120, 170, 235), (40, 70, 150))),
    ("Alex", "MALE", "MAN", "FWB", [2, 2, 3, 3], "FWB from the climbing gym.",
     ((140, 210, 150), (40, 110, 70))),
    ("Sam", "INTERSEX", "NON_BINARY", "CASUAL", [2, 3, 3], "Met at the bar / club. Adventurous.",
     ((220, 180, 90), (150, 100, 20))),
    ("Morgan", "FEMALE", "WOMAN", "EX", [1, 2, 3], "Ex. It's complicated, occasionally rekindled.",
     ((200, 140, 220), (110, 50, 140))),
    ("Taylor", "MALE", "MAN", "ONE_NIGHT_STAND", [2, 3], "One wild night on a work trip.",
     ((120, 210, 210), (30, 120, 120))),
]

partners = []
partner_index = {}  # name -> dict
media_files = {}  # id -> png bytes  (every blob that ends up in the container)
media_rows = []  # rows for the `media` table (encounter photos only; avatars have no row)

for name, sex, gender, rel, bias, note, (c1, c2) in PARTNER_DEFS:
    pid = uid()
    avatar_id = uid()
    media_files[avatar_id] = make_png(160, c1, c2, accent=(255, 255, 255))
    p = {
        "id": pid,
        "displayName": name,
        "isAnonymous": 0,
        "color": "#%02X%02X%02X" % c1,
        "note": note,
        "sex": sex,
        "gender": gender,
        "relationshipType": rel,
        "photoMediaId": avatar_id,
        "archivedAt": None,
        "createdAt": base_created,
        "updatedAt": base_created,
    }
    partners.append(p)
    partner_index[name] = {"row": p, "bias": bias, "id": pid}

# One anonymous partner with NO photo, to exercise the anonymous + no-avatar path.
anon_id = uid()
partners.append({
    "id": anon_id, "displayName": None, "isAnonymous": 1, "color": None,
    "note": "Anonymous — didn't catch a name.", "sex": "MALE", "gender": "MAN",
    "relationshipType": "STRANGER", "photoMediaId": None,
    "archivedAt": None, "createdAt": base_created, "updatedAt": base_created,
})
partner_index["_anon"] = {"row": partners[-1], "bias": [2, 3], "id": anon_id}

# --------------------------------------------------------------------------------------
# Build encounter skeletons: dates (with same-day clusters), partner/solo assignment, tier.
# --------------------------------------------------------------------------------------
start_day = datetime(2025, 12, 15, tzinfo=timezone.utc)
end_day = datetime(2026, 6, 12, tzinfo=timezone.utc)
TARGET = 168

# A weighted pool of who an encounter is "with". "_solo" => no partner.
PARTNERED = ["Jordan", "Jordan", "Jordan", "Jordan", "Riley", "Riley", "Alex", "Alex",
             "Sam", "Morgan", "Taylor", "_anon"]

skeletons = []  # each: {date, partners:[names], tier}
day = start_day
forced_triples = 0
while day <= end_day and len(skeletons) < TARGET:
    roll = random.random()
    if roll < 0.55:
        count = 0
    elif roll < 0.82:
        count = 1
    elif roll < 0.95:
        count = 2
    else:
        count = 3
        forced_triples += 1
    for _ in range(count):
        if len(skeletons) >= TARGET:
            break
        r = random.random()
        if r < 0.11:
            who = ["_solo"]
        elif r < 0.15:
            # threesome / two partners on one encounter (M:N + group kinks)
            who = random.sample(["Jordan", "Riley", "Alex", "Sam", "Taylor"], 2)
        else:
            who = [random.choice(PARTNERED)]
        skeletons.append({"day": day, "who": who})
    day += timedelta(days=1)

# Guarantee a few explicit same-day clusters near the end if the random walk was thin.
while len([s for s in skeletons if True]) < 156:
    skeletons.append({"day": end_day - timedelta(days=random.randint(0, 30)),
                      "who": [random.choice(PARTNERED)]})

# --------------------------------------------------------------------------------------
# Coverage trackers: every value in every category must be used at least once.
# --------------------------------------------------------------------------------------
covered = {name: set() for name in
           ["mood", "protection", "ejac", "practice", "kink", "setting", "occasion", "toy",
            "position", "initiator"]}


def pick(values, k, tier=3, value_tier=None):
    """Pick up to k values whose tier <= encounter tier."""
    pool = [v for v in values if (value_tier or {}).get(v, 1) <= tier]
    if not pool:
        pool = values
    k = min(k, len(pool))
    return random.sample(pool, k) if k > 0 else []


# Occasion -> sensible time-of-day + duration band.
def duration_for(occasion, tier, solo):
    if occasion in ("QUICKIE",):
        return random.randint(4, 14)
    if occasion in ("MORNING_SEX", "WAKE_UP_SEX"):
        return random.randint(10, 28)
    if occasion in ("PHONE_SEX", "SEXTING", "VIDEO_SEX"):
        return random.randint(8, 35)
    if occasion in ("DATE_NIGHT", "ANNIVERSARY", "BIRTHDAY", "REUNION"):
        return random.randint(55, 135)
    if solo:
        return random.randint(5, 30)
    return random.randint(20, 75)


def time_for(occasion):
    if occasion in ("MORNING_SEX", "WAKE_UP_SEX"):
        return random.randint(6, 9), random.randint(0, 59)
    if occasion in ("QUICKIE",):
        return random.choice([7, 8, 12, 13, 18, 22]), random.randint(0, 59)
    return random.randint(20, 23), random.randint(0, 59)


encounters = []
enc_partner_rows = []

for sk in skeletons:
    solo = sk["who"] == ["_solo"]
    pnames = [] if solo else sk["who"]
    # Tier: spouse-leaning encounters skew vanilla; others spicier. Mixed = max of involved.
    if solo:
        tier = random.choice([1, 2, 2, 3])
    else:
        tier = max(random.choice(partner_index[n]["bias"]) for n in pnames)

    occasion_pool = ["REGULAR", "REGULAR", "QUICKIE", "MORNING_SEX", "WAKE_UP_SEX",
                     "SPONTANEOUS", "DATE_NIGHT", "DRUNK_HIGH", "PERIOD_SEX", "MAKEUP_SEX"]
    if solo:
        occasion_pool = ["REGULAR", "QUICKIE", "SPONTANEOUS", "MORNING_SEX", "NONE",
                         "PHONE_SEX", "SEXTING", "VIDEO_SEX"]
    occasion = random.choice(occasion_pool)
    hh, mm = time_for(occasion)
    dt = sk["day"].replace(hour=hh, minute=mm)
    created = millis(dt) + random.randint(60_000, 3_600_000)

    # Mood: tier-appropriate-ish but mood isn't really gated; pick 1 primary.
    mood = random.choice(MOOD)

    # Practices given / received.
    if solo:
        perf = pick(SOLO_PRACTICE, random.randint(1, 2))
        recv = []
    else:
        perf = pick(PRACTICE, random.randint(1, 3), tier, PRACTICE_TIER)
        recv = pick(PRACTICE, random.randint(1, 3), tier, PRACTICE_TIER)
    positions = [] if solo else pick(POSITION, random.randint(1, 4), tier)
    kinks = pick(KINK, random.randint(0, 3) if tier >= 2 else random.randint(0, 1), tier, KINK_TIER)
    settings = pick(SETTING, random.randint(1, 2), tier)
    toys = pick(SOLO_TOY if solo else TOY, random.randint(0, 2), tier)

    # Protection: sensible. Spouse often PILL/IUD/none; casual leans CONDOM/PREP.
    if solo:
        prot = random.choice([["NONE"], [], ["NONE"]])
    elif "Jordan" in pnames:
        prot = random.choice([["IUD"], ["PILL"], ["NONE"], ["WITHDRAWAL"], ["PILL", "WITHDRAWAL"]])
    else:
        prot = random.choice([["CONDOM"], ["CONDOM", "PREP"], ["PREP"], ["CONDOM", "PILL"],
                              ["INTERNAL_CONDOM"], ["NONE"]])

    # Orgasms.
    orgasm_self = random.choice([0, 1, 1, 1, 2, 2, 3])
    ejac = {}
    if orgasm_self > 0 and random.random() < 0.7:
        locs = pick(EJAC, orgasm_self)
        for i in range(orgasm_self):
            ejac[i] = locs[i % len(locs)] if locs else "NONE"

    partner_orgasms = {}
    if not solo:
        for n in pnames:
            partner_orgasms[partner_index[n]["id"]] = random.choice([0, 1, 1, 2, 2, 3])

    rating = None
    if random.random() < 0.9:
        base = {1: [2, 3, 3, 4], 2: [3, 4, 4, 5], 3: [3, 4, 5, 5]}[tier]
        rating = random.choice(base)

    initiator = None if solo else random.choice(INITIATOR)

    eid = uid()
    enc = {
        "id": eid,
        "startAt": millis(dt),
        "durationMin": duration_for(occasion, tier, solo),
        "note": None,
        "satisfactionRating": rating,
        "orgasm": None,
        "mood": mood,
        "initiator": initiator,
        "protectionUsed": ",".join(prot),
        "orgasmCountSelf": orgasm_self,
        "orgasmCountPartner": None,
        "ejaculationLocations": ",".join(f"{i}={loc}" for i, loc in ejac.items()) or None,
        "practicesPerformed": ",".join(perf) or None,
        "practicesReceived": ",".join(recv) or None,
        "positions": ",".join(positions) or None,
        "kinks": ",".join(kinks) or None,
        "contexts": ",".join(settings) or None,
        "toys": ",".join(toys) or None,
        "occasions": occasion,
        "partnerOrgasms": ",".join(f"{k}={v}" for k, v in partner_orgasms.items()) or None,
        "locationId": None,
        "createdAt": created,
        "updatedAt": created,
        "_tier": tier,
        "_solo": solo,
    }
    encounters.append(enc)
    for n in pnames:
        enc_partner_rows.append({"encounterId": eid, "partnerId": partner_index[n]["id"]})

    # Track coverage.
    covered["mood"].add(mood)
    covered["protection"].update(prot)
    covered["ejac"].update(ejac.values())
    covered["practice"].update(perf + recv)
    covered["kink"].update(kinks)
    covered["setting"].update(settings)
    covered["occasion"].add(occasion)
    covered["toy"].update(toys)
    covered["position"].update(positions)
    if initiator:
        covered["initiator"].add(initiator)


# --------------------------------------------------------------------------------------
# Coverage backfill — ensure every value in every category appears at least once.
# Append missing values onto a tier-appropriate (preferably partnered) encounter.
# --------------------------------------------------------------------------------------
partnered_encs = [e for e in encounters if not e["_solo"]]


def _append_csv(enc, col, value):
    cur = enc[col]
    parts = cur.split(",") if cur else []
    if value not in parts:
        parts.append(value)
    enc[col] = ",".join(parts)


def backfill(category, values, col, value_tier=None, partnered_only=False):
    pool = partnered_encs if partnered_only else encounters
    for v in values:
        if v in covered[category]:
            continue
        vt = (value_tier or {}).get(v, 1)
        cands = [e for e in pool if e["_tier"] >= vt] or pool
        enc = random.choice(cands)
        _append_csv(enc, col, v)
        covered[category].add(v)


backfill("mood", MOOD, "mood")  # mood is single-valued; handled specially below
# mood is a single column, not a set — fix any that got comma-joined by backfill.
missing_moods = [m for m in MOOD if m not in covered["mood"]]
# Reassign distinct encounters' mood to cover all moods (single value each).
free = [e for e in encounters]
random.shuffle(free)
mi = 0
for m in MOOD:
    if m in covered["mood"]:
        continue
    free[mi % len(free)]["mood"] = m
    covered["mood"].add(m)
    mi += 1

backfill("protection", PROTECTION, "protectionUsed")
backfill("practice", PRACTICE, "practicesPerformed", PRACTICE_TIER, partnered_only=True)
backfill("position", POSITION, "positions", partnered_only=True)
backfill("kink", KINK, "kinks", KINK_TIER, partnered_only=True)
backfill("setting", SETTING, "contexts")
backfill("toy", TOY, "toys")

# occasion is single-valued: assign missing occasions onto distinct encounters.
random.shuffle(free)
oi = 0
for o in OCCASION:
    if o in covered["occasion"]:
        continue
    free[oi % len(free)]["occasions"] = o
    covered["occasion"].add(o)
    oi += 1

# ejaculation locations need a self-orgasm; ensure each location is placed.
ejac_hosts = [e for e in encounters if (e["orgasmCountSelf"] or 0) > 0]
for loc in EJAC:
    if loc in covered["ejac"]:
        continue
    enc = random.choice(ejac_hosts)
    existing = enc["ejaculationLocations"] or ""
    pairs = dict(p.split("=") for p in existing.split(",")) if existing else {}
    pairs[str(len(pairs))] = loc
    # keep orgasmCountSelf >= number of ejac entries
    enc["orgasmCountSelf"] = max(enc["orgasmCountSelf"] or 0, len(pairs))
    enc["ejaculationLocations"] = ",".join(f"{k}={v}" for k, v in pairs.items())
    covered["ejac"].add(loc)

# initiator: all three already very likely; force if not.
for ini in INITIATOR:
    if ini not in covered["initiator"]:
        random.choice(partnered_encs)["initiator"] = ini
        covered["initiator"].add(ini)

# --------------------------------------------------------------------------------------
# Notes — give a sampling of encounters human-readable notes for realism.
# --------------------------------------------------------------------------------------
NOTE_BANK = [
    "Long lazy morning, didn't want to get out of bed.",
    "Spontaneous after the dinner party.", "Hotel weekend away — best in months.",
    "Quick one before work.", "Reconnected after a rough week.",
    "Tried something new, both into it.", "Anniversary night, candles and all.",
    "Vacation — beach house to ourselves.", "Met up after months apart.",
    "Late night, a little tipsy, lots of laughing.", "Slow and tender.",
    "Adventurous mood, pushed some boundaries.", "Makeup after the argument.",
    "Rainy Sunday, nowhere to be.", "Solo wind-down before sleep.",
]
for e in random.sample(encounters, 70):
    e["note"] = random.choice(NOTE_BANK)

# --------------------------------------------------------------------------------------
# Photos — attach to ~half of the encounters; some get multiple.
# --------------------------------------------------------------------------------------
photo_targets = random.sample(encounters, k=int(len(encounters) * 0.5))
for e in photo_targets:
    n = random.choices([1, 2, 3], weights=[6, 3, 1])[0]
    for _ in range(n):
        mid = uid()
        media_files[mid] = make_png(120, rand_color(), rand_color())
        media_rows.append({
            "id": mid,
            "encounterId": e["id"],
            "encFilePath": mid,  # repointed to the device media dir on import
            "mimeType": "image/png",
            "createdAt": e["createdAt"],
        })

# --------------------------------------------------------------------------------------
# Strip internal helper keys and assemble data.json.
# --------------------------------------------------------------------------------------
for e in encounters:
    e.pop("_tier", None)
    e.pop("_solo", None)

tables = {
    "partners": partners,
    "locations": [],
    "tags": [],
    "positions": [],
    "acts": [],
    "encounters": encounters,
    "media": media_rows,
    "encounter_partner": enc_partner_rows,
    "encounter_position": [],
    "encounter_tag": [],
}
data = {"schemaVersion": 6, "tables": tables}

# --------------------------------------------------------------------------------------
# Write outputs.
# --------------------------------------------------------------------------------------
out = sys.argv[1] if len(sys.argv) > 1 else "build"
os.makedirs(os.path.join(out, "media"), exist_ok=True)
with open(os.path.join(out, "data.json"), "w") as f:
    json.dump(data, f)
for mid, blob in media_files.items():
    with open(os.path.join(out, "media", mid), "wb") as f:
        f.write(blob)

# --------------------------------------------------------------------------------------
# Report.
# --------------------------------------------------------------------------------------
solo_count = sum(1 for r in [] for _ in [0])  # placeholder
solo_count = len(encounters) - len({r["encounterId"] for r in enc_partner_rows})
same_day = {}
for e in encounters:
    d = datetime.fromtimestamp(e["startAt"] / 1000, tz=timezone.utc).date()
    same_day[d] = same_day.get(d, 0) + 1
multi_days = sum(1 for v in same_day.values() if v >= 2)

print(f"partners        : {len(partners)} ({sum(1 for p in partners if p['photoMediaId'])} with photos)")
print(f"encounters      : {len(encounters)}")
print(f"  solo          : {solo_count}")
print(f"  with photos   : {len(photo_targets)} ({len(media_rows)} photo blobs)")
print(f"  multi-partner : {sum(1 for e in encounters if sum(1 for r in enc_partner_rows if r['encounterId']==e['id'])>=2)}")
print(f"days w/ 2+ enc  : {multi_days}")
print(f"media blobs     : {len(media_files)} total (incl. {sum(1 for p in partners if p['photoMediaId'])} avatars)")
print("coverage (used / total):")
report = [("mood", MOOD), ("protection", PROTECTION), ("ejac", EJAC), ("practice", PRACTICE),
          ("kink", KINK), ("setting", SETTING), ("occasion", OCCASION), ("toy", TOY),
          ("position", POSITION), ("initiator", INITIATOR)]
ok = True
for name, vals in report:
    miss = [v for v in vals if v not in covered[name]]
    flag = "OK" if not miss else f"MISSING {miss}"
    if miss:
        ok = False
    print(f"  {name:11s}: {len(covered[name])}/{len(vals)}  {flag}")
print("ALL CATEGORIES COVERED" if ok else "!!! COVERAGE GAPS !!!")
