# Android Pre-Release Prompt Pack for Claude Code

Run these as separate passes before your release build. Each prompt is self-contained — paste it into Claude Code from your project root. Suggested order: UI passes first, security passes second, license/release passes last (so dependency changes from earlier fixes get re-checked).

Tip: run each pass in a fresh session so the audit mindset stays critical instead of carrying over "get it working" context from feature work.

---

## Pass 1 — Material 3 (Material You) theming audit

```
Audit every screen in this app against Material 3 (Material You) guidelines. Specifically check for:
- Dynamic color support via dynamicColorScheme (with a proper fallback ColorScheme for pre-Android 12)
- Correct M3 color roles (primary/secondary/tertiary containers, surface variants, tonal elevation instead of shadow elevation)
- M3 type scale usage instead of hardcoded text sizes
- Updated component shapes and current M3 components (no M2 leftovers)
- Proper dark theme using M3 dark color roles, not inverted colors — verify contrast

List every violation with file and line, then fix them. Do not change app behavior, only visual/theming code.
```

---

## Pass 2 — Edge-to-Edge & Insets

```
Implement full edge-to-edge support for this app:
- Call enableEdgeToEdge() in every activity
- Audit every screen for content drawn under the status bar and navigation bar; apply WindowInsets padding so nothing is clipped or obscured (including IME insets for screens with text input)
- Verify behavior on Android 15+ where edge-to-edge is enforced
- Check both light and dark system bar icon contrast

List each screen you changed and what insets handling was added.
```

---

## Pass 3 — Motion & Micro-interactions

```
Do a motion polish pass following Material motion principles:
- Replace abrupt state changes with animateContentSize, AnimatedVisibility, and spring-based animations
- Add shared element transitions between screens where there is a clear visual continuity (lists -> detail)
- Implement predictive back gesture support
- Add subtle haptic feedback (HapticFeedbackConstants) on key confirm/destructive actions
- Verify ripple and pressed states on all interactive elements

Keep animations subtle and fast (under ~300ms for most). List every change made.
```

---

## Pass 4 — Accessibility Sweep

```
Run a full accessibility audit:
- Add contentDescription to every meaningful image/icon; mark decorative ones as null
- Verify all touch targets are at least 48dp
- Check text contrast ratios against WCAG AA in both light and dark themes
- Verify logical TalkBack traversal order on every screen and add semantics/merge descendants where needed
- Ensure text scales correctly at largest font size setting without clipping

Report every issue with file/line and fix it.
```

<!--
Add new passes below using the same format:

## Pass N — <short name>

```
<self-contained prompt text>
```
-->
