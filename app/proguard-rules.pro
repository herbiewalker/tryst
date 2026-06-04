# Tryst R8/ProGuard rules.

# Privacy: strip verbose/debug/info logging from release builds so no sensitive data
# can ever reach logcat in a shipped build (see CLAUDE.md).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Hilt and Room ship their own consumer rules. Add app-specific keep rules below as the
# app grows (e.g. for any reflection-based serialization in export/import at M5).
