package com.waautoresponder.gemini

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executors

class GeminiReplyService : NotificationListenerService() {

    companion object {
        private const val TAG = "WA-GeminiResponder"
        private const val MAX_SEEN = 250
        private const val SEEN_TTL_MS = 10 * 60 * 1000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val seen = LinkedHashMap<String, Long>()
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (sender.isBlank() || message.isBlank()) return
        val lowerSender = sender.lowercase()
        if (lowerSender == "whatsapp" || lowerSender.contains("new messages")) return

        val apiKey = SecureStore.loadApiKey(this)
        if (apiKey.isBlank()) return

        val now = System.currentTimeMillis()
        purgeSeen(now)

        val messageId = sbn.packageName + "|" + sbn.key + "|" + sender + "|" + message
        synchronized(seen) {
            if (seen.containsKey(messageId)) return
        }
        if (!inFlight.add(messageId)) return

        executor.execute {
            try {
                val result = GeminiClient.generate(
                    apiKey,
                    SecureStore.loadModel(this),
                    SecureStore.loadPrompt(this),
                    sender,
                    message
                )

                if (result.success && result.text.isNotBlank()) {
                    if (sendReply(notification, result.text)) {
                        synchronized(seen) {
                            seen[messageId] = System.currentTimeMillis()
                            trimSeen()
                        }
                    }
                } else {
                    Log.w(TAG, result.text)
                }
            } finally {
                inFlight.remove(messageId)
            }
        }
    }

    private fun purgeSeen(now: Long) {
        synchronized(seen) {
            val iterator = seen.entries.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().value > SEEN_TTL_MS) iterator.remove()
            }
        }
    }

    private fun trimSeen() {
        while (seen.size > MAX_SEEN) {
            val first = seen.keys.firstOrNull() ?: break
            seen.remove(first)
        }
    }

    private fun sendReply(notification: Notification, replyText: String): Boolean {
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isEmpty()) continue

            val intent = Intent()
            val bundle = Bundle()
            remoteInputs.forEach { input ->
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            try {
                action.actionIntent.send(this, 0, intent)
                Log.i(TAG, "Gemini reply sent.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send Gemini reply", e)
            }
        }

        return false
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
