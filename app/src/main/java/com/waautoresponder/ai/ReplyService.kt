package com.waautoresponder.ai

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class ReplyService : NotificationListenerService() {
    private val handledMessages = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Only trigger on WhatsApp Business and regular WhatsApp
        if (packageName != "com.whatsapp.w4b" && packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getString(Notification.EXTRA_TEXT) ?: return

        // Ignore generic summaries
        if (title == "WhatsApp" || title.contains("new messages")) return

        val replyText = getReplyText(text)
        if (replyText == null) return

        // Deduplication to prevent infinite loops / spam
        val msgId = "__" 
        if (handledMessages.contains(msgId)) return
        handledMessages.add(msgId)
        
        if (handledMessages.size > 100) handledMessages.clear()

        sendReply(sbn.notification, replyText)
    }

    private fun getReplyText(incomingText: String): String? {
        val lower = incomingText.lowercase()
        
        // ============================================
        // ADD YOUR RULES HERE!
        // ============================================
        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hi! This is an automated AI response. How can I assist you with your business needs today?"
        }
        if (lower.contains("price") || lower.contains("cost")) {
            return "Our pricing starts at . Please reply with 'details' for more info."
        }
        if (lower.contains("details")) {
            return "We offer custom software, AI solutions, and web development. Check our website or leave a message!"
        }
        
        return null // Ignore messages that do not match rules
    }

    private fun sendReply(notification: Notification, replyText: String) {
        val actions = notification.actions ?: return
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(remoteInput.resultKey, replyText)
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                
                try {
                    action.actionIntent.send(this, 0, intent)
                    Log.i("WA-AutoResponder", "Reply sent successfully!")
                    return 
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}\n