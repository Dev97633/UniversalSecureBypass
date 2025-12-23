package com.flag.secure

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {
    
    companion object {
        const val PREFS_NAME = "SecureBypassPrefs"
        const val KEY_BYPASS_FLAG_SECURE = "bypass_flag_secure"
        const val KEY_BYPASS_DRM = "bypass_drm"
        const val KEY_BYPASS_BLACK_SCREEN = "bypass_black_screen"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val KEY_SYSTEM_APPS = "system_apps"
    }
    
    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvMode: TextView
    private lateinit var switchFlagSecure: SwitchCompat
    private lateinit var switchDrm: SwitchCompat
    private lateinit var switchBlackScreen: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchSystemApps: SwitchCompat
    private lateinit var btnTest: Button
    private lateinit var btnLogs: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        UniversalSecureBypass.init(applicationContext)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupViews()
        loadSettings()
        detectEnvironment()
    }
    
    private fun setupViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvMode = findViewById(R.id.tvMode)
        switchFlagSecure = findViewById(R.id.switchFlagSecure)
        switchDrm = findViewById(R.id.switchDrm)
        switchBlackScreen = findViewById(R.id.switchBlackScreen)
        switchNotifications = findViewById(R.id.switchNotifications)
        switchSystemApps = findViewById(R.id.switchSystemApps)
        btnTest = findViewById(R.id.btnTest)
        btnLogs = findViewById(R.id.btnLogs)
        
        switchFlagSecure.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_FLAG_SECURE, isChecked)
        }
        
        switchDrm.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_DRM, isChecked)
        }
        
        switchBlackScreen.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_BYPASS_BLACK_SCREEN, isChecked)
        }
        
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_SHOW_NOTIFICATIONS, isChecked)
        }
        
        switchSystemApps.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(KEY_SYSTEM_APPS, isChecked)
        }
        
        btnTest.setOnClickListener {
            val intent = Intent(this, TestActivity::class.java)
            startActivity(intent)
        }
        
        btnLogs.setOnClickListener {
            showLogs()
        }
    }
    
    private fun detectEnvironment() {
        val mode = if (UniversalSecureBypass.isRootedMode) {
            "ROOTED (LSPosed/Xposed)"
        } else {
            "UNROOTED (LSPatch)"
        }
        
        tvMode.text = "Mode: $mode"
        
        val isActive = try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        if (isActive) {
            tvStatus.text = "✅ Module is ACTIVE"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "❌ Module NOT ACTIVE"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun loadSettings() {
        switchFlagSecure.isChecked = prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
        switchDrm.isChecked = prefs.getBoolean(KEY_BYPASS_DRM, true)
        switchBlackScreen.isChecked = prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
        switchNotifications.isChecked = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, false)
        switchSystemApps.isChecked = prefs.getBoolean(KEY_SYSTEM_APPS, UniversalSecureBypass.isRootedMode)
        
        switchSystemApps.isEnabled = UniversalSecureBypass.isRootedMode
    }
    
    private fun saveSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        
        when (key) {
            KEY_BYPASS_FLAG_SECURE -> 
                Toast.makeText(this, "FLAG_SECURE bypass ${if (value) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            KEY_BYPASS_DRM -> 
                Toast.makeText(this, "DRM bypass ${if (value) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showLogs() {
        Toast.makeText(this, "Log viewer coming soon", Toast.LENGTH_SHORT).show()
    }
}
