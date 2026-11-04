package com.hisabboi.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.BalanceMath
import com.hisabboi.model.BnDate
import com.hisabboi.model.Business
import com.hisabboi.model.Contact
import com.hisabboi.model.ContactKind
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.Direction
import com.hisabboi.model.LedgerTx
import com.hisabboi.model.LineItem
import com.hisabboi.model.LocalNotifier
import com.hisabboi.model.Money
import com.hisabboi.model.ScanBus
import com.hisabboi.model.SettingsStore
import com.hisabboi.model.Templates
import com.hisabboi.model.TransactionRepository
import com.hisabboi.model.TxSource
import com.hisabboi.model.asNote
import com.hisabboi.model.label
import com.hisabboi.ui.AmountField
import com.hisabboi.ui.Avatar
import com.hisabboi.ui.ChipRow
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Files
import com.hisabboi.ui.MoneyText
import com.hisabboi.ui.NoteField
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.StatTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerUi(
    val title: String = "",
    val offline: Boolean = false,
    val receive: Double = 0.0,
    val pay: Double = 0.0,
    val query: String = "",
    val filter: Int = 0,
    val rows: List<Contact> = emptyList(),
    val businesses: List<Business> = emptyList(),
    val lastTx: Map<String, Long> = emptyMap(),
    val verified: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
    contacts: ContactRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(0)
    private val contactFlow = session.active.flatMapLatest { biz ->
        if (biz == null) flowOf(emptyList()) else contacts.observe(session.user.value?.uid.orEmpty(), biz.id)
    }
    private val txFlow = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid.orEmpty()
        if (biz == null || uid.isBlank()) flowOf(emptyList()) else transactions.observeAll(uid, biz.id)
    }
    private val header = combine(session.active, session.businesses, session.offline) { biz, allBiz, offline ->
        Triple(biz, allBiz, offline)
    }
    val state = combine(header, contactFlow, txFlow, query, filter) { (biz, allBiz, offline), contacts, txs, q, f ->
        val all = contacts.filter { !it.deleted }
        val filtered = all.filter { c ->
            val kindOk = when (f) {
                1 -> c.kind != ContactKind.SUPPLIER
                2 -> c.kind != ContactKind.CUSTOMER
                3 -> c.balance > 0
                4 -> c.balance < 0
                else -> true
            }
            val text = q.trim()
            kindOk && (text.isEmpty() || c.name.contains(text, true) || c.phone.contains(text))
        }
        val (receive, pay) = BalanceMath.totals(all.map { it.balance })
        LedgerUi(
            biz?.name ?: "হিসাব বই",
            offline,
            receive,
            pay,
            q,
            f,
            filtered,
            allBiz.filter { !it.deleted },
            com.hisabboi.model.LedgerList.lastDates(txs),
            verified = biz?.verified == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUi())

    fun search(value: String) { query.value = value }
    fun filter(index: Int) { filter.value = index }
    fun selectBusiness(id: String) { viewModelScope.launch { session.select(id) } }

    fun exportLines(): List<String> {
        val ui = state.value
        return buildList {
            add(ui.title)
            add("মোট পাবো ${Money.format(ui.receive)}")
            add("মোট দেবো ${Money.format(ui.pay)}")
            ui.rows.forEach { add("${it.name}  ${it.phone}  ${Money.format(it.balance)}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateSafe()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = com.hisabboi.ui.Teal, shadowElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                state.title.ifBlank { "খাতা" },
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                modifier = Modifier.weight(1f, fill = false).clickable { sheet = true },
                                maxLines = 1,
                            )
                            if (state.verified) {
                                Spacer(Modifier.width(4.dp))
                                com.hisabboi.ui.VerifiedSeal()
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        com.hisabboi.ui.StatusBadge(
                            if (state.offline) com.hisabboi.model.NoticeKind.PENDING else com.hisabboi.model.NoticeKind.SUCCESS,
                            if (state.offline) "অফলাইনে সংরক্ষিত" else "সিঙ্ক হয়েছে",
                        )
                    }
                    IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "পণ্য স্ক্যান", tint = androidx.compose.ui.graphics.Color.White) }
                    IconButton(onClick = {
                        val bytes = Files.pdf(context, "খাতা", viewModel.exportLines())
                        Files.share(context, "khata.pdf", bytes, "application/pdf")
                    }) { Icon(Icons.Default.FileDownload, contentDescription = "রিপোর্ট", tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("কাস্টমার যোগ")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.hisabboi.ui.MoneySummaryCard("মোট পাবো", state.receive, true, Modifier.weight(1f))
                com.hisabboi.ui.MoneySummaryCard("মোট দেবো", state.pay, false, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                label = { Text("খুঁজুন") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("সব", "কাস্টমার", "সাপ্লায়ার", "পাবো", "দেবো").forEachIndexed { index, label ->
                    androidx.compose.material3.FilterChip(
                        selected = state.filter == index,
                        onClick = { viewModel.filter(index) },
                        label = { Text(label) },
                    )
                }
            }
            if (state.rows.isEmpty()) {
                com.hisabboi.ui.LedgerEmptyState(onAdd)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.rows, key = { it.id }) { contact ->
                        val last = state.lastTx[contact.id] ?: 0L
                        com.hisabboi.ui.ContactAmountRow(
                            name = contact.name,
                            phone = contact.phone,
                            photo = contact.photoBase64,
                            lastLabel = if (last == 0L) "কোনো লেনদেন নেই" else BnDate.format(last),
                            amount = contact.balance,
                            verified = contact.kycVerified,
                            onClick = { onOpen(contact.id) },
                        )
                    }
                }
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ব্যবসা বদলান", style = MaterialTheme.typography.titleMedium)
                state.businesses.forEach { biz ->
                    TextButton(onClick = { viewModel.selectBusiness(biz.id); sheet = false }) { Text(biz.name) }
                }
            }
        }
    }
}

