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
        const val IS_DEBUG = true // Change to false for release builds
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var crashLogger: CrashLogger

    // UI Elements
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
        
        // Initialize crash logging first
        crashLogger = CrashLogger.getInstance(applicationContext)
        crashLogger.installExceptionHandler()
        crashLogger.logEvent("AppStarted", "MainActivity.onCreate()")
        
        try {
            setContentView(R.layout.activity_main)
            
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            // SAFE initialization - wrapped in try-catch
            try {
                // This might fail if UniversalSecureBypass isn't implemented
                UniversalSecureBypass.init(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "UniversalSecureBypass init failed (might be ok): ${e.message}")
            }
            
            bindViews()
            setupSpinner()
            loadSettings()
            setupListeners()
            refreshState()
            
        } catch (e: Exception) {
            crashLogger.logException(e, "MainActivity.onCreate")
            showCrashRecoveryDialog(e)
        }
    }

    override fun onResume() {
        super.onResume()
        crashLogger.logEvent("AppResumed", "MainActivity.onResume()")
        refreshState()
    }

    override fun onPause() {
        super.onPause()
        crashLogger.logEvent("AppPaused", "MainActivity.onPause()")
    }

    override fun onDestroy() {
        crashLogger.logEvent("AppDestroyed", "MainActivity.onDestroy()")
        super.onDestroy()
    }

    // ============= UI SETUP =============
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
            
            // Setup other button listeners
            findViewById<Button>(R.id.btnTest)?.setOnClickListener {
                showTestDialog()
            }
            
            findViewById<Button>(R.id.btnLogs)?.setOnClickListener {
                showCrashReportOptions()
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
            crashLogger.logException(e, "bindViews")
            Log.e(TAG, "Error binding views: ${e.message}")
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
                        try {
                            if (!spinnerInitialized) {
                                spinnerInitialized = true
                                return
                            }

                            val value = parent?.getItemAtPosition(position) as String
                            prefs.edit().putString(KEY_TARGET_APPS, value).apply()
                            crashLogger.logEvent("TargetAppChanged", value)
                            toast("Target apps: $value")
                        } catch (e: Exception) {
                            crashLogger.logException(e, "onItemSelected")
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        } catch (e: Exception) {
            crashLogger.logException(e, "setupSpinner")
        }
    }

    // ============= APP STATE =============
    private fun refreshState() {
        try {
            // SAFE check for rooted mode
            val rooted = try {
                UniversalSecureBypass.isRootedMode
            } catch (e: Exception) {
                false // Default to unrooted if check fails
            }

            tvMode.text = if (rooted)
                "Mode: ROOT (LSPosed/Xposed)"
            else
                "Mode: UNROOTED (LSPatch)"

            updateModuleStatus()
            updateUIForMode(rooted)
            updateLSPatchInfo()
            
            crashLogger.logEvent("AppStateRefreshed", "rooted=$rooted")
        } catch (e: Exception) {
            crashLogger.logException(e, "refreshState")
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
            crashLogger.logException(e, "updateModuleStatus")
            tvStatus.text = "⚠️ Status Unknown"
        }
    }

    private fun isModuleActive(): Boolean {
        return try {
            Class.forName("com.flag.secure.UniversalSecureBypass")
            true
        } catch (e: Throwable) {
            crashLogger.logException(e as Exception, "isModuleActive")
            false
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
            crashLogger.logException(e, "updateUIForMode")
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
            crashLogger.logException(e, "updateLSPatchInfo")
            tvLSPatchInfo.text = "Error loading LSPatch info"
        }
    }

    // ============= SETTINGS & LISTENERS =============
    private fun setupListeners() {
        try {
            // Setup switch listeners to save preferences
            switchFlagSecure.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_FLAG_SECURE, checked).apply()
                crashLogger.logEvent("SettingChanged", "flag_secure=$checked")
            }
            
            switchDrm.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_DRM, checked).apply()
                crashLogger.logEvent("SettingChanged", "drm=$checked")
            }
            
            switchBlackScreen.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BYPASS_BLACK_SCREEN, checked).apply()
                crashLogger.logEvent("SettingChanged", "black_screen=$checked")
            }
            
            switchNotifications.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_SHOW_NOTIFICATIONS, checked).apply()
                crashLogger.logEvent("SettingChanged", "notifications=$checked")
            }

            switchSystemApps.setOnCheckedChangeListener { _, checked ->
                try {
                    val rooted = try {
                        UniversalSecureBypass.isRootedMode
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (rooted) {
                        prefs.edit().putBoolean(KEY_SYSTEM_APPS, checked).apply()
                        crashLogger.logEvent("SettingChanged", "system_apps=$checked")
                    }
                } catch (e: Exception) {
                    crashLogger.logException(e, "switchSystemApps")
                }
            }
        } catch (e: Exception) {
            crashLogger.logException(e, "setupListeners")
        }
    }

    private fun loadSettings() {
        try {
            switchFlagSecure.isChecked = prefs.getBoolean(KEY_BYPASS_FLAG_SECURE, true)
            switchDrm.isChecked = prefs.getBoolean(KEY_BYPASS_DRM, true)
            switchBlackScreen.isChecked = prefs.getBoolean(KEY_BYPASS_BLACK_SCREEN, true)
            switchNotifications.isChecked = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, false)

            val rooted = try {
                UniversalSecureBypass.isRootedMode
            } catch (e: Exception) {
                false
            }
            
            switchSystemApps.isChecked = rooted && prefs.getBoolean(KEY_SYSTEM_APPS, true)
            
            crashLogger.logEvent("SettingsLoaded", "success")
        } catch (e: Exception) {
            crashLogger.logException(e, "loadSettings")
        }
    }

    // ============= SERVICE CONTROLS =============
    fun onStartServiceClicked(v: android.view.View) {
        try {
            crashLogger.logEvent("Service", "Start requested")
            // Note: You need to implement SecureBypassService for this to work
            toast("Service start requested (service not implemented yet)")
        } catch (e: Exception) {
            crashLogger.logException(e, "onStartServiceClicked")
            toast("Failed to start service: ${e.message}")
        }
    }

    fun onStopServiceClicked(v: android.view.View) {
        try {
            crashLogger.logEvent("Service", "Stop requested")
            toast("Service stop requested (service not implemented yet)")
        } catch (e: Exception) {
            crashLogger.logException(e, "onStopServiceClicked")
            toast("Failed to stop service: ${e.message}")
        }
    }

    // ============= BUTTON HANDLERS =============
    fun onRootModeClicked(v: View) {
        try {
            crashLogger.logEvent("Button", "Root Mode clicked")
            toast("Root Mode clicked")
        } catch (e: Exception) {
            crashLogger.logException(e, "onRootModeClicked")
        }
    }
    
    fun onLSPatchModeClicked(v: View) {
        try {
            crashLogger.logEvent("Button", "LSPatch Mode clicked")
            toast("LSPatch Mode clicked")
        } catch (e: Exception) {
            crashLogger.logException(e, "onLSPatchModeClicked")
        }
    }
    
    fun onOpenLSPatchManagerClicked(v: View) {
        try {
            crashLogger.logEvent("Button", "Open LSPatch Manager")
            openLSPatchManager()
        } catch (e: Exception) {
            crashLogger.logException(e, "onOpenLSPatchManagerClicked")
        }
    }

    // ============= HELPER METHODS =============
    private fun toast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            crashLogger.logEvent("Toast", msg)
        } catch (e: Exception) {
            crashLogger.logException(e, "toast")
        }
    }
    
    private fun showTestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Test Module")
            .setMessage("Test functionality will be implemented here.")
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
            crashLogger.logException(e, "shareModuleApk")
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
                
                Patching Apps:
                1. Select "Patch APK" or "Patch installed app"
                2. Choose target app
                3. Select this module APK
                4. Install patched APK
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .setNegativeButton("Download LSPatch") { _, _ ->
                openLSPatchDownload()
            }
            .show()
    }
    
    private fun checkPermissions() {
        val missingPerms = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Storage")
            }
            if (checkSelfPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Overlay")
            }
        }
        
        if (missingPerms.isEmpty()) {
            toast("All permissions granted ✅")
        } else {
            AlertDialog.Builder(this)
                .setTitle("Missing Permissions")
                .setMessage("The following permissions are missing:\n• ${missingPerms.joinToString("\n• ")}")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Cancel", null)
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
            crashLogger.logException(e, "openLSPatchManager")
            toast("Cannot open LSPatch Manager")
        }
    }
    
    private fun openLSPatchDownload() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://github.com/LSPosed/LSPatch/releases"))
            startActivity(intent)
        } catch (e: Exception) {
            crashLogger.logException(e, "openLSPatchDownload")
            toast("Cannot open browser")
        }
    }
    
    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            crashLogger.logException(e, "openAppSettings")
        }
    }

    // ============= CRASH HANDLING =============
    private fun showCrashRecoveryDialog(exception: Exception) {
        AlertDialog.Builder(this)
            .setTitle("Application Error")
            .setMessage("An error occurred during startup. Would you like to send a crash report?")
            .setPositiveButton("Send Report") { _, _ ->
                crashLogger.exportCrashLogs()
                showCrashReportOptions()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                toast("App may be unstable")
            }
            .setCancelable(false)
            .show()
    }

    private fun showCrashReportOptions() {
        val latestCrash = crashLogger.getLatestCrashLog()
        
        AlertDialog.Builder(this)
            .setTitle("Crash Report")
            .setMessage("Crash report generated. What would you like to do?")
            .setPositiveButton("View Report") { _, _ ->
                showCrashLogContent(latestCrash)
            }
            .setNegativeButton("Share Report") { _, _ ->
                shareCrashLog(latestCrash)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showCrashLogContent(crashFile: File?) {
        if (crashFile == null || !crashFile.exists()) {
            toast("No crash log found")
            return
        }
        
        val content = crashFile.readText()
        AlertDialog.Builder(this)
            .setTitle("Crash Report")
            .setMessage(content.take(2000) + if (content.length > 2000) "\n\n... (truncated)" else "")
            .setPositiveButton("OK", null)
            .setNegativeButton("Share") { _, _ -> shareCrashLog(crashFile) }
            .show()
    }

    private fun shareCrashLog(crashFile: File?) {
        if (crashFile == null || !crashFile.exists()) {
            toast("No crash log to share")
            return
        }
        
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                crashFile
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SecureBypass Crash Report")
                putExtra(Intent.EXTRA_TEXT, "Crash report attached")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Crash Report"))
        } catch (e: Exception) {
            crashLogger.logException(e, "shareCrashLog")
            toast("Cannot share crash report")
        }
    }

    private fun restartApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            crashLogger.logException(e, "restartApp")
        }
    }
}

