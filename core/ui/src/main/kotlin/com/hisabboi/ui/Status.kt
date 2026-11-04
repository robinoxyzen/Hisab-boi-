package com.hisabboi.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hisabboi.model.NoticeKind
import com.hisabboi.model.NoticeTone
import com.hisabboi.model.VerificationStatus

data class StatusColors(val background: Color, val accent: Color)

fun statusColors(kind: NoticeKind, dark: Boolean): StatusColors = when (kind) {
    NoticeKind.SUCCESS -> if (dark) StatusColors(Color(0xFF1B3A24), Color(0xFF81C784)) else StatusColors(Color(0xFFE8F5E9), Color(0xFF2E7D32))
    NoticeKind.DONE -> if (dark) StatusColors(Color(0xFF0D2744), Color(0xFF90CAF9)) else StatusColors(Color(0xFFE3F2FD), Color(0xFF1565C0))
    NoticeKind.FAILED -> if (dark) StatusColors(Color(0xFF3F1D1D), Color(0xFFEF9A9A)) else StatusColors(Color(0xFFFFEBEE), Color(0xFFC62828))
    NoticeKind.PENDING -> if (dark) StatusColors(Color(0xFF3A2E10), Color(0xFFFFE082)) else StatusColors(Color(0xFFFFF8E1), Color(0xFFF9A825))
    NoticeKind.INFO -> if (dark) StatusColors(Color(0xFF2A2F33), Color(0xFFB0BEC5)) else StatusColors(Color(0xFFECEFF1), Color(0xFF546E7A))
}

fun statusIcon(kind: NoticeKind): ImageVector = when (kind) {
    NoticeKind.SUCCESS -> Icons.Filled.CheckCircle
    NoticeKind.DONE -> Icons.Filled.DoneAll
    NoticeKind.FAILED -> Icons.Filled.Error
    NoticeKind.PENDING -> Icons.Filled.HourglassEmpty
    NoticeKind.INFO -> Icons.Filled.Info
}

@Composable
fun StatusGlyph(kind: NoticeKind, tint: Color, modifier: Modifier = Modifier, animate: Boolean = kind == NoticeKind.SUCCESS) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "status-icon",
    )
    val shown = if (animate) scale else 1f
    Icon(statusIcon(kind), contentDescription = null, tint = tint, modifier = modifier.scale(if (animate) shown else 1f))
}

@Composable
fun StatusBadge(kind: NoticeKind, label: String, modifier: Modifier = Modifier) {
    val colors = statusColors(kind, dark = androidx.compose.foundation.isSystemInDarkTheme())
    Surface(modifier = modifier, color = colors.background, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(statusIcon(kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.accent)
        }
    }
}

@Composable
fun StatusBanner(
    kind: NoticeKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = statusColors(kind, dark = androidx.compose.foundation.isSystemInDarkTheme())
    Surface(modifier = modifier, color = colors.background, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(kind, colors.accent, Modifier.size(22.dp))
            Text(
                message,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

private val SealBlue = Color(0xFF1D9BF0)

/**
 * Original scalloped seal. Not a Meta or X asset.
 * Tap explains the status. No label sits beside the name.
 */
@Composable
fun VerifiedSeal(
    modifier: Modifier = Modifier,
    message: String = "এই দোকানটি যাচাইকৃত",
) {
    var open by remember { mutableStateOf(false) }
    Canvas(
        modifier
            .size(18.dp)
            .clickable { open = true },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f
        val inner = outer * 0.78f
        val teeth = 12
        val steps = teeth * 12
        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angle = (t * Math.PI * 2.0 - Math.PI / 2.0)
            val wave = (kotlin.math.cos(angle * teeth) + 1.0) / 2.0
            val radius = inner + (outer - inner) * wave.toFloat()
            val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
            val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, SealBlue)
        val check = Path().apply {
            moveTo(cx - outer * 0.34f, cy + outer * 0.02f)
            lineTo(cx - outer * 0.08f, cy + outer * 0.28f)
            lineTo(cx + outer * 0.40f, cy - outer * 0.28f)
        }
        drawPath(
            check,
            Color.White,
            style = Stroke(width = outer * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("ঠিক আছে") } },
        )
    }
}

@Composable
fun NameWithSeal(
    name: String,
    verified: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    message: String = "এই দোকানটি যাচাইকৃত",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (verified) {
            Spacer(Modifier.width(4.dp))
            VerifiedSeal(message = message)
        }
    }
}

/** Status only. Documents, NID and selfies are never drawn here. */
@Composable
fun ShopVerifyMark(
    verified: Boolean,
    status: VerificationStatus,
    onVerify: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isVerified = verified || status == VerificationStatus.VERIFIED
    when {
        isVerified -> VerifiedSeal(modifier)
        status == VerificationStatus.PENDING -> {
            val chip = @Composable { StatusBadge(NoticeKind.PENDING, "অপেক্ষমাণ") }
            if (onVerify == null) chip() else Box(modifier.clickable(onClick = onVerify)) { chip() }
        }
        status == VerificationStatus.REJECTED -> {
            val chip = @Composable { StatusBadge(NoticeKind.FAILED, "ব্যর্থ") }
            if (onVerify == null) chip() else Box(modifier.clickable(onClick = onVerify)) { chip() }
        }
        else -> {
            val gray = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2A2F33) else Color(0xFFECEFF1)
            val ink = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFB0BEC5) else Color(0xFF546E7A)
            Surface(
                modifier = if (onVerify == null) modifier else modifier.clickable(onClick = onVerify),
                color = gray,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "যাচাই করুন",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = ink,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun ResultBanner(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    StatusBanner(NoticeTone.of(text), text, modifier)
}

class StatusSnackbarVisuals(
    override val message: String,
    val kind: NoticeKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

suspend fun SnackbarHostState.showStatus(kind: NoticeKind, message: String) {
    showSnackbar(StatusSnackbarVisuals(message, kind))
}

@Composable
fun StatusSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        val kind = (data.visuals as? StatusSnackbarVisuals)?.kind ?: NoticeTone.of(data.visuals.message)
        StatusBanner(kind, data.visuals.message, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Preview(name = "Status light", showBackground = true)
@Preview(name = "Status dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusPreview() {
    HisabTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(NoticeKind.SUCCESS, "সিঙ্ক হয়েছে")
            StatusBadge(NoticeKind.PENDING, "অফলাইনে সংরক্ষিত")
            StatusBanner(NoticeKind.SUCCESS, "সেভ হয়েছে")
            StatusBanner(NoticeKind.DONE, "সম্পন্ন")
            StatusBanner(NoticeKind.FAILED, "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যাচ্ছে না। আবার চেষ্টা করুন।", actionLabel = "আবার চেষ্টা", onAction = {})
            StatusBanner(NoticeKind.PENDING, "ছবি আপলোড হচ্ছে…")
            StatusBanner(NoticeKind.INFO, "অ্যাপ কোনো টাকা গ্রহণ করে না।")
            NameWithSeal("রবিন স্টোর", verified = true, style = MaterialTheme.typography.titleLarge)
            ShopVerifyMark(verified = false, status = VerificationStatus.NONE, onVerify = {})
            ShopVerifyMark(verified = false, status = VerificationStatus.PENDING, onVerify = {})
            ShopVerifyMark(verified = false, status = VerificationStatus.REJECTED, onVerify = {})
        }
    }
}
