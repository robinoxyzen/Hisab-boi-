package com.hisabboi.feature.account

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.BackupRepository
import com.hisabboi.model.BnDate
import com.hisabboi.model.Business
import com.hisabboi.model.BusinessType
import com.hisabboi.model.CashRepository
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.Direction
import com.hisabboi.model.LedgerTx
import com.hisabboi.model.LockMode
import com.hisabboi.model.LockStore
import com.hisabboi.model.Money
import com.hisabboi.model.NoticeKind
import com.hisabboi.model.ProfilePhotoRepository
import com.hisabboi.model.SettingsDraft
import com.hisabboi.model.SettingsStore
import com.hisabboi.model.TransactionRepository
import com.hisabboi.model.UserFacingError
import com.hisabboi.model.label
import com.hisabboi.ui.Avatar
import com.hisabboi.ui.ChipRow
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Files
import com.hisabboi.ui.Images
import com.hisabboi.ui.MoneyText
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.SoftErrorCard
import com.hisabboi.ui.showStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import java.nio.charset.Charset
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
    private val auth: AuthGateway,
    private val settings: SettingsStore,
    private val lock: LockStore,
    private val backup: BackupRepository,
    private val contacts: ContactRepository,
    private val transactions: TransactionRepository,
    private val cash: CashRepository,
    private val photos: ProfilePhotoRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val user = session.user
    val businesses = session.businesses
    val active = session.active
    val sms = settings.smsMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "device")
    val notifications = settings.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val lockState = lock.snapshot
    var name by mutableStateOf("")
    var owner by mutableStateOf("")
    var phone by mutableStateOf("")
    var type by mutableStateOf(BusinessType.GROCERY)
    var message by mutableStateOf<String?>(null)
    var pin by mutableStateOf("")
    var confirmPin by mutableStateOf("")
    var timeout by mutableStateOf("60")
    var smsDraft by mutableStateOf("device")
    var notifyDraft by mutableStateOf(true)
    var modeDraft by mutableStateOf(LockMode.OFF)
    var quickDraft by mutableStateOf(false)
    var draftsReady by mutableStateOf(false)
    var photoBusy by mutableStateOf(false)
    var photoNotice by mutableStateOf<String?>(null)
    var photoError by mutableStateOf<String?>(null)
    var pendingPhoto by mutableStateOf<ByteArray?>(null)
    private var savedSms = "device"
    private var savedNotify = true
    private var savedMode = LockMode.OFF
    private var savedTimeout = 60
    private var savedQuick = false
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val savedEvents = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            savedSms = settings.smsMode.first()
            savedNotify = settings.notifications.first()
            val snap = lock.snapshot.value
            savedMode = snap.mode
            savedTimeout = snap.timeoutSec
            savedQuick = snap.quickUnlock
            lock.refreshBiometrics()
            smsDraft = savedSms
            notifyDraft = savedNotify
            modeDraft = savedMode
            quickDraft = savedQuick
            timeout = savedTimeout.toString()
            draftsReady = true
        }
    }

    fun settingsInput(): SettingsDraft.Input = SettingsDraft.Input(
        sms = smsDraft,
        notifications = notifyDraft,
        mode = modeDraft,
        pin = pin,
        confirm = confirmPin,
        timeout = timeout,
        savedSms = savedSms,
        savedNotifications = savedNotify,
        savedMode = savedMode,
        savedTimeout = savedTimeout,
        pinAlreadySet = lock.snapshot.value.pinSet,
    )

    fun stagePhoto(uri: Uri) {
        viewModelScope.launch {
            photoBusy = true
            photoError = null
            val bytes = runCatching {
                withContext(Dispatchers.IO) { Images.squareJpegBytes(appContext, uri, maxEdge = 512, quality = 72) }
            }.getOrDefault(ByteArray(0))
            photoBusy = false
            if (bytes.isEmpty()) photoError = "ছবি পড়া যায়নি। অন্য একটি বেছে নিন।"
            else pendingPhoto = bytes
        }
    }

    fun discardPending() {
        pendingPhoto = null
    }

    fun confirmPhoto() {
        val bytes = pendingPhoto ?: return
        val uid = session.user.value?.uid ?: return
        pendingPhoto = null
        viewModelScope.launch {
            photoBusy = true
            photoError = null
            photos.upload(uid, bytes)
                .onSuccess { photoNotice = "ছবি সেভ হয়েছে"; photoError = null }
                .onFailure { photoError = UserFacingError.auth(it.message) }
            photoBusy = false
        }
    }

    fun removePhoto() {
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            photoBusy = true
            photoError = null
            photos.remove(uid)
                .onSuccess { photoNotice = "ছবি সরানো হয়েছে"; photoError = null }
                .onFailure { photoError = UserFacingError.auth(it.message) }
            photoBusy = false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val statement = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else transactions.observeAll(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactTrash = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else contacts.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cashTrash = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else cash.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: String) { viewModelScope.launch { session.select(id) } }

    fun create(onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            session.createBusiness(Business(name = name.trim(), ownerName = owner.trim(), phone = phone.trim(), type = type))
                .onSuccess { onDone() }
                .onFailure { message = it.message }
        }
    }

    fun setSms(mode: String) { viewModelScope.launch { settings.setSmsMode(mode) } }
    fun setNotify(enabled: Boolean) { viewModelScope.launch { settings.setNotifications(enabled) } }

    fun saveSettings() {
        val input = settingsInput()
        val problem = SettingsDraft.validate(input)
        if (problem != null) {
            message = problem
            return
        }
        viewModelScope.launch {
            runCatching {
                settings.setSmsMode(input.sms)
                settings.setNotifications(input.notifications)
                val digits = input.pin.filter { it.isDigit() }
                if (input.mode == LockMode.PIN_AND_BIOMETRIC && !lock.snapshot.value.biometricHardware) {
                    error("এই ফোনে আঙুলের ছাপ সমর্থিত নয়।")
                }
                if (input.mode != LockMode.OFF && digits.isNotBlank()) lock.setPin(digits)
                lock.setMode(input.mode, input.timeout.toInt())
                lock.setQuickUnlock(quickDraft)
            }.onSuccess {
                savedSms = input.sms
                savedNotify = input.notifications
                savedMode = input.mode
                savedTimeout = input.timeout.toInt()
                savedQuick = quickDraft
                pin = ""
                confirmPin = ""
                message = null
                _saved.tryEmit("সেভ হয়েছে")
            }.onFailure { message = UserFacingError.auth(it.message) }
        }
    }

    fun refreshClaim() {
        viewModelScope.launch {
            auth.refreshAdminClaim().onSuccess { admin ->
                message = if (admin) "অ্যাডমিন ক্লেইম সক্রিয়" else "অ্যাডমিন ক্লেইম নেই"
            }.onFailure { message = "ক্লাউড ফাংশন এখনো ডিপ্লয় হয়নি" }
        }
    }

    fun export(onText: (String) -> Unit) {
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            backup.exportJson(uid).onSuccess { onText(it) }.onFailure { message = it.message }
        }
    }

    fun restore(json: String) {
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            backup.restoreJson(uid, json).onSuccess { message = "$it টি ব্যবসা ফিরে এসেছে। ভেরিফাইড ব্যাজ রিস্টোর হয় না।" }
                .onFailure { message = it.message }
        }
    }

    fun restoreContact(id: String) = mutate { uid, bid -> contacts.restore(uid, bid, id) }
    fun restoreTx(tx: LedgerTx) = mutate { uid, bid -> transactions.restore(uid, bid, tx) }
    fun restoreCash(id: String) = mutate { uid, bid ->
        val entry = cashTrash.value.firstOrNull { it.id == id } ?: return@mutate Result.failure(IllegalStateException("নেই"))
        cash.restore(uid, bid, entry)
    }
    fun restoreBusiness(id: String) { viewModelScope.launch { session.restoreBusiness(id) } }
    fun deleteBusiness(id: String) { viewModelScope.launch { session.softDeleteBusiness(id) } }

    private fun mutate(block: suspend (String, String) -> Result<Unit>) {
        val uid = session.user.value?.uid ?: return
        val bid = session.active.value?.id ?: return
        viewModelScope.launch { block(uid, bid) }
    }

    suspend fun reportLines(): List<String> {
        val rows = statement.value.filter { !it.deleted }
        val alive = contactTrash.value.filter { !it.deleted }
        val (receive, pay) = com.hisabboi.model.BalanceMath.totals(alive.map { it.balance })
        return buildList {
            add(active.value?.name ?: "হিসাব বই")
            add("পাবো ${Money.format(receive)}  দেবো ${Money.format(pay)}")
            rows.take(200).forEach { tx ->
                add("${BnDate.format(tx.dateMillis)} ${tx.contactName} ${tx.direction.label()} ${Money.format(tx.amount)}")
            }
        }
    }
}

