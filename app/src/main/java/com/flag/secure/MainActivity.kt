package com.flag.secure

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "SecureBypassPrefs"
        const val KEY_BYPASS_FLAG_SECURE = "bypass_flag_secure"
        const val KEY_BYPASS_DRM = "bypass_drm"
        const val KEY_BYPASS_BLACK_SCREEN = "bypass_black_screen"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val KEY_SYSTEM_APPS = "system_apps"
        const val KEY_TARGET_APPS = "target_apps"

        const val TAG = "SecureBypass"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var lspatchHelper: LSPatchHelper

    private lateinit var tvStatus: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var switchFlagSecure: SwitchCompat
    private lateinit var switchDrm: SwitchCompat
    private lateinit var switchBlackScreen: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchSystemApps: SwitchCompat
    private lateinit var spTargetApps: Spinner
    private lateinit var llRootSettings: LinearLayout
    private lateinit var llLSPatchSettings: LinearLayout
    private lateinit var tvLSPatchInfo: TextView

    private var spinnerInitialized = false

    // ---------------- ACTIVITY LIFECYCLE ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Inflate layout FIRST
        setContentView(R.layout.activity_main)

        // 2️⃣ SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // ❌ REMOVED (FIX #1)
        // UniversalSecureBypass.init(applicationContext)
        // Xposed / hook code MUST NOT run from Activity

        // 3️⃣ Helpers
        lspatchHelper = LSPatchHelper(this)

        // 4️⃣ Bind views
        bindViews()

        // 5️⃣ UI setup
        setupSpinner()
        loadSettings()
        setupListeners()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    // ---------------- VIEW BINDING ----------------

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvMode = findViewById(R.id.tvMode)
        tvInstructions = findViewById(R.id.tvInstructions)
        switchFlagSecure = findViewById(R.id.switchFlagSecure)
        switchDrm = findViewById(R.id.switchDrm)
        switchBlackScreen = findViewById(R.id.switchBlackScreen)
        switchNotifications = findViewById(R.id.switchNotifications)
        switchSystemApps = findViewById(R.id.switchSystemApps)
        spTargetApps = findViewById(R.id.spTargetApps)
        llRootSettings = findViewById(R.id.llRootSettings)
        llLSPatchSettings = findViewById(R.id.llLSPatchSettings)
        tvLSPatchInfo = findViewById(R.id.tvLSPatchInfo)
    }

    // ---------------- SPINNER ----------------

    private fun setupSpinner() {
        val apps = arrayOf(
            "All Streaming Apps",
            "Netflix Only",
            "YouTube Only",
            "Amazon Prime Only",
            "Disney+ Only",
            "Custom Selection"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            apps
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spTargetApps.adapter = adapter

        spTargetApps.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (!spinnerInitialized) {
                        spinnerInitialized = true
                        return
                    }

                    val value = parent?.getItemAtPosition(position) as String
                    prefs.edit().putString(KEY_TARGET_APPS, value).apply()
                    toast("Target apps: $value")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    // ---------------- STATE ----------------

    private fun refreshState() {
        val rooted = try {
            UniversalSecureBypass.isRootedMode
        } catch (e: Throwable) {
            false
        }

        tvMode.text = if (rooted)
            "Mode: ROOT (LSPosed/Xposed)"
        else
            "Mode: UNROOTED (LSPatch)"

        updateModuleStatus()
        updateUIForMode(rooted)
        updateLSPatchInfo()
    }

    private fun updateModuleStatus() {
        val active = isModuleActive()

        if (active) {
            tvStatus.text = "✅ Module ACTIVE"
            tvStatus.setTextColor(color(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "❌ Module NOT ACTIVE"
            tvStatus.setTextColor(color(android.R.color.holo_red_dark))
        }
    }

    private fun updateUIForMode(rooted: Boolean) {
        if (rooted) {
            llRootSettings.visibility = View.VISIBLE
            llLSPatchSettings.visibility = View.GONE
            switchSystemApps.isEnabled = true
            tvInstructions.text =
                "Rooted mode: enable module in LSPosed and select target apps."
        } else {
            llRootSettings.visibility = View.GONE
            llLSPatchSettings.visibility = View.VISIBLE
            switchSystemApps.isChecked = false
            switchSystemApps.isEnabled = false
            tvInstructions.text =
                if (lspatchHelper.isLSPatchInstalled())
                    "Use LSPatch to embed this module into target apps."
                else
                    "Install LSPatch Manager to continue."
        }
    }

    private fun updateLSPatchInfo() {
        val installed = lspatchHelper.isLSPatchInstalled()
        tvLSPatchInfo.text =
            if (installed)
                "LSPatch Installed ✅\nPatch apps using LSPatch Manager."
            else
                "LSPatch Not Installed ❌"
    }

    // ---------------- LISTENERS ----------------

    private fun setupListeners() {
        switchFlagSecure.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_BYPASS_FLAG_SECURE, v).apply()
        }

        switchDrm.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_BYPASS_DRM, v).apply()
        }

        switchBlackScreen.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_BYPASS_BLACK_SCREEN, v).apply()
        }

        switchNotifications.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_SHOW_NOTIFICATIONS, v).apply()
        }

        switchSystemApps.setOnCheckedChangeListener { _, v ->
            if (UniversalSecureBypass.isRootedMode) {
                prefs.edit().putBoolean(KEY_SYSTEM_APPS, v).apply()
            }
        }
    }

    // ---------------- SETTINGS ----------------

    private fun loadSettings() {
        switchFlagSecure.isChecked =
            prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
        switchDrm.isChecked =
            prefs.getBoolean(KEY_BYPASS_DRM, true)
        switchBlackScreen.isChecked =
            prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
        switchNotifications.isChecked =
            prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, false)

        switchSystemApps.isChecked =
            UniversalSecureBypass.isRootedMode &&
                    prefs.getBoolean(KEY_SYSTEM_APPS, true)
    }

    // ---------------- HELPERS ----------------

    private fun isModuleActive(): Boolean {
        return try {
            Class.forName("com.flag.secure.UniversalSecureBypass")
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ✅ FIX #4 — SAFE COLOR ACCESS
    private fun color(id: Int): Int {
        return if (Build.VERSION.SDK_INT >= 23)
            getColor(id)
        else
            resources.getColor(id)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- SHARE APK ----------------

    private fun shareModuleApk() {
        val apkFile = File(applicationInfo.publicSourceDir)

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // ✅ FIX #2
            apkFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Module APK"))
    }

    // ---------------- PERMISSIONS ----------------

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