// ============= CRASH LOGGER CLASS =============
class CrashLogger private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: CrashLogger? = null
        
        fun getInstance(context: Context): CrashLogger =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CrashLogger(context.applicationContext).also { INSTANCE = it }
            }
    }
    
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val logFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun installExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logException(throwable as Exception, "UncaughtException", thread.name)
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Error in exception handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    fun logException(exception: Exception, source: String, threadName: String = Thread.currentThread().name) {
        executor.execute {
            try {
                val crashFile = createCrashLogFile()
                val writer = FileWriter(crashFile, true)
                
                writer.use {
                    it.write("=".repeat(80) + "\n")
                    it.write("CRASH REPORT\n")
                    it.write("Time: ${logFormat.format(Date())}\n")
                    it.write("Source: $source\n")
                    it.write("Thread: $threadName\n")
                    
                    // Get app version info
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        it.write("App Version: ${packageInfo.versionName} (${packageInfo.versionCode})\n")
                    } catch (e: Exception) {
                        it.write("App Version: Unknown\n")
                    }
                    
                    it.write("Android API: ${Build.VERSION.SDK_INT}\n")
                    it.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    it.write("-".repeat(80) + "\n")
                    it.write("Exception: ${exception.javaClass.name}\n")
                    it.write("Message: ${exception.message}\n")
                    it.write("Stack Trace:\n")
                    exception.printStackTrace(PrintWriter(it))
                    it.write("\n" + "=".repeat(80) + "\n\n")
                }
                
                Log.e(MainActivity.TAG, "Crash logged: $source", exception)
                
                cleanupOldLogs(10) // Keep last 10 logs
                
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Failed to write crash log", e)
            }
        }
    }
    
    fun logEvent(event: String, details: String) {
        executor.execute {
            try {
                val logFile = getEventLogFile()
                val writer = FileWriter(logFile, true)
                
                writer.use {
                    it.write("[${logFormat.format(Date())}] $event: $details\n")
                }
                
                Log.d(MainActivity.TAG, "$event: $details")
                
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Failed to write event log", e)
            }
        }
    }
    
    private fun createCrashLogFile(): File {
        val logsDir = File(context.filesDir, "crash_logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        
        val timestamp = dateFormat.format(Date())
        return File(logsDir, "crash_${timestamp}.txt")
    }
    
    private fun getEventLogFile(): File {
        val logsDir = File(context.filesDir, "crash_logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, "events.log")
    }
    
    fun getLatestCrashLog(): File? {
        val logsDir = File(context.filesDir, "crash_logs")
        if (!logsDir.exists()) return null
        
        return logsDir.listFiles { file -> 
            file.name.startsWith("crash_") 
        }?.maxByOrNull { it.lastModified() }
    }
    
    fun cleanupOldLogs(maxLogs: Int) {
        executor.execute {
            try {
                val logsDir = File(context.filesDir, "crash_logs")
                if (!logsDir.exists()) return@execute
                
                val crashLogs = logsDir.listFiles { file -> 
                    file.name.startsWith("crash_") 
                }?.sortedByDescending { it.lastModified() }
                
                crashLogs?.let {
                    if (it.size > maxLogs) {
                        it.subList(maxLogs, it.size).forEach { file ->
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Failed to cleanup old logs", e)
            }
        }
    }
    
    fun exportCrashLogs(): Uri? {
        return try {
            val logsDir = File(context.filesDir, "crash_logs")
            if (!logsDir.exists()) return null
            
            val latestCrash = getLatestCrashLog()
            if (latestCrash != null && latestCrash.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    latestCrash
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Failed to export crash logs", e)
            null
        }
    }
}

