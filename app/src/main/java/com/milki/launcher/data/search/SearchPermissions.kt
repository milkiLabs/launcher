package com.milki.launcher.data.search

import android.Manifest

/**
 * Manifest permission strings required by the search providers.
 *
 * Single source of truth so contacts/files providers (and any permission
 * handling code) reference identical permission strings instead of mixing
 * fully-qualified names and imports.
 */

/** Runtime permission required to search device contacts. */
const val READ_CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS

/** Permission for full file access on Android 11+ (API 30). */
const val MANAGE_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.MANAGE_EXTERNAL_STORAGE

/** Legacy storage permission for file search below Android 11. */
const val READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
