package com.hisabboi.feature.catalog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.Catalog
import com.hisabboi.model.LineItem
import com.hisabboi.model.Money
import com.hisabboi.model.Product
import com.hisabboi.model.ProductRepository
import com.hisabboi.model.ScanBus
import com.hisabboi.model.UserFacingError
import com.hisabboi.ui.AmountField
import com.hisabboi.ui.Avatar
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Images
import com.hisabboi.ui.MoneyText
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.SoftErrorCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

sealed interface ScanHit {
    data class Found(val product: Product) : ScanHit
    data class Missing(val barcode: String) : ScanHit
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryScanViewModel @Inject constructor(
    session: ActiveBusinessSource,
    private val products: ProductRepository,
    private val scanBus: ScanBus,
) : ViewModel() {
    private val uid = session.user
    private val active = session.active
    val catalog = session.active.flatMapLatest { biz ->
        val id = session.user.value?.uid
        if (biz == null || id == null) flowOf(emptyList()) else products.observe(id, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var hit by mutableStateOf<ScanHit?>(null)
    var manual by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    private var lastCode = ""

    fun onBarcode(raw: String) {
        if (hit != null) return
        val code = raw.trim()
        if (code.isBlank() || code == lastCode) return
        lastCode = code
        val known = Catalog.find(catalog.value, code)
        hit = if (known != null) ScanHit.Found(known) else ScanHit.Missing(code)
    }

    fun submitManual() {
        lastCode = ""
        onBarcode(manual)
        manual = ""
    }

    fun dismiss() {
        hit = null
        message = null
        lastCode = ""
    }

    fun prepare(product: Product) {
        scanBus.publish(
            listOf(LineItem(name = product.name, barcode = product.barcode.ifBlank { null }, qty = 1)),
            product.sellPrice.takeIf { it > 0.0 },
        )
    }

    fun adjust(product: Product, delta: Double) {
        val biz = active.value ?: return
        val id = uid.value?.uid ?: return
        viewModelScope.launch {
            products.adjustStock(id, biz.id, product.id, delta)
                .onSuccess { updated ->
                    hit = ScanHit.Found(updated)
                    message = "স্টক আপডেট হয়েছে"
                }
                .onFailure { message = UserFacingError.auth(it.message) }
        }
    }
}

@Composable
fun InventoryScannerScreen(
    onBack: () -> Unit,
    onMissing: (String) -> Unit,
    onSale: () -> Unit,
    onCash: (String) -> Unit,
    viewModel: InventoryScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var stockOpen by remember { mutableStateOf(false) }
    var cashOpen by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    val missing = viewModel.hit as? ScanHit.Missing
    LaunchedEffect(missing?.barcode) {
        val code = missing?.barcode ?: return@LaunchedEffect
        viewModel.dismiss()
        onMissing(code)
    }
    val found = viewModel.hit as? ScanHit.Found
    LaunchedEffect(found?.product?.id, found?.product?.stock) {
        if (found != null) ScanFeedback.ping(context)
    }
    SimpleScaffold("পণ্য স্ক্যান", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (granted) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        torch = torch,
                        onBarcode = viewModel::onBarcode,
                    )
                    IconButton(
                        onClick = { torch = !torch },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                    ) {
                        Icon(
                            if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "ফ্ল্যাশ",
                            tint = Color.White,
                        )
                    }
                }
            } else {
                EmptyHint("ক্যামেরা দরকার", "বারকোড পড়তে ক্যামেরার অনুমতি দিন। না দিলে নিচে কোড লিখে খুঁজতে পারবেন।")
                PrimaryButton("অনুমতি দিন", { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(horizontal = 16.dp))
                if (asked && !granted) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("সেটিংসে অনুমতি দিন") }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.manual,
                    onValueChange = { viewModel.manual = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("বারকোড লিখে খুঁজুন") },
                    singleLine = true,
                )
                PrimaryButton("খুঁজুন", viewModel::submitManual, viewModel.manual.isNotBlank())
                viewModel.message?.let { com.hisabboi.ui.ResultBanner(it) }
                val product = found?.product
                if (product != null) {
                    ProductFoundCard(
                        product = product,
                        onSale = {
                            viewModel.prepare(product)
                            onSale()
                        },
                        onCash = { cashOpen = true },
                        onStock = { stockOpen = true },
                        onDismiss = viewModel::dismiss,
                    )
                }
            }
        }
    }
    val product = found?.product
    if (stockOpen && product != null) {
        StockDialog(
            product = product,
            onDismiss = { stockOpen = false },
            onDelta = {
                viewModel.adjust(product, it)
                stockOpen = false
            },
        )
    }
    if (cashOpen && product != null) {
        AlertDialog(
            onDismissRequest = { cashOpen = false },
            title = { Text("ক্যাশ এন্ট্রি") },
            text = {
                Column {
                    listOf("sale" to "ক্যাশ বিক্রি", "purchase" to "ক্যাশ কেনা", "expense" to "খরচ").forEach { (kind, label) ->
                        TextButton(onClick = {
                            viewModel.prepare(product)
                            cashOpen = false
                            onCash(kind)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cashOpen = false }) { Text("বাতিল") } },
        )
    }
}

@Composable
fun ProductFoundCard(
    product: Product,
    onSale: () -> Unit,
    onCash: () -> Unit,
    onStock: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(product.barcode, style = MaterialTheme.typography.bodySmall)
            MoneyText(product.sellPrice, receive = true)
            Text(Catalog.stockLabel(product.stock, product.unit))
            TextButton(onClick = onSale) { Text("বিক্রিতে যোগ") }
            TextButton(onClick = onCash) { Text("ক্যাশ এন্ট্রি") }
            TextButton(onClick = onStock) { Text("স্টক ঠিক করুন") }
            TextButton(onClick = onDismiss) { Text("আবার স্ক্যান") }
        }
    }
}

