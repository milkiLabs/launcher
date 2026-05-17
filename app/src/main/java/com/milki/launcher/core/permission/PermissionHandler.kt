
package com.milki.launcher.core.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.PermissionAccessState

interface PermissionStateSink {
    fun updateContactsPermission(state: PermissionAccessState)
    fun updateFilesPermission(state: PermissionAccessState)
}

class PermissionHandler(
    private val activity: ComponentActivity,
    private val permissionStateSink: PermissionStateSink
) {
    private val accessStateStore = SharedPreferencesPermissionAccessStateStore(activity)

        private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

        private lateinit var callPermissionLauncher: ActivityResultLauncher<String>

        private lateinit var filesPermissionLauncher: ActivityResultLauncher<String>

        private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    
        var onPermissionResult: ((permission: String, granted: Boolean) -> Unit)? = null

        fun setup() {
        registerContactsLauncher()
        registerCallLauncher()
        registerFilesLauncher()
        registerManageStorageLauncher()
    }

        private fun registerContactsLauncher() {
        contactsPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.READ_CONTACTS,
                isGranted = isGranted,
                updateState = permissionStateSink::updateContactsPermission
            )
        }
    }

        private fun registerCallLauncher() {
        callPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.CALL_PHONE,
                isGranted = isGranted
            )
        }
    }

        private fun registerFilesLauncher() {
        filesPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                isGranted = isGranted,
                updateState = permissionStateSink::updateFilesPermission
            )
        }
    }

        private fun registerManageStorageLauncher() {
        manageStorageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val hasPermission = PermissionUtil.hasFilesPermission(activity)
            handleManageStorageResult(hasPermission)
        }
    }

        fun updateStates() {
        updateContactsPermissionState()
        updateFilesPermissionState()
    }

        private fun updateContactsPermissionState() {
        permissionStateSink.updateContactsPermission(
            accessStateForRuntimePermission(Manifest.permission.READ_CONTACTS)
        )
    }

    private fun updateFilesPermissionState() {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accessStateForSpecialPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else {
            accessStateForRuntimePermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionStateSink.updateFilesPermission(state)
    }

        fun requestContactsPermission() {
        requestRuntimePermission(
            permission = Manifest.permission.READ_CONTACTS,
            launcher = contactsPermissionLauncher,
            updateState = permissionStateSink::updateContactsPermission
        )
    }

        fun requestCallPermission() {
        requestRuntimePermission(
            permission = Manifest.permission.CALL_PHONE,
            launcher = callPermissionLauncher
        )
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

        fun requestFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageStoragePermission()
        } else {
            requestReadStoragePermission()
        }
    }

        private fun requestManageStoragePermission() {
        if (PermissionUtil.hasFilesPermission(activity)) {
            accessStateStore.clearRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            permissionStateSink.updateFilesPermission(PermissionAccessState.GRANTED)
            onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, true)
            return
        }

        val settingsIntent = PermissionSettingsNavigator.manageStorageIntent(activity)
        if (settingsIntent != null) {
            manageStorageLauncher.launch(settingsIntent)
        } else {
            Toast.makeText(
                activity,
                PermissionMessages.FILE_ACCESS_SETTINGS_UNAVAILABLE,
                Toast.LENGTH_SHORT
            ).show()
            onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, false)
        }
    }

        private fun requestReadStoragePermission() {
        requestRuntimePermission(
            permission = Manifest.permission.READ_EXTERNAL_STORAGE,
            launcher = filesPermissionLauncher,
            updateState = permissionStateSink::updateFilesPermission
        )
    }

    private fun requestRuntimePermission(
        permission: String,
        launcher: ActivityResultLauncher<String>,
        updateState: ((PermissionAccessState) -> Unit)? = null
    ) {
        if (hasPermission(permission)) {
            accessStateStore.clearRequiresSettings(permission)
            updateState?.invoke(PermissionAccessState.GRANTED)
            onPermissionResult?.invoke(permission, true)
            return
        }

        if (accessStateStore.requiresSettings(permission)) {
            updateState?.invoke(PermissionAccessState.REQUIRES_SETTINGS)
            Toast.makeText(
                activity,
                PermissionMessages.blockedMessage(permission),
                Toast.LENGTH_LONG
            ).show()
            PermissionSettingsNavigator.openApplicationDetailsSettings(activity)
            onPermissionResult?.invoke(permission, false)
            return
        }

        launcher.launch(permission)
    }

    private fun handleRuntimePermissionResult(
        permission: String,
        isGranted: Boolean,
        updateState: ((PermissionAccessState) -> Unit)? = null
    ) {
        val resolvedState = PermissionOutcomeResolver.resolveRuntimeRequestResult(
            isGranted = isGranted,
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission)
        )

        when (resolvedState) {
            PermissionAccessState.GRANTED -> {
                accessStateStore.clearRequiresSettings(permission)
            }

            PermissionAccessState.CAN_REQUEST -> {
                accessStateStore.clearRequiresSettings(permission)
                Toast.makeText(
                    activity,
                    PermissionMessages.declinedMessage(permission),
                    Toast.LENGTH_SHORT
                ).show()
            }

            PermissionAccessState.REQUIRES_SETTINGS -> {
                accessStateStore.markRequiresSettings(permission)
                Toast.makeText(
                    activity,
                    PermissionMessages.blockedMessage(permission),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        updateState?.invoke(resolvedState)
        onPermissionResult?.invoke(permission, isGranted)
    }

    private fun handleManageStorageResult(hasPermission: Boolean) {
        val state = if (hasPermission) {
            accessStateStore.clearRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            PermissionAccessState.GRANTED
        } else {
            accessStateStore.markRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            Toast.makeText(
                activity,
                PermissionMessages.FILE_ACCESS_NOT_GRANTED,
                Toast.LENGTH_LONG
            ).show()
            PermissionAccessState.REQUIRES_SETTINGS
        }

        permissionStateSink.updateFilesPermission(state)
        onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, hasPermission)
    }

    private fun accessStateForRuntimePermission(permission: String): PermissionAccessState {
        if (hasPermission(permission)) {
            accessStateStore.clearRequiresSettings(permission)
            return PermissionAccessState.GRANTED
        }

        return if (accessStateStore.requiresSettings(permission)) {
            PermissionAccessState.REQUIRES_SETTINGS
        } else {
            PermissionAccessState.CAN_REQUEST
        }
    }

    private fun accessStateForSpecialPermission(permission: String): PermissionAccessState {
        if (PermissionUtil.hasFilesPermission(activity)) {
            accessStateStore.clearRequiresSettings(permission)
            return PermissionAccessState.GRANTED
        }

        return if (accessStateStore.requiresSettings(permission)) {
            PermissionAccessState.REQUIRES_SETTINGS
        } else {
            PermissionAccessState.CAN_REQUEST
        }
    }

}
