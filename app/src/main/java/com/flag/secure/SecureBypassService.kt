package com.flag.secure

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast

class SecureBypassService : Service() {
    
    companion object {
        const val ACTION_START = "com.flag.secure.START_SERVICE"
        const val ACTION_STOP = "com.flag.secure.STOP_SERVICE"
        const val ACTION_CHECK = "com.flag.secure.CHECK_SERVICE"
        
        private var isRunning = false
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startService()
            }
            ACTION_STOP -> {
                stopService()
            }
            ACTION_CHECK -> {
                checkStatus()
            }
        }
        return START_STICKY
    }
    
    private fun startService() {
        if (!isRunning) {
            isRunning = true
            UniversalSecureBypass.log("Service started")
            
            // Start foreground service if needed
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Create notification channel and start foreground service
                // This is simplified - in real implementation you'd create a notification
            }
            
            Toast.makeText(this, "Secure Bypass Service Started", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopService() {
        if (isRunning) {
            isRunning = false
            UniversalSecureBypass.log("Service stopped")
            stopSelf()
            Toast.makeText(this, "Secure Bypass Service Stopped", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkStatus() {
        val status = if (isRunning) "running" else "stopped"
        UniversalSecureBypass.log("Service status: $status")
        
        // Broadcast status
        val broadcastIntent = Intent(ModuleReceiver.ACTION_MODULE_STATUS)
        broadcastIntent.putExtra(ModuleReceiver.EXTRA_STATUS, status)
        broadcastIntent.putExtra(ModuleReceiver.EXTRA_PACKAGE, packageName)
        sendBroadcast(broadcastIntent)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        isRunning = false
        UniversalSecureBypass.log("Service destroyed")
        super.onDestroy()
    }
}
