package com.flag.secure
import android.content.Intent // <-- Add this import
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LogViewerActivity : AppCompatActivity() {
    
    private lateinit var tvLogs: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button
    private lateinit var btnShare: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        
        tvLogs = findViewById(R.id.tvLogs)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClear = findViewById(R.id.btnClear)
        btnShare = findViewById(R.id.btnShare)
        
        tvLogs.movementMethod = ScrollingMovementMethod()
        
        btnRefresh.setOnClickListener {
            loadLogs()
        }
        
        btnClear.setOnClickListener {
            clearLogs()
        }
        
        btnShare.setOnClickListener {
            shareLogs()
        }
        
        loadLogs()
    }
    
    private fun loadLogs() {
        try {
            val logFile = File(filesDir, "logs/module_log.txt")
            if (logFile.exists()) {
                val logContent = logFile.readText()
                tvLogs.text = if (logContent.isNotEmpty()) {
                    logContent
                } else {
                    "No logs available. Logs will appear here when the module is active."
                }
                
                // Scroll to bottom
                val scrollAmount = tvLogs.layout.getLineTop(tvLogs.lineCount) - tvLogs.height
                if (scrollAmount > 0) {
                    tvLogs.scrollTo(0, scrollAmount)
                } else {
                    tvLogs.scrollTo(0, 0)
                }
            } else {
                tvLogs.text = "Log file not found. Module may not be active."
            }
        } catch (e: Exception) {
            tvLogs.text = "Error loading logs: ${e.message}"
        }
    }
    
    private fun clearLogs() {
        try {
            val logFile = File(filesDir, "logs/module_log.txt")
            if (logFile.exists()) {
                logFile.delete()
                tvLogs.text = "Logs cleared."
                UniversalSecureBypass.log("Logs cleared by user")
            }
        } catch (e: Exception) {
            tvLogs.text = "Error clearing logs: ${e.message}"
        }
    }
    
    private fun shareLogs() {
        try {
            val logFile = File(filesDir, "logs/module_log.txt")
            if (logFile.exists() && logFile.length() > 0) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(logFile))
                    putExtra(Intent.EXTRA_SUBJECT, "Universal Secure Bypass Logs")
                    putExtra(Intent.EXTRA_TEXT, "Logs from Universal Secure Bypass module")
                }
                startActivity(Intent.createChooser(shareIntent, "Share Logs"))
            } else {
                tvLogs.text = "No logs to share."
            }
        } catch (e: Exception) {
            tvLogs.text = "Error sharing logs: ${e.message}"
        }
    }
}
