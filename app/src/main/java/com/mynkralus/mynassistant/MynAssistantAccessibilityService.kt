package com.mynkralus.mynassistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Acts only on a pending action explicitly requested by the user from MynAssistant.
 * The service is limited to WhatsApp packages and requires the user to enable it in
 * Android Accessibility settings before WhatsApp calls/messages can be automated.
 */
class MynAssistantAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_PACKAGES) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val action = prefs.getString(KEY_PENDING_ACTION, "").orEmpty()
        if (action != ACTION_CALL && action != ACTION_MESSAGE) return

        val requestedAt = prefs.getLong(KEY_PENDING_AT, 0L)
        if (requestedAt <= 0L || System.currentTimeMillis() - requestedAt > ACTION_TIMEOUT_MS) {
            finishPendingAction(prefs, "expired")
            return
        }

        val root = rootInActiveWindow ?: return
        if (!hasMessageComposer(root)) return

        when (action) {
            ACTION_CALL -> {
                val callButton = clickableAncestor(findVoiceCallButton(root)) ?: return
                if (callButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    finishPendingAction(prefs, "started")
                }
            }
            ACTION_MESSAGE -> handlePendingMessage(root, prefs)
        }
    }

    private fun handlePendingMessage(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences) {
        val message = prefs.getString(KEY_PENDING_MESSAGE, "").orEmpty()
        if (message.isBlank()) {
            finishPendingAction(prefs, "expired")
            return
        }

        if (!prefs.getBoolean(KEY_MESSAGE_READY, false)) {
            val editor = findEditableNode(root) ?: return
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            }
            if (editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                prefs.edit().putBoolean(KEY_MESSAGE_READY, true).apply()
            }
            return
        }

        val sendButton = clickableAncestor(findSendButton(root)) ?: return
        if (sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            finishPendingAction(prefs, "sent")
        }
    }

    private fun hasMessageComposer(root: AccessibilityNodeInfo): Boolean =
        findEditableNode(root) != null

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findEditableNode(child)
            if (match != null) return match
        }
        return null
    }

    private fun findVoiceCallButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = labels(node)
        val isVoiceCall = labels.any {
            it.contains("voice call") ||
                it.contains("audio call") ||
                it.contains("sesli arama") ||
                it.contains("sesli ara")
        }
        if (isVoiceCall && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findVoiceCallButton(child)
            if (match != null) return match
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = labels(node)
        val isSend = labels.any {
            it == "send" || it == "gönder" || it == "gonder" ||
                it.contains("send message") || it.contains("mesajı gönder") || it.contains("mesaji gonder")
        }
        if (isSend && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findSendButton(child)
            if (match != null) return match
        }
        return null
    }

    private fun labels(node: AccessibilityNodeInfo): List<String> = listOfNotNull(
        node.text?.toString(),
        node.contentDescription?.toString(),
        node.hintText?.toString()
    ).map { it.trim().lowercase(Locale.ROOT) }

    private fun clickableAncestor(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var node = start
        repeat(6) {
            if (node == null) return null
            if (node.isClickable && node.isEnabled) return node
            node = node.parent
        }
        return null
    }

    private fun finishPendingAction(prefs: android.content.SharedPreferences, status: String) {
        prefs.edit()
            .remove(KEY_PENDING_ACTION)
            .remove(KEY_PENDING_AT)
            .remove(KEY_PENDING_MESSAGE)
            .remove(KEY_MESSAGE_READY)
            .putString(KEY_STATUS, status)
            .apply()
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val PREFS_NAME = "mynassistant_prefs"
        private const val KEY_PENDING_ACTION = "pending_whatsapp_action"
        private const val KEY_PENDING_AT = "pending_whatsapp_action_at"
        private const val KEY_PENDING_MESSAGE = "pending_whatsapp_message"
        private const val KEY_MESSAGE_READY = "pending_whatsapp_message_ready"
        private const val KEY_STATUS = "whatsapp_auto_call_status"
        private const val ACTION_CALL = "CALL"
        private const val ACTION_MESSAGE = "MESSAGE"
        private const val ACTION_TIMEOUT_MS = 45_000L
        private val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
