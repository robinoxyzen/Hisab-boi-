package com.hisabboi.feature.reminders

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.BnDate
import com.hisabboi.model.Contact
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.Money
import com.hisabboi.model.Reminder
import com.hisabboi.model.ReminderChannel
import com.hisabboi.model.ReminderRepository
import com.hisabboi.model.ReminderScheduler
import com.hisabboi.model.Templates
import com.hisabboi.model.label
import com.hisabboi.ui.ChipRow
import com.hisabboi.ui.Files
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val bid = inputData.getString("businessId") ?: return Result.failure()
        val id = inputData.getString("id") ?: return Result.failure()
        if (inputData.getString("channel") == "gateway") {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("businesses").document(bid)
                .collection("reminders").document(id)
                .update("status", "queued")
                .await()
        }
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                android.app.NotificationChannel("hisab_pay", "পেমেন্ট ও তাগাদা", android.app.NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "hisab_pay")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("তাগাদা")
            .setContentText(inputData.getString("body").orEmpty())
            .setAutoCancel(true)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification) }
        return Result.success()
    }
}

@Singleton
class WorkReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {
    override fun schedule(reminder: Reminder, uid: String, businessId: String) {
        val delay = (reminder.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "uid" to uid,
                    "businessId" to businessId,
                    "id" to reminder.id,
                    "channel" to reminder.channel.name.lowercase(),
                    "body" to reminder.message,
                ),
            )
            .addTag(reminder.id)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override fun cancel(id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(id)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds @Singleton abstract fun scheduler(impl: WorkReminderScheduler): ReminderScheduler
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    contactsRepo: ContactRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val preset: String = savedStateHandle.get<String>("contactId").orEmpty()
    val contacts = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else contactsRepo.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(emptyList()) else reminders.observe(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selected by mutableStateOf(setOf<String>())
    var channel by mutableStateOf(ReminderChannel.DEVICE)
    var message by mutableStateOf("")
    var scheduleDays by mutableStateOf("")
    var note by mutableStateOf<String?>(null)
    private var seeded = false

    fun seed(list: List<Contact>) {
        if (seeded) return
        seeded = true
        if (preset.isNotBlank()) selected = setOf(preset)
        val biz = session.active.value?.name ?: "দোকান"
        val first = list.firstOrNull { it.id == preset && it.balance > 0 }
        message = Templates.reminder(biz, first?.balance ?: 0.0)
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun send(context: Context) {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        val chosen = contacts.value.filter { it.id in selected && it.balance > 0 }
        if (chosen.isEmpty()) {
            note = "বকেয়া আছে এমন কাউকে বেছে নিন"
            return
        }
        val days = scheduleDays.toIntOrNull()
        val whenAt = if (days != null && days > 0) System.currentTimeMillis() + days * 86_400_000L else 0L
        viewModelScope.launch {
            chosen.forEach { contact ->
                val text = message.ifBlank { Templates.reminder(biz.name, contact.balance) }
                val status = when {
                    whenAt > 0 -> "scheduled"
                    channel == ReminderChannel.GATEWAY -> "queued"
                    else -> channel.name.lowercase()
                }
                val saved = reminders.add(
                    uid,
                    biz.id,
                    Reminder(
                        contactId = contact.id,
                        contactName = contact.name,
                        phone = contact.phone,
                        channel = channel,
                        message = text,
                        amount = contact.balance,
                        scheduledAt = whenAt,
                        status = status,
                    ),
                ).getOrNull() ?: return@forEach
                if (whenAt > 0 && saved.id.isNotBlank()) scheduler.schedule(saved, uid, biz.id)
                if (whenAt == 0L) {
                    when (channel) {
                        ReminderChannel.DEVICE -> if (contact.phone.isNotBlank()) runCatching { Files.sms(context, contact.phone, text) }
                        ReminderChannel.WHATSAPP -> if (contact.phone.isNotBlank()) runCatching { Files.whatsApp(context, contact.phone, text) }
                        ReminderChannel.IMAGE -> {
                            val png = Files.dueCard(context, biz.name, contact.name, Money.format(contact.balance), text)
                            Files.share(context, "tagada.png", png, "image/png")
                        }
                        ReminderChannel.GATEWAY -> Unit
                    }
                }
            }
            note = "তাগাদা তৈরি হয়েছে"
        }
    }
}

@Composable
fun ReminderScreen(onBack: () -> Unit, viewModel: ReminderViewModel = hiltViewModel()) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val due = contacts.filter { !it.deleted && it.balance > 0 }
    androidx.compose.runtime.LaunchedEffect(due.size) { viewModel.seed(due) }
    SimpleScaffold("তাগাদা", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text("যাদের কাছে পাবো")
                ChipRow(ReminderChannel.entries.map { it.label() to (viewModel.channel == it) }) { index ->
                    viewModel.channel = ReminderChannel.entries[index]
                }
                OutlinedTextField(viewModel.message, { viewModel.message = it }, label = { Text("মেসেজ") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(viewModel.scheduleDays, { viewModel.scheduleDays = it.filter { ch -> ch.isDigit() } }, label = { Text("কত দিন পরে (খালি থাকলে এখনই)") }, modifier = Modifier.fillMaxWidth())
            }
            items(due, key = { it.id }) { contact ->
                ListItem(
                    headlineContent = { Text(contact.name) },
                    supportingContent = { Text(Money.format(contact.balance)) },
                    leadingContent = {
                        Checkbox(checked = contact.id in viewModel.selected, onCheckedChange = { viewModel.toggle(contact.id) })
                    },
                )
            }
            item {
                PrimaryButton("পাঠান", { viewModel.send(context) })
                com.hisabboi.ui.ResultBanner(viewModel.note)
                Text("ইতিহাস")
            }
            items(history, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text("${item.contactName} · ${item.channel.label()}") },
                    supportingContent = {
                        val (kind, label) = com.hisabboi.model.NoticeTone.reminder(item.status)
                        Row {
                            com.hisabboi.ui.StatusBadge(kind, label)
                            if (item.scheduledAt > 0) Text(" ${BnDate.format(item.scheduledAt)}")
                        }
                    },
                )
            }
        }
    }
}
