package io.fidelitycard.app.data

import android.content.Context

/** Which side of the app a device is currently using by default (`TODO.md` "Product / UX"). */
enum class AppMode { ISSUER, COLLECTOR }

private const val PREFS_NAME = "fidelity_card_prefs"
private const val KEY_MODE = "app_mode"

/**
 * Remembers which mode (issuer/collector) to open straight into, so
 * returning to the app doesn't re-ask "who are you" on every single
 * launch. This doesn't restrict a device to one mode - SPEC/SPECS.md §4
 * still allows both issuer identities and collector cards on the same
 * device - it only picks which one is the default landing screen; the
 * other is always still one tap away (see the "Switch mode" action on
 * `BusinessListScreen`/`CardListScreen`). Plain `SharedPreferences`: a
 * single value, nothing here needs anything heavier.
 */
class ModePreference(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: AppMode?
        get() = prefs.getString(KEY_MODE, null)?.let { AppMode.valueOf(it) }
        set(value) {
            prefs.edit().putString(KEY_MODE, value?.name).apply()
        }
}