data class ContactUi(
    val contact: Contact? = null,
    val rows: List<Pair<LedgerTx, Double>> = emptyList(),
    val computed: Double = 0.0,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val contacts: ContactRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {
    private val contactId: String = savedStateHandle.get<String>("contactId").orEmpty()
    val state = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid.orEmpty()
        if (biz == null) flowOf(ContactUi()) else {
            combine(contacts.observe(uid, biz.id), transactions.observeForContact(uid, biz.id, contactId)) { list, txs ->
                val contact = list.firstOrNull { it.id == contactId }
                val visible = txs.filter { !it.deleted }.sortedBy { it.dateMillis }
                var running = 0.0
                val rows = visible.map { tx ->
                    running += BalanceMath.delta(tx.direction, tx.amount)
                    tx to running
                }.asReversed()
                ContactUi(contact, rows, running)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactUi())

    fun reconcile() {
        val ui = state.value
        val contact = ui.contact ?: return
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch { contacts.setBalance(uid, biz.id, contact.id, ui.computed) }
    }

    fun remove(tx: LedgerTx) {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch { transactions.softDelete(uid, biz.id, tx) }
    }

    fun removeContact() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch { contacts.softDelete(uid, biz.id, contactId) }
    }

    fun reportLines(): List<String> {
        val ui = state.value
        val name = ui.contact?.name ?: "পরিচিতি"
        return buildList {
            add(name)
            add("ব্যালেন্স ${Money.format(ui.computed)}")
            ui.rows.forEach { (tx, bal) ->
                add("${BnDate.format(tx.dateMillis)}  ${tx.direction.label()}  ${Money.format(tx.amount)}  ${tx.description}  ব্যালেন্স ${Money.format(bal)}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onEntry: (String, String) -> Unit,
    onKyc: (String) -> Unit,
    onRemind: (String) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateSafe()
    val contact = state.contact
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val balance = contact?.balance ?: state.computed
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    com.hisabboi.ui.NameWithSeal(
                        contact?.name ?: "পরিচিতি",
                        verified = contact?.kycVerified == true,
                        message = "এই কাস্টমার যাচাইকৃত",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "আরও") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("সম্পাদনা") }, onClick = { menu = false; contact?.let { onEdit(it.id) } })
                        DropdownMenuItem(text = { Text("কেওয়াইসি") }, onClick = { menu = false; contact?.let { onKyc(it.id) } })
                        DropdownMenuItem(text = { Text("মিলান") }, onClick = { menu = false; viewModel.reconcile() })
                        DropdownMenuItem(text = { Text("মুছুন") }, onClick = { menu = false; viewModel.removeContact() })
                    }
                },
            )
        },
        bottomBar = {
            if (contact != null) {
                com.hisabboi.ui.DualActionBar(
                    onGave = { onEntry(contact.id, "gave") },
                    onReceived = { onEntry(contact.id, "received") },
                    modifier = Modifier.padding(16.dp).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (contact == null) {
                item { EmptyHint("খুঁজে পাওয়া যায়নি", "এই পরিচিতি মুছে ফেলা হয়েছে বা এখনো সিঙ্ক হয়নি।") }
            } else {
                item {
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(contact.name, contact.photoBase64)
                                Column {
                                    Text(contact.phone.ifBlank { "ফোন নেই" }, style = MaterialTheme.typography.bodyMedium)
                                    if (contact.kycVerified) com.hisabboi.ui.StatusBadge(com.hisabboi.model.NoticeKind.SUCCESS, "যাচাইকৃত")
                                }
                            }
                            Text(if (balance >= 0) "পাবো" else "দেবো", color = if (balance >= 0) com.hisabboi.ui.Receive else com.hisabboi.ui.Payable)
                            Text(
                                Money.format(kotlin.math.abs(balance)),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = if (balance >= 0) com.hisabboi.ui.Receive else com.hisabboi.ui.Payable,
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        QuickAction(Icons.Default.Call, "কল") {
                            if (contact.phone.isNotBlank()) {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${contact.phone}")))
                            }
                        }
                        QuickAction(Icons.AutoMirrored.Filled.Message, "তাগাদা") { onRemind(contact.id) }
                        QuickAction(Icons.Default.Chat, "হোয়াটসঅ্যাপ") {
                            if (contact.phone.isNotBlank()) Files.whatsApp(context, contact.phone, com.hisabboi.model.Templates.reminder("", kotlin.math.abs(contact.balance)))
                        }
                        QuickAction(Icons.Default.Description, "রিপোর্ট") {
                            val bytes = Files.pdf(context, contact.name, viewModel.reportLines())
                            Files.share(context, "contact.pdf", bytes, "application/pdf")
                        }
                    }
                }
            }
            items(state.rows.size) { index ->
                val (tx, _) = state.rows[index]
                val day = BnDate.format(tx.dateMillis)
                val showDay = index == 0 || BnDate.format(state.rows[index - 1].first.dateMillis) != day
                Column {
                    if (showDay) {
                        Text(day, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    ListItem(
                        headlineContent = { Text(tx.direction.label()) },
                        supportingContent = { Text(tx.description.ifBlank { BnDate.formatTime(tx.dateMillis) }) },
                        trailingContent = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                MoneyText(tx.amount, tx.direction == Direction.RECEIVED)
                                TextButton(onClick = { viewModel.remove(tx) }) { Text("মুছুন") }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = com.hisabboi.ui.Teal) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val contacts: ContactRepository,
) : ViewModel() {
    private val contactId: String = savedStateHandle.get<String>("contactId").orEmpty()
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var kind by mutableStateOf(ContactKind.CUSTOMER)
    var address by mutableStateOf("")
    var reference by mutableStateOf("")
    var referencePhone by mutableStateOf("")
    var limit by mutableStateOf("")
    var photo by mutableStateOf("")
    var saved by mutableStateOf(false)
        private set

    init {
        if (contactId.isNotBlank() && contactId != "new") {
            viewModelScope.launch {
                val biz = session.active.value ?: return@launch
                val uid = session.user.value?.uid ?: return@launch
                contacts.observe(uid, biz.id).first().firstOrNull { it.id == contactId }?.let { c ->
                    name = c.name
                    phone = c.phone
                    kind = c.kind
                    address = c.address
                    reference = c.referenceName
                    referencePhone = c.referencePhone
                    limit = c.creditLimit?.let { Money.format(it, false) } ?: ""
                    photo = c.photoBase64
                }
            }
        }
    }

    fun save() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            contacts.upsert(
                uid,
                biz.id,
                Contact(
                    id = if (contactId == "new") "" else contactId,
                    name = name.trim(),
                    phone = phone.trim(),
                    kind = kind,
                    address = address,
                    referenceName = reference,
                    referencePhone = referencePhone,
                    creditLimit = Money.parse(limit),
                    photoBase64 = photo,
                ),
            )
            saved = true
        }
    }
}

@Composable
fun ContactEditScreen(onBack: () -> Unit, viewModel: ContactEditViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.photo = com.hisabboi.ui.Images.jpeg(context, uri, 512, 60) }
    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onBack() }
    SimpleScaffold(if (viewModel.name.isBlank()) "নতুন পরিচিতি" else "সম্পাদনা", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(viewModel.name, { viewModel.name = it }, label = { Text("নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.phone, { viewModel.phone = it }, label = { Text("ফোন") }, modifier = Modifier.fillMaxWidth())
            ChipRow(
                listOf(
                    "কাস্টমার" to (viewModel.kind == ContactKind.CUSTOMER),
                    "সাপ্লায়ার" to (viewModel.kind == ContactKind.SUPPLIER),
                    "উভয়" to (viewModel.kind == ContactKind.BOTH),
                ),
            ) { index -> viewModel.kind = ContactKind.entries[index] }
            OutlinedTextField(viewModel.address, { viewModel.address = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.reference, { viewModel.reference = it }, label = { Text("রেফারেন্স") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.referencePhone, { viewModel.referencePhone = it }, label = { Text("রেফারেন্স ফোন") }, modifier = Modifier.fillMaxWidth())
            AmountField(viewModel.limit, { viewModel.limit = it }, "ক্রেডিট সীমা (ঐচ্ছিক)")
            TextButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("ছবি")
            }
            PrimaryButton("সেভ", viewModel::save, viewModel.name.isNotBlank())
        }
    }
}

sealed interface EntryEffect {
    data class Sms(val phone: String, val body: String) : EntryEffect
    data class Done(val message: String) : EntryEffect
}

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val contacts: ContactRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsStore,
    private val notifier: LocalNotifier,
    private val scanBus: ScanBus,
) : ViewModel() {
    val contactId: String = savedStateHandle.get<String>("contactId").orEmpty()
    val direction: Direction = if (savedStateHandle.get<String>("direction") == "received") Direction.RECEIVED else Direction.GAVE
    var amount by mutableStateOf("")
    var note by mutableStateOf("")
    var date by mutableStateOf(System.currentTimeMillis())
    var sms by mutableStateOf(true)
    var photo by mutableStateOf("")
    var contact by mutableStateOf<Contact?>(null)
    var warnCredit by mutableStateOf(false)
    private val items = mutableListOf<LineItem>()
    private val _effects = MutableSharedFlow<EntryEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val biz = session.active.value ?: return@launch
            val uid = session.user.value?.uid ?: return@launch
            contact = contacts.observe(uid, biz.id).first().firstOrNull { it.id == contactId }
        }
        viewModelScope.launch {
            scanBus.payload.collect { payload ->
                if (payload == null) return@collect
                payload.items.forEach { incoming ->
                    val index = items.indexOfFirst { incoming.barcode?.isNotBlank() == true && it.barcode == incoming.barcode }
                    if (index >= 0) items[index] = items[index].copy(qty = items[index].qty + incoming.qty)
                    else items += incoming
                }
                note = items.asNote()
                scanBus.consume(payload.id)
            }
        }
    }

    fun requestSave() {
        val parsed = Money.parse(amount) ?: return
        val c = contact
        if (c != null && BalanceMath.exceedsCredit(c.balance, direction, parsed, c.creditLimit)) {
            warnCredit = true
        } else save()
    }

    fun save() {
        warnCredit = false
        val parsed = Money.parse(amount) ?: return
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        val c = contact ?: return
        viewModelScope.launch {
            val mode = settings.smsMode.first()
            val useGateway = sms && mode == "gateway"
            val tx = LedgerTx(
                contactId = c.id,
                contactName = c.name,
                direction = direction,
                amount = parsed,
                description = note,
                items = items.toList(),
                dateMillis = date,
                photoBase64 = photo,
                smsRequested = useGateway,
                source = TxSource.MANUAL,
            )
            val saved = transactions.add(uid, biz.id, tx).getOrElse {
                _effects.tryEmit(EntryEffect.Done(it.message ?: "সেভ হয়নি"))
                return@launch
            }
            val balance = c.balance + BalanceMath.delta(direction, parsed)
            if (direction == Direction.RECEIVED && settings.notifications.first()) {
                notifier.show("পেমেন্ট সফল", "${Money.format(parsed)} · ${c.name}", saved.id)
            }
            if (sms && c.phone.isNotBlank() && !useGateway) {
                _effects.tryEmit(EntryEffect.Sms(c.phone, Templates.transaction(biz.name, direction, parsed, balance)))
            }
            _effects.tryEmit(EntryEffect.Done("সেভ হয়েছে"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(onBack: () -> Unit, onScan: () -> Unit, viewModel: EntryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val photoPick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.photo = com.hisabboi.ui.Images.jpeg(context, uri, 1024, 65) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EntryEffect.Sms -> runCatching { Files.sms(context, effect.phone, effect.body) }
                is EntryEffect.Done -> if (effect.message == "সেভ হয়েছে") onBack() else problem = effect.message
            }
        }
    }
    val title = if (viewModel.direction == Direction.RECEIVED) "টাকা পেলাম" else "টাকা দিলাম"
    SimpleScaffold(title, onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(viewModel.contact?.name ?: "", style = MaterialTheme.typography.titleMedium)
            val parsed = com.hisabboi.model.AmountKeypad.evaluate(viewModel.amount)
            Text(
                parsed?.let { Money.format(it) } ?: "৳০",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (viewModel.direction == Direction.RECEIVED) com.hisabboi.ui.Receive else com.hisabboi.ui.Payable,
            )
            Text(viewModel.amount.ifBlank { "0" }, style = MaterialTheme.typography.bodySmall)
            com.hisabboi.ui.AmountKeypad(onKey = { key ->
                viewModel.amount = com.hisabboi.model.AmountKeypad.push(viewModel.amount, key)
            })
            NoteField(viewModel.note, { viewModel.note = it }, onScan)
            TextButton(onClick = { showDate = true }) { Text("তারিখ: ${BnDate.format(viewModel.date)}") }
            TextButton(onClick = {
                photoPick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (viewModel.photo.isBlank()) "বিলের ছবি (ঐচ্ছিক)" else "ছবি যোগ হয়েছে") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("লেনদেনের মেসেজ", modifier = Modifier.weight(1f))
                Switch(viewModel.sms, { viewModel.sms = it })
            }
            problem?.let { com.hisabboi.ui.ResultBanner(it) }
            PrimaryButton("সেভ করুন", {
                val value = com.hisabboi.model.AmountKeypad.evaluate(viewModel.amount)
                if (value != null) viewModel.amount = com.hisabboi.model.AmountKeypad.plain(value)
                viewModel.requestSave()
            }, parsed != null && parsed > 0)
        }
    }
    if (showDate) {
        val dateState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = viewModel.date)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { viewModel.date = it }
                    showDate = false
                }) { Text("ঠিক আছে") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("বাতিল") } },
        ) { androidx.compose.material3.DatePicker(state = dateState) }
    }
    if (viewModel.warnCredit) {
        AlertDialog(
            onDismissRequest = { viewModel.warnCredit = false },
            title = { Text("ক্রেডিট সীমা") },
            text = { Text("এই এন্ট্রিতে কাস্টমারের সীমা পেরিয়ে যাবে। তারপরও সেভ করবেন?") },
            confirmButton = { TextButton(onClick = viewModel::save) { Text("সেভ") } },
            dismissButton = { TextButton(onClick = { viewModel.warnCredit = false }) { Text("না") } },
        )
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateSafe(): androidx.compose.runtime.State<T> =
    collectAsStateWithLifecycle()
