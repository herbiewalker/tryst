# Tryst R8/ProGuard rules.

# Privacy: strip ALL android.util.Log calls from release builds so no sensitive data
# can ever reach logcat in a shipped build (CLAUDE.md hard-constraint #4).
# w/e were added after the v0.5.0 audit (Bundle-B N6): the pre-existing v/d/i strip
# missed the release-log leak vector opened by BackupViewModel's TRYSTIMPORT Log.e.
# -assumenosideeffects lets R8 eliminate the whole call site including argument
# evaluation, so `e.message` / `throwable.stackTrace` never get computed in release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Hilt and Room ship their own consumer rules. Add app-specific keep rules below as the
# app grows (e.g. for any reflection-based serialization in export/import at M5).
