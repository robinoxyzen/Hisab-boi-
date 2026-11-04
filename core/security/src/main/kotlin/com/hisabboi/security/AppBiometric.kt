package com.hisabboi.security

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hisabboi.model.BiometricRead

data class BioStatus(val hardware: Boolean, val enrolled: Boolean, val useStrong: Boolean)

object AppBiometric {
    fun findFragmentActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return null
    }

    fun status(context: Context): BioStatus {
        val manager = BiometricManager.from(context)
        val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val weak = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val read = BiometricRead.status(strong, weak)
        return BioStatus(read.hardware, read.enrolled, read.useStrong)
    }

    fun enrollIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("আঙুলের ছাপের স্ক্রিন খোলা যায়নি।")
            return
        }
        val bio = status(context)
        if (!bio.hardware) {
            onError("এই ফোনে আঙুলের ছাপ সমর্থিত নয়।")
            return
        }
        if (!bio.enrolled) {
            onError("এই ফোনে আঙুলের ছাপ সেট করা নেই।")
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        return
                    }
                    onError(
                        when (errorCode) {
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> "এই ফোনে আঙুলের ছাপ সেট করা নেই।"
                            BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                                "অনেকবার ভুল হয়েছে। একটু পরে আবার চেষ্টা করুন।"
                            else -> "আঙুলের ছাপ মিলল না।"
                        },
                    )
                }
            },
        )
        runCatching { prompt.authenticate(promptInfo(bio.useStrong, title, subtitle)) }
            .onFailure { onError("আঙুলের ছাপ এখন খোলা যায়নি।") }
    }

    private fun promptInfo(useStrong: Boolean, title: String, subtitle: String): BiometricPrompt.PromptInfo {
        val biometric = if (useStrong) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Device PIN or pattern is the fallback on Android 11 and newer.
            builder.setAllowedAuthenticators(biometric or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            builder.setNegativeButtonText("বাতিল")
        }
        return builder.build()
    }
}