@Composable
fun BusinessListScreen(onBack: () -> Unit, onCreate: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val list by viewModel.businesses.collectAsStateWithLifecycle()
    SimpleScaffold("অ্যাকাউন্ট", onBack) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(list.filter { !it.deleted }, key = { it.id }) { biz ->
                ListItem(
                    headlineContent = { com.hisabboi.ui.NameWithSeal(biz.name, biz.verified) },
                    supportingContent = { Text(biz.type.label()) },
                    modifier = Modifier.clickable { viewModel.select(biz.id) },
                    trailingContent = { TextButton(onClick = { viewModel.deleteBusiness(biz.id) }) { Text("মুছুন") } },
                )
            }
            item { PrimaryButton("নতুন ব্যবসা", onCreate, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
fun CreateBusinessScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    SimpleScaffold("নতুন ব্যবসা", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(viewModel.name, { viewModel.name = it }, label = { Text("ব্যবসার নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.owner, { viewModel.owner = it }, label = { Text("মালিকের নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.phone, { viewModel.phone = it }, label = { Text("ফোন") }, modifier = Modifier.fillMaxWidth())
            ChipRow(BusinessType.entries.map { it.label() to (viewModel.type == it) }) { viewModel.type = BusinessType.entries[it] }
            PrimaryButton("শুরু", { viewModel.create(onBack) }, viewModel.name.isNotBlank())
            com.hisabboi.ui.ResultBanner(viewModel.message)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onResetPin: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val lock by viewModel.lockState.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    var showPin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { text ->
            snackbar.showStatus(NoticeKind.SUCCESS, text)
        }
    }
    val context = LocalContext.current
    val input = viewModel.settingsInput()
    val hardware = lock.biometricHardware
    val dirty = viewModel.draftsReady && (SettingsDraft.dirty(input) || viewModel.quickDraft != lock.quickUnlock)
    fun confirmBiometric(onOk: () -> Unit) {
        com.hisabboi.security.AppBiometric.authenticate(
            context,
            title = "আঙুলের ছাপ যাচাই",
            subtitle = "চালু করার আগে একবার ছাপ দিন",
            onSuccess = onOk,
            onError = { viewModel.message = it },
        )
    }
    androidx.compose.material3.Scaffold(
        snackbarHost = { com.hisabboi.ui.StatusSnackbarHost(snackbar) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("সেটিংস") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.hisabboi.ui.SectionCard("প্রোফাইল") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(user?.displayName ?: "দোকানদার", user?.photoUrl.orEmpty(), Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.displayName ?: "দোকানদার")
                        Text("গ্যালারি বা ক্যামেরা থেকে ছবি বদলান", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onOpenProfile) { Text("ছবি বদলান") }
            }
            com.hisabboi.ui.SectionCard("এসএমএস") {
                ChipRow(listOf("ফোনের এসএমএস" to (viewModel.smsDraft == "device"), "ক্লাউড গেটওয়ে" to (viewModel.smsDraft == "gateway"))) {
                    viewModel.smsDraft = if (it == 0) "device" else "gateway"
                }
                Text("গেটওয়ে কী না থাকলে ফোনের এসএমএস অ্যাপ খুলবে।")
            }
            com.hisabboi.ui.SectionCard("নোটিফিকেশন") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("নোটিফিকেশন", modifier = Modifier.weight(1f))
                    Switch(viewModel.notifyDraft, { viewModel.notifyDraft = it })
                }
            }
            com.hisabboi.ui.SectionCard("অ্যাপ লক") {
                val choices = buildList {
                    add(LockMode.OFF to "বন্ধ")
                    add(LockMode.PIN to "পিন")
                    if (hardware) add(LockMode.PIN_AND_BIOMETRIC to "পিন + আঙুল")
                }
                ChipRow(choices.map { (mode, label) -> label to (viewModel.modeDraft == mode) }) { index ->
                    val picked = choices[index].first
                    if (picked == LockMode.PIN_AND_BIOMETRIC && viewModel.modeDraft != LockMode.PIN_AND_BIOMETRIC) {
                        confirmBiometric { viewModel.modeDraft = picked }
                    } else {
                        viewModel.modeDraft = picked
                    }
                }
                if (hardware) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("আঙুলের ছাপ দিয়ে লগইন", modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.quickDraft,
                            onCheckedChange = { enabled ->
                                if (enabled) confirmBiometric { viewModel.quickDraft = true }
                                else viewModel.quickDraft = false
                            },
                        )
                    }
                    Text("গুগল সেশন থাকে। অ্যাপ খুললে শুধু আঙুল চায়।")
                }
                if (hardware && !lock.biometricAvailable) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(com.hisabboi.security.AppBiometric.enrollIntent()) }
                    }) { Text("সেটিংসে আঙুলের ছাপ যোগ করুন") }
                }
                if (viewModel.modeDraft != LockMode.OFF) {
                    OutlinedTextField(
                        viewModel.pin,
                        { viewModel.pin = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text(if (lock.pinSet) "নতুন পিন (৪–৬)" else "পিন (৪–৬)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        trailingIcon = {
                            TextButton(onClick = { showPin = !showPin }) { Text(if (showPin) "লুকান" else "দেখুন") }
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        viewModel.confirmPin,
                        { viewModel.confirmPin = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("পিন নিশ্চিত করুন") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showConfirm) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        trailingIcon = {
                            TextButton(onClick = { showConfirm = !showConfirm }) { Text(if (showConfirm) "লুকান" else "দেখুন") }
                        },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    viewModel.timeout,
                    { viewModel.timeout = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("ব্যাকগ্রাউন্ড টাইমআউট (সেকেন্ড)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    supportingText = { Text("${SettingsDraft.TIMEOUT_MIN}–${SettingsDraft.TIMEOUT_MAX}") },
                    singleLine = true,
                )
            }
            com.hisabboi.ui.SectionCard("অ্যাকাউন্ট") {
                TextButton(onClick = onResetPin) { Text("পিন রিসেট (গুগল দিয়ে)") }
                TextButton(onClick = viewModel::refreshClaim) { Text("ভূমিকা রিফ্রেশ") }
            }
            com.hisabboi.ui.ResultBanner(viewModel.message)
            PrimaryButton("সেভ করুন", viewModel::saveSettings, dirty)
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export { json -> Files.write(context, uri, json.toByteArray()) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charset.forName("UTF-8")) }.orEmpty()
            viewModel.restore(text)
        }
    }
    SimpleScaffold("ব্যাকআপ", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("JSON ব্যাকআপ এই ফোনে ডাউনলোড হয়। ফিরিয়ে আনলে ভেরিফাইড ব্যাজ আবার দেওয়া হয় না।")
            PrimaryButton("JSON সেভ", { save.launch("hisab-boi-backup.json") })
            TextButton(onClick = { open.launch(arrayOf("application/json", "text/*")) }) { Text("JSON থেকে ফিরিয়ে আনুন") }
            com.hisabboi.ui.ResultBanner(viewModel.message)
        }
    }
}

