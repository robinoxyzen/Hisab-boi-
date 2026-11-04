package com.hisabboi.feature.cashbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.BnDate
import com.hisabboi.model.CashEntry
import com.hisabboi.model.CashKind
import com.hisabboi.model.CashRepository
import com.hisabboi.model.LineItem
import com.hisabboi.model.Money
import com.hisabboi.model.ScanBus
import com.hisabboi.model.asNote
import com.hisabboi.model.label
import com.hisabboi.model.signed
import com.hisabboi.ui.AmountField
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Files
import com.hisabboi.ui.NoteField
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.StatTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CashUi(
    val sales: Double = 0.0,
    val cash: Double = 0.0,
    val inn: Double = 0.0,
    val out: Double = 0.0,
    val rows: List<CashEntry> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashViewModel @Inject constructor(
    session: ActiveBusinessSource,
    private val cash: CashRepository,
) : ViewModel() {
    val uid = session.user
    val active = session.active
    val state = session.active.flatMapLatest { biz ->
        val id = session.user.value?.uid
        if (biz == null || id == null) flowOf(CashUi()) else cash.observe(id, biz.id).map { entries ->
            val alive = entries.filter { !it.deleted }
            val start = BnDate.startOfDay()
            val today = alive.filter { it.dateMillis >= start }
            CashUi(
                sales = today.filter { it.kind == CashKind.SALE }.sumOf { it.amount },
                cash = alive.sumOf { it.kind.signed(it.amount) },
                inn = today.filter { it.kind.signed(1.0) > 0 }.sumOf { it.amount },
                out = today.filter { it.kind.signed(1.0) < 0 }.sumOf { it.amount },
                rows = alive,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashUi())

    fun remove(entry: CashEntry) {
        val biz = active.value ?: return
        val id = uid.value?.uid ?: return
        viewModelScope.launch { cash.softDelete(id, biz.id, entry) }
    }

    fun lines(): List<String> {
        val ui = state.value
        return buildList {
            add("আজকের বিক্রি ${Money.format(ui.sales)}")
            add("বর্তমান ক্যাশ ${Money.format(ui.cash)}")
            add("আজ পেলাম ${Money.format(ui.inn)}")
            add("আজ দিলাম ${Money.format(ui.out)}")
            ui.rows.filter { it.dateMillis >= BnDate.startOfDay() }.forEach {
                add("${it.kind.label()} ${Money.format(it.amount)} ${it.note}")
            }
        }
    }
}

@Composable
fun CashboxScreen(onAdd: (String) -> Unit, viewModel: CashViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val day = BnDate.startOfDay()
    val book = com.hisabboi.model.CashBook.todayWithRunning(state.rows, day)
    SimpleScaffold("ক্যাশ") { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("চলতি ব্যালেন্স", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Money.format(state.cash),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.cash >= 0) com.hisabboi.ui.Receive else com.hisabboi.ui.Payable,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("আজ ঢুকলো", state.inn, true, Modifier.weight(1f))
                    StatTile("আজ বেরোলো", state.out, false, Modifier.weight(1f))
                }
                com.hisabboi.ui.StatusBanner(
                    com.hisabboi.model.NoticeKind.INFO,
                    "টাকা আপনার ক্যাশে থাকে। অ্যাপ কোনো টাকা গ্রহণ করে না।",
                    Modifier.padding(top = 8.dp),
                )
                Text("নতুন এন্ট্রি", modifier = Modifier.padding(top = 12.dp))
                CashKind.entries.forEach { kind ->
                    ListItem(
                        headlineContent = { Text(kind.label()) },
                        modifier = Modifier.clickable { onAdd(kind.name.lowercase()) },
                    )
                }
                TextButton(onClick = {
                    val bytes = Files.pdf(context, "ক্যাশ রিপোর্ট", viewModel.lines())
                    Files.share(context, "cash.pdf", bytes, "application/pdf")
                }) { Text("আজকের রিপোর্ট") }
                Text("আজকের খাতা", modifier = Modifier.padding(top = 8.dp))
            }
            if (book.isEmpty()) item { EmptyHint("আজ ক্যাশ খালি", "বিক্রি, কেনা, খরচ বা মালিকের জমা-তোলা যোগ করুন।") }
            items(book, key = { it.first.id }) { (entry, running) ->
                ListItem(
                    headlineContent = { Text(entry.kind.label()) },
                    supportingContent = { Text("${BnDate.formatTime(entry.dateMillis)} ${entry.note}") },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(entry.amount), color = if (entry.kind.signed(1.0) >= 0) com.hisabboi.ui.Receive else com.hisabboi.ui.Payable)
                            Text("ব্যালেন্স ${Money.format(running)}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.remove(entry) }) { Text("মুছুন") }
                        }
                    },
                )
            }
        }
    }
}

@HiltViewModel
class CashEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val cash: CashRepository,
    private val scanBus: ScanBus,
) : ViewModel() {
    val kind: CashKind = CashKind.entries.firstOrNull { it.name.equals(savedStateHandle.get<String>("kind"), true) } ?: CashKind.SALE
    var amount by mutableStateOf("")
    var note by mutableStateOf("")
    var saved by mutableStateOf(false)
    private val items = mutableListOf<LineItem>()

    init {
        viewModelScope.launch {
            scanBus.payload.collect { payload ->
                if (payload == null) return@collect
                items += payload.items
                note = items.asNote()
                val suggested = payload.suggestedAmount
                if (suggested != null && suggested > 0 && amount.isBlank()) {
                    amount = com.hisabboi.model.AmountKeypad.plain(suggested)
                }
                scanBus.consume(payload.id)
            }
        }
    }

    fun save() {
        val parsed = Money.parse(amount) ?: return
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            cash.add(
                uid,
                biz.id,
                CashEntry(kind = kind, amount = parsed, note = note, items = items.toList(), dateMillis = System.currentTimeMillis()),
            )
            saved = true
        }
    }
}

@Composable
fun CashEntryScreen(onBack: () -> Unit, onScan: () -> Unit, viewModel: CashEntryViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onBack() }
    SimpleScaffold(viewModel.kind.label(), onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField(viewModel.amount, { viewModel.amount = it })
            NoteField(viewModel.note, { viewModel.note = it }, onScan, "নোট")
            PrimaryButton("সেভ", viewModel::save, Money.parse(viewModel.amount) != null)
        }
    }
}
