package com.flag.secure

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LSPatchHelper(private val context: Context) {
    
    companion object {
        // ORIGINAL LSPatch package
        const val LSPATCH_ORIGINAL_PACKAGE = "org.lsposed.lspatch"
        const val LSPATCH_ORIGINAL_MANAGER_PACKAGE = "org.lsposed.lspatch.manager"
        
        // JINGMATRIX FORK package
        const val LSPATCH_JINGMATRIX_PACKAGE = "io.github.jingmatrix.lspatch"
        
        // URLs
        const val LSPATCH_ORIGINAL_URL = "https://github.com/LSPosed/LSPatch/releases"
        const val LSPATCH_JINGMATRIX_URL = "https://github.com/JingMatrix/LSPatch/releases"
        
        // Common target apps for patching
        val COMMON_TARGET_APPS = listOf(
            "com.netflix.mediaclient" to "Netflix",
            "com.amazon.avod.thirdpartyclient" to "Amazon Prime Video",
            "com.google.android.youtube" to "YouTube",
            "com.disney.disneyplus" to "Disney+",
            "com.hulu.plus" to "Hulu",
            "com.hbo.hbonow" to "HBO Max",
            "com.spotify.music" to "Spotify",
            "com.crunchyroll.crunchyroid" to "Crunchyroll",
            "com.plexapp.android" to "Plex",
            "com.vudu.phone" to "Vudu",
            "com.paramountplus" to "Paramount+",
            "com.peacocktv.peacockandroid" to "Peacock TV",
            "tv.twitch.android.app" to "Twitch",
            "com.apple.android.music" to "Apple Music",
            "com.sonymobile.music.youtube" to "YouTube Music"
        )
    }
    
    // Which LSPatch package is installed?
    enum class LSPatchType {
        ORIGINAL,  // org.lsposed.lspatch
        JINGMATRIX, // io.github.jingmatrix.lspatch
        NONE
    }
    
    /**
     * Check which LSPatch version is installed
     */
    fun getInstalledLSPatchType(): LSPatchType {
        return try {
            // Check Jingmatrix fork first (since you have this)
            context.packageManager.getPackageInfo(LSPATCH_JINGMATRIX_PACKAGE, 0)
            LSPatchType.JINGMATRIX
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                // Check original LSPatch
                context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_PACKAGE, 0)
                LSPatchType.ORIGINAL
            } catch (e2: PackageManager.NameNotFoundException) {
                try {
                    // Check original manager
                    context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_MANAGER_PACKAGE, 0)
                    LSPatchType.ORIGINAL
                } catch (e3: PackageManager.NameNotFoundException) {
                    LSPatchType.NONE
                }
            }
        }
    }
    
    /**
     * Check if ANY LSPatch is installed
     */
    fun isLSPatchInstalled(): Boolean {
        return getInstalledLSPatchType() != LSPatchType.NONE
    }
    
    /**
     * Get LSPatch version if installed
     */
    fun getLSPatchVersion(): String? {
        return when (val type = getInstalledLSPatchType()) {
            LSPatchType.JINGMATRIX -> {
                try {
                    val info = context.packageManager.getPackageInfo(LSPATCH_JINGMATRIX_PACKAGE, 0)
                    "Jingmatrix Fork ${info.versionName} (${info.versionCode})"
                } catch (e: Exception) {
                    "Jingmatrix Fork (Unknown version)"
                }
            }
            LSPatchType.ORIGINAL -> {
                try {
                    val info = context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_PACKAGE, 0)
                    "Original ${info.versionName} (${info.versionCode})"
                } catch (e: PackageManager.NameNotFoundException) {
                    try {
                        val info = context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_MANAGER_PACKAGE, 0)
                        "Original Manager ${info.versionName} (${info.versionCode})"
                    } catch (e2: PackageManager.NameNotFoundException) {
                        "Original (Unknown version)"
                    }
                }
            }
            LSPatchType.NONE -> null
        }
    }
    
    /**
     * Get installed package name
     */
    fun getLSPatchPackageName(): String? {
        return when (val type = getInstalledLSPatchType()) {
            LSPatchType.JINGMATRIX -> LSPATCH_JINGMATRIX_PACKAGE
            LSPatchType.ORIGINAL -> {
                // Check which original package is installed
                return try {
                    context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_PACKAGE, 0)
                    LSPATCH_ORIGINAL_PACKAGE
                } catch (e: PackageManager.NameNotFoundException) {
                    try {
                        context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_MANAGER_PACKAGE, 0)
                        LSPATCH_ORIGINAL_MANAGER_PACKAGE
                    } catch (e2: PackageManager.NameNotFoundException) {
                        null
                    }
                }
            }
            LSPatchType.NONE -> null
        }
    }
    
    /**
     * Open LSPatch Manager app if installed
     */
    fun openLSPatchManager(): Boolean {
        val packageName = getLSPatchPackageName()
        if (packageName == null) {
            openLSPatchGuide()
            return false
        }
        
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                openLSPatchGuide()
                false
            }
        } catch (e: Exception) {
            log("Failed to open LSPatch Manager: ${e.message}")
            openLSPatchGuide()
            false
        }
    }
    
    /**
     * Open appropriate LSPatch download page
     */
    fun openLSPatchGuide() {
        val type = getInstalledLSPatchType()
        val url = if (type == LSPatchType.JINGMATRIX) {
            LSPATCH_JINGMATRIX_URL
        } else {
            LSPATCH_ORIGINAL_URL
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            log("Failed to open LSPatch guide: ${e.message}")
            showToast("Cannot open browser. Please visit: $url")
        }
    }
    
    /**
     * Create detailed embedding instructions
     */
    fun createModuleEmbeddingInfo(): String {
        val modulePath = getModuleApkPath()
        val isInstalled = isLSPatchInstalled()
        val version = getLSPatchVersion()
        val type = getInstalledLSPatchType()
        
        return buildString {
            append("📱 LSPatch Embedding Instructions\n\n")
            
            if (isInstalled && version != null) {
                append("✅ $version detected\n\n")
            } else {
                append("❌ LSPatch NOT detected\n\n")
                append("1. Install LSPatch from:\n")
                append("   • Jingmatrix Fork: $LSPATCH_JINGMATRIX_URL\n")
                append("   • Original: $LSPATCH_ORIGINAL_URL\n\n")
            }
            
            append("2. Open LSPatch Manager\n\n")
            append("3. Tap \"Manage\" → \"Patch with embedded modules\"\n\n")
            append("4. Select target app (e.g., Netflix)\n\n")
            
            if (modulePath != null) {
                append("5. Select this module APK:\n")
                append("   ${File(modulePath).name}\n\n")
            } else {
                append("5. Select this module APK\n\n")
            }
            
            append("6. Tap \"Start Patch\"\n\n")
            append("7. Install the patched APK\n\n")
            append("8. Use patched app instead of original\n\n")
            
            append("⚠️ Important Notes:\n")
            append("• Each app needs to be patched individually\n")
            append("• Keep original APK backups\n")
            append("• Some apps may detect modifications\n")
            append("• DRM content may still be protected\n")
            
            if (modulePath != null) {
                append("\n📦 Module APK: $modulePath")
            }
        }
    }
    
    /**
     * Get the path to this module's APK
     */
    fun getModuleApkPath(): String? {
        return try {
            context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        } catch (e: Exception) {
            log("Failed to get module APK path: ${e.message}")
            null
        }
    }
    
    /**
     * Get module APK file
     */
    fun getModuleApkFile(): File? {
        return getModuleApkPath()?.let { File(it) }
    }
    
    /**
     * Check required permissions for LSPatch
     */
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
        
        // Check storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add("Storage Permission")
            }
        }
        
        return missingPermissions
    }
    
    /**
     * Get intent to request a specific permission
     */
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
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else {
                    null
                }
            }
            "Storage Permission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, 
                            Uri.parse("package:${context.packageName}"))
                    } else {
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }
    
    /**
     * Share module APK with other apps
     */
    fun shareModuleApk(): Boolean {
        val moduleFile = getModuleApkFile()
        if (moduleFile == null || !moduleFile.exists()) {
            showToast("Module APK not found")
            return false
        }
        
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    moduleFile
                )
            } else {
                Uri.fromFile(moduleFile)
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Universal Secure Bypass Module")
                putExtra(Intent.EXTRA_TEXT, """
                    Universal Secure Bypass Module
                    
                    Compatible with:
                    • Original LSPatch (org.lsposed.lspatch)
                    • Jingmatrix Fork (io.github.jingmatrix.lspatch)
                    
                    Features:
                    • Bypass FLAG_SECURE for screenshots
                    • Works on rooted/unrooted devices
                    
                    GitHub: https://github.com/Dev97633/UniversalSecureBypass
                """.trimIndent())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share Module APK"))
            true
        } catch (e: Exception) {
            log("Failed to share module APK: ${e.message}")
            showToast("Cannot share APK: ${e.message}")
            false
        }
    }
    
    /**
     * Get list of installed apps that can be patched
     */
    fun getInstallableApps(): List<Pair<String, String>> {
        val installableApps = mutableListOf<Pair<String, String>>()
        
        val packages = context.packageManager.getInstalledPackages(0)
        
        packages.forEach { packageInfo ->
            if ((packageInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                packageInfo.packageName != context.packageName) {
                
                val appName = packageInfo.applicationInfo.loadLabel(context.packageManager).toString()
                installableApps.add(packageInfo.packageName to appName)
            }
        }
        
        return installableApps.sortedBy { it.second }
    }
    
    /**
     * Get LSPatch compatibility info
     */
    fun getCompatibilityInfo(): String {
        val sdkVersion = Build.VERSION.SDK_INT
        val androidVersion = Build.VERSION.RELEASE
        val lspatchType = getInstalledLSPatchType()
        val lspatchVersion = getLSPatchVersion()
        
        return buildString {
            append("📱 Device Information:\n")
            append("• Android: $androidVersion (SDK $sdkVersion)\n")
            append("• Device: ${Build.MODEL} (${Build.BRAND})\n")
            append("• Rooted: ${if (UniversalSecureBypass.isRootedMode) "Yes" else "No"}\n\n")
            
            append("🔧 LSPatch Information:\n")
            append("• Installed: ${if (lspatchType != LSPatchType.NONE) "✅ Yes" else "❌ No"}\n")
            if (lspatchVersion != null) {
                append("• Type: $lspatchVersion\n")
            }
            
            append("\n📦 Package Names Checked:\n")
            append("• Jingmatrix: $LSPATCH_JINGMATRIX_PACKAGE\n")
            append("• Original: $LSPATCH_ORIGINAL_PACKAGE\n")
            append("• Original Manager: $LSPATCH_ORIGINAL_MANAGER_PACKAGE\n")
        }
    }
    
    /**
     * Debug method to log all LSPatch info
     */
    fun debugLSPatchInfo(): String {
        val sb = StringBuilder()
        
        sb.append("=== LSPatch Debug Info ===\n\n")
        
        val type = getInstalledLSPatchType()
        sb.append("LSPatch Type: $type\n")
        sb.append("LSPatch Version: ${getLSPatchVersion()}\n")
        sb.append("Package Name: ${getLSPatchPackageName()}\n\n")
        
        // Check each package individually
        sb.append("Package Checks:\n")
        
        // Jingmatrix
        try {
            val jingmatrixInfo = context.packageManager.getPackageInfo(LSPATCH_JINGMATRIX_PACKAGE, 0)
            sb.append("• $LSPATCH_JINGMATRIX_PACKAGE: ✅ v${jingmatrixInfo.versionName}\n")
        } catch (e: PackageManager.NameNotFoundException) {
            sb.append("• $LSPATCH_JINGMATRIX_PACKAGE: ❌ Not installed\n")
        }
        
        // Original
        try {
            val originalInfo = context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_PACKAGE, 0)
            sb.append("• $LSPATCH_ORIGINAL_PACKAGE: ✅ v${originalInfo.versionName}\n")
        } catch (e: PackageManager.NameNotFoundException) {
            sb.append("• $LSPATCH_ORIGINAL_PACKAGE: ❌ Not installed\n")
        }
        
        // Original Manager
        try {
            val managerInfo = context.packageManager.getPackageInfo(LSPATCH_ORIGINAL_MANAGER_PACKAGE, 0)
            sb.append("• $LSPATCH_ORIGINAL_MANAGER_PACKAGE: ✅ v${managerInfo.versionName}\n")
        } catch (e: PackageManager.NameNotFoundException) {
            sb.append("• $LSPATCH_ORIGINAL_MANAGER_PACKAGE: ❌ Not installed\n")
        }
        
        return sb.toString()
    }
    
    /**
     * Show toast message
     */
    private fun showToast(message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Simple log function
     */
    private fun log(message: String) {
        android.util.Log.d("LSPatchHelper", message)
    }
}
