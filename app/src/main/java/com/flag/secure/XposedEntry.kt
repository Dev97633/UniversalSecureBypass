package com.flag.secure

import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * ONLY Xposed/LSPosed will load this class.
 * Normal app process will NEVER touch it.
 */
class XposedEntry :
    IXposedHookZygoteInit,
    IXposedHookLoadPackage,
    IXposedHookInitPackageResources {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        XposedBridge.log("UniversalSecureBypass: initZygote")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Delegate to your existing logic
            UniversalSecureBypass.onPackageLoaded(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        try {
            UniversalSecureBypass.onResourcesLoaded(resparam)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
