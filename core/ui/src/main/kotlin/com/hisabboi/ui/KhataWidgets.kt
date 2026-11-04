package com.hisabboi.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hisabboi.model.Money
import com.hisabboi.model.NoticeKind

@Composable
fun MoneySummaryCard(title: String, amount: Double, receive: Boolean, modifier: Modifier = Modifier) {
    val tint = if (receive) Receive else Payable
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(
                Money.format(amount),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = tint,
            )
        }
    }
}

@Composable
fun ContactAmountRow(
    name: String,
    phone: String,
    photo: String,
    lastLabel: String,
    amount: Double,
    onClick: () -> Unit,
    verified: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val receive = amount >= 0
    val tint = if (receive) Receive else Payable
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, photo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameWithSeal(name, verified, message = "এই কাস্টমার যাচাইকৃত")
            if (phone.isNotBlank()) {
                Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            Text(lastLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(kotlin.math.abs(amount)), color = tint, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(if (receive) "পাবো" else "দেবো", color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun LedgerEmptyArt(modifier: Modifier = Modifier) {
    val green = Teal
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = green.copy(alpha = 0.15f),
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.5f),
            cornerRadius = CornerRadius(28f, 28f),
        )
        val book = Path().apply {
            moveTo(w * 0.28f, h * 0.32f)
            lineTo(w * 0.48f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.7f)
            lineTo(w * 0.28f, h * 0.64f)
            close()
        }
        drawPath(book, green)
        val page = Path().apply {
            moveTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.72f, h * 0.32f)
            lineTo(w * 0.72f, h * 0.64f)
            lineTo(w * 0.52f, h * 0.7f)
            close()
        }
        drawPath(page, paper)
        drawPath(page, green.copy(alpha = 0.35f))
        drawCircle(color = green, radius = w * 0.06f, center = Offset(w * 0.62f, h * 0.22f))
    }
}

@Composable
fun LedgerEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LedgerEmptyArt(Modifier.size(160.dp))
        Text("এখনো কোনো কাস্টমার নেই", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "পাবো-দেবো হিসাব রাখতে প্রথম কাস্টমার যোগ করুন।",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) { Text("কাস্টমার যোগ") }
    }
}

@Composable
fun DualActionBar(onGave: () -> Unit, onReceived: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onGave,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Payable, contentColor = Color.White),
        ) { Text("টাকা দিলাম") }
        Button(
            onClick = onReceived,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Receive, contentColor = Color.White),
        ) { Text("টাকা পেলাম") }
    }
}

@Composable
fun AmountKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("7", "8", "9", "−"),
        listOf("0", "00", ".", "×"),
        listOf("C", "÷", "="),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val token = if (key == "−") "-" else key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onKey(token) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            content()
        }
    }
}

@Preview(name = "খাতা", showBackground = true, widthDp = 360, heightDp = 720)
@Preview(name = "খাতা ডার্ক", showBackground = true, widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KhataHomePreview() {
    HisabTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.background(Teal).padding(16.dp)) {
                Text("রবিন স্টোর", color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                StatusBadge(NoticeKind.SUCCESS, "সিঙ্ক হয়েছে")
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneySummaryCard("মোট পাবো", 12500.0, true, Modifier.weight(1f))
                    MoneySummaryCard("মোট দেবো", 3400.0, false, Modifier.weight(1f))
                }
                ContactAmountRow("করিম মিয়া", "01711000000", "", "৪ নভেম্বর ২০২৬", 1500.0, {})
                ContactAmountRow("সাপ্লায়ার রহিম", "01819000000", "", "কোনো লেনদেন নেই", -800.0, {})
                LedgerEmptyState({})
            }
        }
    }
}

@Preview(name = "কীপ্যাড", showBackground = true, widthDp = 360)
@Composable
private fun KeypadPreview() {
    HisabTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("৳১,২৫০", style = MaterialTheme.typography.headlineMedium)
            AmountKeypad({})
            DualActionBar({}, {})
        }
    }
}