@Composable
fun StatementScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val rows by viewModel.statement.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SimpleScaffold("লেনদেন বিবরণী", onBack) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                TextButton(onClick = {
                    viewModel.viewModelScope.launch {
                        val bytes = Files.pdf(context, "বিবরণী", viewModel.reportLines())
                        Files.share(context, "statement.pdf", bytes, "application/pdf")
                    }
                }) { Text("পিডিএফ") }
                TextButton(onClick = {
                    viewModel.viewModelScope.launch {
                        val lines = viewModel.reportLines().map { listOf(it) }
                        Files.share(context, "statement.csv", Files.csv(lines).toByteArray(), "text/csv")
                    }
                }) { Text("সিএসভি") }
            }
            items(rows.filter { !it.deleted }, key = { it.id }) { tx ->
                ListItem(
                    headlineContent = { Text("${tx.contactName} · ${tx.direction.label()}") },
                    supportingContent = { Text("${BnDate.format(tx.dateMillis)} ${tx.description}") },
                    trailingContent = { MoneyText(tx.amount, tx.direction == Direction.RECEIVED) },
                )
            }
        }
    }
}

@Composable
fun ReportsScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    SimpleScaffold("রিপোর্ট", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            PrimaryButton("খাতার সারাংশ শেয়ার", {
                viewModel.viewModelScope.launch {
                    Files.share(context, "report.pdf", Files.pdf(context, "রিপোর্ট", viewModel.reportLines()), "application/pdf")
                }
            })
        }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val contacts by viewModel.contactTrash.collectAsStateWithLifecycle()
    val txs by viewModel.statement.collectAsStateWithLifecycle()
    val cash by viewModel.cashTrash.collectAsStateWithLifecycle()
    val businesses by viewModel.businesses.collectAsStateWithLifecycle()
    SimpleScaffold("মুছে ফেলা", onBack) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Text("ব্যবসা", modifier = Modifier.padding(16.dp)) }
            items(businesses.filter { it.deleted }, key = { it.id }) { biz ->
                ListItem(headlineContent = { Text(biz.name) }, trailingContent = { TextButton(onClick = { viewModel.restoreBusiness(biz.id) }) { Text("ফেরত") } })
            }
            item { Text("পরিচিতি", modifier = Modifier.padding(16.dp)) }
            items(contacts.filter { it.deleted }, key = { it.id }) { c ->
                ListItem(headlineContent = { Text(c.name) }, trailingContent = { TextButton(onClick = { viewModel.restoreContact(c.id) }) { Text("ফেরত") } })
            }
            item { Text("লেনদেন", modifier = Modifier.padding(16.dp)) }
            items(txs.filter { it.deleted }, key = { it.id }) { tx ->
                ListItem(headlineContent = { Text(tx.contactName) }, trailingContent = { TextButton(onClick = { viewModel.restoreTx(tx) }) { Text("ফেরত") } })
            }
            item { Text("ক্যাশ", modifier = Modifier.padding(16.dp)) }
            items(cash.filter { it.deleted }, key = { it.id }) { entry ->
                ListItem(headlineContent = { Text(entry.kind.label()) }, trailingContent = { TextButton(onClick = { viewModel.restoreCash(entry.id) }) { Text("ফেরত") } })
            }
            if (contacts.none { it.deleted } && txs.none { it.deleted }) {
                item { EmptyHint("খালি", "মুছে ফেলা জিনিস এখানে ফেরত আনা যায়।") }
            }
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onEditBusiness: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.stagePhoto(uri)
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri.value
        if (ok && uri != null) viewModel.stagePhoto(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = Files.cameraCaptureUri(context)
            cameraUri.value = uri
            take.launch(uri)
        } else {
            viewModel.photoError = "ক্যামেরার অনুমতি নেই। গ্যালারি থেকে বেছে নিন।"
        }
    }
    val name = user?.displayName?.ifBlank { null } ?: "দোকানদার"
    SimpleScaffold("প্রোফাইল", onBack) { padding ->
        ProfilePhotoPane(
            name = name,
            email = user?.email.orEmpty(),
            photo = user?.photoUrl.orEmpty(),
            busy = viewModel.photoBusy,
            error = viewModel.photoError,
            notice = viewModel.photoNotice,
            businessName = active?.name ?: "কোনো ব্যবসা নেই",
            ownerName = active?.ownerName.orEmpty(),
            phone = active?.phone.orEmpty(),
            typeLabel = active?.type?.label().orEmpty(),
            verified = active?.verified == true,
            onGallery = {
                pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
            onRemove = viewModel::removePhoto,
            onEditBusiness = onEditBusiness,
            modifier = Modifier.padding(padding),
        )
    }
    viewModel.pendingPhoto?.let { bytes ->
        val image = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        AlertDialog(
            onDismissRequest = viewModel::discardPending,
            title = { Text("প্রোফাইল ছবি") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "প্রোফাইল",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(160.dp).clip(CircleShape),
                        )
                    }
                    Text("ছবিটি বর্গাকার কেটে বৃত্তে দেখানো হবে।")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmPhoto) { Text("ব্যবহার করুন") } },
            dismissButton = { TextButton(onClick = viewModel::discardPending) { Text("বাতিল") } },
        )
    }
}

