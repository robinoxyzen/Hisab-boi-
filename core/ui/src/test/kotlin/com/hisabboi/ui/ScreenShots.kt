package com.hisabboi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hisabboi.model.NoticeKind
import com.hisabboi.model.VerificationStatus
import org.junit.Rule
import org.junit.Test

class ScreenShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun login() {
        paparazzi.snapshot {
            HisabTheme {
                LoginScreen(busy = false, error = null, onGoogle = {}, onRetry = {})
            }
        }
    }

    @Test
    fun loginError() {
        paparazzi.snapshot {
            HisabTheme {
                LoginScreen(
                    busy = false,
                    error = "Failed to get document because the client is offline.",
                    onGoogle = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun statusAndSeal() {
        paparazzi.snapshot {
            HisabTheme {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameWithSeal("রবিন স্টোর", verified = true)
                    ShopVerifyMark(verified = false, status = VerificationStatus.PENDING, onVerify = {})
                    ShopVerifyMark(verified = false, status = VerificationStatus.REJECTED, onVerify = {})
                    StatusBanner(NoticeKind.SUCCESS, "সেভ হয়েছে")
                    StatusBanner(NoticeKind.FAILED, "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যাচ্ছে না। আবার চেষ্টা করুন।")
                }
            }
        }
    }

    @Test
    fun khataHome() {
        paparazzi.snapshot {
            HisabTheme {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        MoneySummaryCard("মোট পাবো", 12500.0, true, Modifier.weight(1f))
                        MoneySummaryCard("মোট দেবো", 800.0, false, Modifier.weight(1f))
                    }
                    ContactAmountRow("করিম", "01700000000", "", "৪ নভেম্বর", 500.0, {}, verified = true)
                    LedgerEmptyState({})
                }
            }
        }
    }
}
