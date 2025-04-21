package fr.bdst.fastphotosrenamer.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class for storage permissions
 */
class StoragePermissionHelper {
    companion object {
        const val REQUEST_STORAGE_PERMISSION = 100
        
        /**
         * Checks if storage permissions are granted
         */
        fun hasStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11 and above
                true // Use scoped storage, no need for external storage permission
            } else {
                // For Android 10 and below
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * Request storage permissions
         */
        fun requestStoragePermission(activity: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }
    }
}