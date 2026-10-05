package com.waautoresponder.ai

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

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(ReplyService.PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        findViewById<Button>(R.id.addRuleButton).setOnClickListener {
            val keyword = findViewById<EditText>(R.id.keywordInput).text.toString().trim()
            val reply = findViewById<EditText>(R.id.replyInput).text.toString().trim()

            if (keyword.isBlank() || reply.isBlank()) {
                Toast.makeText(this, "Enter both an incoming keyword and a reply.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rules = RuleStore.load(prefs).toMutableMap()
            rules[keyword.lowercase()] = reply
            RuleStore.save(prefs, rules)

            findViewById<EditText>(R.id.keywordInput).text.clear()
            findViewById<EditText>(R.id.replyInput).text.clear()
            renderRules()
            Toast.makeText(this, "Rule saved.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.clearRulesButton).setOnClickListener {
            RuleStore.save(prefs, emptyMap())
            renderRules()
            Toast.makeText(this, "All rules cleared.", Toast.LENGTH_SHORT).show()
        }

        renderRules()
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val permButton = findViewById<Button>(R.id.btnPermission)

        if (isNotificationServiceEnabled()) {
            statusText.text = "Status: READY — notification access granted"
            permButton.isEnabled = false
        } else {
            statusText.text = "Status: ACTION REQUIRED\nGrant notification access below."
            permButton.isEnabled = true
        }

        renderRules()
    }

    private fun renderRules() {
        val rules = RuleStore.load(prefs)
        val text = if (rules.isEmpty()) {
            "No rules yet."
        } else {
            buildString {
                append("Saved rules:\n")
                rules.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                    append(index + 1)
                    append(". If message contains \"")
                    append(entry.key)
                    append("\" → ")
                    append(entry.value)
                    append('\n')
                }
            }.trimEnd()
        }
        findViewById<TextView>(R.id.rulesText).text = text
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false

        return flat.split(":").any { name ->
            ComponentName.unflattenFromString(name)?.packageName == packageName
        }
    }
}
