/**
 * JsonHelpers.kt - Shared kotlinx.serialization Json builders
 *
 * Centralizes the near-identical Json configurations that previously lived
 * next to each persistence surface (HomeItem.json, settingsStorageJson,
 * backupJson, drag payload codec). Every surface now derives from one of
 * two baselines so encoding behavior stays consistent app-wide:
 *
 * - [lenientJson]: ignores unknown keys, for data that must survive
 *   forward/backward version drift (settings, backups, external payloads)
 * - [strictJson]: rejects unknown keys, for formats where corruption or
 *   schema mismatch should fail loudly instead of being silently skipped
 *
 * Both encode defaults so output remains deterministic across versions.
 * Per-surface extras (classDiscriminator, prettyPrint, ...) are layered on
 * via the trailing lambda.
 */

package com.milki.launcher.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

/**
 * Lenient baseline: unknown keys are ignored, defaults are encoded.
 */
fun lenientJson(config: JsonBuilder.() -> Unit = {}): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    config()
}

/**
 * Strict baseline: unknown keys are rejected, defaults are encoded.
 */
fun strictJson(config: JsonBuilder.() -> Unit = {}): Json = Json {
    encodeDefaults = true
    config()
}
