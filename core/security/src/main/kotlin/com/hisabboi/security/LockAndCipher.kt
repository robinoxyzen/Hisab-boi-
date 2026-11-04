package com.hisabboi.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hisabboi.model.FieldCipher
import com.hisabboi.model.LockMode
import com.hisabboi.model.LockSnapshot
import com.hisabboi.model.LockStore
import com.hisabboi.model.PinHash
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : LockStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hisab_lock",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val biometricAvailable =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    private var backgroundAt = 0L
    private val _snapshot = MutableStateFlow(read())
    override val snapshot: StateFlow<LockSnapshot> = _snapshot

    override fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() })
        val salt = PinHash.salt()
        prefs.edit()
            .putString("salt", PinHash.toHex(salt))
            .putString("hash", PinHash.hash(pin, salt))
            .apply()
        publish(locked = false)
    }

    override fun checkPin(pin: String): Boolean {
        val saltHex = prefs.getString("salt", null) ?: return false
        val hash = prefs.getString("hash", null) ?: return false
        val ok = PinHash.verify(pin, PinHash.fromHex(saltHex), hash)
        if (ok) unlock()
        return ok
    }

    override fun setMode(mode: LockMode, timeoutSec: Int) {
        prefs.edit()
            .putString("mode", mode.name)
            .putInt("timeout", timeoutSec.coerceIn(15, 24 * 3600))
            .apply()
        publish(locked = _snapshot.value.locked && mode != LockMode.OFF)
    }

    override fun clear() {
        prefs.edit().clear().apply()
        publish(locked = false)
    }

    override fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    override fun onForeground() {
        val mode = mode()
        if (mode == LockMode.OFF || !pinSet()) return
        val elapsed = System.currentTimeMillis() - backgroundAt
        if (backgroundAt == 0L || elapsed > timeout() * 1000L) {
            lockNow()
        }
    }

    override fun unlock() = publish(locked = false)

    override fun lockNow() {
        if (mode() != LockMode.OFF && pinSet()) publish(locked = true)
    }

    private fun publish(locked: Boolean) {
        _snapshot.value = LockSnapshot(
            mode = mode(),
            pinSet = pinSet(),
            locked = locked && mode() != LockMode.OFF && pinSet(),
            timeoutSec = timeout(),
            biometricAvailable = biometricAvailable,
        )
    }

    private fun read(): LockSnapshot {
        val shouldLock = mode() != LockMode.OFF && pinSet()
        return LockSnapshot(mode(), pinSet(), shouldLock, timeout(), biometricAvailable)
    }

    private fun mode(): LockMode =
        runCatching { LockMode.valueOf(prefs.getString("mode", "OFF") ?: "OFF") }.getOrDefault(LockMode.OFF)

    private fun pinSet(): Boolean = !prefs.getString("hash", null).isNullOrBlank()

    private fun timeout(): Int = prefs.getInt("timeout", 60)
}

@Singleton
class AndroidFieldCipher @Inject constructor() : FieldCipher {
    override fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + body.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(body, 0, out, iv.size, body.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(payload: String): String {
        if (payload.isEmpty()) return ""
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(body).toString(Charsets.UTF_8)
    }

    private fun secret(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "hisab_boi_field_key"
    }
}
