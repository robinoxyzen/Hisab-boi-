package com.hisabboi.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.Business
import com.hisabboi.model.PaymentProfile
import com.hisabboi.model.PublicProjection
import com.hisabboi.model.SessionUser
import com.hisabboi.model.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val auth: AuthGateway,
    private val db: FirebaseFirestore,
    private val prefs: UserPrefs,
) : ActiveBusinessSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _businesses = MutableStateFlow<List<Business>>(emptyList())
    private val _active = MutableStateFlow<Business?>(null)
    private val _ready = MutableStateFlow(false)
    private val _offline = MutableStateFlow(false)
    private var registration: ListenerRegistration? = null

    override val user: StateFlow<SessionUser?> = auth.currentUser
    override val businesses: StateFlow<List<Business>> = _businesses
    override val active: StateFlow<Business?> = _active
    override val ready: StateFlow<Boolean> = _ready
    override val offline: StateFlow<Boolean> = _offline

    init {
        scope.launch {
            auth.currentUser.collectLatest { session ->
                registration?.remove()
                registration = null
                if (session == null) {
                    _businesses.value = emptyList()
                    _active.value = null
                    _ready.value = true
                    return@collectLatest
                }
                _ready.value = false
                launch { runCatching { auth.refreshAdminClaim() } }
                registration = db.collection("users").document(session.uid).collection("businesses")
                    .addSnapshotListener { snap, err ->
                        if (err != null || snap == null) return@addSnapshotListener
                        _offline.value = snap.metadata.isFromCache
                        _businesses.value = snap.documents.map { it.toBusiness() }
                        if (!snap.metadata.isFromCache || _businesses.value.isNotEmpty()) {
                            _ready.value = true
                        }
                    }
                kotlinx.coroutines.delay(2_500)
                _ready.value = true
            }
        }
        scope.launch {
            kotlinx.coroutines.flow.combine(_businesses, prefs.activeBusinessId) { list, id ->
                val alive = list.filter { !it.deleted }
                alive.firstOrNull { it.id == id } ?: alive.firstOrNull()
            }.collect { picked ->
                _active.value = picked
                if (picked != null) prefs.setActive(picked.id)
            }
        }
    }

    override suspend fun select(id: String) {
        prefs.setActive(id)
    }

    override suspend fun createBusiness(draft: Business): Result<Business> = runCatching {
        val uid = uid()
        val ref = db.collection("users").document(uid).collection("businesses").document()
        val now = System.currentTimeMillis()
        val created = draft.copy(
            id = ref.id,
            verified = false,
            verificationStatus = VerificationStatus.NONE,
            verificationReason = "",
            deleted = false,
            createdAt = now,
            updatedAt = now,
        )
        ref.set(created.toMap()).await()
        prefs.setActive(ref.id)
        created
    }.mapError()

    override suspend fun updateBusiness(draft: Business): Result<Unit> = runCatching {
        val uid = uid()
        val ref = db.collection("users").document(uid).collection("businesses").document(draft.id)
        val current = ref.get().await().toBusiness()
        val now = System.currentTimeMillis()
        val merged = draft.copy(
            verified = current.verified,
            verificationStatus = current.verificationStatus,
            verificationReason = current.verificationReason,
            createdAt = current.createdAt,
            updatedAt = now,
        )
        ref.set(merged.toMap()).await()
        syncPublic(uid, merged)
    }.mapError()

    override suspend fun softDeleteBusiness(id: String): Result<Unit> = runCatching {
        val uid = uid()
        val ref = db.collection("users").document(uid).collection("businesses").document(id)
        val current = ref.get().await().toBusiness()
        ref.set(current.copy(deleted = true, updatedAt = System.currentTimeMillis(), publicOptIn = false).toMap()).await()
        db.collection("publicShops").document(id).delete().await()
        Unit
    }.mapError()

    override suspend fun restoreBusiness(id: String): Result<Unit> = runCatching {
        val uid = uid()
        val ref = db.collection("users").document(uid).collection("businesses").document(id)
        val current = ref.get().await().toBusiness()
        ref.set(current.copy(deleted = false, updatedAt = System.currentTimeMillis()).toMap()).await()
        Unit
    }.mapError()

    private suspend fun syncPublic(uid: String, business: Business) {
        val publicRef = db.collection("publicShops").document(business.id)
        if (!business.publicOptIn || business.deleted) {
            runCatching { publicRef.delete().await() }
            return
        }
        val paymentSnap = db.collection("users").document(uid)
            .collection("businesses").document(business.id)
            .collection("paymentProfile").document("default").get().await()
        val payment = if (paymentSnap.exists()) paymentSnap.toPayment() else PaymentProfile()
        val shop = PublicProjection.shop(business, uid, payment, business.updatedAt)
        publicRef.set(PublicProjection.shopMap(shop)).await()
    }

    private fun uid(): String = auth.currentUser.value?.uid ?: error("লগইন করা নেই")
}

internal fun <T> Result<T>.mapError(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure(IllegalStateException(it.bangla(), it)) },
)
