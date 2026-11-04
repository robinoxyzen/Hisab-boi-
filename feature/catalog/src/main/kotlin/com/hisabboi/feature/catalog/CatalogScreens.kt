package com.hisabboi.feature.catalog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.LineItem
import com.hisabboi.model.Product
import com.hisabboi.model.ProductRepository
import com.hisabboi.model.ScanBus
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductViewModel @Inject constructor(
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

    var lines by mutableStateOf(listOf<LineItem>())
    var unknownBarcode by mutableStateOf<String?>(null)
    var unknownName by mutableStateOf("")
    var suggestion by mutableStateOf<String?>(null)
    private val seen = MutableStateFlow("")

    fun onBarcode(code: String) {
        if (code.isBlank() || unknownBarcode != null) return
        if (seen.value == code) return
        seen.value = code
        val known = catalog.value.firstOrNull { !it.deleted && it.barcode == code }
        if (known != null) add(LineItem(known.name, code, 1)) else {
            unknownBarcode = code
            unknownName = ""
        }
    }

    fun onText(text: String) {
        if (text.isBlank() || unknownBarcode != null) return
        suggestion = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)
    }

    fun acceptSuggestion() {
        val name = suggestion?.trim().orEmpty()
        if (name.isNotBlank()) add(LineItem(name, null, 1))
        suggestion = null
    }

    fun saveUnknown() {
        val code = unknownBarcode ?: return
        val name = unknownName.trim()
        if (name.isBlank()) return
        val biz = active.value ?: return
        val id = uid.value?.uid ?: return
        viewModelScope.launch {
            products.upsert(id, biz.id, Product(barcode = code, name = name))
            add(LineItem(name, code, 1))
            unknownBarcode = null
            seen.value = ""
        }
    }

    fun dismissUnknown() {
        unknownBarcode = null
        seen.value = ""
    }

    private fun add(item: LineItem) {
        val current = lines.toMutableList()
        val index = current.indexOfFirst { item.barcode != null && it.barcode == item.barcode }
        if (index >= 0) current[index] = current[index].copy(qty = current[index].qty + 1)
        else current += item
        lines = current
        suggestion = null
    }

    fun finish() {
        if (lines.isNotEmpty()) scanBus.publish(lines)
    }

    fun rename(product: Product, name: String) {
        val biz = active.value ?: return
        val id = uid.value?.uid ?: return
        viewModelScope.launch { products.upsert(id, biz.id, product.copy(name = name)) }
    }

    fun remove(product: Product) {
        val biz = active.value ?: return
        val id = uid.value?.uid ?: return
        viewModelScope.launch { products.softDelete(id, biz.id, product.id) }
    }
}

