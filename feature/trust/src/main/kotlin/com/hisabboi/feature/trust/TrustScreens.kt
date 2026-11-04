package com.hisabboi.feature.trust

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.FieldCipher
import com.hisabboi.model.IdentityVerifier
import com.hisabboi.model.KycProfile
import com.hisabboi.model.KycRepository
import com.hisabboi.model.VerificationPacket
import com.hisabboi.model.VerificationRepository
import com.hisabboi.model.VerificationStatus
import com.hisabboi.ui.Images
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
import javax.inject.Inject

/**
 * KYC stays inside this shopkeeper's business. It is not copied to the public shop page.
 * The NID number is encrypted with the device Keystore before it is written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KycViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ActiveBusinessSource,
    private val kyc: KycRepository,
    private val cipher: FieldCipher,
) : ViewModel() {
    private val contactId: String = savedStateHandle.get<String>("contactId").orEmpty()
    val profile = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(KycProfile()) else kyc.observe(uid, biz.id, contactId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KycProfile())

    var nid by mutableStateOf("")
    var address by mutableStateOf("")
    var reference by mutableStateOf("")
    var referencePhone by mutableStateOf("")
    var photo by mutableStateOf("")
    var front by mutableStateOf("")
    var back by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    fun save() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        val encrypted = try {
            if (nid.isBlank()) profile.value.nidEncrypted else cipher.encrypt(nid.filter { it.isDigit() })
        } catch (error: Exception) {
            message = "এনআইডি এনক্রিপ্ট করা যায়নি"
            return
        }
        val last4 = nid.filter { it.isDigit() }.takeLast(4).ifBlank { profile.value.nidLast4 }
        viewModelScope.launch {
            kyc.save(
                uid,
                biz.id,
                contactId,
                KycProfile(
                    nidEncrypted = encrypted,
                    nidLast4 = last4,
                    address = address.ifBlank { profile.value.address },
                    referenceName = reference.ifBlank { profile.value.referenceName },
                    referencePhone = referencePhone.ifBlank { profile.value.referencePhone },
                    photoBase64 = photo.ifBlank { profile.value.photoBase64 },
                    nidFrontBase64 = front.ifBlank { profile.value.nidFrontBase64 },
                    nidBackBase64 = back.ifBlank { profile.value.nidBackBase64 },
                ),
            ).onSuccess { message = "কেওয়াইসি সেভ হয়েছে" }.onFailure { message = it.message }
        }
    }

    fun delete() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch {
            kyc.delete(uid, biz.id, contactId)
            message = "কেওয়াইসি মুছে ফেলা হয়েছে"
        }
    }
}

@Composable
fun KycScreen(onBack: () -> Unit, viewModel: KycViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    SimpleScaffold("কাস্টমার কেওয়াইসি", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("এই তথ্য শুধু আপনার দোকানের। অন্য দোকান, পাবলিক পেজ বা অ্যাডমিন এটি দেখতে পায় না।")
            if (profile.nidLast4.isNotBlank()) Text("সংরক্ষিত এনআইডি শেষ চার: ${profile.nidLast4}")
            if (profile.isComplete()) Text("যাচাইকৃত")
            OutlinedTextField(viewModel.nid, { viewModel.nid = it }, label = { Text("এনআইডি নম্বর") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.address, { viewModel.address = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.reference, { viewModel.reference = it }, label = { Text("রেফারেন্স") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.referencePhone, { viewModel.referencePhone = it }, label = { Text("রেফারেন্স ফোন") }, modifier = Modifier.fillMaxWidth())
            ShotButton("কাস্টমারের ছবি") { viewModel.photo = it }
            ShotButton("এনআইডি সামনে") { viewModel.front = it }
            ShotButton("এনআইডি পেছনে") { viewModel.back = it }
            PrimaryButton("সেভ", viewModel::save)
            TextButton(onClick = viewModel::delete) { Text("কেওয়াইসি মুছুন") }
            com.hisabboi.ui.ResultBanner(viewModel.message)
        }
    }
}

@Composable
private fun ShotButton(label: String, onJpeg: (String) -> Unit) {
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) onJpeg(Images.jpeg(bitmap, 1024, 60))
    }
    TextButton(onClick = { camera.launch(null) }) { Text(label) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
    private val verifier: IdentityVerifier,
    private val cipher: FieldCipher,
    repository: VerificationRepository,
) : ViewModel() {
    var nid by mutableStateOf("")
    var front by mutableStateOf("")
    var back by mutableStateOf("")
    var selfie by mutableStateOf("")
    var trade by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    val mine = session.active.flatMapLatest { biz ->
        val uid = session.user.value?.uid
        if (biz == null || uid == null) flowOf(VerificationPacket()) else repository.observeMine(uid, biz.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationPacket())

    fun submit() {
        val biz = session.active.value ?: return
        val uid = session.user.value?.uid ?: return
        if (front.isBlank() || back.isBlank() || selfie.isBlank() || nid.isBlank()) {
            message = "এনআইডি, সামনে-পেছনের ছবি এবং সেলফি লাগবে"
            return
        }
        val encrypted = try {
            cipher.encrypt(nid.filter { it.isDigit() })
        } catch (error: Exception) {
            message = "এনআইডি এনক্রিপ্ট করা যায়নি"
            return
        }
        viewModelScope.launch {
            verifier.submit(
                VerificationPacket(
                    businessId = biz.id,
                    ownerUid = uid,
                    businessName = biz.name,
                    nidEncrypted = encrypted,
                    nidLast4 = nid.filter { it.isDigit() }.takeLast(4),
                    nidFrontBase64 = front,
                    nidBackBase64 = back,
                    selfieBase64 = selfie,
                    tradeLicenseBase64 = trade,
                    status = VerificationStatus.PENDING,
                ),
            ).onSuccess { message = "যাচাইয়ের জন্য পাঠানো হয়েছে" }
                .onFailure { message = it.message }
        }
    }
}

@Composable
fun VerificationScreen(onBack: () -> Unit, viewModel: VerifyViewModel = hiltViewModel()) {
    val mine by viewModel.mine.collectAsStateWithLifecycle()
    SimpleScaffold("ভেরিফাইড দোকান", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            run {
                val (kind, label) = com.hisabboi.model.NoticeTone.verification(mine.status)
                com.hisabboi.ui.StatusBadge(kind, label)
            }
            if (mine.reason.isNotBlank()) Text("কারণ: ${mine.reason}")
            Text("কাগজ শুধু আপনি ও প্ল্যাটফর্ম অ্যাডমিন দেখতে পারবেন। পাবলিক পেজে শুধু সবুজ ব্যাজ যায়।")
            OutlinedTextField(viewModel.nid, { viewModel.nid = it }, label = { Text("এনআইডি নম্বর") }, modifier = Modifier.fillMaxWidth())
            ShotButton("এনআইডি সামনে") { viewModel.front = it }
            ShotButton("এনআইডি পেছনে") { viewModel.back = it }
            ShotButton("লাইভ সেলফি") { viewModel.selfie = it }
            ShotButton("ট্রেড লাইসেন্স (ঐচ্ছিক)") { viewModel.trade = it }
            PrimaryButton("জমা দিন", viewModel::submit)
            com.hisabboi.ui.ResultBanner(viewModel.message)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
    private val repository: VerificationRepository,
) : ViewModel() {
    val isAdmin = session.user
    val queue = repository.observeQueue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selected = MutableStateFlow<Pair<String, String>?>(null)
    val detail = selected.flatMapLatest { key ->
        if (key == null) flowOf(VerificationPacket()) else repository.observeMine(key.first, key.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationPacket())
    var reason by mutableStateOf("")

    fun open(ownerUid: String, businessId: String) {
        selected.value = ownerUid to businessId
    }

    fun review(approve: Boolean) {
        val current = detail.value
        if (current.businessId.isBlank()) return
        viewModelScope.launch {
            repository.review(current.businessId, current.ownerUid, approve, reason)
        }
    }
}

@Composable
fun AdminScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val user by viewModel.isAdmin.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    SimpleScaffold("অ্যাডমিন", onBack) { padding ->
        if (user?.isAdmin != true) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text("এই স্ক্রিন শুধু অ্যাডমিন কাস্টম ক্লেইম থাকলে খোলে।")
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(queue, key = { it.businessId }) { item ->
                    ListItem(
                        headlineContent = { Text(item.businessName.ifBlank { item.businessId }) },
                        supportingContent = {
                            val (kind, label) = com.hisabboi.model.NoticeTone.verification(item.status)
                            com.hisabboi.ui.StatusBadge(kind, label)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            TextButton(onClick = { viewModel.open(item.ownerUid, item.businessId) }) { Text("খুলুন") }
                        },
                    )
                }
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(detail.businessName)
                        if (detail.nidLast4.isNotBlank()) Text("এনআইডি শেষ চার ${detail.nidLast4}")
                        if (detail.selfieBase64.isNotBlank()) Text("সেলফি আছে")
                        if (detail.nidFrontBase64.isNotBlank()) Text("এনআইডি সামনে আছে")
                        OutlinedTextField(viewModel.reason, { viewModel.reason = it }, label = { Text("বাতিলের কারণ") }, modifier = Modifier.fillMaxWidth())
                        PrimaryButton("অনুমোদন", { viewModel.review(true) })
                        TextButton(onClick = { viewModel.review(false) }) { Text("বাতিল") }
                    }
                }
            }
        }
    }
}
