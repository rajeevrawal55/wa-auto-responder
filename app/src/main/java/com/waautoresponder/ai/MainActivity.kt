package com.waautoresponder.ai

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText: TextView = findViewById(R.id.statusText)
        val permButton: Button = findViewById(R.id.btnPermission)

        permButton.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    override fun onResume() {
        super.onResume()
        val statusText: TextView = findViewById(R.id.statusText)
        val permButton: Button = findViewById(R.id.btnPermission)
        if (isNotificationServiceEnabled()) {
            statusText.text = "Status: READY (Permissions Granted)"
            permButton.isEnabled = false
        } else {
            statusText.text = "Status: ACTION REQUIRED\nPlease Grant Permission below."
            permButton.isEnabled = true
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }
}\n