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
import java.lang.Thread.getDefaultUncaughtExceptionHandler
import java.lang.Thread.setDefaultUncaughtExceptionHandler
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
        
        // Crash logging constants
        const val CRASH_LOG_DIR = "crash_logs"
        const val CRASH_LOG_PREFIX = "crash_"
        const val MAX_CRASH_LOGS = 10
        const val TAG = "SecureBypass"
        
        // App info (we'll get these from package manager)
        const val IS_DEBUG = true  // Change based on build type
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var lspatchHelper: LSPatchHelper
    private lateinit var crashLogger: CrashLogger

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
    private lateinit var btnTest: Button
    private lateinit var btnLogs: Button
    private lateinit var btnSetup: Button
    private lateinit var btnShareModule: Button
    private lateinit var btnLSPatchGuide: Button
    private lateinit var btnCheckPermissions: Button

    private var spinnerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize crash logging before anything else
        crashLogger = CrashLogger.getInstance(applicationContext)
        crashLogger.installExceptionHandler()
        
        // Log app start
        crashLogger.logEvent("AppStarted", "MainActivity.onCreate()")
        
        try {
            setContentView(R.layout.activity_main)

            UniversalSecureBypass.init(applicationContext)
            lspatchHelper = LSPatchHelper(this)
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

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

    // ---------------- CRASH LOGGING SYSTEM ----------------

    private fun showCrashRecoveryDialog(exception: Exception) {
        AlertDialog.Builder(this)
            .setTitle("Application Error")
            .setMessage("An error occurred. Would you like to send a crash report?")
            .setPositiveButton("Send Report") { _, _ ->
                crashLogger.exportCrashLogs()
                showCrashReportOptions()
            }
            .setNegativeButton("Restart App") { _, _ ->
                restartApp()
            }
            .setNeutralButton("Continue Anyway") { _, _ ->
                // Just continue with potentially broken state
                Toast.makeText(this, "App may be unstable", Toast.LENGTH_LONG).show()
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
            .setNeutralButton("Delete All Reports") { _, _ ->
                deleteAllCrashLogs()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showCrashLogContent(crashFile: File?) {
        if (crashFile == null || !crashFile.exists()) {
            Toast.makeText(this, "No crash log found", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "No crash log to share", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Cannot share crash report: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllCrashLogs() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Crash Reports")
            .setMessage("Are you sure you want to delete all crash reports?")
            .setPositiveButton("Delete") { _, _ ->
                crashLogger.cleanupOldLogs(0) // Delete all logs
                Toast.makeText(this, "All crash reports deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    // ---------------- INIT ----------------

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
            btnTest = findViewById(R.id.btnTest)
            btnLogs = findViewById(R.id.btnLogs)
            btnSetup = findViewById(R.id.btnSetup)
            btnShareModule = findViewById(R.id.btnShareModule)
            btnLSPatchGuide = findViewById(R.id.btnLSPatchGuide)
            btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
            
            // Find the ScrollView and add debug button if needed
            if (IS_DEBUG) {
                val scrollView = findViewById<ScrollView?>(android.R.id.content)
                scrollView?.let {
                    // Get the LinearLayout inside ScrollView
                    val linearLayout = it.getChildAt(0) as? LinearLayout
                    linearLayout?.let { layout ->
                        val crashLogButton = Button(this).apply {
                            text = "View Crash Logs"
                            setOnClickListener {
                                showCrashReportOptions()
                            }
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 16, 0, 16)
                            }
                        }
                        // Add button before the footer
                        val childCount = layout.childCount
                        if (childCount > 0) {
                            layout.addView(crashLogButton, childCount - 1)
                        } else {
                            layout.addView(crashLogButton)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            crashLogger.logException(e, "bindViews")
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
            throw e
        }
    }

    // ---------------- STATE ----------------

    private fun refreshState() {
        try {
            val rooted = UniversalSecureBypass.isRootedMode

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
        }
    }

    // ---------------- LISTENERS ----------------

    private fun setupListeners() {
        try {
            switchFlagSecure.save(KEY_BYPASS_FLAG_SECURE)
            switchDrm.save(KEY_BYPASS_DRM)
            switchBlackScreen.save(KEY_BYPASS_BLACK_SCREEN)
            switchNotifications.save(KEY_SHOW_NOTIFICATIONS)

            switchSystemApps.setOnCheckedChangeListener { _, checked ->
                try {
                    if (UniversalSecureBypass.isRootedMode) {
                        prefs.edit().putBoolean(KEY_SYSTEM_APPS, checked).apply()
                        crashLogger.logEvent("SystemAppsChanged", "enabled=$checked")
                    }
                } catch (e: Exception) {
                    crashLogger.logException(e, "switchSystemApps.onCheckedChange")
                }
            }
            
            // Setup button listeners
            btnTest.setOnClickListener {
                try {
                    crashLogger.logEvent("TestButtonClicked", "Test Module")
                    // TODO: Implement test functionality
                    Toast.makeText(this, "Test functionality not implemented yet", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnTest.onClick")
                }
            }
            
            btnLogs.setOnClickListener {
                try {
                    crashLogger.logEvent("LogsButtonClicked", "View Logs")
                    showCrashReportOptions()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnLogs.onClick")
                }
            }
            
            btnSetup.setOnClickListener {
                try {
                    crashLogger.logEvent("SetupButtonClicked", "Setup Guide")
                    showSetupGuide()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnSetup.onClick")
                }
            }
            
            btnShareModule.setOnClickListener {
                try {
                    crashLogger.logEvent("ShareModuleButtonClicked", "Share Module")
                    shareModuleApk()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnShareModule.onClick")
                }
            }
            
            btnLSPatchGuide.setOnClickListener {
                try {
                    crashLogger.logEvent("LSPatchGuideButtonClicked", "LSPatch Guide")
                    showLSPatchGuide()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnLSPatchGuide.onClick")
                }
            }
            
            btnCheckPermissions.setOnClickListener {
                try {
                    crashLogger.logEvent("CheckPermissionsButtonClicked", "Check Permissions")
                    checkPermissions()
                } catch (e: Exception) {
                    crashLogger.logException(e, "btnCheckPermissions.onClick")
                }
            }
        } catch (e: Exception) {
            crashLogger.logException(e, "setupListeners")
        }
    }

    private fun SwitchCompat.save(key: String) {
        setOnCheckedChangeListener { _, checked ->
            try {
                prefs.edit().putBoolean(key, checked).apply()
                crashLogger.logEvent("SettingChanged", "$key=$checked")
            } catch (e: Exception) {
                crashLogger.logException(e, "SwitchCompat.save.$key")
            }
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

            crashLogger.logEvent("ServiceStarted", "SecureBypassService")
            toast("Service started")
        } catch (e: Exception) {
            crashLogger.logException(e, "onStartServiceClicked")
            toast("Failed to start service: ${e.message}")
        }
    }

    fun onStopServiceClicked(v: android.view.View) {
        try {
            startService(
                Intent(this, SecureBypassService::class.java)
                    .setAction(SecureBypassService.ACTION_STOP)
            )
            crashLogger.logEvent("ServiceStopped", "SecureBypassService")
            toast("Service stopped")
        } catch (e: Exception) {
            crashLogger.logException(e, "onStopServiceClicked")
            toast("Failed to stop service: ${e.message}")
        }
    }
    
    // ---------------- BUTTON HANDLERS ----------------
    
    fun onRootModeClicked(v: View) {
        try {
            crashLogger.logEvent("RootModeButtonClicked", "Root Mode")
            // TODO: Implement root mode switch
            Toast.makeText(this, "Switching to Root Mode", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            crashLogger.logException(e, "onRootModeClicked")
        }
    }
    
    fun onLSPatchModeClicked(v: View) {
        try {
            crashLogger.logEvent("LSPatchModeButtonClicked", "LSPatch Mode")
            // TODO: Implement LSPatch mode switch
            Toast.makeText(this, "Switching to LSPatch Mode", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            crashLogger.logException(e, "onLSPatchModeClicked")
        }
    }
    
    fun onOpenLSPatchManagerClicked(v: View) {
        try {
            crashLogger.logEvent("OpenLSPatchManagerClicked", "Open LSPatch Manager")
            openLSPatchManager()
        } catch (e: Exception) {
            crashLogger.logException(e, "onOpenLSPatchManagerClicked")
        }
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
                UniversalSecureBypass.isRootedMode &&
                        prefs.getBoolean(KEY_SYSTEM_APPS, true)
                        
            crashLogger.logEvent("SettingsLoaded", "fromSharedPreferences")
        } catch (e: Exception) {
            crashLogger.logException(e, "loadSettings")
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

    private fun toast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            crashLogger.logEvent("ToastShown", msg)
        } catch (e: Exception) {
            crashLogger.logException(e, "toast")
        }
    }
    
    // ---------------- NEW HELPER METHODS ----------------
    
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
            // Get the APK file path
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
                    putExtra(Intent.EXTRA_TEXT, "Check out this Universal Secure Bypass module!")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share Module APK"))
            } else {
                Toast.makeText(this, "APK file not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            crashLogger.logException(e, "shareModuleApk")
            Toast.makeText(this, "Failed to share APK: ${e.message}", Toast.LENGTH_SHORT).show()
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
                
                Note: Some apps may detect and block LSPatch.
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
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Storage")
            }
            if (checkSelfPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPerms.add("Overlay")
            }
        }
        
        if (missingPerms.isEmpty()) {
            Toast.makeText(this, "All permissions granted ✅", Toast.LENGTH_SHORT).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Missing Permissions")
                .setMessage("The following permissions are missing:\n\n• ${missingPerms.joinToString("\n• ")}\n\nGrant them in Settings.")
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
                Toast.makeText(this, "LSPatch Manager not installed", Toast.LENGTH_SHORT).show()
                openLSPatchDownload()
            }
        } catch (e: Exception) {
            crashLogger.logException(e, "openLSPatchManager")
            Toast.makeText(this, "Cannot open LSPatch Manager", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openLSPatchDownload() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LSPosed/LSPatch/releases"))
            startActivity(intent)
        } catch (e: Exception) {
            crashLogger.logException(e, "openLSPatchDownload")
            Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show()
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
}

// ---------------- CRASH LOGGER CLASS ----------------

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
        val defaultHandler = getDefaultUncaughtExceptionHandler()
        
        setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log the crash
                logException(throwable as Exception, "UncaughtException", thread.name)
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Error in exception handler", e)
            } finally {
                // Call original handler
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
                    
                    // Get app version info from package manager
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        it.write("App Version: ${packageInfo.versionName} (${packageInfo.versionCode})\n")
                    } catch (e: Exception) {
                        it.write("App Version: Unknown\n")
                    }
                    
                    it.write("Android API: ${Build.VERSION.SDK_INT}\n")
                    it.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    it.write("Rooted: ${UniversalSecureBypass.isRootedMode}\n")
                    it.write("-".repeat(80) + "\n")
                    it.write("Exception: ${exception.javaClass.name}\n")
                    it.write("Message: ${exception.message}\n")
                    it.write("Stack Trace:\n")
                    exception.printStackTrace(PrintWriter(it))
                    it.write("\n" + "=".repeat(80) + "\n\n")
                }
                
                Log.e(MainActivity.TAG, "Crash logged: $source", exception)
                
                // Clean up old logs
                cleanupOldLogs(MainActivity.MAX_CRASH_LOGS)
                
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
        val logsDir = File(context.filesDir, MainActivity.CRASH_LOG_DIR)
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        
        val timestamp = dateFormat.format(Date())
        return File(logsDir, "${MainActivity.CRASH_LOG_PREFIX}${timestamp}.txt")
    }
    
    private fun getEventLogFile(): File {
        val logsDir = File(context.filesDir, MainActivity.CRASH_LOG_DIR)
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, "events.log")
    }
    
    fun getLatestCrashLog(): File? {
        val logsDir = File(context.filesDir, MainActivity.CRASH_LOG_DIR)
        if (!logsDir.exists()) return null
        
        return logsDir.listFiles { file -> 
            file.name.startsWith(MainActivity.CRASH_LOG_PREFIX) 
        }?.maxByOrNull { it.lastModified() }
    }
    
    fun cleanupOldLogs(maxLogs: Int) {
        executor.execute {
            try {
                val logsDir = File(context.filesDir, MainActivity.CRASH_LOG_DIR)
                if (!logsDir.exists()) return@execute
                
                val crashLogs = logsDir.listFiles { file -> 
                    file.name.startsWith(MainActivity.CRASH_LOG_PREFIX) 
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
            val logsDir = File(context.filesDir, MainActivity.CRASH_LOG_DIR)
            if (!logsDir.exists()) return null
            
            // Return the latest crash log
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
