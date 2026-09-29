// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generic, type-preserving dump/restore of the app's plain [SharedPreferences] stores (QOL-5) —
 * theme, general, Insights layout, gallery layout. None of these are sensitive (no PIN, vault, or
 * biometric config lives here), so they're safe to fold into the encrypted backup as `settings.json`,
 * the same way [BackupManager] generically dumps DB tables. SharedPreferences distinguishes Int/Long/
 * Float/String/Boolean/Set&lt;String&gt; by which getter throws, so each value is tagged with its
 * type on the way out and restored with the matching `putX` on the way in — restoring the wrong type
 * (e.g. `putInt` for a value the live code reads with `getLong`) would throw a `ClassCastException`
 * on first read.
 */
object PrefsBackup {
    /** The named [SharedPreferences] stores this app writes — see the `*Preferences.kt` files in `core.prefs`. */
    val STORE_NAMES = listOf("tryst_appearance", "tryst_general", "tryst_insights", "tryst_gallery")

    fun dumpAll(context: Context): JSONObject {
        val stores = JSONObject()
        for (name in STORE_NAMES) {
            stores.put(name, dump(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        return stores
    }

    /** Restores every store present in [stores]; a store or key missing from an older/partial backup is left untouched. */
    fun restoreAll(context: Context, stores: JSONObject) {
        for (name in STORE_NAMES) {
            val entries = stores.optJSONObject(name) ?: continue
            restore(context.getSharedPreferences(name, Context.MODE_PRIVATE), entries)
        }
    }

    private fun dump(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put(TYPE, "bool").put(VALUE, value)
                is Int -> entry.put(TYPE, "int").put(VALUE, value)
                is Long -> entry.put(TYPE, "long").put(VALUE, value)
                is Float -> entry.put(TYPE, "float").put(VALUE, value.toDouble())
                is String -> entry.put(TYPE, "string").put(VALUE, value)
                is Set<*> -> entry.put(TYPE, "stringset").put(VALUE, JSONArray(value))
                else -> continue // Unknown value type — skip rather than write something restore can't type back.
            }
            out.put(key, entry)
        }
        return out
    }

    private fun restore(prefs: SharedPreferences, entries: JSONObject) {
        val editor = prefs.edit()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = entries.optJSONObject(key) ?: continue
            when (entry.optString(TYPE)) {
                "bool" -> editor.putBoolean(key, entry.getBoolean(VALUE))
                "int" -> editor.putInt(key, entry.getInt(VALUE))
                "long" -> editor.putLong(key, entry.getLong(VALUE))
                "float" -> editor.putFloat(key, entry.getDouble(VALUE).toFloat())
                "string" -> editor.putString(key, entry.getString(VALUE))
                "stringset" -> {
                    val arr = entry.getJSONArray(VALUE)
                    editor.putStringSet(key, (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) })
                }
                else -> Unit // Unrecognized type tag (future format) — skip, forward-compatible.
            }
        }
        editor.apply()
    }

    private const val TYPE = "t"
    private const val VALUE = "v"
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
