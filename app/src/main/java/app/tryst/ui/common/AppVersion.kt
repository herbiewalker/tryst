package app.tryst.ui.common

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * The app's own version, read from the installed package (no BuildConfig dependency, so no extra
 * Gradle buildFeature). [code] drives the post-update "What's new" trigger; [name] is shown in the
 * About and What's-new screens. Both fail soft to a neutral default if the package can't be read.
 */
object AppVersion {
    fun code(context: Context): Long = packageInfo(context)?.let { longVersionCode(it) } ?: 0L

    fun name(context: Context): String = packageInfo(context)?.versionName ?: ""

    private fun packageInfo(context: Context): PackageInfo? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()

    @Suppress("DEPRECATION") // versionCode is fine on minSdk 31; longVersionCode is the modern accessor.
    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
}
