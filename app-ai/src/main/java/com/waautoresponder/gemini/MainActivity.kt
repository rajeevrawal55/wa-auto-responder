package com.waautoresponder.gemini

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<EditText>(R.id.promptInput).setText(SecureStore.loadPrompt(this))

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val key = findViewById<EditText>(R.id.apiKeyInput).text.toString().trim()
            val prompt = findViewById<EditText>(R.id.promptInput).text.toString().trim()

            if (key.isNotBlank()) {
                SecureStore.saveApiKey(this, key)
                findViewById<EditText>(R.id.apiKeyInput).text.clear()
            }
            SecureStore.savePrompt(this, if (prompt.isBlank()) SecureStore.defaultPrompt() else prompt)

            Toast.makeText(this, "Saved securely.", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            val resultView = findViewById<TextView>(R.id.testResult)
            resultView.text = "Testing Gemini…"

            val typedKey = findViewById<EditText>(R.id.apiKeyInput).text.toString().trim()
            if (typedKey.isNotBlank()) SecureStore.saveApiKey(this, typedKey)
            val prompt = findViewById<EditText>(R.id.promptInput).text.toString().trim()
            if (prompt.isNotBlank()) SecureStore.savePrompt(this, prompt)

            executor.execute {
                val result = GeminiClient.generate(
                    SecureStore.loadApiKey(this),
                    SecureStore.loadModel(this),
                    SecureStore.loadPrompt(this),
                    "Test customer",
                    "Hello, I need a bike spare part."
                )
                runOnUiThread {
                    resultView.text = if (result.success) "Gemini OK: " + result.text else result.text
                    updateStatus()
                }
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasKey = SecureStore.loadApiKey(this).isNotBlank()
        val hasNotificationAccess = isNotificationServiceEnabled()

        findViewById<TextView>(R.id.statusText).text = when {
            hasKey && hasNotificationAccess -> "Status: READY — Gemini key saved and notification access granted"
            !hasKey && !hasNotificationAccess -> "Status: ACTION REQUIRED — save Gemini API key and grant notification access"
            !hasKey -> "Status: ACTION REQUIRED — save Gemini API key"
            else -> "Status: ACTION REQUIRED — grant notification access"
        }

        findViewById<Button>(R.id.btnPermission).isEnabled = !hasNotificationAccess
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false
        return flat.split(":").any { item ->
            ComponentName.unflattenFromString(item)?.packageName == packageName
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
