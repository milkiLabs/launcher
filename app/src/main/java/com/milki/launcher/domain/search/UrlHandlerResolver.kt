/**
 * UrlHandlerResolver.kt - Resolves which apps can handle a given URL
 *
 * This service uses Android's PackageManager to determine which installed
 * apps are capable of opening a specific URL. This allows the launcher to
 * show users which app will handle a URL before they tap it.
 *
 * WHY THIS EXISTS:
 * When a user types "youtube.com/watch?v=xyz", we want to:
 * 1. Show that YouTube can open this URL (if installed)
 * 2. Still offer browser as a fallback
 * 3. Let the user know what will happen when they tap
 */

package com.milki.launcher.domain.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.milki.launcher.domain.model.UrlHandlerApp

/**
 * Resolves which apps can handle a given URL.
 *
 * This class encapsulates all the logic for URL intent resolution.
 * It uses Android's PackageManager to query the system about which
 * apps can handle specific URLs.
 *
 * THREADING:
 * This class performs I/O operations (PackageManager queries).
 * Call these methods from a background thread (Dispatchers.IO or Default).
 *
 * @property context The application context for accessing PackageManager
 */
class UrlHandlerResolver(
    private val context: Context
) {
    /**
     * The PackageManager instance used for querying apps.
     * Cached for performance since we use it frequently.
     */
    private val packageManager: PackageManager = context.packageManager

    /**
     * Cache for dynamically discovered browser packages.
     * 
     * WHY CACHE THIS:
     * Querying the OS for browser packages is an expensive operation.
     * We don't want to do this every time isBrowserPackage() is called.
     * The list of installed browsers rarely changes, so we cache it
     * after the first query.
     * 
     * NULL vs EMPTY SET:
     * - null means we haven't queried yet
     * - empty set means we queried but found no browsers
     */
    private var cachedBrowserPackages: Set<String>? = null

    /**
     * Resolves the app that will handle a URL when the user taps it.
     *
     * This method determines which app Android would launch if the user
     * tapped the URL. It considers:
     * - User's previously set default app (if any)
     * - Apps that can handle this specific URL/domain
     * - Browser fallback if no specific app
     *
     * HOW IT WORKS:
     * 1. Create an ACTION_VIEW intent for the URL
     * 2. Resolve the "best" activity for this intent
     * 3. Return information about that activity
     *
     * EXAMPLE RESULTS:
     * - "youtube.com/watch?v=xyz" → YouTube app (if installed)
     * - "twitter.com/user" → Twitter/X app (if installed)
     * - "example.com" → Chrome or default browser
     *
     * @param url The URL to resolve (must be a valid http/https URL)
     * @return UrlHandlerApp with information about the handler, or null if no app can handle it
     */
    fun resolveUrlHandler(url: String): UrlHandlerApp? {
        /**
         * Create an ACTION_VIEW intent for the URL.
         * This is the standard intent for opening URLs in Android.
         *
         * INTENT FLAGS:
         * - FLAG_MATCH_DEFAULT_ONLY: Only return apps that have been set as default
         *   for this type of content. This helps us find the user's preferred app.
         */
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        return try {
            /**
             * Resolve the activity that will handle this intent.
             * This returns the "best" match - either the user's default
             * or the system's best guess.
             *
             * On Android 11+ (API 30+), we need to handle the new package
             * visibility restrictions. Apps must declare which packages they
             * want to see in the manifest. Our queries are defined in
             * AndroidManifest.xml under <queries>.
             */
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            /**
             * If we found a handler, extract its information.
             * If no handler found, the URL will open in browser (fallback).
             */
            resolveInfo?.let { info ->
                createHandlerApp(info)
            }
        } catch (e: Exception) {
            /**
             * If anything goes wrong (malformed URL, security exception, etc.),
             * return null to indicate browser fallback.
             */
            null
        }
    }

    /**
     * Gets all apps that can handle a URL (not just the default).
     *
     * This is useful for showing the user all their options,
     * or for building a custom chooser dialog.
     *
     * HOW IT WORKS:
     * 1. Query all activities that can handle the URL intent
     * 2. Filter to only those with appropriate domain approval
     * 3. Return the list with the default handler marked
     *
     * @param url The URL to query handlers for
     * @return List of UrlHandlerApp objects, with isDefault set on the preferred app
     */
    fun getAllUrlHandlers(url: String): List<UrlHandlerApp> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        return try {
            /**
             * Query all activities that can handle this URL.
             * This returns ALL apps that can handle the URL, not just the default.
             */
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }

            /**
             * Resolve the default handler so we can mark it.
             * This helps the user know which app will open if they just tap.
             */
            val defaultHandler = resolveUrlHandler(url)

            /**
             * Convert ResolveInfo objects to our domain model.
             * We filter out the launcher itself to avoid showing it as an option.
             */
            resolveInfos
                .filter { it.activityInfo.packageName != context.packageName }
                .mapNotNull { info ->
                    val handlerApp = createHandlerApp(info)
                    /**
                     * Mark the default handler so the UI can highlight it.
                     */
                    if (handlerApp != null && handlerApp.id == defaultHandler?.id) {
                        handlerApp.copy(isDefault = true)
                    } else {
                        handlerApp
                    }
                }
                .sortedByDescending { it.isDefault }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Checks if a URL should be handled by a specific app (deep link).
     *
     * Deep links are URLs that open specific apps:
     * - youtube.com → YouTube app
     * - twitter.com → Twitter app
     * - maps.google.com → Google Maps
     *
     * This is determined by Android's App Links system, where apps
     * declare which URLs they can handle in their manifest.
     *
     * @param url The URL to check
     * @return true if a specific app (not browser) will handle this URL
     */
    fun isDeepLink(url: String): Boolean {
        val handler = resolveUrlHandler(url) ?: return false

        /**
         * Check if the handler is a browser.
         * Browsers can handle any URL, but they're not "deep links".
         *
         * We detect browsers by checking if they handle generic http URLs
         * without domain restrictions. This is a heuristic and may not
         * catch all browsers, but it works for common cases.
         */
        return !isBrowserPackage(handler.packageName)
    }

    /**
     * Heuristic to detect if a package is a browser.
     *
     * Browsers are apps that can handle any http/https URL.
     * 
     * DETECTION STRATEGY:
     * This uses a hybrid approach for maximum accuracy:
     * 
     * 1. DYNAMIC DETECTION (Primary):
     *    Query the OS for apps that can handle generic http URLs.
     *    This catches ANY browser the user has installed, including
     *    obscure ones from F-Droid like Kiwi, Ghostery, Tor, Waterfox.
     * 
     * 2. HARDCODED FALLBACK (Secondary):
     *    A small list of the most popular browsers as a safety net.
     *    This handles edge cases where the dynamic query might miss
     *    a browser due to unusual manifest configurations.
     * 
     * WHY THIS APPROACH IS BETTER:
     * - Adapts to any browser the user installs
     * - No need to update the list when new browsers appear
     * - Works with browsers from any app store
     * - Still has a safety net for edge cases
     *
     * @param packageName The package name to check
     * @return true if this package is likely a browser
     */
    private fun isBrowserPackage(packageName: String): Boolean {
        /**
         * Step 1: Check our dynamic cache first.
         * If we haven't queried the OS yet, do so now.
         */
        if (cachedBrowserPackages == null) {
            cachedBrowserPackages = getDynamicBrowserPackages()
        }

        /**
         * Step 2: Check if the package is in our dynamically discovered list.
         * This catches ANY browser installed on the device.
         */
        if (cachedBrowserPackages?.contains(packageName) == true) {
            return true
        }

        /**
         * Step 3: Fallback to a small list of known top browsers.
         * This is a safety net for edge cases where the dynamic query
         * might miss a browser due to unusual manifest configurations.
         * 
         * We only include the most popular browsers here to keep the
         * list maintainable. The dynamic detection should catch most.
         */
        val knownBrowsers = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser"
        )

        return packageName in knownBrowsers
    }

    /**
     * Dynamically queries the OS for all installed web browsers.
     *
     * HOW THIS WORKS:
     * We create a generic http intent and ask Android: "Who can handle this?"
     * Any app that can open a generic website is, by definition, a browser.
     * 
     * THE INTENT:
     * - ACTION_VIEW with a generic http URL (google.com)
     * - CATEGORY_BROWSABLE indicates this can be opened in a browser
     * 
     * MATCH_ALL FLAG:
     * We use MATCH_ALL to get ALL apps that can handle the URL,
     * not just the default one.
     * 
     * ANDROID VERSION HANDLING:
     * Android 13 (Tiramisu) introduced a new API with ResolveInfoFlags.
     * We handle both old and new APIs for compatibility.
     *
     * @return Set of package names that can act as web browsers
     */
    private fun getDynamicBrowserPackages(): Set<String> {
        /**
         * Create a generic http intent.
         * We use google.com as a simple, always-available test URL.
         */
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        
        /**
         * CATEGORY_BROWSABLE indicates that the activity can be safely
         * invoked from a browser. Most browsers declare this category.
         */
        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)

        /**
         * Query the package manager for all activities that can handle
         * this intent. The MATCH_ALL flag ensures we get ALL matches,
         * not just the default/preferred one.
         */
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                browserIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        }

        /**
         * Extract just the package names from the ResolveInfo objects.
         * This gives us a set of all browser packages on the device.
         */
        return resolveInfos.map { it.activityInfo.packageName }.toSet()
    }

    /**
     * Creates a UrlHandlerApp from a ResolveInfo object.
     *
     * This extracts the relevant information from Android's ResolveInfo
     * into our domain model. We get:
     * - Package name (unique identifier)
     * - Activity name (specific activity that handles the URL)
     * - Label (human-readable name)
     *
     * @param resolveInfo The ResolveInfo from PackageManager
     * @return UrlHandlerApp or null if the info is invalid
     */
    private fun createHandlerApp(resolveInfo: ResolveInfo): UrlHandlerApp? {
        return try {
            val activityInfo = resolveInfo.activityInfo

            /**
             * Get the app's label (human-readable name).
             * This could be "YouTube", "Chrome", "Maps", etc.
             *
             * loadLabel() returns a CharSequence which we convert to String.
             */
            val label = resolveInfo.loadLabel(packageManager).toString()

            UrlHandlerApp(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = label,
                isDefault = false
            )
        } catch (e: Exception) {
            null
        }
    }
}