@Composable
fun ProfilePhotoPane(
    name: String,
    email: String,
    photo: String,
            busy: Boolean,
            error: String?,
            notice: String? = null,
    businessName: String,
    ownerName: String,
    phone: String,
    typeLabel: String,
    verified: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit,
    onEditBusiness: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(name, photo, Modifier.size(96.dp))
            if (busy) CircularProgressIndicator()
        }
        com.hisabboi.ui.NameWithSeal(name, verified)
        Text(email)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onGallery, enabled = !busy) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Text("গ্যালারি")
            }
            TextButton(onClick = onCamera, enabled = !busy) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("ক্যামেরা")
            }
        }
        if (photo.isNotBlank()) {
            TextButton(onClick = onRemove, enabled = !busy) { Text("ছবি সরান") }
        }
        if (busy) com.hisabboi.ui.StatusBanner(NoticeKind.PENDING, "ছবি আপলোড হচ্ছে…")
        notice?.let { com.hisabboi.ui.ResultBanner(it) }
        error?.let { SoftErrorCard(UserFacingError.auth(it), onRetry = null) }
        Text(businessName)
        if (ownerName.isNotBlank()) Text(ownerName)
        if (phone.isNotBlank()) Text(phone)
        if (typeLabel.isNotBlank()) Text(typeLabel)
        PrimaryButton("ব্যবসার প্রোফাইল", onEditBusiness)
    }
}