@Composable
fun ScannerScreen(onDone: () -> Unit, viewModel: ProductViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    SimpleScaffold("পণ্য স্ক্যান", onDone) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!granted) {
                EmptyHint("ক্যামেরা দরকার", "পণ্যের বারকোড বা নাম পড়তে ক্যামেরার অনুমতি দিন।")
                PrimaryButton("অনুমতি দিন", { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(16.dp))
            } else {
                CameraPreview(
                    Modifier.fillMaxWidth().weight(1f),
                    onBarcode = viewModel::onBarcode,
                    onText = viewModel::onText,
                )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                viewModel.suggestion?.let { text ->
                    Text("প্যাকেটের লেখা: $text")
                    Row {
                        TextButton(onClick = viewModel::acceptSuggestion) { Text("নাম হিসেবে নিন") }
                        TextButton(onClick = { viewModel.suggestion = null }) { Text("বাদ") }
                    }
                }
                viewModel.lines.forEach { Text("• ${it.name}" + if (it.qty > 1) " × ${it.qty}" else "") }
                PrimaryButton("নোটের সাথে যোগ করুন", { viewModel.finish(); onDone() }, viewModel.lines.isNotEmpty())
            }
        }
    }
    val code = viewModel.unknownBarcode
    if (code != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnknown,
            title = { Text("নতুন বারকোড") },
            text = {
                Column {
                    Text(code)
                    OutlinedTextField(viewModel.unknownName, { viewModel.unknownName = it }, label = { Text("পণ্যের নাম") })
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveUnknown) { Text("সেভ") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUnknown) { Text("বাতিল") } },
        )
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onBarcode: (String) -> Unit, onText: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analyzer = remember { FrameAnalyzer(onBarcode, onText) }
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            analyzer.stop()
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private class FrameAnalyzer(
    private val onBarcode: (String) -> Unit,
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val barcodes = BarcodeScanning.getClient()
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = runCatching {
        val options = com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions.Builder().build()
        TextRecognition.getClient(options)
    }.getOrNull()
    private var busy = false
    private var lastCode = ""
    private var lastCodeAt = 0L
    private var lastTextAt = 0L

    fun stop() {
        barcodes.close()
        latin.close()
        devanagari?.close()
    }

    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (busy || media == null) {
            image.close()
            return
        }
        busy = true
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        barcodes.process(input)
            .addOnSuccessListener { codes ->
                val raw = codes.firstNotNullOfOrNull { it.rawValue }?.trim().orEmpty()
                val now = System.currentTimeMillis()
                if (raw.isNotEmpty()) {
                    if (raw != lastCode || now - lastCodeAt > 2_000) {
                        lastCode = raw
                        lastCodeAt = now
                        onBarcode(raw)
                    }
                    busy = false
                    image.close()
                } else if (now - lastTextAt > 1_500) {
                    lastTextAt = now
                    readText(input, image)
                } else {
                    busy = false
                    image.close()
                }
            }
            .addOnFailureListener {
                busy = false
                image.close()
            }
    }

    private fun readText(input: InputImage, image: ImageProxy) {
        latin.process(input).addOnSuccessListener { latinText ->
            val deva = devanagari
            if (deva == null) {
                emit(latinText)
                busy = false
                image.close()
            } else {
                deva.process(input).addOnCompleteListener { task ->
                    val extra = if (task.isSuccessful) task.result else null
                    emit(latinText, extra)
                    busy = false
                    image.close()
                }
            }
        }.addOnFailureListener {
            busy = false
            image.close()
        }
    }

    private fun emit(first: Text, second: Text? = null) {
        val best = listOfNotNull(prominent(first), second?.let { prominent(it) }).maxByOrNull { it.length }
        if (!best.isNullOrBlank()) onText(best)
    }

    private fun prominent(text: Text): String? {
        val block = text.textBlocks.maxByOrNull { block ->
            val box = block.boundingBox
            if (box == null) block.text.length else box.width() * box.height()
        }
        return block?.text?.trim()?.takeIf { it.isNotEmpty() }
    }
}

@Composable
fun ProductsScreen(onBack: () -> Unit, viewModel: ProductViewModel = hiltViewModel()) {
    val products by viewModel.catalog.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Product?>(null) }
    var name by remember { mutableStateOf("") }
    SimpleScaffold("পণ্য তালিকা", onBack) { padding ->
        val alive = products.filter { !it.deleted }
        if (alive.isEmpty()) {
            EmptyHint("কোনো পণ্য নেই", "স্ক্যান করে বারকোড সেভ করলে পরের বার নাম নিজেই বসবে।")
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(alive, key = { it.id }) { product ->
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = { Text(product.barcode) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { editing = product; name = product.name }) { Text("বদল") }
                                TextButton(onClick = { viewModel.remove(product) }) { Text("মুছুন") }
                            }
                        },
                    )
                }
            }
        }
    }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("নাম") },
            text = { OutlinedTextField(name, { name = it }) },
            confirmButton = {
                TextButton(onClick = { editing?.let { viewModel.rename(it, name) }; editing = null }) { Text("সেভ") }
            },
        )
    }
}
