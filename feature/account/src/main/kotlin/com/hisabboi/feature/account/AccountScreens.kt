package com.hisabboi.feature.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.hisabboi.model.SettingsStore
import com.hisabboi.model.TransactionRepository
import com.hisabboi.model.label
import com.hisabboi.ui.ChipRow
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Files
import com.hisabboi.ui.MoneyText
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    var timeout by mutableStateOf("60")

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

    fun applyLock(mode: LockMode) {
        val digits = pin.filter { it.isDigit() }
        if (mode != LockMode.OFF) {
            if (digits.length !in 4..6) {
                message = "৪ থেকে ৬ সংখ্যার পিন দিন"
                return
            }
            lock.setPin(digits)
        }
        lock.setMode(mode, timeout.toIntOrNull() ?: 60)
        message = "লক সেট হয়েছে"
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
                    headlineContent = { Text(biz.name + if (biz.verified) " · ভেরিফাইড" else "") },
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
            viewModel.message?.let { Text(it) }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onResetPin: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val sms by viewModel.sms.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val lock by viewModel.lockState.collectAsStateWithLifecycle()
    SimpleScaffold("সেটিংস", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("এসএমএস")
            ChipRow(listOf("ফোনের এসএমএস" to (sms == "device"), "ক্লাউড গেটওয়ে" to (sms == "gateway"))) {
                viewModel.setSms(if (it == 0) "device" else "gateway")
            }
            Text("গেটওয়ে কী না থাকলে ফোনের এসএমএস অ্যাপ খুলবে।")
            androidx.compose.foundation.layout.Row {
                Text("নোটিফিকেশন", modifier = Modifier.weight(1f))
                Switch(notifications, viewModel::setNotify)
            }
            Text("অ্যাপ লক")
            OutlinedTextField(viewModel.pin, { viewModel.pin = it }, label = { Text("পিন (৪–৬)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.timeout, { viewModel.timeout = it.filter { ch -> ch.isDigit() } }, label = { Text("ব্যাকগ্রাউন্ড টাইমআউট (সেকেন্ড)") }, modifier = Modifier.fillMaxWidth())
            ChipRow(listOf("বন্ধ" to (lock.mode == LockMode.OFF), "পিন" to (lock.mode == LockMode.PIN), "পিন + আঙুল" to (lock.mode == LockMode.PIN_AND_BIOMETRIC))) {
                viewModel.applyLock(LockMode.entries[it])
            }
            TextButton(onClick = onResetPin) { Text("পিন রিসেট (গুগল দিয়ে)") }
            TextButton(onClick = viewModel::refreshClaim) { Text("ভূমিকা রিফ্রেশ") }
            viewModel.message?.let { Text(it) }
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
            viewModel.message?.let { Text(it) }
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
    val user by viewModel.user.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    SimpleScaffold("প্রোফাইল", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(user?.displayName ?: "দোকানদার")
            Text(user?.email ?: "")
            Text(active?.name ?: "কোনো ব্যবসা নেই")
            Text(active?.ownerName.orEmpty())
            Text(active?.phone.orEmpty())
            active?.let { Text(it.type.label()) }
            if (active?.verified == true) Text("ভেরিফাইড দোকান")
            PrimaryButton("ব্যবসার প্রোফাইল", onEditBusiness)
        }
    }
}
