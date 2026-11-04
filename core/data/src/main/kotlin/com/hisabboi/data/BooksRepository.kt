package com.hisabboi.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.hisabboi.model.BalanceMath
import com.hisabboi.model.CashEntry
import com.hisabboi.model.CashRepository
import com.hisabboi.model.Contact
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.KycStatus
import com.hisabboi.model.LedgerTx
import com.hisabboi.model.LineItem
import com.hisabboi.model.PaymentProfile
import com.hisabboi.model.PaymentRepository
import com.hisabboi.model.Product
import com.hisabboi.model.ProductRepository
import com.hisabboi.model.PublicProjection
import com.hisabboi.model.ScanBus
import com.hisabboi.model.ScanPayload
import com.hisabboi.model.TransactionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

internal fun FirebaseFirestore.biz(uid: String, bid: String) =
    collection("users").document(uid).collection("businesses").document(bid)

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : ContactRepository {
    override fun observe(uid: String, businessId: String): Flow<List<Contact>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("contacts").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.map { it.toContact() }.sortedBy { it.name })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsert(uid: String, businessId: String, contact: Contact): Result<Contact> = runCatching {
        val col = db.biz(uid, businessId).collection("contacts")
        val ref = if (contact.id.isBlank()) col.document() else col.document(contact.id)
        val now = System.currentTimeMillis()
        val existing = if (contact.id.isBlank()) null else runCatching { ref.get().await().toContact() }.getOrNull()
        val saved = contact.copy(
            id = ref.id,
            balance = existing?.balance ?: contact.balance,
            kycStatus = existing?.kycStatus ?: contact.kycStatus,
            createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now,
            deleted = existing?.deleted ?: false,
        )
        ref.set(saved.toMap()).await()
        saved
    }.mapError()

    override suspend fun setBalance(uid: String, businessId: String, contactId: String, balance: Double): Result<Unit> =
        runCatching {
            db.biz(uid, businessId).collection("contacts").document(contactId)
                .update(mapOf("balance" to balance, "updatedAt" to System.currentTimeMillis())).await()
            Unit
        }.mapError()

    override suspend fun softDelete(uid: String, businessId: String, contactId: String): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("contacts").document(contactId)
            .update(mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis())).await()
        Unit
    }.mapError()

    override suspend fun restore(uid: String, businessId: String, contactId: String): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("contacts").document(contactId)
            .update(mapOf("deleted" to false, "updatedAt" to System.currentTimeMillis())).await()
        Unit
    }.mapError()

    override suspend fun updateKycStatus(uid: String, businessId: String, contactId: String, status: KycStatus) {
        db.biz(uid, businessId).collection("contacts").document(contactId)
            .update(mapOf("kycStatus" to status.name.lowercase(), "updatedAt" to System.currentTimeMillis())).await()
    }
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : TransactionRepository {
    override fun observeAll(uid: String, businessId: String): Flow<List<LedgerTx>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("transactions")
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.map { it.toTx() })
            }
        awaitClose { reg.remove() }
    }

    override fun observeForContact(uid: String, businessId: String, contactId: String): Flow<List<LedgerTx>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("transactions")
            .whereEqualTo("contactId", contactId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.map { it.toTx() }.sortedByDescending { it.dateMillis })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun add(uid: String, businessId: String, tx: LedgerTx): Result<LedgerTx> = runCatching {
        val ref = db.biz(uid, businessId).collection("transactions").document()
        val now = System.currentTimeMillis()
        val saved = tx.copy(id = ref.id, createdAt = now, deleted = false)
        val batch = db.batch()
        batch.set(ref, saved.toMap())
        batch.update(
            db.biz(uid, businessId).collection("contacts").document(tx.contactId),
            mapOf(
                "balance" to FieldValue.increment(BalanceMath.delta(tx.direction, tx.amount)),
                "updatedAt" to now,
            ),
        )
        batch.commit().await()
        saved
    }.mapError()

    override suspend fun softDelete(uid: String, businessId: String, tx: LedgerTx): Result<Unit> = runCatching {
        if (tx.deleted) return@runCatching
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.update(
            db.biz(uid, businessId).collection("transactions").document(tx.id),
            mapOf("deleted" to true),
        )
        batch.update(
            db.biz(uid, businessId).collection("contacts").document(tx.contactId),
            mapOf(
                "balance" to FieldValue.increment(-BalanceMath.delta(tx.direction, tx.amount)),
                "updatedAt" to now,
            ),
        )
        batch.commit().await()
        Unit
    }.mapError()

    override suspend fun restore(uid: String, businessId: String, tx: LedgerTx): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.update(
            db.biz(uid, businessId).collection("transactions").document(tx.id),
            mapOf("deleted" to false),
        )
        batch.update(
            db.biz(uid, businessId).collection("contacts").document(tx.contactId),
            mapOf(
                "balance" to FieldValue.increment(BalanceMath.delta(tx.direction, tx.amount)),
                "updatedAt" to now,
            ),
        )
        batch.commit().await()
        Unit
    }.mapError()
}

