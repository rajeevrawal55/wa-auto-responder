package com.waautoresponder.gemini

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStore {
    private const val PREFS = "gemini_secure_prefs"
    private const val KEY_ALIAS = "wa_gemini_api_key"
    private const val API_KEY_CIPHER = "api_key_cipher"
    private const val API_KEY_IV = "api_key_iv"
    private const val PROMPT = "prompt"
    private const val MODEL = "model"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(API_KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(API_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val data = prefs.getString(API_KEY_CIPHER, null) ?: return ""
            val iv = prefs.getString(API_KEY_IV, null) ?: return ""

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun savePrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PROMPT, prompt).apply()
    }

    fun loadPrompt(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROMPT, defaultPrompt()).orEmpty()

    fun saveModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(MODEL, model).apply()
    }

    fun loadModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODEL, "gemini-2.5-flash").orEmpty()

    fun defaultPrompt(): String =
        "You are the WhatsApp customer support assistant for Autoss.in, an Indian motorcycle spare-parts business. " +
        "Reply briefly, politely and helpfully. Ask for bike model/year when needed. " +
        "Never invent stock, price, delivery date, payment status or order status. " +
        "If the answer needs business-specific information you do not know, ask the customer to wait for a human reply. " +
        "Keep replies under 80 words unless the customer clearly needs more detail."
}
