package com.hisabboi.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabboi.model.LockMode
import com.hisabboi.model.LockStore
import com.hisabboi.model.NoticeKind
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.StatusBanner
import com.hisabboi.ui.Teal

@Composable
fun LockScreen(lock: LockStore, resetMode: Boolean, onRequestReset: () -> Unit, onFinished: () -> Unit = {}) {
    val state by lock.snapshot.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var tries by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { lock.refreshBiometrics() }
    val showFingerprint = !resetMode && state.mode == LockMode.PIN_AND_BIOMETRIC
    fun prompt() {
        AppBiometric.authenticate(
            context,
            title = "হিসাব বই আনলক",
            subtitle = "আঙুলের ছাপ দিন",
            onSuccess = { error = null; lock.unlock() },
            onError = { error = it },
        )
    }
    LaunchedEffect(tries, state.biometricAvailable, showFingerprint) {
        if (showFingerprint && state.biometricAvailable && tries >= 0) prompt()
    }
    LockPane(
        resetMode = resetMode,
        pin = pin,
        onPin = { pin = it },
        error = error,
        showFingerprint = showFingerprint,
        fingerprintReady = state.biometricAvailable,
        onFingerprint = { tries += 1 },
        onEnroll = {
            runCatching { context.startActivity(AppBiometric.enrollIntent()) }
                .onFailure { error = "সেটিংস খোলা যায়নি।" }
        },
        onSubmit = {
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
        onReset = onRequestReset,
    )
}

@Composable
fun QuickUnlockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var tries by remember { mutableIntStateOf(0) }
    val ready = remember { AppBiometric.status(context).enrolled }
    fun prompt() {
        AppBiometric.authenticate(
            context,
            title = "আঙুলের ছাপ দিয়ে লগইন",
            subtitle = "গুগল সেশন আগের মতোই আছে। শুধু এই ফোন খুলুন।",
            onSuccess = onUnlocked,
            onError = { error = it },
        )
    }
    LaunchedEffect(tries) { if (ready) prompt() }
    LockPane(
        resetMode = false,
        pin = "",
        onPin = {},
        error = error,
        showFingerprint = true,
        fingerprintReady = ready,
        onFingerprint = { tries += 1 },
        onEnroll = { runCatching { context.startActivity(AppBiometric.enrollIntent()) } },
        onSubmit = {},
        onReset = {},
        title = "আঙুলের ছাপ দিয়ে লগইন",
        hidePin = true,
    )
}

@Composable
fun LockPane(
    resetMode: Boolean,
    pin: String,
    onPin: (String) -> Unit,
    error: String?,
    showFingerprint: Boolean,
    fingerprintReady: Boolean,
    onFingerprint: () -> Unit,
    onEnroll: () -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    title: String = if (resetMode) "নতুন পিন দিন" else "পিন দিয়ে খুলুন",
    hidePin: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("হিসাব বই", style = MaterialTheme.typography.headlineMedium, color = Teal)
        Text(title)
        if (showFingerprint) {
            TextButton(onClick = { if (fingerprintReady) onFingerprint() else onEnroll() }) {
                Icon(Icons.Filled.Fingerprint, contentDescription = "আঙুলের ছাপ")
                Text(if (fingerprintReady) "আঙুলের ছাপ" else "আঙুলের ছাপ যোগ করুন")
            }
        }
        if (showFingerprint && !fingerprintReady) {
            StatusBanner(NoticeKind.PENDING, "এই ফোনে আঙুলের ছাপ সেট করা নেই।")
            TextButton(onClick = onEnroll) { Text("সেটিংসে যোগ করুন") }
        }
        if (!hidePin) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) onPin(it.filter { ch -> ch.isDigit() }) },
                label = { Text("পিন") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
            error?.let { StatusBanner(NoticeKind.FAILED, it) }
            PrimaryButton(if (resetMode) "পিন সেভ" else "খুলুন", onSubmit)
            if (!resetMode) TextButton(onClick = onReset) { Text("পিন ভুলে গেছেন? গুগল দিয়ে রিসেট") }
        } else {
            error?.let { StatusBanner(NoticeKind.FAILED, it) }
        }
    }
}