@Singleton
class CashRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : CashRepository {
    override fun observe(uid: String, businessId: String): Flow<List<CashEntry>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("cashEntries")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.map { it.toCash() }.sortedByDescending { it.dateMillis })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun add(uid: String, businessId: String, entry: CashEntry): Result<CashEntry> = runCatching {
        val ref = db.biz(uid, businessId).collection("cashEntries").document()
        val saved = entry.copy(id = ref.id, createdAt = System.currentTimeMillis(), deleted = false)
        ref.set(saved.toMap()).await()
        saved
    }.mapError()

    override suspend fun softDelete(uid: String, businessId: String, entry: CashEntry): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("cashEntries").document(entry.id)
            .update("deleted", true).await()
        Unit
    }.mapError()

    override suspend fun restore(uid: String, businessId: String, entry: CashEntry): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("cashEntries").document(entry.id)
            .update("deleted", false).await()
        Unit
    }.mapError()
}

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : ProductRepository {
    override fun observe(uid: String, businessId: String): Flow<List<Product>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("products").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.map { it.toProduct() }.sortedBy { it.name })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsert(uid: String, businessId: String, product: Product): Result<Product> = runCatching {
        val col = db.biz(uid, businessId).collection("products")
        val existing = col.whereEqualTo("barcode", product.barcode).get().await().documents.firstOrNull()
        val ref = when {
            product.id.isNotBlank() -> col.document(product.id)
            existing != null -> existing.reference
            else -> col.document()
        }
        val saved = product.copy(id = ref.id, updatedAt = System.currentTimeMillis(), deleted = false)
        ref.set(
            mapOf(
                "barcode" to saved.barcode,
                "name" to saved.name,
                "deleted" to false,
                "updatedAt" to saved.updatedAt,
            ),
            SetOptions.merge(),
        ).await()
        saved
    }.mapError()

    override suspend fun softDelete(uid: String, businessId: String, productId: String): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("products").document(productId)
            .update(mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis())).await()
        Unit
    }.mapError()
}

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : PaymentRepository {
    override fun observe(uid: String, businessId: String): Flow<PaymentProfile> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("paymentProfile").document("default")
            .addSnapshotListener { snap, _ ->
                trySend(if (snap != null && snap.exists()) snap.toPayment() else PaymentProfile())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Saves the shopkeeper's own account details.
     * No payment is captured. If the shop is public, the same details are copied onto the public page
     * so a customer can pay the shopkeeper directly.
     */
    override suspend fun save(uid: String, businessId: String, profile: PaymentProfile): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("paymentProfile").document("default")
            .set(PublicProjection.paymentMap(profile)).await()
        val businessSnap = db.biz(uid, businessId).get().await()
        if (businessSnap.exists()) {
            val business = businessSnap.toBusiness()
            if (business.publicOptIn && !business.deleted) {
                val shop = PublicProjection.shop(business, uid, profile, System.currentTimeMillis())
                db.collection("publicShops").document(businessId).set(PublicProjection.shopMap(shop)).await()
            }
        }
    }.mapError()
}

@Singleton
class ScanBusImpl @Inject constructor() : ScanBus {
    private val _payload = MutableStateFlow<ScanPayload?>(null)
    override val payload: StateFlow<ScanPayload?> = _payload
    override fun publish(items: List<LineItem>) {
        _payload.value = ScanPayload(System.nanoTime(), items)
    }
    override fun consume(id: Long) {
        if (_payload.value?.id == id) _payload.value = null
    }
}
