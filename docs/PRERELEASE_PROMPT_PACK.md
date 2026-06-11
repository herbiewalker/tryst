# Android Pre-Release Prompt Pack for Claude Code

Run these as separate passes before your release build. Each prompt is self-contained — paste it into Claude Code from your project root. Suggested order: UI passes first, security passes second, license/release passes last (so dependency changes from earlier fixes get re-checked).

Tip: run each pass in a fresh session so the audit mindset stays critical instead of carrying over "get it working" context from feature work.

---

## Pass 1 — Material 3 / Modern UI Audit

```
Audit every screen in this app against Material 3 (Material You) guidelines. Specifically check for:
- Dynamic color support via dynamicColorScheme (with a proper fallback ColorScheme for pre-Android 12)
- Correct M3 color roles (primary/secondary/tertiary containers, surface variants, tonal elevation instead of shadow elevation)
- M3 type scale usage instead of hardcoded text sizes
- Updated component shapes and current M3 components (no M2 leftovers)
- Proper dark theme using M3 dark color roles, not inverted colors — verify contrast

List every violation with file and line, then fix them. Do not change app behavior, only visual/theming code.
```

## Pass 2 — Edge-to-Edge & Insets

```
Implement full edge-to-edge support for this app:
- Call enableEdgeToEdge() in every activity
- Audit every screen for content drawn under the status bar and navigation bar; apply WindowInsets padding so nothing is clipped or obscured (including IME insets for screens with text input)
- Verify behavior on Android 15+ where edge-to-edge is enforced
- Check both light and dark system bar icon contrast

List each screen you changed and what insets handling was added.
```

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

## Pass 5 — Adaptive Layouts (skip if phone-only is acceptable)

```
Audit the app for tablet and foldable support:
- Introduce WindowSizeClass-based layouts for compact/medium/expanded widths
- Convert primary list/detail screens to two-pane layouts on expanded width
- Verify no stretched-phone-layout screens and no state loss on configuration change / fold-unfold

List the screens changed and the layout strategy used for each.
```

---

## Pass 6 — Manifest & Exported Components (Security)

```
Audit AndroidManifest.xml for security issues, using OWASP MASVS as the standard:
- List every Activity, Service, BroadcastReceiver, and ContentProvider that is exported (explicitly via exported="true" or implicitly via intent filters). For each, state whether exporting is justified; set exported="false" or add permission protection where it is not
- Audit every deep link / intent filter: confirm all incoming data is validated and sanitized before use
- Check for debuggable=true, allowBackup misconfiguration, and cleartext traffic permissions
- Flag any dangerous permissions requested that the app does not clearly need

Report findings as a table, then apply fixes.
```

## Pass 7 — Secrets, Storage & Logging (Security)

```
Audit the entire codebase for sensitive data exposure, using OWASP MASVS as the standard:
- Search for hardcoded API keys, tokens, passwords, or secrets in code, strings.xml, BuildConfig, and gradle files
- Find any sensitive data (tokens, PII, credentials) written to Log.d/Log.e/println or persisted in plain SharedPreferences or unencrypted files
- Verify sensitive storage uses EncryptedSharedPreferences / Jetpack Security and that keys live in the Android Keystore
- Confirm auth tokens are stored encrypted, refreshed properly, and never placed in URLs

Remember: assume the APK will be decompiled — flag anything secret that ships client-side and propose moving it server-side. Report findings with file/line, then fix what can be fixed in-code.
```

## Pass 8 — Network Security (Security)

```
Audit all networking code against OWASP MASVS network requirements:
- Verify a network_security_config.xml exists that disables cleartext traffic app-wide
- Review the Retrofit/OkHttp (or equivalent) configuration: TLS only, sane timeouts, no trust-all TrustManagers or hostname verifier overrides anywhere in the codebase
- Identify whether certificate pinning is appropriate for any high-value endpoints; if recommending it, also note the cert-rotation maintenance risk
- Confirm server responses are validated/parsed defensively rather than trusted blindly

Report findings, then fix.
```

## Pass 9 — WebView & Input Validation (Security — skip if no WebViews)

```
Audit every WebView and all user-supplied input paths:
- For each WebView: is JavaScript enabled and is that required? Is file access disabled? Is any sensitive data passed into loadUrl or addJavascriptInterface? Are loaded URLs validated against an allowlist?
- Audit all input entry points (intents, deep links, file pickers, text fields feeding queries/paths) for validation. Note where client-side validation exists but server-side re-validation is the real control.

Report and fix.
```

## Pass 10 — Dependency Vulnerabilities & GPL License Compliance

```
This app is licensed under GPLv3. Audit the full dependency tree:
1. Run/inspect the Gradle dependency tree and flag any dependency with known CVEs or that is significantly outdated; propose version bumps and apply safe ones
2. Check the license of every direct and transitive dependency for GPLv3 compatibility. Flag anything incompatible (e.g., proprietary SDKs, licenses with GPL-incompatible terms). Apache 2.0 and MIT are compatible with GPLv3; confirm anything unusual
3. Verify a LICENSE file with the full GPLv3 text exists at the repo root, and that source headers / README state the license
4. Generate or update an open-source licenses/notices screen or file listing all dependencies and their licenses

Output a table: dependency, version, license, compatible (yes/no), action taken.
```

## Pass 11 — Release Build Hardening

```
Prepare the release build configuration:
- Enable R8 minification and resource shrinking for the release build type; add/repair proguard-rules.pro so the app still functions (keep rules for reflection, serialization, etc.)
- Confirm debuggable is false and all debug logging is stripped or gated out of release builds
- Verify versionCode/versionName are bumped and signing config does not leak keystore credentials into the repo
- Build the release variant and report any R8 warnings or runtime issues to resolve

If the app handles sensitive data or has abuse potential, also assess whether Play Integrity API integration is warranted and outline the steps (do not implement unless I confirm).
```

## Pass 12 — Final Pre-Release Checklist

```
Do a final pre-release review and produce a go/no-go report:
- Re-run a quick scan for: exported components, hardcoded secrets, cleartext traffic, sensitive logging (anything reintroduced by recent changes)
- Confirm GPLv3 LICENSE file, license headers, and the dependency notices screen are present and current
- Confirm release build compiles with R8 enabled and the app launches
- List any remaining TODO/FIXME/HACK comments in the codebase with an assessment of whether each blocks release
- Summarize: items resolved, items deferred (with risk level), and a final go/no-go recommendation

Note honestly which checks an automated review cannot fully verify (e.g., real-device testing, pentest) so I know what still needs a human pass.
```

---

## Notes

- **GPLv3 reminder:** publishing on Google Play is fine, but the source you publish must match the binary you ship. Tag the release commit.
- **AI audits catch the common ~80%.** If the app handles genuinely sensitive data, run MobSF (free) against the release APK and consider a human security review before launch.
- Re-run Passes 6–8 any time a dependency or networking change happens after the audits.
