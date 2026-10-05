package com.waautoresponder.ai

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.LinkedHashMap

class ReplyService : NotificationListenerService() {

    companion object {
        const val PREFS_NAME = "wa_auto_responder"
        private const val TAG = "WA-AutoResponder"
        private const val MAX_SEEN = 200
        private const val SEEN_TTL_MS = 5 * 60 * 1000L
    }

    private val seen = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() || text.isBlank()) return

        val lowerTitle = title.lowercase()
        if (lowerTitle == "whatsapp" || lowerTitle.contains("new messages")) return

        val rules = RuleStore.load(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
        if (rules.isEmpty()) return

        val replyText = rules.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (keyword, _) -> text.contains(keyword, ignoreCase = true) }
            ?.value
            ?: return

        val now = System.currentTimeMillis()
        purgeSeen(now)

        val messageId = sbn.packageName + "|" + sbn.key + "|" + title + "|" + text
        if (seen.containsKey(messageId)) return

        if (sendReply(notification, replyText)) {
            seen[messageId] = now
            if (seen.size > MAX_SEEN) {
                val oldest = seen.keys.firstOrNull()
                if (oldest != null) seen.remove(oldest)
            }
        }
    }

    private fun purgeSeen(now: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > SEEN_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun sendReply(notification: Notification, replyText: String): Boolean {
        val actions = notification.actions ?: return false

        val replyActions = actions.filter { action ->
            val remoteInputs = action.remoteInputs
            remoteInputs != null && remoteInputs.isNotEmpty()
        }

        for (action in replyActions) {
            val remoteInputs = action.remoteInputs ?: continue
            val intent = Intent()
            val bundle = Bundle()

            remoteInputs.forEach { remoteInput ->
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            try {
                action.actionIntent.send(this, 0, intent)
                Log.i(TAG, "Automatic reply sent.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send automatic reply", e)
            }
        }

        return false
    }
}

object RuleStore {
    private const val KEY_RULES = "rules"

    fun load(prefs: android.content.SharedPreferences): Map<String, String> {
        val raw = prefs.getString(KEY_RULES, "").orEmpty()
        if (raw.isBlank()) return emptyMap()

        val result = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                result[decode(parts[0]).lowercase()] = decode(parts[1])
            }
        }
        return result
    }

    fun save(prefs: android.content.SharedPreferences, rules: Map<String, String>) {
        val raw = rules.entries.joinToString("\n") { entry ->
            encode(entry.key) + "\t" + encode(entry.value)
        }
        prefs.edit().putString(KEY_RULES, raw).apply()
    }

    private fun encode(value: String): String =
        value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")

    private fun decode(value: String): String =
        value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
