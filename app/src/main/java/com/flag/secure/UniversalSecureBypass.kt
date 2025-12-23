package com.flag.secure

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class UniversalSecureBypass : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    
    companion object {
        const val TAG = "UniversalSecureBypass"
        const val VERSION = "2.0.0"
        
        @JvmStatic
        var isRootedMode = false
        @JvmStatic
        var isLSPatchMode = false
        @JvmStatic
        var isActive = true
        @JvmStatic
        var modulePath: String? = null
        
        @JvmStatic
        private var appContext: Context? = null
        
        @JvmStatic
        fun init(context: Context?) {
            appContext = context
            detectEnvironment()
        }
        
        @JvmStatic
        private fun detectEnvironment() {
            try {
                val rootPaths = arrayOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/data/local/xbin/su",
                    "/data/local/bin/su",
                    "/system/sd/xbin/su",
                    "/system/bin/failsafe/su",
                    "/data/local/su"
                )
                
                var hasRoot = false
                for (path in rootPaths) {
                    if (File(path).exists()) {
                        hasRoot = true
                        break
                    }
                }
                
                try {
                    Class.forName("com.topjohnwu.magisk.core.Const")
                    hasRoot = true
                    Log.d(TAG, "Magisk detected")
                } catch (e: ClassNotFoundException) {}
                
                try {
                    val lsposedClass = Class.forName("org.lsposed.lspd.BuildConfig")
                    val versionField = lsposedClass.getDeclaredField("VERSION_NAME")
                    val version = versionField.get(null) as String
                    Log.d(TAG, "LSPosed detected: $version")
                    isRootedMode = true
                    return
                } catch (e: Exception) {}
                
                try {
                    Class.forName("de.robv.android.xposed.XposedBridge")
                    isRootedMode = true
                    Log.d(TAG, "Xposed detected")
                } catch (e: ClassNotFoundException) {}
                
                try {
                    val currentPackage = appContext?.packageName
                    val targetPackage = System.getProperty("lspatch.target.package")
                    
                    if (targetPackage != null && currentPackage != targetPackage) {
                        isLSPatchMode = true
                        Log.d(TAG, "LSPatch embedded mode detected")
                        Log.d(TAG, "Target package: $targetPackage, Current: $currentPackage")
                    }
                } catch (e: Exception) {}
                
                if (!isRootedMode && !isLSPatchMode) {
                    isLSPatchMode = true
                }
                
                Log.d(TAG, "Environment detected - Rooted: $isRootedMode, LSPatch: $isLSPatchMode")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting environment: ${e.message}")
                isLSPatchMode = true
            }
        }
        
        @JvmStatic
        fun log(message: String) {
            val logMsg = "[${if (isRootedMode) "ROOT" else "LSPATCH"}] $message"
            Log.d(TAG, logMsg)
            
            try {
                XposedBridge.log("$TAG: $logMsg")
            } catch (e: NoClassDefFoundError) {}
            
            logToFile(logMsg)
        }
        
        @JvmStatic
        private fun logToFile(message: String) {
            try {
                val logDir = if (appContext != null) {
                    File(appContext!!.filesDir, "logs")
                } else {
                    File("/sdcard/UniversalSecureBypass")
                }
                
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                
                val logFile = File(logDir, "module_log.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(Date())
                
                val logEntry = "$timestamp - $message\n"
                
                java.io.FileWriter(logFile, true).use { writer ->
                    writer.write(logEntry)
                }
            } catch (e: Exception) {}
        }
    }
    
    data class ModuleConfig(
        var bypassFlagSecure: Boolean = true,
        var bypassDrm: Boolean = true,
        var bypassBlackScreen: Boolean = true,
        var showNotifications: Boolean = false,
        var targetPackages: Set<String> = setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.google.android.youtube",
            "com.hulu.plus",
            "com.disney.disneyplus",
            "com.hbo.hbonow",
            "com.spotify.music"
        ),
        var systemApps: Boolean = false
    )
    
    private val config = ModuleConfig()
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        modulePath = startupParam?.modulePath
        isRootedMode = true
        log("Initialized in ROOTED mode (LSPosed/Xposed)")
        log("Module version: $VERSION")
        log("Module path: $modulePath")
    }
    
    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam?) {}
    
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        
        log("=== Loading package: $packageName ===")
        log("Process: ${lpparam.processName}")
        log("UID: ${lpparam.appInfo?.uid ?: -1}")
        
        try {
            val currentApp = XposedHelpers.callStaticMethod(
                Class.forName("android.app.ActivityThread"),
                "currentApplication"
            ) as? Application
            appContext = currentApp
        } catch (e: Exception) {}
        
        if (isRootedMode) {
            setupRootedMode(lpparam)
        } else {
            setupLSPatchMode(lpparam)
        }
    }
    
    private fun setupRootedMode(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        
        if (packageName == "android" || packageName.startsWith("com.android.")) {
            setupSystemHooks(lpparam)
        } else if (config.targetPackages.contains(packageName) || config.systemApps) {
            setupAppHooks(lpparam)
        }
        
        setupUniversalHooks(lpparam)
    }
    
    private fun setupLSPatchMode(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        
        if (config.targetPackages.contains(packageName)) {
            log("Setting up LSPatch hooks for $packageName")
            setupAppHooks(lpparam)
            setupUniversalHooks(lpparam)
            setupLSPatchSpecificHooks(lpparam)
        } else {
            log("Skipping non-target app in LSPatch mode: $packageName")
        }
    }
    
    private fun setupUniversalHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        
        try {
            val hook = XposedHelpers.findAndHookMethod(
                Window::class.java,
                "setFlags",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (config.bypassFlagSecure) {
                            try {
                                var flags = param.args[0] as Int
                                val original = flags
                                
                                flags = flags and LayoutParams.FLAG_SECURE.inv()
                                
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val secureDisplayFlag = 0x00000004
                                        flags = flags and secureDisplayFlag.inv()
                                    } catch (e: Exception) {}
                                }
                                
                                if (flags != original) {
                                    param.args[0] = flags
                                    log("Window.setFlags: Removed secure flags (0x${original.toString(16)} -> 0x${flags.toString(16)})")
                                }
                            } catch (e: Exception) {
                                log("Error in Window.setFlags hook: ${e.message}")
                            }
                        }
                    }
                }
            )
            hooks.add(hook)
            log("Hooked Window.setFlags")
        } catch (t: Throwable) {
            log("Failed to hook Window.setFlags: ${t.message}")
        }
        
        try {
            val hook = XposedHelpers.findAndHookMethod(
                "android.view.SurfaceView",
                classLoader,
                "setSecure",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (config.bypassFlagSecure) {
                            param.args[0] = false
                            log("SurfaceView.setSecure forced to false")
                        }
                    }
                }
            )
            hooks.add(hook)
            log("Hooked SurfaceView.setSecure")
        } catch (t: Throwable) {
            log("Failed to hook SurfaceView.setSecure: ${t.message}")
        }
        
        try {
            val hook = XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (config.bypassFlagSecure) {
                            val activity = param.thisObject as Activity
                            val window = activity.window
                            if (window != null) {
                                val attrs = window.attributes
                                if (attrs != null && attrs.flags and LayoutParams.FLAG_SECURE != 0) {
                                    attrs.flags = attrs.flags and LayoutParams.FLAG_SECURE.inv()
                                    window.attributes = attrs
                                    log("Removed FLAG_SECURE from Activity window in onCreate")
                                }
                            }
                        }
                    }
                }
            )
            hooks.add(hook)
            log("Hooked Activity.onCreate")
        } catch (t: Throwable) {
            log("Failed to hook Activity.onCreate: ${t.message}")
        }
        
        if (config.bypassDrm) {
            setupDrmBypassHooks(lpparam)
        }
        
        if (config.bypassBlackScreen) {
            setupBlackScreenPrevention(lpparam)
        }
    }
    
    private fun setupSystemHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        
        log("Setting up SYSTEM hooks (rooted mode only)")
        
        try {
            val windowStateClass = XposedHelpers.findClass("com.android.server.wm.WindowState", classLoader)
            
            val hook1 = XposedHelpers.findAndHookMethod(
                windowStateClass,
                "isSecureLocked",
                XC_MethodReplacement.returnConstant(false)
            )
            hooks.add(hook1)
            log("Hooked WindowState.isSecureLocked")
            
            val hook2 = XposedHelpers.findAndHookMethod(
                "com.android.server.wm.WindowManagerService",
                classLoader,
                "isSecureLocked",
                windowStateClass,
                XC_MethodReplacement.returnConstant(false)
            )
            hooks.add(hook2)
            log("Hooked WindowManagerService.isSecureLocked")
            
        } catch (t: Throwable) {
            log("Failed to hook WindowManagerService: ${t.message}")
        }
        
        try {
            val hook1 = XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerGlobal",
                classLoader,
                "addView",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
                Display::class.java,
                Window::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (config.bypassFlagSecure) {
                            val params = param.args[1] as? LayoutParams
                            if (params != null && params.flags and LayoutParams.FLAG_SECURE != 0) {
                                params.flags = params.flags and LayoutParams.FLAG_SECURE.inv()
                                log("Removed FLAG_SECURE in WindowManagerGlobal.addView")
                            }
                        }
                    }
                }
            )
            hooks.add(hook1)
            
            val hook2 = XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerGlobal",
                classLoader,
                "updateViewLayout",
                View::class.java,
                ViewGroup.LayoutParams::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (config.bypassFlagSecure) {
                            val params = param.args[1] as? LayoutParams
                            if (params != null && params.flags and LayoutParams.FLAG_SECURE != 0) {
                                params.flags = params.flags and LayoutParams.FLAG_SECURE.inv()
                                log("Removed FLAG_SECURE in WindowManagerGlobal.updateViewLayout")
                            }
                        }
                    }
                }
            )
            hooks.add(hook2)
            
            log("Hooked WindowManagerGlobal methods")
            
        } catch (t: Throwable) {
            log("Failed to hook WindowManagerGlobal: ${t.message}")
        }
        
        try {
            val hook = XposedHelpers.findAndHookMethod(
                "android.view.Display",
                classLoader,
                "isSecure",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = false
                        log("Display.isSecure forced to false")
                    }
                }
            )
            hooks.add(hook)
            log("Hooked Display.isSecure")
        } catch (t: Throwable) {
            log("Failed to hook Display.isSecure: ${t.message}")
        }
    }
    
    private fun setupAppHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val classLoader = lpparam.classLoader
        
        log("Setting up APP-SPECIFIC hooks for $packageName")
        
        when (packageName) {
            "com.netflix.mediaclient" -> setupNetflixHooks(lpparam)
            "com.amazon.avod.thirdpartyclient" -> setupAmazonHooks(lpparam)
            "com.google.android.youtube" -> setupYouTubeHooks(lpparam)
            "com.disney.disneyplus" -> setupDisneyHooks(lpparam)
            "com.hulu.plus" -> setupHuluHooks(lpparam)
            "com.hbo.hbonow" -> setupHboHooks(lpparam)
            "com.spotify.music" -> setupSpotifyHooks(lpparam)
        }
        
        try {
            val textureViewClass = XposedHelpers.findClassIfExists("android.view.TextureView", classLoader)
            if (textureViewClass != null) {
                val hook = XposedHelpers.findAndHookMethod(
                    textureViewClass,
                    "setSecure",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = false
                            log("TextureView.setSecure forced to false")
                        }
                    }
                )
                hooks.add(hook)
                log("Hooked TextureView.setSecure")
            }
        } catch (t: Throwable) {
            log("Failed to hook TextureView: ${t.message}")
        }
    }
    
    private fun setupLSPatchSpecificHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Setting up LSPatch-specific hooks")
        
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ActivityThread",
                lpparam.classLoader,
                "getPackageManager",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {}
                }
            )
        } catch (t: Throwable) {}
    }
    
    private fun setupDrmBypassHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        
        log("Setting up DRM bypass hooks")
        
        try {
            val mediaDrmClass = XposedHelpers.findClassIfExists("android.media.MediaDrm", classLoader)
            if (mediaDrmClass != null) {
                val hook1 = XposedHelpers.findAndHookMethod(
                    mediaDrmClass,
                    "getPropertyString",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val property = param.args[0] as? String
                            if (property == "securityLevel") {
                                param.result = "L3"
                                log("MediaDrm security level downgraded to L3")
                            }
                        }
                    }
                )
                hooks.add(hook1)
                
                val hook2 = XposedHelpers.findAndHookMethod(
                    mediaDrmClass,
                    "openSession",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result == null || (param.result is ByteArray && (param.result as ByteArray).isEmpty())) {
                                param.result = ByteArray(16) { it.toByte() }
                                log("Provided fake DRM session ID")
                            }
                        }
                    }
                )
                hooks.add(hook2)
                
                log("Hooked MediaDrm methods")
            }
        } catch (t: Throwable) {
            log("Failed to hook MediaDrm: ${t.message}")
        }
        
        try {
            val mediaPlayerClass = XposedHelpers.findClassIfExists("android.media.MediaPlayer", classLoader)
            if (mediaPlayerClass != null) {
                val hook = XposedHelpers.findAndHookMethod(
                    mediaPlayerClass,
                    "prepareDrm",
                    java.util.UUID::class.java,
                    XC_MethodReplacement.returnConstant(true)
                )
                hooks.add(hook)
                log("Hooked MediaPlayer.prepareDrm")
            }
        } catch (t: Throwable) {
            log("Failed to hook MediaPlayer: ${t.message}")
        }
    }
    
    private fun setupBlackScreenPrevention(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val hook = XposedHelpers.findAndHookMethod(
                View::class.java,
                "draw",
                android.graphics.Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as View
                            if (view.visibility == View.VISIBLE && view.width > 0 && view.height > 0) {
                                val bg = view.background
                                if (bg != null) {
                                    try {
                                        val bgStr = bg.toString().toLowerCase()
                                        if (bgStr.contains("color=0xff000000") || 
                                            bgStr.contains("color=#000000") ||
                                            bgStr.contains("black") && view.alpha == 1.0f) {
                                            
                                            view.alpha = 0.5f
                                            log("Detected and dimmed black overlay")
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            )
            hooks.add(hook)
            log("Hooked View.draw for black screen detection")
        } catch (t: Throwable) {
            log("Failed to hook View.draw: ${t.message}")
        }
    }
    
    private fun setupNetflixHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.netflix.mediaclient.service.player.bladerunnerclient.BladeRunnerClient",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            XposedHelpers.findAndHookMethod(
                "com.netflix.mediaclient.service.configuration.crypto.CryptoManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("Netflix hooks installed")
        } catch (t: Throwable) {
            log("Netflix hooks failed: ${t.message}")
        }
    }
    
    private fun setupYouTubeHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.libraries.youtube.media.interfaces.Player",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            
            log("YouTube hooks installed")
        } catch (t: Throwable) {
            log("YouTube hooks failed: ${t.message}")
        }
    }
    
    private fun setupAmazonHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.amazon.avod.thirdpartyclient.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Amazon Prime hooks installed")
        } catch (t: Throwable) {
            log("Amazon Prime hooks failed: ${t.message}")
        }
    }
    
    private fun setupDisneyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.disney.disneyplus.drm.DrmHelper",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Disney+ hooks installed")
        } catch (t: Throwable) {
            log("Disney+ hooks failed: ${t.message}")
        }
    }
    
    private fun setupHuluHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.hulu.plus.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Hulu hooks installed")
        } catch (t: Throwable) {
            log("Hulu hooks failed: ${t.message}")
        }
    }
    
    private fun setupHboHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.hbo.hbonow.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("HBO Max hooks installed")
        } catch (t: Throwable) {
            log("HBO Max hooks failed: ${t.message}")
        }
    }
    
    private fun setupSpotifyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.spotify.music.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Spotify hooks installed")
        } catch (t: Throwable) {
            log("Spotify hooks failed: ${t.message}")
        }
    }
}
