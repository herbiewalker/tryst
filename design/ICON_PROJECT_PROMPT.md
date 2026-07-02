# Tryst — Icon Set Design Project (QOL-4)

This is the design brief + kickoff prompt for the v0.2.0 icon refresh. It is written to run as a
**claude.ai Project** (vector/SVG via Artifacts), and it follows the DevPlaybook methodology
(spec-first foundation → produce → Definition of Done → handoff).

## How to run it
1. **claude.ai** (web or desktop), **Artifacts ON** (Settings → Feature preview). Use **Opus 4.x** for
   design quality (Sonnet for faster iteration).
2. Create a **Project** named "Tryst icon set" so the style system stays consistent across the chat.
3. **Upload the current icons as reference** (`app/src/main/res/drawable/ic_act_*.xml`,
   `ic_launcher_foreground.xml`) so Claude matches the existing line-art system.
4. Paste the **kickoff prompt** below as the first message. Work the phases in order; don't start the
   icon set until the system spec (Phase 1) is locked.

---

## KICKOFF PROMPT (paste into the Project)

> You are my design partner for a cohesive **app icon set** for **Tryst**, a privacy-first, local-only
> Android app for journaling intimate encounters (Material 3, purple/green theme). We will work like an
> engineering project: **lock the system spec first, then produce against it, then verify each icon
> against a Definition of Done.** Render everything as **SVG in Artifacts** so I can see it live.
>
> ### Hard rules (do not violate)
> 1. **Vector only — SVG**, destined for Android **vector drawables**. No raster/PNG, no photos.
> 2. **Tasteful & abstract, never explicit.** These icons represent sexual acts but must read as clean,
>    symbolic line-art suitable for a general app listing (think suggestive geometry, not anatomy).
>    The existing set is the bar: e.g. "oral" is an abstract lens/curve shape, not a depiction.
> 3. **One consistent system.** Every act icon shares the same canvas, grid, stroke weight, corner
>    radius, and construction logic. Consistency beats cleverness.
> 4. **Monochrome, single-color.** Act icons are drawn in one color (black on transparent) and are
>    **tinted at runtime** by the app theme — so do **not** bake in brand colors; use stroke + an
>    optional low-alpha fill of the *same* color for depth, exactly like the current set.
> 5. **Match (or deliberately, with my sign-off, evolve) the existing spec**, so dropping the new files
>    in is a pure drawable swap.
>
> ### Phase 1 — Lock the icon system spec (do this first, nothing else)
> Propose and we agree on a one-page spec, matching the current system unless you argue to change it:
> - **Act-icon canvas:** 24×24dp viewport, with a defined live area / padding (current set fills ~4–20).
> - **Stroke:** ~2.2 width, round cap + round join (current values).
> - **Fill rule:** primary shape is stroked; optional secondary detail as a solid or low-alpha
>   (~0.18) fill of the same color for depth.
> - **Construction grid** (keylines/optical sizing) so all icons feel like a family.
> - **Naming:** `ic_act_<concept>.xml` (keep existing names; see list below).
> Deliver the spec as text **plus** 2–3 sample icons drawn against it so we can sanity-check the feel
> before committing to the whole set.
>
> ### Phase 2 — Launcher icon (Android adaptive)
> Replace the current placeholder flame. Produce an **adaptive icon** as three SVG layers on a 108dp
> canvas with the standard safe zone (centre ~66dp):
> - **Foreground** — the Tryst mark (something evocative but discreet/ambiguous; it sits on a home
>   screen, so err tasteful and abstract). 
> - **Background** — a solid or simple gradient using the brand palette (**purple primary `#6A4BAA`**,
>   **green secondary `#2E6A4E`**; near-white surface `#FBF8FF`).
> - **Monochrome** — a single-path silhouette for Android 13+ **themed icons** (this is currently
>   missing — add it).
> Give me 2–3 distinct directions for the mark before refining one.
>
> ### Phase 3 — The act-icon set
> Once the spec is locked, draw the full set below against it, in **review batches of ~5** (show the
> batch, I approve, continue). Each must be recognisable at 24dp and consistent with its siblings.
>
> ### Phase 4 — Definition of Done (apply to every icon)
> - [ ] Drawn on the agreed canvas/grid; stroke + fill match the spec.
> - [ ] Single color, transparent background, no baked-in brand color (tintable).
> - [ ] Reads clearly at 24dp and is visually a member of the family.
> - [ ] Tasteful/abstract — would not embarrass on an app-store screenshot.
> - [ ] Delivered as clean SVG (minimal nodes, no editor cruft) ready to convert to a vector drawable.
>
> ### Handoff format
> Give me each icon as an individual **SVG** (I'll convert to Android `<vector>` via Android Studio
> Asset Studio or `svg2vectordrawable`). For the launcher, give the three layers separately. Where an
> SVG maps cleanly, you may also output the Android `<vector>` XML directly (24dp viewport,
> `strokeWidth="2.2"`, `strokeLineCap="round"`, `fillColor` black) to save a step.
>
> ### The set to produce (match these existing names; all abstract/symbolic)
> `ic_act_kiss`, `ic_act_embrace`, `ic_act_oral`, `ic_act_vulva`, `ic_act_breasts`, `ic_act_hand`
> (manual/fingering), `ic_act_anal`, `ic_act_sixtynine`, `ic_act_massage`, `ic_act_prostate`,
> plus a neutral **`ic_act_custom`** fallback (a generic "spark/heart/asterisk" used for any
> user-created act — since v10 this covers *all* user-added/adopted acts, so it earns extra polish).
> *(The set was trimmed in v10 with the built-in catalog — FDP-2/D-41: explicit-named icons like
> `ic_act_fisting` were deleted because resource names ship in the APK. Don't reintroduce them.)*
>
> Start with **Phase 1**: give me the proposed spec and 2–3 sample icons. Wait for my approval before
> the full set.

---

## Repo context for whoever runs this
- **Current style** (the bar to match): `ic_act_*.xml` — 24dp viewport, `strokeColor #000000`,
  `strokeWidth 2.2`, round cap/join, optional `fillAlpha 0.18`; monochrome, tinted at runtime by
  `ui/common/ActVisuals.kt` (named `PracticeVisuals` before v10). Because tint happens in code,
  **swapping the drawables needs no code change** (QOL-4 is a pure asset swap).
- **Launcher today:** `res/drawable/ic_launcher_foreground.xml` (placeholder flame, marked "Replace at
  M8") + `res/mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`. **No monochrome layer yet**
  — add one for Android 13 themed icons.
- **Palette:** purple primary `#6A4BAA` (dark `#CFBCFF`), green secondary `#2E6A4E`, surface `#FBF8FF`
  (`ui/theme/Color.kt`).
- **Privacy note:** the app is F-Droid-bound and screenshot-blocked (`FLAG_SECURE`); the launcher icon
  is the *one* thing visible on a home screen, so its discretion matters most.
- **Acceptance for QOL-4:** new act set drops into `res/drawable/` under the same names; new adaptive
  launcher (fg + bg + monochrome) wired in `mipmap-anydpi-v26/`; app builds; icons render and tint in
  the act picker (`ActVisuals`).
