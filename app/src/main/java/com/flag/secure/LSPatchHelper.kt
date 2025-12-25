package com.flag.secure

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class LSPatchHelper(private val context: Context) {
    
    companion object {
        // Jingmatrix fork package name (THIS IS WHAT YOU HAVE)
        const val JINGMATRIX_PACKAGE = "io.github.jingmatrix.lspatch"
        
        // Original LSPatch package names
        const val ORIGINAL_PACKAGE = "org.lsposed.lspatch"
        const val ORIGINAL_MANAGER_PACKAGE = "org.lsposed.lspatch.manager"
        
        // URLs
        const val JINGMATRIX_URL = "https://github.com/JingMatrix/LSPatch/releases"
        const val ORIGINAL_URL = "https://github.com/LSPosed/LSPatch/releases"
        
        private const val TAG = "LSPatchHelper"
    }
    
    /**
     * Check if ANY LSPatch is installed
     */
    fun isLSPatchInstalled(): Boolean {
        Log.d(TAG, "Checking LSPatch installation...")
        
        // Check ALL packages
        val installed = isPackageInstalled(JINGMATRIX_PACKAGE) ||
                       isPackageInstalled(ORIGINAL_PACKAGE) ||
                       isPackageInstalled(ORIGINAL_MANAGER_PACKAGE)
        
        Log.d(TAG, "LSPatch installed: $installed")
        return installed
    }
    
    /**
     * Get which LSPatch version is installed
     */
    fun getLSPatchVersion(): String? {
        return when {
            isPackageInstalled(JINGMATRIX_PACKAGE) -> {
                val version = getPackageVersion(JINGMATRIX_PACKAGE)
                "Jingmatrix Fork ${version ?: "(Unknown)"}"
            }
            isPackageInstalled(ORIGINAL_PACKAGE) -> {
                val version = getPackageVersion(ORIGINAL_PACKAGE)
                "Original ${version ?: "(Unknown)"}"
            }
            isPackageInstalled(ORIGINAL_MANAGER_PACKAGE) -> {
                val version = getPackageVersion(ORIGINAL_MANAGER_PACKAGE)
                "Original Manager ${version ?: "(Unknown)"}"
            }
            else -> null
        }
    }
    
    /**
     * Get installed package name
     */
    fun getLSPatchPackageName(): String? {
        return when {
            isPackageInstalled(JINGMATRIX_PACKAGE) -> JINGMATRIX_PACKAGE
            isPackageInstalled(ORIGINAL_PACKAGE) -> ORIGINAL_PACKAGE
            isPackageInstalled(ORIGINAL_MANAGER_PACKAGE) -> ORIGINAL_MANAGER_PACKAGE
            else -> null
        }
    }
    
    /**
     * Open LSPatch Manager app
     */
    fun openLSPatchManager(): Boolean {
        Log.d(TAG, "Trying to open LSPatch Manager...")
        
        val packageName = getLSPatchPackageName()
        if (packageName == null) {
            Log.d(TAG, "No LSPatch package found")
            return false
        }
        
        Log.d(TAG, "Found package: $packageName")
        
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                Log.d(TAG, "Found launch intent, starting activity...")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Log.d(TAG, "No launch intent found for: $packageName")
                // Try to open app info instead
                openAppInfo(packageName)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open LSPatch Manager: ${e.message}")
            false
        }
    }
    
    /**
     * Open app info/settings for a package
     */
    private fun openAppInfo(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a specific package is installed
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            Log.d(TAG, "✅ Package found: $packageName")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "❌ Package NOT found: $packageName")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package $packageName: ${e.message}")
            false
        }
    }
    
    /**
     * Get package version info
     */
    private fun getPackageVersion(packageName: String): String? {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Debug: Check all packages and return detailed info
     */
    fun debugCheckAllPackages(): String {
        val sb = StringBuilder()
        
        sb.append("=== LSPatch Detection Debug ===\n\n")
        
        // Check Jingmatrix
        val hasJingmatrix = isPackageInstalled(JINGMATRIX_PACKAGE)
        val jingmatrixVersion = getPackageVersion(JINGMATRIX_PACKAGE)
        sb.append("1. Jingmatrix Fork:\n")
        sb.append("   Package: $JINGMATRIX_PACKAGE\n")
        sb.append("   Installed: ${if (hasJingmatrix) "✅ YES" else "❌ NO"}\n")
        if (hasJingmatrix && jingmatrixVersion != null) {
            sb.append("   Version: $jingmatrixVersion\n")
        }
        sb.append("\n")
        
        // Check Original
        val hasOriginal = isPackageInstalled(ORIGINAL_PACKAGE)
        val originalVersion = getPackageVersion(ORIGINAL_PACKAGE)
        sb.append("2. Original LSPatch:\n")
        sb.append("   Package: $ORIGINAL_PACKAGE\n")
        sb.append("   Installed: ${if (hasOriginal) "✅ YES" else "❌ NO"}\n")
        if (hasOriginal && originalVersion != null) {
            sb.append("   Version: $originalVersion\n")
        }
        sb.append("\n")
        
        // Check Original Manager
        val hasManager = isPackageInstalled(ORIGINAL_MANAGER_PACKAGE)
        val managerVersion = getPackageVersion(ORIGINAL_MANAGER_PACKAGE)
        sb.append("3. Original Manager:\n")
        sb.append("   Package: $ORIGINAL_MANAGER_PACKAGE\n")
        sb.append("   Installed: ${if (hasManager) "✅ YES" else "❌ NO"}\n")
        if (hasManager && managerVersion != null) {
            sb.append("   Version: $managerVersion\n")
        }
        sb.append("\n")
        
        // Summary
        sb.append("=== SUMMARY ===\n")
        sb.append("• Total detected: ${if (hasJingmatrix || hasOriginal || hasManager) "✅ INSTALLED" else "❌ NOT INSTALLED"}\n")
        sb.append("• Primary package: ${getLSPatchPackageName() ?: "None"}\n")
        sb.append("• Version: ${getLSPatchVersion() ?: "Unknown"}\n")
        
        return sb.toString()
    }
    
    /**
     * Get simple status string for UI
     */
    fun getStatusString(): String {
        val installed = isLSPatchInstalled()
        val version = getLSPatchVersion()
        
        return if (installed) {
            "✅ LSPatch Installed\n$version"
        } else {
            "❌ LSPatch Not Installed\nInstall from:\n• Jingmatrix: $JINGMATRIX_URL\n• Original: $ORIGINAL_URL"
        }
    }
    
    /**
     * Open appropriate download page
     */
    fun openDownloadPage() {
        val url = if (isPackageInstalled(JINGMATRIX_PACKAGE)) {
            JINGMATRIX_URL
        } else {
            ORIGINAL_URL
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser: ${e.message}")
            showToast("Cannot open browser. Visit: $url")
        }
    }
    
    /**
     * Show toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
