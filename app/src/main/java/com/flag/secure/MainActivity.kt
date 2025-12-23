package com.flag.secure

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        const val KEY_LSPATCH_MODE = "lspatch_mode"
        const val KEY_TARGET_APPS = "target_apps"
    }
    
    private lateinit var prefs: SharedPreferences
    private lateinit var lspatchHelper: LSPatchHelper
    
    // UI Components
    private lateinit var tvStatus: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var switchFlagSecure: SwitchCompat
    private lateinit var switchDrm: SwitchCompat
    private lateinit var switchBlackScreen: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchSystemApps: SwitchCompat
    private lateinit var btnTest: Button
    private lateinit var btnLogs: Button
    private lateinit var btnSetup: Button
    private lateinit var btnShareModule: Button
    private lateinit var btnLSPatchGuide: Button
    private lateinit var spTargetApps: Spinner
    private lateinit var llRootSettings: LinearLayout
    private lateinit var llLSPatchSettings: LinearLayout
    private lateinit var tvLSPatchInfo: TextView
    private lateinit var btnCheckPermissions: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize components
        UniversalSecureBypass.init(applicationContext)
        lspatchHelper = LSPatchHelper(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initializeViews()
        loadSettings()
        setupListeners()
        detectEnvironment()
        updateUIForMode()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh status when returning to activity
        detectEnvironment()
    }
    
    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvMode = findViewById(R.id.tvMode)
        tvInstructions = findViewById(R.id.tvInstructions)
        switchFlagSecure = findViewById(R.id.switchFlagSecure)
        switchDrm = findViewById(R.id.switchDrm)
        switchBlackScreen = findViewById(R.id.switchBlackScreen)
        switchNotifications = findViewById(R.id.switchNotifications)
        switchSystemApps = findViewById(R.id.switchSystemApps)
        btnTest = findViewById(R.id.btnTest)
        btnLogs = findViewById(R.id.btnLogs)
        btnSetup = findViewById(R.id.btnSetup)
        btnShareModule = findViewById(R.id.btnShareModule)
        btnLSPatchGuide = findViewById(R.id.btnLSPatchGuide)
        spTargetApps = findViewById(R.id.spTargetApps)
        llRootSettings = findViewById(R.id.llRootSettings)
        llLSPatchSettings = findViewById(R.id.llLSPatchSettings)
        tvLSPatchInfo = findViewById(R.id.tvLSPatchInfo)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
        
        // Setup target apps spinner
        val targetApps = arrayOf(
            "All Streaming Apps",
            "Netflix Only",
            "YouTube Only",
            "Amazon Prime Only",
            "Disney+ Only",
            "Hulu Only",
            "HBO Max Only",
            "Spotify Only",
            "Custom Selection"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, targetApps)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTargetApps.adapter = adapter
    }
    
    private fun setupListeners() {
        // Settings switches
        switchFlagSecure.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_FLAG_SECURE, isChecked)
            showToast("FLAG_SECURE bypass ${if (isChecked) "enabled" else "disabled"}")
        }
        
        switchDrm.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_DRM, isChecked)
            showToast("DRM bypass ${if (isChecked) "enabled" else "disabled"}")
        }
        
        switchBlackScreen.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_BLACK_SCREEN, isChecked)
            showToast("Black screen prevention ${if (isChecked) "enabled" else "disabled"}")
        }
        
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_SHOW_NOTIFICATIONS, isChecked)
            showToast("Notifications ${if (isChecked) "enabled" else "disabled"}")
        }
        
        switchSystemApps.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_SYSTEM_APPS, isChecked)
            showToast("System apps hooking ${if (isChecked) "enabled" else "disabled"}")
        }
        
        // Buttons
        btnTest.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
        
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
        
        btnSetup.setOnClickListener {
            showSetupInstructions()
        }
        
        btnShareModule.setOnClickListener {
            shareModuleApk()
        }
        
        btnLSPatchGuide.setOnClickListener {
            lspatchHelper.openLSPatchGuide()
        }
        
        btnCheckPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }
        
        // Target apps spinner
        spTargetApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedApp = parent?.getItemAtPosition(position) as String
                saveSetting(KEY_TARGET_APPS, selectedApp)
                showToast("Target apps set to: $selectedApp")
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun detectEnvironment() {
        // Detect mode
        val isRooted = UniversalSecureBypass.isRootedMode
        val modeText = if (isRooted) {
            "ROOTED (LSPosed/Xposed)"
        } else {
            "UNROOTED (LSPatch)"
        }
        
        tvMode.text = "Mode: $modeText"
        
        // Check if module is active
        val isModuleActive = try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        if (isModuleActive) {
            tvStatus.text = "✅ Module is ACTIVE"
            tvStatus.setTextColor(getColorCompat(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "❌ Module NOT ACTIVE"
            tvStatus.setTextColor(getColorCompat(android.R.color.holo_red_dark))
            
            // Show appropriate instructions
            if (isRooted) {
                tvInstructions.text = "Module installed but not active in LSPosed/Xposed. Enable it in your Xposed manager."
            } else {
                if (lspatchHelper.isLSPatchInstalled()) {
                    tvInstructions.text = "LSPatch detected. You need to embed this module into target apps using LSPatch Manager."
                } else {
                    tvInstructions.text = "LSPatch not installed. Install LSPatch Manager to use this module on unrooted devices."
                }
            }
        }
        
        // Update LSPatch info
        updateLSPatchInfo()
    }
    
    private fun updateUIForMode() {
        val isRooted = UniversalSecureBypass.isRootedMode
        
        if (isRooted) {
            // Rooted mode UI
            llRootSettings.visibility = android.view.View.VISIBLE
            llLSPatchSettings.visibility = android.view.View.GONE
            switchSystemApps.isEnabled = true
            tvInstructions.text = "Rooted mode active. Module will work system-wide for selected apps."
        } else {
            // LSPatch mode UI
            llRootSettings.visibility = android.view.View.GONE
            llLSPatchSettings.visibility = android.view.View.VISIBLE
            switchSystemApps.isEnabled = false
            switchSystemApps.isChecked = false
            
            if (lspatchHelper.isLSPatchInstalled()) {
                tvInstructions.text = "LSPatch mode. Embed module into target apps using LSPatch Manager."
            } else {
                tvInstructions.text = "Install LSPatch Manager to use this module on unrooted devices."
            }
        }
    }
    
    private fun updateLSPatchInfo() {
        val isLSPatchInstalled = lspatchHelper.isLSPatchInstalled()
        val modulePath = lspatchHelper.getModuleApkPath()
        
        val info = StringBuilder()
        info.append("LSPatch Status: ${if (isLSPatchInstalled) "✅ Installed" else "❌ Not Installed"}\n\n")
        
        if (isLSPatchInstalled) {
            info.append("To use this module:\n")
            info.append("1. Open LSPatch Manager\n")
            info.append("2. Tap 'Manage' → 'Patch with embedded modules'\n")
            info.append("3. Select target app\n")
            info.append("4. Select this module APK\n")
            info.append("5. Tap 'Start Patch'\n")
            info.append("6. Install the patched APK\n")
            info.append("7. Use patched app instead of original\n")
        } else {
            info.append("Install LSPatch Manager from:\n")
            info.append("https://github.com/LSPosed/LSPatch/releases\n\n")
            info.append("LSPatch allows Xposed modules to work on unrooted devices.")
        }
        
        if (modulePath != null) {
            info.append("\n\nModule APK path:\n$modulePath")
        }
        
        tvLSPatchInfo.text = info.toString()
    }
    
    private fun showSetupInstructions() {
        val isRooted = UniversalSecureBypass.isRootedMode
        
        val instructions = if (isRooted) {
            """
            📱 ROOTED MODE SETUP (LSPosed/Xposed)
            
            1. Ensure you have:
               • Root access (Magisk recommended)
               • LSPosed/Xposed framework installed
            
            2. Install this module APK
            
            3. Open LSPosed Manager
            
            4. Enable this module
            
            5. Select target apps to hook
            
            6. Reboot your device
            
            7. Module will work system-wide
            
            ✅ Features in rooted mode:
            • System-wide FLAG_SECURE bypass
            • Global DRM bypass
            • All apps supported
            • Better performance
            """
        } else {
            """
            📱 UNROOTED MODE SETUP (LSPatch)
            
            1. Install LSPatch Manager from GitHub
            
            2. Open LSPatch Manager
            
            3. Tap "Manage" → "Patch with embedded modules"
            
            4. Select target app (e.g., Netflix)
            
            5. Select this module APK
            
            6. Tap "Start Patch"
            
            7. Install the patched APK
            
            8. Use patched app instead of original
            
            ⚠️ Limitations in unrooted mode:
            • Must patch each app individually
            • Cannot hook system apps
            • Some DRM may still block
            • Performance overhead
            """
        }.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Setup Instructions")
            .setMessage(instructions)
            .setPositiveButton("OK", null)
            .setNegativeButton("Open Guide") { _, _ ->
                if (isRooted) {
                    openWebPage("https://github.com/LSPosed/LSPosed")
                } else {
                    lspatchHelper.openLSPatchGuide()
                }
            }
            .show()
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = lspatchHelper.checkPermissions()
        
        if (missingPermissions.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Check")
                .setMessage("✅ All required permissions are granted!")
                .setPositiveButton("OK", null)
                .show()
        } else {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Missing Permissions")
                .setMessage("The following permissions are required for LSPatch:\n\n${missingPermissions.joinToString("\n")}")
            
            // Add buttons for each missing permission
            missingPermissions.forEach { permission ->
                val intent = lspatchHelper.getPermissionIntent(permission)
                if (intent != null) {
                    dialog.setPositiveButton("Grant $permission") { _, _ ->
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            showToast("Cannot open settings: ${e.message}")
                        }
                    }
                }
            }
            
            dialog.setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun shareModuleApk() {
        val modulePath = lspatchHelper.getModuleApkPath()
        if (modulePath == null) {
            showToast("Cannot find module APK")
            return
        }
        
        val file = File(modulePath)
        if (!file.exists()) {
            showToast("Module APK not found")
            return
        }
        
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Universal Secure Bypass Module")
            putExtra(Intent.EXTRA_TEXT, "Universal Secure Bypass Module v2.0.0\nWorks on both rooted and unrooted devices!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Module APK"))
        } catch (e: Exception) {
            showToast("Cannot share APK: ${e.message}")
        }
    }
    
    private fun loadSettings() {
        switchFlagSecure.isChecked = prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
        switchDrm.isChecked = prefs.getBoolean(KEY_BYPASS_DRM, true)
        switchBlackScreen.isChecked = prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
        switchNotifications.isChecked = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, false)
        switchSystemApps.isChecked = prefs.getBoolean(KEY_SYSTEM_APPS, UniversalSecureBypass.isRootedMode)
        
        // Load target apps selection
        val savedTargetApps = prefs.getString(KEY_TARGET_APPS, "All Streaming Apps")
        val adapter = spTargetApps.adapter as ArrayAdapter<String>
        val position = adapter.getPosition(savedTargetApps)
        if (position >= 0) {
            spTargetApps.setSelection(position)
        }
    }
    
    private fun saveSetting(key: String, value: Any) {
        when (value) {
            is Boolean -> prefs.edit().putBoolean(key, value).apply()
            is String -> prefs.edit().putString(key, value).apply()
            is Int -> prefs.edit().putInt(key, value).apply()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun getColorCompat(colorResId: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(colorResId, theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(colorResId)
        }
    }
    
    private fun openWebPage(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            showToast("Cannot open browser: ${e.message}")
        }
    }
    
    fun onRootModeClicked(view: android.view.View) {
        // Force root mode (for testing/development)
        UniversalSecureBypass.isRootedMode = true
        UniversalSecureBypass.isLSPatchMode = false
        updateUIForMode()
        detectEnvironment()
        showToast("Forced ROOT mode (for testing)")
    }
    
    fun onLSPatchModeClicked(view: android.view.View) {
        // Force LSPatch mode (for testing/development)
        UniversalSecureBypass.isRootedMode = false
        UniversalSecureBypass.isLSPatchMode = true
        updateUIForMode()
        detectEnvironment()
        showToast("Forced LSPatch mode (for testing)")
    }
    
    fun onStartServiceClicked(view: android.view.View) {
        val intent = Intent(this, SecureBypassService::class.java)
        intent.action = SecureBypassService.ACTION_START
        startService(intent)
        showToast("Service started")
    }
    
    fun onStopServiceClicked(view: android.view.View) {
        val intent = Intent(this, SecureBypassService::class.java)
        intent.action = SecureBypassService.ACTION_STOP
        startService(intent)
        showToast("Service stopped")
    }
    
    fun onOpenLSPatchManagerClicked(view: android.view.View) {
        lspatchHelper.openLSPatchManager()
    }
}
