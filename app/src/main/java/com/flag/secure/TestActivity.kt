package com.flag.secure

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TestActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        setContentView(R.layout.activity_test)
        
        val tvStatus = findViewById<TextView>(R.id.tvTestStatus)
        val btnScreenshot = findViewById<Button>(R.id.btnTestScreenshot)
        val btnClose = findViewById<Button>(R.id.btnTestClose)
        
        tvStatus.text = "Testing FLAG_SECURE bypass...\nWindow has FLAG_SECURE: true"
        
        btnScreenshot.setOnClickListener {
            testScreenshot()
        }
        
        btnClose.setOnClickListener {
            finish()
        }
    }
    
    private fun testScreenshot() {
        try {
            val view = window.decorView.rootView
            view.isDrawingCacheEnabled = true
            val bitmap = view.drawingCache
            if (bitmap != null) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(filesDir, "test_screenshot_$timestamp.png")
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                Toast.makeText(this, "✅ Screenshot saved!\n${file.absolutePath}", Toast.LENGTH_LONG).show()
                
                testMediaProjection()
            } else {
                Toast.makeText(this, "❌ Could not capture screenshot", Toast.LENGTH_SHORT).show()
            }
            view.isDrawingCacheEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testMediaProjection() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        if (mediaProjectionManager != null) {
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(intent, 100)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            Toast.makeText(this, "✅ MediaProjection permission granted!", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 100) {
            Toast.makeText(this, "❌ MediaProjection permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
