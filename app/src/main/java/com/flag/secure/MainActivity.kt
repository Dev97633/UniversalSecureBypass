package com.flag.secure

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        UniversalSecureBypass.init(applicationContext)
        lspatchHelper = LSPatchHelper(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        bindViews()
        setupSpinner()
        loadSettings()
        setupListeners()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    // ---------------- INIT ----------------

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
                    view: android.view.View?,
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
        val rooted = UniversalSecureBypass.isRootedMode

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
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "❌ Module NOT ACTIVE"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun updateUIForMode(rooted: Boolean) {
        if (rooted) {
            llRootSettings.visibility = LinearLayout.VISIBLE
            llLSPatchSettings.visibility = LinearLayout.GONE
            switchSystemApps.isEnabled = true
            tvInstructions.text =
                "Rooted mode: enable module in LSPosed and select target apps."
        } else {
            llRootSettings.visibility = LinearLayout.GONE
            llLSPatchSettings.visibility = LinearLayout.VISIBLE
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
        val sb = StringBuilder()
        val installed = lspatchHelper.isLSPatchInstalled()

        sb.append("LSPatch: ")
            .append(if (installed) "Installed ✅" else "Not Installed ❌")
            .append("\n\n")

        if (installed) {
            sb.append(
                """
                Steps:
                1. Open LSPatch Manager
                2. Patch target app
                3. Select this module APK
                4. Install patched APK
                """.trimIndent()
            )
        }

        tvLSPatchInfo.text = sb.toString()
    }

    // ---------------- LISTENERS ----------------

    private fun setupListeners() {
        switchFlagSecure.save(KEY_BYPASS_FLAG_SECURE)
        switchDrm.save(KEY_BYPASS_DRM)
        switchBlackScreen.save(KEY_BYPASS_BLACK_SCREEN)
        switchNotifications.save(KEY_SHOW_NOTIFICATIONS)

        switchSystemApps.setOnCheckedChangeListener { _, checked ->
            if (UniversalSecureBypass.isRootedMode) {
                prefs.edit().putBoolean(KEY_SYSTEM_APPS, checked).apply()
            }
        }
    }

    private fun SwitchCompat.save(key: String) {
        setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
    }

    // ---------------- SERVICE ----------------

    fun onStartServiceClicked(v: android.view.View) {
        val intent = Intent(this, SecureBypassService::class.java)
            .setAction(SecureBypassService.ACTION_START)

        if (Build.VERSION.SDK_INT >= 26)
            startForegroundService(intent)
        else
            startService(intent)

        toast("Service started")
    }

    fun onStopServiceClicked(v: android.view.View) {
        startService(
            Intent(this, SecureBypassService::class.java)
                .setAction(SecureBypassService.ACTION_STOP)
        )
        toast("Service stopped")
    }

    // ---------------- HELPERS ----------------

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

    private fun isModuleActive(): Boolean {
        return try {
            Class.forName("com.flag.secure.UniversalSecureBypass")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
