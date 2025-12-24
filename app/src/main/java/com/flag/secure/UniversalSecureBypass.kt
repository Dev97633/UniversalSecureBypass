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
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SAFE container class.
 * NEVER directly loaded by Android launcher.
 * Called ONLY from XposedEntry.
 */
class UniversalSecureBypass {

    companion object {

        const val TAG = "UniversalSecureBypass"
        const val VERSION = "2.0.0"

        @JvmStatic var isRootedMode = false
        @JvmStatic var isLSPatchMode = false
        @JvmStatic var isActive = true
        @JvmStatic var modulePath: String? = null
        @JvmStatic private var appContext: Context? = null

        // ===== SAFE UI INIT =====
        @JvmStatic
        fun init(context: Context?) {
            appContext = context
            detectEnvironment()
        }

        // ===== XPOSED ENTRY BRIDGE =====
        @JvmStatic
        fun onZygoteInit(startupParam: IXposedHookZygoteInit.StartupParam?) {
            modulePath = startupParam?.modulePath
            isRootedMode = true
            log("Initialized in ROOTED mode")
            log("Module version: $VERSION")
            log("Module path: $modulePath")
        }

        @JvmStatic
        fun onPackageLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
            handleLoadPackageInternal(lpparam)
        }

        @JvmStatic
        fun onResourcesLoaded(resparam: XC_InitPackageResources.InitPackageResourcesParam?) {
            // kept for compatibility
        }

        // ===== ENV DETECTION =====
        private fun detectEnvironment() {
            try {
                val rootPaths = arrayOf(
                    "/system/bin/su", "/system/xbin/su", "/sbin/su",
                    "/data/local/xbin/su", "/data/local/bin/su",
                    "/system/sd/xbin/su", "/system/bin/failsafe/su",
                    "/data/local/su"
                )

                for (p in rootPaths) {
                    if (File(p).exists()) {
                        isRootedMode = true
                        break
                    }
                }

                try {
                    Class.forName("org.lsposed.lspd.BuildConfig")
                    isRootedMode = true
                } catch (_: Throwable) {}

                try {
                    Class.forName("de.robv.android.xposed.XposedBridge")
                    isRootedMode = true
                } catch (_: Throwable) {}

                if (!isRootedMode) {
                    isLSPatchMode = true
                }

            } catch (e: Throwable) {
                isLSPatchMode = true
            }
        }

        // ===== LOGGING =====
        @JvmStatic
        fun log(msg: String) {
            val finalMsg = "[${if (isRootedMode) "ROOT" else "LSPATCH"}] $msg"
            Log.d(TAG, finalMsg)
            try { XposedBridge.log("$TAG: $finalMsg") } catch (_: Throwable) {}
            logToFile(finalMsg)
        }

        private fun logToFile(message: String) {
            try {
                val dir = appContext?.filesDir ?: File("/sdcard/UniversalSecureBypass")
                val logDir = File(dir, "logs")
                if (!logDir.exists()) logDir.mkdirs()

                val file = File(logDir, "module_log.txt")
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                file.appendText("$time - $message\n")
            } catch (_: Throwable) {}
        }

        // ===== CORE PACKAGE HANDLER =====
        private fun handleLoadPackageInternal(lpparam: XC_LoadPackage.LoadPackageParam) {
            val pkg = lpparam.packageName
            log("Loading package: $pkg")

            try {
                val app = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ActivityThread"),
                    "currentApplication"
                ) as? Application
                appContext = app
            } catch (_: Throwable) {}

            setupUniversalHooks(lpparam)

            when (pkg) {
                "com.netflix.mediaclient" -> setupNetflixHooks(lpparam)
                "com.amazon.avod.thirdpartyclient" -> setupAmazonHooks(lpparam)
                "com.google.android.youtube" -> setupYouTubeHooks(lpparam)
                "com.disney.disneyplus" -> setupDisneyHooks(lpparam)
                "com.hulu.plus" -> setupHuluHooks(lpparam)
                "com.hbo.hbonow" -> setupHboHooks(lpparam)
                "com.spotify.music" -> setupSpotifyHooks(lpparam)
            }
        }

        // ===== UNIVERSAL HOOKS =====
        private fun setupUniversalHooks(lpparam: XC_LoadPackage.LoadPackageParam) {

            XposedHelpers.findAndHookMethod(
                Window::class.java,
                "setFlags",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val flags = param.args[0] as Int
                        param.args[0] = flags and LayoutParams.FLAG_SECURE.inv()
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val act = param.thisObject as Activity
                        act.window?.clearFlags(LayoutParams.FLAG_SECURE)
                    }
                }
            )
        }

        // ===== APP-SPECIFIC HOOKS =====
        private fun setupNetflixHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.netflix.mediaclient.service.player.bladerunnerclient.BladeRunnerClient",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupYouTubeHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.google.android.libraries.youtube.media.interfaces.Player",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupAmazonHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.amazon.avod.thirdpartyclient.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupDisneyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.disney.disneyplus.drm.DrmHelper",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupHuluHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.hulu.plus.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupHboHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.hbo.hbonow.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }

        private fun setupSpotifyHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "com.spotify.music.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
        }
    }
}
