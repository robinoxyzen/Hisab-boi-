package com.hisabboi.feature.payments

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.CashEntry
import com.hisabboi.model.CashKind
import com.hisabboi.model.CashRepository
import com.hisabboi.model.Contact
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.Direction
import com.hisabboi.model.LedgerTx
import com.hisabboi.model.LocalNotifier
import com.hisabboi.model.Money
import com.hisabboi.model.PaymentProfile
import com.hisabboi.model.PaymentQrContent
import com.hisabboi.model.PaymentRepository
import com.hisabboi.model.SettingsStore
import com.hisabboi.model.TransactionRepository
import com.hisabboi.model.TxSource
import com.hisabboi.ui.AmountField
import com.hisabboi.ui.Files
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.QrImage
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.qrBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Customers pay the shopkeeper's own bKash, Nagad, Rocket, bank, or Bangla QR account.
 * হিসাব বই does not receive, hold, route, or process that money. There is no payment gateway.
 * "পেমেন্ট পেয়েছি" only writes the shopkeeper's own ledger entry and a notification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QrViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
    private val payments: PaymentRepository,
    private val contacts: ContactRepository,
    private val transactions: TransactionRepository,
    private val cash: CashRepository,
    private val notifier: LocalNotifier,
    private val settings: SettingsStore,
) : ViewModel() {
    var editing by mutableStateOf(false)
    var holder by mutableStateOf("")
    var bkash by mutableStateOf("")
    var nagad by mutableStateOf("")
    var rocket by mutableStateOf("")
    var bankName by mutableStateOf("")
    var bankAccount by mutableStateOf("")
    var branch by mutableStateOf("")
    var banglaQr by mutableStateOf("")
    var amount by mutableStateOf("")
    var payer by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    val business = session.active
    val profile = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(PaymentProfile()) else payments.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentProfile())

    val people = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else contacts.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun beginEdit(current: PaymentProfile) {
        holder = current.holderName
        bkash = current.bkash
        nagad = current.nagad
        rocket = current.rocket
        bankName = current.bankName
        bankAccount = current.bankAccount
        branch = current.bankBranch
        banglaQr = current.banglaQr
        editing = true
    }

    fun saveProfile() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            payments.save(
                uid,
                biz.id,
                PaymentProfile(holder, bkash, nagad, rocket, bankName, bankAccount, branch, banglaQr),
            )
            editing = false
        }
    }

    fun confirmReceived(contact: Contact?) {
        val parsed = Money.parse(amount) ?: return
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            if (contact != null) {
                val tx = transactions.add(
                    uid,
                    biz.id,
                    LedgerTx(
                        contactId = contact.id,
                        contactName = contact.name,
                        direction = Direction.RECEIVED,
                        amount = parsed,
                        description = "QR পেমেন্ট নিশ্চিত",
                        dateMillis = System.currentTimeMillis(),
                        source = TxSource.QR_CONFIRM,
                    ),
                ).getOrNull()
                if (settings.notifications.first()) {
                    notifier.show("পেমেন্ট সফল", "${Money.format(parsed)} · ${contact.name}", tx?.id)
                }
            } else if (settings.notifications.first()) {
                notifier.show("পেমেন্ট সফল", "${Money.format(parsed)} · ${payer.ifBlank { "কাস্টমার" }}", null)
            }
            cash.add(
                uid,
                biz.id,
                CashEntry(
                    kind = CashKind.QR_IN,
                    amount = parsed,
                    note = payer.ifBlank { contact?.name ?: "QR" },
                    dateMillis = System.currentTimeMillis(),
                ),
            )
            message = "খাতায় যোগ হয়েছে। টাকা সরাসরি আপনার অ্যাকাউন্টে যায়, অ্যাপ তা গ্রহণ করে না।"
            amount = ""
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(viewModel: QrViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val business by viewModel.business.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val payload = PaymentQrContent.build(profile, business?.name ?: "হিসাব বই")
    var contactMenu by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Contact?>(null) }
    SimpleScaffold("আমার QR") { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("কাস্টমার বিকাশ, নগদ, রকেট বা ব্যাংক অ্যাপ দিয়ে স্ক্যান করলে টাকা সরাসরি আপনার নিজের অ্যাকাউন্টে যাবে। হিসাব বই কোনো পেমেন্ট গ্রহণ বা প্রসেস করে না।")
            if (profile.hasAny() && !viewModel.editing) {
                QrImage(payload, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                if (profile.holderName.isNotBlank()) Text(profile.holderName)
                listOf("bKash" to profile.bkash, "Nagad" to profile.nagad, "Rocket" to profile.rocket)
                    .filter { it.second.isNotBlank() }
                    .forEach { Text("${it.first}: ${it.second}") }
                if (profile.bankAccount.isNotBlank()) Text("${profile.bankName} ${profile.bankAccount}")
                Row {
                    TextButton(onClick = {
                        val bitmap = qrBitmap(payload)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        Files.share(context, "amar-qr.png", stream.toByteArray(), "image/png")
                    }) { Text("শেয়ার") }
                    TextButton(onClick = { viewModel.beginEdit(profile) }) { Text("বদলান") }
                }
            } else {
                OutlinedTextField(viewModel.holder, { viewModel.holder = it }, label = { Text("অ্যাকাউন্টের নাম") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.bkash, { viewModel.bkash = it }, label = { Text("bKash নম্বর") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.nagad, { viewModel.nagad = it }, label = { Text("Nagad নম্বর") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.rocket, { viewModel.rocket = it }, label = { Text("Rocket নম্বর") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.bankName, { viewModel.bankName = it }, label = { Text("ব্যাংকের নাম") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.bankAccount, { viewModel.bankAccount = it }, label = { Text("অ্যাকাউন্ট নম্বর") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.branch, { viewModel.branch = it }, label = { Text("শাখা") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(viewModel.banglaQr, { viewModel.banglaQr = it }, label = { Text("বাংলা QR মার্চেন্ট স্ট্রিং") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                PrimaryButton("সেভ", viewModel::saveProfile)
            }
            Text("পেমেন্ট পেয়েছি", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            AmountField(viewModel.amount, { viewModel.amount = it })
            ExposedDropdownMenuBox(expanded = contactMenu, onExpandedChange = { contactMenu = it }) {
                OutlinedTextField(
                    value = chosen?.name ?: viewModel.payer,
                    onValueChange = { viewModel.payer = it; chosen = null },
                    label = { Text("কে দিল") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = contactMenu, onDismissRequest = { contactMenu = false }) {
                    people.filter { !it.deleted }.take(20).forEach { person ->
                        DropdownMenuItem(text = { Text(person.name) }, onClick = { chosen = person; contactMenu = false })
                    }
                }
            }
            PrimaryButton("পেমেন্ট পেয়েছি", { viewModel.confirmReceived(chosen) }, Money.parse(viewModel.amount) != null)
            com.hisabboi.ui.ResultBanner(viewModel.message)
            com.hisabboi.ui.StatusBanner(
                com.hisabboi.model.NoticeKind.INFO,
                "কাস্টমার সরাসরি আপনার অ্যাকাউন্টে টাকা দেয়। হিসাব বই কোনো পেমেন্ট গ্রহণ করে না।",
            )
        }
    }
}
