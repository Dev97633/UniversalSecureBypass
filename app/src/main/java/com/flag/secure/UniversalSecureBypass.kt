package com.flag.secure

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main module class - works for both Xposed (rooted) and LSPatch (non-rooted)
 */
class UniversalSecureBypass : IXposedHookLoadPackage {
    
    companion object {
        const val TAG = "UniversalSecureBypass"
        const val VERSION = "2.0.0"
        const val PREFS_NAME = "SecureBypassPrefs"
        
        // Settings keys
        const val KEY_BYPASS_FLAG_SECURE = "bypass_flag_secure"
        const val KEY_BYPASS_DRM = "bypass_drm"
        const val KEY_BYPASS_BLACK_SCREEN = "bypass_black_screen"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val KEY_SYSTEM_APPS = "system_apps"
        const val KEY_TARGET_APPS = "target_apps"
        
        @JvmStatic var isRootedMode = false
        @JvmStatic var isLSPatchMode = false
        @JvmStatic var isActive = true
        @JvmStatic var modulePath: String? = null
        @JvmStatic private var appContext: Context? = null
        @JvmStatic private lateinit var prefs: SharedPreferences
        
        // Target streaming apps
        private val streamingApps = listOf(
            "netflix", "youtube", "amazon", "disney", "hotstar",
            "hbo", "hulu", "primevideo", "crunchyroll", "funimation",
            "paramount", "peacock", "plex", "twitch", "vudu",
            "spotify", "max", "apple.tv", "paramountplus", "showtime",
            "starz", "mubi", "criterion", "kanopy", "tubi"
        )

        // Package names for major streaming services
        private val streamingPackages = mapOf(
            "Netflix" to listOf("com.netflix.mediaclient", "com.netflix.ninja"),
            "YouTube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
            "Amazon Prime" to listOf("com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"),
            "Disney+" to listOf("com.disney.disneyplus", "com.disney.disneyplus.prod"),
            "HBO Max" to listOf("com.hbo.hbonow", "com.hbo.hbomax"),
            "Hulu" to listOf("com.hulu.plus", "com.hulu.livingroomplus"),
            "Spotify" to listOf("com.spotify.music", "com.spotify.tv.android"),
            "Apple TV+" to listOf("com.apple.atve.android.appletv", "com.apple.atve.androidtv.appletv"),
            "Paramount+" to listOf("com.cbs.ott", "com.cbs.app"),
            "Peacock" to listOf("com.peacocktv.peacockandroid", "com.peacocktv.peacockandroid.tv")
        )

        // ===== SAFE UI INIT =====
        @JvmStatic
        fun init(context: Context?) {
            try {
                appContext = context
                if (context != null) {
                    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
                detectEnvironment()
                log("Initialized successfully. Mode: ${if (isRootedMode) "ROOT" else "LSPATCH"}")
            } catch (e: Exception) {
                log("Init failed: ${e.message}")
            }
        }

        // ===== GETTERS FOR MainActivity =====
        @JvmStatic
        fun getIsRootedMode(): Boolean = isRootedMode
        
        @JvmStatic
        fun getIsLSPatchMode(): Boolean = isLSPatchMode
        
        @JvmStatic
        fun getIsActive(): Boolean = isActive

        // ===== XPOSED ENTRY BRIDGE =====
        @JvmStatic
        fun onZygoteInit(startupParam: IXposedHookZygoteInit.StartupParam?) {
            modulePath = startupParam?.modulePath
            isRootedMode = true
            isActive = true
            log("Xposed zygote initialized")
            log("Module version: $VERSION")
            log("Module path: $modulePath")
        }

        @JvmStatic
        fun onPackageLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
            
        }

        @JvmStatic
        fun onResourcesLoaded(resparam: XC_InitPackageResources.InitPackageResourcesParam?) {
            // Resource hooking placeholder
        }

        // ===== ENV DETECTION =====
        private fun detectEnvironment() {
            try {
                // Check for root
                val rootPaths = arrayOf(
                    "/system/bin/su", "/system/xbin/su", "/sbin/su",
                    "/data/local/xbin/su", "/data/local/bin/su",
                    "/system/sd/xbin/su", "/system/bin/failsafe/su",
                    "/data/local/su"
                )

                isRootedMode = rootPaths.any { File(it).exists() }

                // Check for Xposed/LSPosed
                try {
                    Class.forName("de.robv.android.xposed.XposedBridge")
                    isRootedMode = true
                    log("Detected Xposed framework")
                } catch (_: Throwable) {}

                try {
                    Class.forName("org.lsposed.lspd.BuildConfig")
                    isRootedMode = true
                    log("Detected LSPosed framework")
                } catch (_: Throwable) {}

                // If not rooted, assume LSPatch
                if (!isRootedMode) {
                    isLSPatchMode = true
                    log("Running in LSPatch mode")
                }

            } catch (e: Throwable) {
                isLSPatchMode = true
                log("Environment detection failed, defaulting to LSPatch")
            }
        }

        // ===== PACKAGE FILTERING =====
        private fun shouldHookPackage(packageName: String): Boolean {
            if (appContext == null) return true
            
            val targetSetting = prefs.getString(KEY_TARGET_APPS, "All Streaming Apps") ?: "All Streaming Apps"
            val hookSystemApps = prefs.getBoolean(KEY_SYSTEM_APPS, false)
            
            // Check if it's a system app
            if (!hookSystemApps && isSystemApp(packageName)) {
                return false
            }
            
            return when (targetSetting) {
                "All Streaming Apps" -> isStreamingApp(packageName)
                "Netflix Only" -> packageName.contains("netflix", ignoreCase = true)
                "YouTube Only" -> packageName.contains("youtube", ignoreCase = true) || 
                                 packageName == "com.google.android.youtube"
                "Amazon Prime Only" -> packageName.contains("amazon", ignoreCase = true) && 
                                      (packageName.contains("video", ignoreCase = true) || 
                                       packageName.contains("avod", ignoreCase = true))
                "Disney+ Only" -> packageName.contains("disney", ignoreCase = true) || 
                                 packageName.contains("hotstar", ignoreCase = true)
                "Custom Selection" -> true // User will manually select
                else -> isStreamingApp(packageName)
            }
        }
        
        private fun isSystemApp(packageName: String): Boolean {
            return try {
                val packageInfo = appContext?.packageManager?.getPackageInfo(packageName, 0)
                packageInfo?.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                false
            }
        }
        
        private fun isStreamingApp(packageName: String): Boolean {
            // Check against known streaming packages
            return streamingPackages.values.any { packages -> 
                packages.any { packageName.startsWith(it) }
            } || streamingApps.any { packageName.contains(it, ignoreCase = true) }
        }

        // ===== LOGGING =====
        @JvmStatic
        fun log(msg: String) {
            val finalMsg = "[${if (isRootedMode) "ROOT" else "LSPATCH"}] $msg"
            Log.d(TAG, finalMsg)
            try { 
                XposedBridge.log("$TAG: $finalMsg") 
            } catch (_: Throwable) {
                // Xposed not available, use regular log
            }
            logToFile(finalMsg)
        }

        @JvmStatic
        fun logToFile(message: String) {
            try {
                val context = appContext ?: return
                val logDir = File(context.filesDir, "module_logs")
                if (!logDir.exists()) logDir.mkdirs()

                val file = File(logDir, "secure_bypass.log")
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                file.appendText("$time - $message\n")
                
                // Limit log file size
                if (file.length() > 1024 * 1024) { // 1MB
                    val lines = file.readLines()
                    if (lines.size > 1000) {
                        file.writeText(lines.takeLast(1000).joinToString("\n"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file: ${e.message}")
            }
        }

        // ===== TOAST UTILITY =====
        @JvmStatic
        fun showToast(context: Context?, message: String) {
            context?.let {
                try {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    log("Failed to show toast: ${e.message}")
                }
            }
        }
    }

    // ===== IXposedHookLoadPackage IMPLEMENTATION =====
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        onPackageLoaded(lpparam)
    }

    // ===== CORE PACKAGE HANDLER =====
    private fun handleLoadPackageInternal(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        
        // Skip system framework and ourselves
        if (packageName == "android" || packageName == "com.flag.secure") {
            return
        }
        
        if (!shouldHookPackage(packageName)) {
            log("Skipping package: $packageName")
            return
        }
        
        log("Hooking package: $packageName")
        
        // Get settings
        val bypassFlagSecure = prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
        val bypassDrm = prefs.getBoolean(KEY_BYPASS_DRM, true)
        val bypassBlackScreen = prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
        
        log("Settings - FlagSecure: $bypassFlagSecure, DRM: $bypassDrm, BlackScreen: $bypassBlackScreen")
        
        // Apply hooks based on settings
        if (bypassFlagSecure) {
            setupFlagSecureHooks(lpparam)
        }
        
        if (bypassDrm) {
            setupDrmHooks(lpparam)
        }
        
        if (bypassBlackScreen) {
            setupBlackScreenHooks(lpparam)
        }
        
        // App-specific hooks
        setupAppSpecificHooks(lpparam, packageName)
    }

    // ===== UNIVERSAL HOOKS =====
    private fun setupFlagSecureHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook 1: Window.setFlags() - Clear FLAG_SECURE when set
            XposedHelpers.findAndHookMethod(
                Window::class.java,
                "setFlags",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val flags = param.args[0] as Int
                        val mask = param.args[1] as Int
                        
                        // Clear FLAG_SECURE (8192) if it's being set
                        if ((mask and LayoutParams.FLAG_SECURE) != 0) {
                            param.args[0] = flags and LayoutParams.FLAG_SECURE.inv()
                            log("Cleared FLAG_SECURE in setFlags()")
                        }
                    }
                }
            )
            
            // Hook 2: Activity.onCreate() - Clear any existing FLAG_SECURE
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        activity.window?.clearFlags(LayoutParams.FLAG_SECURE)
                        log("Cleared FLAG_SECURE in ${activity.javaClass.name}.onCreate()")
                    }
                }
            )
            
            // Hook 3: Window.addFlags() - Prevent adding FLAG_SECURE
            XposedHelpers.findAndHookMethod(
                Window::class.java,
                "addFlags",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val flags = param.args[0] as Int
                        
                        // Block FLAG_SECURE addition
                        if ((flags and LayoutParams.FLAG_SECURE) != 0) {
                            param.args[0] = flags and LayoutParams.FLAG_SECURE.inv()
                            log("Blocked FLAG_SECURE addition in addFlags()")
                        }
                    }
                }
            )
            
            // Hook 4: Window.clearFlags() - Make sure FLAG_SECURE stays cleared
            XposedHelpers.findAndHookMethod(
                Window::class.java,
                "clearFlags",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Ensure FLAG_SECURE is never re-added after clear
                        val activity = try {
                            XposedHelpers.getObjectField(param.thisObject, "mActivity") as? Activity
                        } catch (e: Throwable) { null }
                        
                        activity?.window?.clearFlags(LayoutParams.FLAG_SECURE)
                    }
                }
            )
            
            log("FLAG_SECURE hooks installed successfully")
            
        } catch (e: Throwable) {
            log("Error setting up FLAG_SECURE hooks: ${e.message}")
        }
    }
    
    private fun setupDrmHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Widevine DRM
            val drmClasses = listOf(
                "android.media.MediaDrm",
                "com.widevine.alpha.WidevineDrm",
                "com.google.android.exoplayer2.drm.WidevineDrm",
                "com.google.android.exoplayer2.drm.FrameworkMediaDrm"
            )
            
            for (className in drmClasses) {
                try {
                    val drmClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    if (drmClass != null) {
                        // Hook getPropertyString to return L3 security
                        XposedHelpers.findAndHookMethod(
                            drmClass,
                            "getPropertyString",
                            String::class.java,
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    val propertyName = param.args[0] as String
                                    if (propertyName.contains("security", ignoreCase = true) ||
                                        propertyName.contains("level", ignoreCase = true)) {
                                        // Return L3 (software) security level
                                        param.result = "L3"
                                        log("Overrode DRM security level to L3")
                                    }
                                }
                            }
                        )
                        
                        // Hook openSession to log success
                        XposedHelpers.findAndHookMethod(
                            drmClass,
                            "openSession",
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    log("DRM session opened successfully")
                                }
                            }
                        )
                        
                        log("Hooked DRM class: $className")
                    }
                } catch (e: Throwable) {
                    // Class not found, continue
                }
            }
            
            log("DRM hooks installed successfully")
            
        } catch (e: Throwable) {
            log("Error setting up DRM hooks: ${e.message}")
        }
    }
    
    private fun setupBlackScreenHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook SurfaceView to prevent hiding
            val surfaceViewClass = XposedHelpers.findClassIfExists(
                "android.view.SurfaceView",
                lpparam.classLoader
            )
            
            if (surfaceViewClass != null) {
                XposedHelpers.findAndHookMethod(
                    surfaceViewClass,
                    "setVisibility",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val visibility = param.args[0] as Int
                            // Prevent hiding (View.INVISIBLE = 4, View.GONE = 8)
                            if (visibility == 4 || visibility == 8) {
                                param.args[0] = 0 // View.VISIBLE
                                log("Prevented SurfaceView from being hidden")
                            }
                        }
                    }
                )
            }
            
            // Hook TextureView
            val textureViewClass = XposedHelpers.findClassIfExists(
                "android.view.TextureView",
                lpparam.classLoader
            )
            
            if (textureViewClass != null) {
                XposedHelpers.findAndHookMethod(
                    textureViewClass,
                    "setVisibility",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val visibility = param.args[0] as Int
                            if (visibility == 4 || visibility == 8) {
                                param.args[0] = 0 // View.VISIBLE
                                log("Prevented TextureView from being hidden")
                            }
                        }
                    }
                )
            }
            
            // Hook View.setVisibility for general prevention
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        val className = view.javaClass.name
                        
                        // Only intercept for video-related views
                        if (className.contains("Video", ignoreCase = true) ||
                            className.contains("Player", ignoreCase = true) ||
                            className.contains("Surface", ignoreCase = true) ||
                            className.contains("Texture", ignoreCase = true)) {
                            
                            val visibility = param.args[0] as Int
                            if (visibility == 4 || visibility == 8) {
                                param.args[0] = 0 // View.VISIBLE
                                log("Prevented $className from being hidden")
                            }
                        }
                    }
                }
            )
            
            log("Black screen prevention hooks installed")
            
        } catch (e: Throwable) {
            log("Error setting up black screen hooks: ${e.message}")
        }
    }
    
    private fun setupAppSpecificHooks(lpparam: XC_LoadPackage.LoadPackageParam, packageName: String) {
        when {
            packageName.contains("netflix", ignoreCase = true) -> setupNetflixHooks(lpparam)
            packageName.contains("youtube", ignoreCase = true) -> setupYouTubeHooks(lpparam)
            packageName.contains("amazon", ignoreCase = true) && 
            (packageName.contains("video", ignoreCase = true) || 
             packageName.contains("avod", ignoreCase = true)) -> setupAmazonHooks(lpparam)
            packageName.contains("disney", ignoreCase = true) || 
            packageName.contains("hotstar", ignoreCase = true) -> setupDisneyHooks(lpparam)
            packageName.contains("hbo", ignoreCase = true) || 
            packageName.contains("hbomax", ignoreCase = true) -> setupHboHooks(lpparam)
            packageName.contains("hulu", ignoreCase = true) -> setupHuluHooks(lpparam)
            packageName.contains("spotify", ignoreCase = true) -> setupSpotifyHooks(lpparam)
            packageName.contains("apple.tv", ignoreCase = true) || 
            packageName.contains("atve", ignoreCase = true) -> setupAppleTvHooks(lpparam)
        }
    }
    
    // ===== APP-SPECIFIC HOOKS =====
    private fun setupNetflixHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Netflix specific DRM check
            XposedHelpers.findAndHookMethod(
                "com.netflix.mediaclient.service.player.bladerunnerclient.BladeRunnerClient",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Netflix hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Netflix hooks: ${e.message}")
        }
    }
    
    private fun setupYouTubeHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // YouTube DRM
            XposedHelpers.findAndHookMethod(
                "com.google.android.libraries.youtube.media.interfaces.Player",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("YouTube hooks installed")
        } catch (e: Throwable) {
            log("Failed to install YouTube hooks: ${e.message}")
        }
    }
    
    private fun setupAmazonHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Amazon Prime Video
            XposedHelpers.findAndHookMethod(
                "com.amazon.avod.thirdpartyclient.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Amazon Prime hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Amazon hooks: ${e.message}")
        }
    }
    
    private fun setupDisneyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Disney+ / Hotstar
            XposedHelpers.findAndHookMethod(
                "com.disney.disneyplus.drm.DrmHelper",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Disney+ hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Disney+ hooks: ${e.message}")
        }
    }
    
    private fun setupHboHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // HBO Max
            XposedHelpers.findAndHookMethod(
                "com.hbo.hbonow.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("HBO Max hooks installed")
        } catch (e: Throwable) {
            log("Failed to install HBO hooks: ${e.message}")
        }
    }
    
    private fun setupHuluHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hulu
            XposedHelpers.findAndHookMethod(
                "com.hulu.plus.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Hulu hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Hulu hooks: ${e.message}")
        }
    }
    
    private fun setupSpotifyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Spotify (for video content)
            XposedHelpers.findAndHookMethod(
                "com.spotify.music.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Spotify hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Spotify hooks: ${e.message}")
        }
    }
    
    private fun setupAppleTvHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Apple TV+
            XposedHelpers.findAndHookMethod(
                "com.apple.atve.android.appletv.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Apple TV+ hooks installed")
        } catch (e: Throwable) {
            log("Failed to install Apple TV+ hooks: ${e.message}")
        }
    }
}
