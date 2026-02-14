/**
 * AppIconRequest.kt - Domain model for app icon loading requests
 * 
 * This data class represents a request to load an app icon.
 * It's used by the UI layer to request icons from the data layer.
 * 
 * By having this in the domain layer, the UI doesn't need to know
 * about Coil or how icons are loaded - it just creates a request.
 * 
 * Location: domain/model/AppIconRequest.kt
 * Architecture Layer: Domain (used by both UI and Data layers)
 */

package com.milki.launcher.domain.model

/**
 * Request object for loading an app's icon.
 * 
 * This is a simple data class that wraps a package name. It's used
 * as a type-safe identifier for icon loading requests in Coil.
 * 
 * Why use a custom type instead of String?
 * - Type safety: Only AppIconRequest objects trigger icon loading
 * - Clear intent: Makes code more readable and self-documenting
 * - Extensibility: Can add fields later (icon size, density, etc.)
 * 
 * @property packageName The Android package name (e.g., "com.google.android.youtube")
 * 
 * Example usage in Compose:
 * ```kotlin
 * Image(
 *     painter = rememberAsyncImagePainter(AppIconRequest("com.google.android.youtube")),
 *     contentDescription = null
 * )
 * ```
 */
data class AppIconRequest(val packageName: String)
