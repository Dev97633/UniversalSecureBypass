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
        const val LSPATCH_PACKAGE = "org.lsposed.lspatch"
        const val LSPATCH_MANAGER_PACKAGE = "org.lsposed.lspatch.manager"
        const val LSPATCH_GITHUB_URL = "https://github.com/LSPosed/LSPatch/releases"
        const val LSPATCH_WIKI_URL = "https://github.com/LSPosed/LSPatch/wiki"
        const val LSPATCH_ISSUES_URL = "https://github.com/LSPosed/LSPatch/issues"
        
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
    
    /**
     * Check if LSPatch is installed on the device
     */
    fun isLSPatchInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(LSPATCH_PACKAGE, 0) != null ||
            context.packageManager.getPackageInfo(LSPATCH_MANAGER_PACKAGE, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get LSPatch version if installed
     */
    fun getLSPatchVersion(): String? {
        return try {
            val info = context.packageManager.getPackageInfo(LSPATCH_PACKAGE, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                val info = context.packageManager.getPackageInfo(LSPATCH_MANAGER_PACKAGE, 0)
                "${info.versionName} (${info.versionCode})"
            } catch (e2: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    
    /**
     * Get LSPatch package info
     */
    fun getLSPatchPackageInfo(): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(LSPATCH_PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                context.packageManager.getPackageInfo(LSPATCH_MANAGER_PACKAGE, 0)
            } catch (e2: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    
    /**
     * Open LSPatch Manager app if installed
     */
    fun openLSPatchManager(): Boolean {
        return try {
            // Try to open LSPatch Manager
            val intent = context.packageManager.getLaunchIntentForPackage(LSPATCH_MANAGER_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                // Try the main package
                val intent2 = context.packageManager.getLaunchIntentForPackage(LSPATCH_PACKAGE)
                if (intent2 != null) {
                    context.startActivity(intent2)
                    true
                } else {
                    openLSPatchGuide()
                    false
                }
            }
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to open LSPatch Manager: ${e.message}")
            openLSPatchGuide()
            false
        }
    }
    
    /**
     * Open LSPatch GitHub releases page
     */
    fun openLSPatchGuide() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LSPATCH_GITHUB_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to open LSPatch guide: ${e.message}")
            showToast("Cannot open browser. Please visit: $LSPATCH_GITHUB_URL")
        }
    }
    
    /**
     * Open LSPatch Wiki for documentation
     */
    fun openLSPatchWiki() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LSPATCH_WIKI_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to open LSPatch wiki: ${e.message}")
        }
    }
    
    /**
     * Open LSPatch Issues page
     */
    fun openLSPatchIssues() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LSPATCH_ISSUES_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to open LSPatch issues: ${e.message}")
        }
    }
    
    /**
     * Create detailed embedding instructions
     */
    fun createModuleEmbeddingInfo(): String {
        val modulePath = getModuleApkPath()
        val isInstalled = isLSPatchInstalled()
        val version = getLSPatchVersion()
        
        return buildString {
            append("📱 LSPatch Embedding Instructions\n\n")
            
            if (isInstalled && version != null) {
                append("✅ LSPatch $version detected\n\n")
            } else {
                append("❌ LSPatch NOT detected\n\n")
                append("1. Install LSPatch Manager from:\n")
                append("   $LSPATCH_GITHUB_URL\n\n")
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
            UniversalSecureBypass.log("Failed to get module APK path: ${e.message}")
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
        
        // Check overlay permission (required for some LSPatch versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                missingPermissions.add("Overlay Permission")
            }
        }
        
        // Check install unknown apps permission (required for installing patched APKs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                missingPermissions.add("Install Unknown Apps Permission")
            }
        }
        
        // Check storage permission for accessing APK files
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
                    // For Android 11+, use ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
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
     * Request all missing permissions
     */
    fun requestAllPermissions(): List<Intent> {
        val missingPermissions = checkPermissions()
        val intents = mutableListOf<Intent>()
        
        missingPermissions.forEach { permission ->
            getPermissionIntent(permission)?.let { intents.add(it) }
        }
        
        return intents
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
                    Universal Secure Bypass Module v2.0.0
                    
                    Features:
                    • Bypass FLAG_SECURE for screenshots
                    • Bypass basic DRM protections
                    • Works on rooted (LSPosed) and unrooted (LSPatch) devices
                    • Automatic mode detection
                    • Black screen prevention
                    
                    GitHub: https://github.com/yourusername/UniversalSecureBypass
                """.trimIndent())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share Module APK"))
            true
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to share module APK: ${e.message}")
            showToast("Cannot share APK: ${e.message}")
            false
        }
    }
    
    /**
     * Create backup of original APK before patching
     */
    fun createApkBackup(packageName: String): File? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val sourceFile = File(appInfo.sourceDir)
            val backupDir = File(context.getExternalFilesDir(null), "apk_backups")
            
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "${packageName}_${timestamp}.apk")
            
            sourceFile.copyTo(backupFile, overwrite = true)
            
            UniversalSecureBypass.log("Created APK backup: ${backupFile.absolutePath}")
            backupFile
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to create APK backup: ${e.message}")
            null
        }
    }
    
    /**
     * Get list of installed apps that can be patched
     */
    fun getInstallableApps(): List<Pair<String, String>> {
        val installableApps = mutableListOf<Pair<String, String>>()
        
        // Get all installed apps
        val packages = context.packageManager.getInstalledPackages(0)
        
        packages.forEach { packageInfo ->
            // Filter out system apps and our own module
            if ((packageInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                packageInfo.packageName != context.packageName) {
                
                val appName = packageInfo.applicationInfo.loadLabel(context.packageManager).toString()
                installableApps.add(packageInfo.packageName to appName)
            }
        }
        
        // Sort by app name
        return installableApps.sortedBy { it.second }
    }
    
    /**
     * Check if an app is already patched with this module
     */
    fun isAppPatched(packageName: String): Boolean {
        return try {
            // This is a simplified check - in reality, you'd need to check the patched APK
            // or use LSPatch's API to check
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            
            // Check for signs of patching (this is just a heuristic)
            val apkFile = File(appInfo.sourceDir)
            val apkSize = apkFile.length()
            
            // Patched APKs are usually larger than original
            // You could also check for specific metadata or resources
            apkSize > 5 * 1024 * 1024 // Arbitrary size check
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate LSPatch CLI command for patching
     */
    fun generatePatchCommand(targetApkPath: String, outputDir: String = "/sdcard/patched_apks"): String {
        val modulePath = getModuleApkPath()
        
        return if (modulePath != null) {
            """
            # LSPatch CLI Command
            # Save this as a shell script or run manually
            
            java -jar lspatch-cli.jar \
                -m "$modulePath" \
                "$targetApkPath" \
                -o "$outputDir" \
                --skip-sign
            
            # Alternative: Using LSPatch Manager via ADB
            adb shell am start -n org.lsposed.lspatch.manager/.ui.MainActivity
            
            # The patched APK will be saved to: $outputDir
            """.trimIndent()
        } else {
            "# Error: Module APK not found"
        }
    }
    
    /**
     * Create patching configuration file
     */
    fun createPatchConfig(packageName: String, configName: String = "universal_bypass"): String {
        return """
            {
              "name": "$configName",
              "version": "2.0.0",
              "package": "$packageName",
              "module": "${context.packageName}",
              "timestamp": "${System.currentTimeMillis()}",
              "hooks": [
                {
                  "class": "android.view.Window",
                  "method": "setFlags",
                  "params": ["int", "int"],
                  "hook": "before",
                  "action": "removeSecureFlag"
                },
                {
                  "class": "android.view.SurfaceView",
                  "method": "setSecure",
                  "params": ["boolean"],
                  "hook": "before",
                  "action": "setFalse"
                },
                {
                  "class": "android.media.MediaDrm",
                  "method": "getPropertyString",
                  "params": ["java.lang.String"],
                  "hook": "after",
                  "action": "downgradeSecurityLevel"
                }
              ],
              "permissions": [
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE"
              ]
            }
        """.trimIndent()
    }
    
    /**
     * Save patch configuration to file
     */
    fun savePatchConfig(packageName: String, configName: String = "universal_bypass"): File? {
        return try {
            val configDir = File(context.getExternalFilesDir(null), "patch_configs")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            
            val configFile = File(configDir, "${packageName}_${configName}.json")
            val configContent = createPatchConfig(packageName, configName)
            
            FileOutputStream(configFile).use { out ->
                out.write(configContent.toByteArray())
            }
            
            UniversalSecureBypass.log("Saved patch config: ${configFile.absolutePath}")
            configFile
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to save patch config: ${e.message}")
            null
        }
    }
    
    /**
     * Get LSPatch compatibility info
     */
    fun getCompatibilityInfo(): String {
        val sdkVersion = Build.VERSION.SDK_INT
        val androidVersion = Build.VERSION.RELEASE
        val isEmulator = Build.FINGERPRINT.contains("generic") || 
                         Build.FINGERPRINT.contains("emulator") ||
                         Build.FINGERPRINT.contains("test-keys")
        
        val lspatchInstalled = isLSPatchInstalled()
        val lspatchVersion = getLSPatchVersion()
        
        return buildString {
            append("📱 Device Information:\n")
            append("• Android: $androidVersion (SDK $sdkVersion)\n")
            append("• Device: ${Build.MODEL} (${Build.BRAND})\n")
            append("• Emulator: ${if (isEmulator) "Yes" else "No"}\n")
            append("• Rooted: ${if (UniversalSecureBypass.isRootedMode) "Yes" else "No"}\n\n")
            
            append("🔧 LSPatch Information:\n")
            append("• Installed: ${if (lspatchInstalled) "✅ Yes" else "❌ No"}\n")
            if (lspatchVersion != null) {
                append("• Version: $lspatchVersion\n")
            }
            
            append("\n⚠️ Compatibility Notes:\n")
            if (sdkVersion < 21) {
                append("• ❌ Android 5.0+ required\n")
            }
            if (isEmulator) {
                append("• ⚠️ Some features may not work on emulator\n")
            }
            if (!lspatchInstalled && !UniversalSecureBypass.isRootedMode) {
                append("• ❌ Install LSPatch for unrooted usage\n")
            }
        }
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
     * Debug method to log all LSPatch info
     */
    fun debugLSPatchInfo(): String {
        val sb = StringBuilder()
        
        sb.append("=== LSPatch Debug Info ===\n\n")
        
        // Check LSPatch installation
        sb.append("LSPatch Installed: ${isLSPatchInstalled()}\n")
        sb.append("LSPatch Version: ${getLSPatchVersion()}\n\n")
        
        // Check permissions
        val missingPerms = checkPermissions()
        sb.append("Missing Permissions:\n")
        if (missingPerms.isEmpty()) {
            sb.append("  ✅ None\n")
        } else {
            missingPerms.forEach { perm ->
                sb.append("  ❌ $perm\n")
            }
        }
        sb.append("\n")
        
        // Module info
        val modulePath = getModuleApkPath()
        sb.append("Module APK Path: $modulePath\n")
        sb.append("Module APK Exists: ${if (modulePath != null) File(modulePath).exists() else "N/A"}\n\n")
        
        // Device info
        sb.append("Device SDK: ${Build.VERSION.SDK_INT}\n")
        sb.append("Device Brand: ${Build.BRAND}\n")
        sb.append("Device Model: ${Build.MODEL}\n")
        
        return sb.toString()
    }
    
    /**
     * Export debug info to file
     */
    fun exportDebugInfo(): File? {
        return try {
            val debugDir = File(context.getExternalFilesDir(null), "debug_info")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            
            val debugFile = File(debugDir, "lspatch_debug_${System.currentTimeMillis()}.txt")
            val debugContent = debugLSPatchInfo()
            
            FileOutputStream(debugFile).use { out ->
                out.write(debugContent.toByteArray())
            }
            
            UniversalSecureBypass.log("Exported debug info: ${debugFile.absolutePath}")
            debugFile
        } catch (e: Exception) {
            UniversalSecureBypass.log("Failed to export debug info: ${e.message}")
            null
        }
    }
}
