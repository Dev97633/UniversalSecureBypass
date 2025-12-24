package com.flag.secure

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Set content view FIRST
            setContentView(R.layout.activity_main)
            
            // Initialize essential components
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            // Initialize UniversalSecureBypass carefully
            try {
                UniversalSecureBypass.init(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init UniversalSecureBypass", e)
            }
            
            lspatchHelper = LSPatchHelper(this)
            
            bindViews()
            setupSpinner()
            loadSettings()
            setupListeners()
            refreshState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate", e)
            showSimpleErrorDialog(e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun bindViews() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in bindViews", e)
            throw e
        }
    }

    private fun setupSpinner() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupSpinner", e)
            throw e
        }
    }

    private fun refreshState() {
        try {
            val rooted = try {
                UniversalSecureBypass.isRootedMode
            } catch (e: Exception) {
                Log.e(TAG, "Error checking rooted mode", e)
                false
            }

            tvMode.text = if (rooted)
                "Mode: ROOT (LSPosed/Xposed)"
            else
                "Mode: UNROOTED (LSPatch)"

            updateModuleStatus()
            updateUIForMode(rooted)
            updateLSPatchInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error in refreshState", e)
        }
    }

    private fun updateModuleStatus() {
        try {
            val active = isModuleActive()

            if (active) {
                tvStatus.text = "✅ Module ACTIVE"
                tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvStatus.text = "❌ Module NOT ACTIVE"
                tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateModuleStatus", e)
            tvStatus.text = "⚠️ Status Unknown"
        }
    }

    private fun updateUIForMode(rooted: Boolean) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateUIForMode", e)
        }
    }

    private fun updateLSPatchInfo() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateLSPatchInfo", e)
            tvLSPatchInfo.text = "Error loading LSPatch info"
        }
    }

    private fun setupListeners() {
        try {
            switchFlagSecure.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_FLAG_SECURE, checked).apply()
            }
            
            switchDrm.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_DRM, checked).apply()
            }
            
            switchBlackScreen.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_BLACK_SCREEN, checked).apply()
            }
            
            switchNotifications.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_SHOW_NOTIFICATIONS, checked).apply()
            }

            switchSystemApps.setOnCheckedChangeListener { _, checked ->
                try {
                    if (UniversalSecureBypass.isRootedMode) {
                        prefs.edit().putBoolean(KEY_SYSTEM_APPS, checked).apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in switchSystemApps listener", e)
                }
            }
            
            // Setup other button listeners
            findViewById<Button>(R.id.btnTest)?.setOnClickListener {
                toast("Test functionality not implemented yet")
            }
            
            findViewById<Button>(R.id.btnLogs)?.setOnClickListener {
                showLogsDialog()
            }
            
            findViewById<Button>(R.id.btnSetup)?.setOnClickListener {
                showSetupGuide()
            }
            
            findViewById<Button>(R.id.btnShareModule)?.setOnClickListener {
                shareModuleApk()
            }
            
            findViewById<Button>(R.id.btnLSPatchGuide)?.setOnClickListener {
                showLSPatchGuide()
            }
            
            findViewById<Button>(R.id.btnCheckPermissions)?.setOnClickListener {
                checkPermissions()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupListeners", e)
        }
    }

    // ---------------- SERVICE ----------------

    fun onStartServiceClicked(v: android.view.View) {
        try {
            val intent = Intent(this, SecureBypassService::class.java)
                .setAction(SecureBypassService.ACTION_START)

            if (Build.VERSION.SDK_INT >= 26)
                startForegroundService(intent)
            else
                startService(intent)

            toast("Service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service", e)
            toast("Failed to start service: ${e.message}")
        }
    }

    fun onStopServiceClicked(v: android.view.View) {
        try {
            startService(
                Intent(this, SecureBypassService::class.java)
                    .setAction(SecureBypassService.ACTION_STOP)
            )
            toast("Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
            toast("Failed to stop service: ${e.message}")
        }
    }
    
    // ---------------- BUTTON HANDLERS ----------------
    
    fun onRootModeClicked(v: View) {
        toast("Root Mode clicked")
    }
    
    fun onLSPatchModeClicked(v: View) {
        toast("LSPatch Mode clicked")
    }
    
    fun onOpenLSPatchManagerClicked(v: View) {
        openLSPatchManager()
    }

    // ---------------- HELPERS ----------------

    private fun loadSettings() {
        try {
            switchFlagSecure.isChecked =
                prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
            switchDrm.isChecked =
                prefs.getBoolean(KEY_BYPASS_DRM, true)
            switchBlackScreen.isChecked =
                prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
            switchNotifications.isChecked =
                prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, false)

            switchSystemApps.isChecked =
                try {
                    UniversalSecureBypass.isRootedMode &&
                            prefs.getBoolean(KEY_SYSTEM_APPS, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading system apps setting", e)
                    false
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadSettings", e)
        }
    }

    private fun isModuleActive(): Boolean {
        return try {
            Class.forName("com.flag.secure.UniversalSecureBypass")
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun toast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast", e)
        }
    }
    
    // ---------------- SIMPLE ERROR HANDLING ----------------
    
    private fun showSimpleErrorDialog(e: Exception) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("App Error")
                .setMessage("An error occurred: ${e.message}\n\nApp may not work correctly.")
                .setPositiveButton("Continue") { _, _ ->
                    // Continue anyway
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun showLogsDialog() {
        // Simple logs dialog for now
        AlertDialog.Builder(this)
            .setTitle("Logs")
            .setMessage("Logging system is not fully implemented yet.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showSetupGuide() {
        AlertDialog.Builder(this)
            .setTitle("Setup Guide")
            .setMessage(
                """
                Rooted Mode:
                1. Install LSPosed/Xposed framework
                2. Enable module in LSPosed
                3. Select target apps
                4. Reboot device
                
                Unrooted Mode:
                1. Install LSPatch Manager
                2. Patch target app with this module
                3. Install patched APK
                4. Launch patched app
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun shareModuleApk() {
        try {
            val apkPath = applicationInfo.publicSourceDir
            val apkFile = File(apkPath)
            
            if (apkFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    apkFile
                )
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_SUBJECT, "Universal Secure Bypass Module")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share Module APK"))
            } else {
                toast("APK file not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing APK", e)
            toast("Failed to share APK")
        }
    }
    
    private fun showLSPatchGuide() {
        AlertDialog.Builder(this)
            .setTitle("LSPatch Guide")
            .setMessage(
                """
                LSPatch Installation:
                1. Download LSPatch Manager from GitHub
                2. Install LSPatch Manager
                3. Open LSPatch Manager
                4. Grant necessary permissions
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .setNegativeButton("Download") { _, _ ->
                openLSPatchDownload()
            }
            .show()
    }
    
    private fun checkPermissions() {
        val missingPerms = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Storage")
            }
            if (checkSelfPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Overlay")
            }
        }
        
        if (missingPerms.isEmpty()) {
            toast("All permissions granted ✅")
        } else {
            AlertDialog.Builder(this)
                .setTitle("Missing Permissions")
                .setMessage("The following permissions are missing:\n• ${missingPerms.joinToString("\n• ")}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun openLSPatchManager() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("org.lsposed.lspatch")
            if (intent != null) {
                startActivity(intent)
            } else {
                toast("LSPatch Manager not installed")
                openLSPatchDownload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening LSPatch Manager", e)
            toast("Cannot open LSPatch Manager")
        }
    }
    
    private fun openLSPatchDownload() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LSPosed/LSPatch/releases"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening browser", e)
            toast("Cannot open browser")
        }
    }
}
