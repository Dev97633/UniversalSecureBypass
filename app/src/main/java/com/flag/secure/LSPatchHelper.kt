package com.flag.secure

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class LSPatchHelper(private val context: Context) {
    
    companion object {
        const val LSPATCH_PACKAGE = "org.lsposed.lspatch"
        const val LSPATCH_MANAGER_PACKAGE = "org.lsposed.lspatch.manager"
        const val LSPATCH_GITHUB_URL = "https://github.com/LSPosed/LSPatch/releases"
    }
    
    fun isLSPatchInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(LSPATCH_PACKAGE, 0) != null ||
            context.packageManager.getPackageInfo(LSPATCH_MANAGER_PACKAGE, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun openLSPatchGuide() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LSPATCH_GITHUB_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to open LSPatch guide: ${e.message}")
        }
    }
    
    fun openLSPatchManager() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(LSPATCH_MANAGER_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                openLSPatchGuide()
            }
        } catch (e: Exception) {
            openLSPatchGuide()
        }
    }
    
    fun createModuleEmbeddingInfo(): String {
        val modulePath = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        return """
            LSPatch Embedding Instructions:
            
            1. Install LSPatch Manager
            2. Open LSPatch Manager
            3. Tap "Manage" → "Patch with embedded modules"
            4. Select target app
            5. Select this module APK: $modulePath
            6. Tap "Start Patch"
            7. Install the patched APK
            8. Use patched app instead of original
            
            Note: Each app needs to be patched individually.
        """.trimIndent()
    }
    
    fun getModuleApkPath(): String? {
        return try {
            context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        } catch (e: Exception) {
            null
        }
    }
    
    fun checkPermissions(): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                missingPermissions.add("Overlay Permission")
            }
        }
        
        // Check install unknown apps permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                missingPermissions.add("Install Unknown Apps Permission")
            }
        }
        
        return missingPermissions
    }
    
    fun getPermissionIntent(permission: String): Intent? {
        return when (permission) {
            "Overlay Permission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:${context.packageName}"))
                } else {
                    null
                }
            }
            "Install Unknown Apps Permission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, 
                        Uri.parse("package:${context.packageName}"))
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
