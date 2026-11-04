package com.hisabboi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0E7C66)
val TealDark = Color(0xFF085447)
val Indigo = Color(0xFF243E73)
val Paper = Color(0xFFF3F6F4)
val Ink = Color(0xFF14221E)
val Payable = Color(0xFF9E2A2B)
val Receive = Color(0xFF0E7C66)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EA),
    onPrimaryContainer = TealDark,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F8),
    onSecondaryContainer = Color(0xFF14284F),
    tertiary = Color(0xFFB86E00),
    background = Paper,
    surface = Color.White,
    surfaceVariant = Color(0xFFE4EEEA),
    onSurface = Ink,
    onBackground = Ink,
    error = Payable,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7DDEC8),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0E5346),
    secondary = Color(0xFFC5D2F6),
    background = Color(0xFF101614),
    surface = Color(0xFF18211E),
    error = Color(0xFFFFB4AB),
)

private val Bengali = FontFamily(Font(R.font.noto_sans_bengali, FontWeight.Normal))

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Bengali, fontWeight = FontWeight.Normal, fontSize = 14.sp),
)

@Composable
fun HisabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        content = content,
    )
}
