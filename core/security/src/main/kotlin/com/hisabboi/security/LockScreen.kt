package com.hisabboi.security

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabboi.model.LockMode
import com.hisabboi.model.LockStore
import com.hisabboi.ui.AmountField
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.Teal

@Composable
fun LockScreen(lock: LockStore, resetMode: Boolean, onRequestReset: () -> Unit, onFinished: () -> Unit = {}) {
    val state by lock.snapshot.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(state.mode, state.biometricAvailable, resetMode) {
        if (!resetMode && state.mode == LockMode.PIN_AND_BIOMETRIC && state.biometricAvailable) {
            val activity = context as? FragmentActivity ?: return@LaunchedEffect
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        lock.unlock()
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("হিসাব বই আনলক")
                .setSubtitle("আঙুলের ছাপ দিন")
                .setNegativeButtonText("পিন ব্যবহার করুন")
                .build()
            runCatching { prompt.authenticate(info) }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("হিসাব বই", style = MaterialTheme.typography.headlineMedium, color = Teal)
        Text(if (resetMode) "নতুন পিন দিন" else "পিন দিয়ে খুলুন")
        androidx.compose.material3.OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it.filter { ch -> ch.isDigit() } },
            label = { Text("পিন") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
        error?.let { Text(it) }
        PrimaryButton(
            if (resetMode) "পিন সেভ" else "খুলুন",
            {
                if (pin.length !in 4..6) {
                    error = "৪ থেকে ৬ সংখ্যা"
                } else if (resetMode) {
                    lock.setPin(pin)
                    lock.setMode(LockMode.PIN, state.timeoutSec)
                    error = null
                    onFinished()
                } else if (!lock.checkPin(pin)) {
                    error = "পিন মিলেনি"
                }
            },
        )
        if (!resetMode) TextButton(onClick = onRequestReset) { Text("পিন ভুলে গেছেন? গুগল দিয়ে রিসেট") }
    }
}