@Composable
private fun StockDialog(product: Product, onDismiss: () -> Unit, onDelta: (Double) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("স্টক") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Catalog.stockLabel(product.stock, product.unit))
                TextButton(onClick = { onDelta(1.0) }) { Text("+১") }
                TextButton(onClick = { onDelta(-1.0) }) { Text("−১") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } },
    )
}

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val products: ProductRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var barcode by mutableStateOf(Uri.decode(savedStateHandle.get<String>("barcode").orEmpty()))
    var productId by mutableStateOf(savedStateHandle.get<String>("id").orEmpty())
    var name by mutableStateOf("")
    var buy by mutableStateOf("")
    var sell by mutableStateOf("")
    var stock by mutableStateOf("")
    var unit by mutableStateOf("পিস")
    var photo by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var done by mutableStateOf(false)

    init {
        val id = productId
        if (id.isNotBlank()) {
            viewModelScope.launch {
                val biz = session.active.value ?: return@launch
                val uid = session.user.value?.uid ?: return@launch
                products.observe(uid, biz.id).first().firstOrNull { it.id == id }?.let { fill(it) }
            }
        }
    }

    private fun fill(product: Product) {
        barcode = product.barcode
        name = product.name
        buy = plain(product.buyPrice)
        sell = plain(product.sellPrice)
        stock = plain(product.stock)
        unit = product.unit.ifBlank { "পিস" }
        photo = product.photoBase64
        productId = product.id
    }

    fun onPhoto(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { Images.squareJpegBytes(context, uri, 512, 70) }
            if (bytes.isEmpty()) error = "ছবি পড়া যায়নি। অন্য একটি বেছে নিন।"
            else photo = Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    fun clearPhoto() {
        photo = ""
    }

    fun save() {
        if (name.isBlank()) {
            error = "পণ্যের নাম লিখুন"
            return
        }
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        saving = true
        viewModelScope.launch {
            products.upsert(
                uid,
                biz.id,
                Product(
                    id = productId,
                    barcode = barcode.trim(),
                    name = name.trim(),
                    buyPrice = Money.parse(buy) ?: 0.0,
                    sellPrice = Money.parse(sell) ?: 0.0,
                    stock = Money.parse(stock) ?: 0.0,
                    unit = unit.ifBlank { "পিস" },
                    photoBase64 = photo,
                ),
            ).onSuccess { done = true }
                .onFailure {
                    error = UserFacingError.auth(it.message)
                    saving = false
                }
        }
    }

    private fun plain(n: Double): String = when {
        n == 0.0 -> ""
        n % 1.0 == 0.0 -> n.toLong().toString()
        else -> String.format(Locale.US, "%.2f", n)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormScreen(onBack: () -> Unit, viewModel: ProductFormViewModel = hiltViewModel()) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhoto(uri)
    }
    LaunchedEffect(viewModel.done) { if (viewModel.done) onBack() }
    val title = if (viewModel.productId.isBlank()) "নতুন পণ্য" else "পণ্য বদলান"
    SimpleScaffold(title, onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(viewModel.barcode, { viewModel.barcode = it }, label = { Text("বারকোড") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(viewModel.name, { viewModel.name = it }, label = { Text("নাম") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            AmountField(viewModel.buy, { viewModel.buy = it }, "কেনা দাম")
            AmountField(viewModel.sell, { viewModel.sell = it }, "বিক্রির দাম")
            OutlinedTextField(
                viewModel.stock,
                { raw -> viewModel.stock = raw.filter { it.isDigit() || it == '.' } },
                label = { Text("স্টক") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("একক")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.UNITS.forEach { unit ->
                    FilterChip(selected = viewModel.unit == unit, onClick = { viewModel.unit = unit }, label = { Text(unit) })
                }
            }
            if (viewModel.photo.isNotBlank()) {
                Avatar(viewModel.name.ifBlank { "পণ্য" }, viewModel.photo)
                TextButton(onClick = viewModel::clearPhoto) { Text("ছবি সরান") }
            }
            TextButton(onClick = {
                pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (viewModel.photo.isBlank()) "ছবি যোগ (ঐচ্ছিক)" else "ছবি বদলান") }
            viewModel.error?.let { SoftErrorCard(it, onRetry = null) }
            PrimaryButton("সেভ", viewModel::save, viewModel.name.isNotBlank() && !viewModel.saving)
        }
    }
}

internal object ScanFeedback {
    fun ping(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 250)
        }
    }
}
