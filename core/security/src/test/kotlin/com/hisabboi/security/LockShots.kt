package com.hisabboi.security

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LockShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun lockWithFingerprint() {
        paparazzi.snapshot {
            com.hisabboi.ui.HisabTheme {
                LockPane(
                    resetMode = false,
                    pin = "",
                    onPin = {},
                    error = null,
                    showFingerprint = true,
                    fingerprintReady = true,
                    onFingerprint = {},
                    onEnroll = {},
                    onSubmit = {},
                    onReset = {},
                )
            }
        }
    }

    @Test
    fun lockNeedsEnrollment() {
        paparazzi.snapshot {
            com.hisabboi.ui.HisabTheme {
                LockPane(
                    resetMode = false,
                    pin = "",
                    onPin = {},
                    error = null,
                    showFingerprint = true,
                    fingerprintReady = false,
                    onFingerprint = {},
                    onEnroll = {},
                    onSubmit = {},
                    onReset = {},
                )
            }
        }
    }

    @Test
    fun quickUnlock() {
        paparazzi.snapshot {
            com.hisabboi.ui.HisabTheme {
                LockPane(
                    resetMode = false,
                    pin = "",
                    onPin = {},
                    error = null,
                    showFingerprint = true,
                    fingerprintReady = true,
                    onFingerprint = {},
                    onEnroll = {},
                    onSubmit = {},
                    onReset = {},
                    title = "আঙুলের ছাপ দিয়ে লগইন",
                    hidePin = true,
                )
            }
        }
    }
}
