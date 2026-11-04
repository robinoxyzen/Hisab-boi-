package com.hisabboi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hisabboi.model.Money
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun MoneyText(amount: Double, receive: Boolean, modifier: Modifier = Modifier) {
    val color = if (receive) Receive else Payable
    Text(Money.format(amount), color = color, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
fun StatTile(title: String, amount: Double, receive: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            MoneyText(amount, receive)
        }
    }
}

@Composable
fun Avatar(name: String, photo: String, modifier: Modifier = Modifier) {
    val letter = name.trim().firstOrNull()?.toString() ?: "?"
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (photo.isNotBlank()) {
            val bytes = runCatching { Base64.decode(photo, Base64.DEFAULT) }.getOrNull()
            if (bytes != null) {
                AsyncImage(model = bytes, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Text(letter, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        } else {
            Text(letter, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun NoteField(value: String, onChange: (String) -> Unit, onScan: (() -> Unit)?, label: String = "বিবরণ") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = {
            if (onScan != null) {
                IconButton(onClick = onScan) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "পণ্য স্ক্যান")
                }
            }
        },
        minLines = 2,
    )
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String = "টাকার পরিমাণ") {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = buildString {
                var dot = false
                raw.forEach { ch ->
                    val mapped = "০১২৩৪৫৬৭৮৯".indexOf(ch).let { if (it >= 0) '0' + it else ch }
                    when {
                        mapped.isDigit() -> append(mapped)
                        mapped == '.' && !dot -> {
                            dot = true
                            append('.')
                        }
                    }
                }
            }
            onChange(cleaned)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("৳") },
        supportingText = {
            val n = Money.parse(value)
            if (n != null) Text(Money.format(n))
        },
        singleLine = true,
    )
}

@Composable
fun ChipRow(labels: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, (label, selected) ->
            FilterChip(selected = selected, onClick = { onPick(index) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleScaffold(title: String, onBack: (() -> Unit)? = null, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান") }
                    }
                },
            )
        },
    ) { padding -> content(padding) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) { Text(text) }
}

fun qrBitmap(content: String, size: Int = 720, foreground: Int = 0xFF0E7C66.toInt(), background: Int = 0xFFFFFFFF.toInt()): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) foreground else background)
        }
    }
    return bitmap
}

@Composable
fun QrImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = androidx.compose.runtime.remember(content) { qrBitmap(content) }
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR কোড",
        modifier = modifier,
    )
}

object Files {
    fun pdf(context: Context, title: String, lines: List<String>): ByteArray = pdfWith(context, title, lines)

    private fun pdfWith(context: Context, title: String, lines: List<String>): ByteArray {
        val face = runCatching {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
        }.getOrNull() ?: Typeface.DEFAULT
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            this.typeface = face
            color = android.graphics.Color.BLACK
        }
        val titlePaint = Paint(paint).apply { textSize = 20f }
        val width = 595
        val height = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNum).create())
        var canvas = page.canvas
        var y = 48f
        canvas.drawText(title, 40f, y, titlePaint)
        y += 32f
        lines.forEach { line ->
            if (y > height - 48) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNum).create())
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line.take(90), 40f, y, paint)
            y += 22f
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun csv(rows: List<List<String>>): String = buildString {
        append('\uFEFF')
        rows.forEach { row ->
            append(row.joinToString(",") { cell -> "\"" + cell.replace("\"", "\"\"") + "\"" })
            append('\n')
        }
    }

    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }

    fun share(context: Context, name: String, bytes: ByteArray, mime: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার"))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার"))
    }

    fun sms(context: Context, phone: String, body: String) {
        val uri = Uri.parse("smsto:$phone")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply { putExtra("sms_body", body) }
        context.startActivity(intent)
    }

    fun whatsApp(context: Context, phone: String, text: String) {
        val digits = phone.filter { it.isDigit() }
        val intl = when {
            digits.startsWith("880") -> digits
            digits.startsWith("0") && digits.length >= 11 -> "88$digits"
            else -> digits
        }
        val uri = Uri.parse("https://wa.me/$intl?text=${Uri.encode(text)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun directions(context: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
        }
    }

    fun dueCard(context: Context, shop: String, contact: String, amount: String, note: String): ByteArray {
        val face = runCatching {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
        }.getOrNull() ?: Typeface.DEFAULT
        val bitmap = Bitmap.createBitmap(900, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#F3F6F4"))
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0E7C66")
            textSize = 42f
            this.typeface = face
        }
        val body = Paint(title).apply {
            color = android.graphics.Color.parseColor("#14221E")
            textSize = 32f
        }
        canvas.drawText("হিসাব বই · তাগাদা", 48f, 90f, title)
        canvas.drawText(shop, 48f, 170f, body)
        canvas.drawText(contact, 48f, 230f, body)
        canvas.drawText(amount, 48f, 310f, title)
        canvas.drawText(note.take(80), 48f, 390f, body)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}

object Images {
    fun jpeg(context: Context, uri: Uri, maxEdge: Int = 1024, quality: Int = 65): String {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return ""
        return jpeg(bitmap, maxEdge, quality)
    }

    fun jpeg(bitmap: Bitmap, maxEdge: Int, quality: Int): String {
        val scale = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1).toFloat() / maxEdge
        val target = if (scale > 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true)
        } else bitmap
        val stream = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
