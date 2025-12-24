package com.flag.secure

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * UniversalSecureBypass
 * Works with Xposed / LSPosed / LSPatch
 */
class UniversalSecureBypass : IXposedHookLoadPackage {

    companion object {

        const val TAG = "UniversalSecureBypass"
        const val VERSION = "2.0.0"
        const val PREFS_NAME = "SecureBypassPrefs"

        const val KEY_BYPASS_FLAG_SECURE = "bypass_flag_secure"
        const val KEY_BYPASS_DRM = "bypass_drm"
        const val KEY_BYPASS_BLACK_SCREEN = "bypass_black_screen"
        const val KEY_SYSTEM_APPS = "system_apps"
        const val KEY_TARGET_APPS = "target_apps"

        @JvmStatic private var appContext: Context? = null
        @JvmStatic private lateinit var prefs: SharedPreferences

        @JvmStatic
        fun init(context: Context?) {
            appContext = context
            if (context != null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        @JvmStatic
        private fun ensureContext() {
            if (appContext == null) {
                appContext = AndroidAppHelper.currentApplication()
            }
            if (::prefs.isInitialized.not() && appContext != null) {
                prefs = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        @JvmStatic
        fun log(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val finalMsg = "[$time] $msg"
            Log.d(TAG, finalMsg)
            try {
                XposedBridge.log("$TAG: $finalMsg")
            } catch (_: Throwable) {}
        }

        @JvmStatic
        fun showToast(context: Context?, msg: String) {
            try {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
    }

    /* ========================================================= */
    /* =================== ENTRY POINT ========================= */
    /* ========================================================= */

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            handleLoadPackageInternal(lpparam)
        } catch (t: Throwable) {
            log("Fatal error: ${t.message}")
        }
    }

    /* ========================================================= */
    /* ================= CORE LOGIC ============================ */
    /* ========================================================= */

    private fun handleLoadPackageInternal(lpparam: XC_LoadPackage.LoadPackageParam) {

        ensureContext()

        val packageName = lpparam.packageName

        if (packageName == "android" || packageName == "com.flag.secure") {
            return
        }

        log("Loaded package: $packageName")

        val bypassFlagSecure = prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
        val bypassDrm = prefs.getBoolean(KEY_BYPASS_DRM, true)
        val bypassBlackScreen = prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)

        log("Settings → FLAG_SECURE=$bypassFlagSecure DRM=$bypassDrm BLACK=$bypassBlackScreen")

        if (bypassFlagSecure) setupFlagSecureHooks()
        if (bypassDrm) setupDrmHooks(lpparam)
        if (bypassBlackScreen) setupBlackScreenHooks()

        setupAppSpecificHooks(lpparam, packageName)
    }

    /* ========================================================= */
    /* ================= FLAG_SECURE =========================== */
    /* ========================================================= */

    private fun setupFlagSecureHooks() {

        XposedHelpers.findAndHookMethod(
            Window::class.java,
            "setFlags",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    val mask = param.args[1] as Int
                    if ((mask and LayoutParams.FLAG_SECURE) != 0) {
                        param.args[0] = flags and LayoutParams.FLAG_SECURE.inv()
                        log("FLAG_SECURE blocked (setFlags)")
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            Window::class.java,
            "addFlags",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    if ((flags and LayoutParams.FLAG_SECURE) != 0) {
                        param.args[0] = flags and LayoutParams.FLAG_SECURE.inv()
                        log("FLAG_SECURE blocked (addFlags)")
                    }
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
                    act.window.clearFlags(LayoutParams.FLAG_SECURE)
                    log("FLAG_SECURE cleared in Activity.onCreate")
                }
            }
        )
    }

    /* ========================================================= */
    /* ================= DRM BYPASS ============================ */
    /* ========================================================= */

    private fun setupDrmHooks(lpparam: XC_LoadPackage.LoadPackageParam) {

        val drmClasses = listOf(
            "android.media.MediaDrm",
            "com.google.android.exoplayer2.drm.FrameworkMediaDrm",
            "com.widevine.alpha.WidevineDrm"
        )

        for (clsName in drmClasses) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue

                XposedHelpers.findAndHookMethod(
                    cls,
                    "getPropertyString",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = "L3"
                            log("DRM downgraded to L3 ($clsName)")
                        }
                    }
                )

            } catch (_: Throwable) {}
        }
    }

    /* ========================================================= */
    /* ================= BLACK SCREEN ========================== */
    /* ========================================================= */

    private fun setupBlackScreenHooks() {

        XposedHelpers.findAndHookMethod(
            View::class.java,
            "setVisibility",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    val cls = view.javaClass.name

                    if (
                        cls.contains("Surface", true) ||
                        cls.contains("Texture", true) ||
                        cls.contains("Video", true) ||
                        cls.contains("Player", true)
                    ) {
                        val v = param.args[0] as Int
                        if (v == View.GONE || v == View.INVISIBLE) {
                            param.args[0] = View.VISIBLE
                            log("Black screen prevented: $cls")
                        }
                    }
                }
            }
        )
    }

    /* ========================================================= */
    /* ================= APP SPECIFIC ========================== */
    /* ========================================================= */

    private fun setupAppSpecificHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        pkg: String
    ) {
        when {
            pkg.contains("netflix", true) -> setupNetflix(lpparam)
            pkg.contains("youtube", true) -> setupYouTube(lpparam)
            pkg.contains("amazon", true) -> setupAmazon(lpparam)
            pkg.contains("disney", true) || pkg.contains("hotstar", true) -> setupDisney(lpparam)
            pkg.contains("hbo", true) -> setupHbo(lpparam)
            pkg.contains("hulu", true) -> setupHulu(lpparam)
            pkg.contains("spotify", true) -> setupSpotify(lpparam)
        }
    }

    private fun setupNetflix(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.netflix.mediaclient.service.player.bladerunnerclient.BladeRunnerClient",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Netflix hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupYouTube(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.libraries.youtube.media.interfaces.Player",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("YouTube hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupAmazon(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.amazon.avod.thirdpartyclient.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Amazon hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupDisney(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.disney.disneyplus.drm.DrmHelper",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Disney hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupHbo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.hbo.hbonow.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("HBO hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupHulu(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.hulu.plus.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Hulu hooks installed")
        } catch (_: Throwable) {}
    }

    private fun setupSpotify(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.spotify.music.drm.DrmManager",
                lpparam.classLoader,
                "isSecureSurfaceRequired",
                XC_MethodReplacement.returnConstant(false)
            )
            log("Spotify hooks installed")
        } catch (_: Throwable) {}
    }
}
