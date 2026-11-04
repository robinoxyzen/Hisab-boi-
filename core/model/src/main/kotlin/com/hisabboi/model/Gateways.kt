package com.hisabboi.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Auth is a single seam. Google is the only provider today.
 * Another provider can be added by a new implementation without rewriting features.
 */
interface AuthGateway {
    val currentUser: StateFlow<SessionUser?>
    suspend fun signIn(host: Any): Result<SessionUser>
    suspend fun reauthenticate(host: Any): Result<SessionUser>
    suspend fun refreshAdminClaim(): Result<Boolean>
    fun signOut()
}

interface ActiveBusinessSource {
    val user: StateFlow<SessionUser?>
    val businesses: StateFlow<List<Business>>
    val active: StateFlow<Business?>
    val ready: StateFlow<Boolean>
    val offline: StateFlow<Boolean>
    suspend fun select(id: String)
    suspend fun createBusiness(draft: Business): Result<Business>
    suspend fun updateBusiness(draft: Business): Result<Unit>
    suspend fun softDeleteBusiness(id: String): Result<Unit>
    suspend fun restoreBusiness(id: String): Result<Unit>
}

interface ContactRepository {
    fun observe(uid: String, businessId: String): Flow<List<Contact>>
    suspend fun upsert(uid: String, businessId: String, contact: Contact): Result<Contact>
    suspend fun setBalance(uid: String, businessId: String, contactId: String, balance: Double): Result<Unit>
    suspend fun softDelete(uid: String, businessId: String, contactId: String): Result<Unit>
    suspend fun restore(uid: String, businessId: String, contactId: String): Result<Unit>
    suspend fun updateKycStatus(uid: String, businessId: String, contactId: String, status: KycStatus)
}

interface TransactionRepository {
    fun observeAll(uid: String, businessId: String): Flow<List<LedgerTx>>
    fun observeForContact(uid: String, businessId: String, contactId: String): Flow<List<LedgerTx>>
    suspend fun add(uid: String, businessId: String, tx: LedgerTx): Result<LedgerTx>
    suspend fun softDelete(uid: String, businessId: String, tx: LedgerTx): Result<Unit>
    suspend fun restore(uid: String, businessId: String, tx: LedgerTx): Result<Unit>
}

interface CashRepository {
    fun observe(uid: String, businessId: String): Flow<List<CashEntry>>
    suspend fun add(uid: String, businessId: String, entry: CashEntry): Result<CashEntry>
    suspend fun softDelete(uid: String, businessId: String, entry: CashEntry): Result<Unit>
    suspend fun restore(uid: String, businessId: String, entry: CashEntry): Result<Unit>
}

interface ProductRepository {
    fun observe(uid: String, businessId: String): Flow<List<Product>>
    suspend fun upsert(uid: String, businessId: String, product: Product): Result<Product>
    suspend fun softDelete(uid: String, businessId: String, productId: String): Result<Unit>
}

interface PaymentRepository {
    fun observe(uid: String, businessId: String): Flow<PaymentProfile>
    suspend fun save(uid: String, businessId: String, profile: PaymentProfile): Result<Unit>
}

interface PublicShopRepository {
    fun observe(id: String): Flow<PublicShop?>
    fun observeAll(): Flow<List<PublicShop>>
    suspend fun unpublish(id: String): Result<Unit>
}

interface KycRepository {
    fun observe(uid: String, businessId: String, contactId: String): Flow<KycProfile>
    suspend fun save(uid: String, businessId: String, contactId: String, profile: KycProfile): Result<Unit>
    suspend fun delete(uid: String, businessId: String, contactId: String): Result<Unit>
}

interface VerificationRepository {
    fun observeMine(uid: String, businessId: String): Flow<VerificationPacket>
    fun observeQueue(): Flow<List<VerificationPacket>>
    suspend fun submit(packet: VerificationPacket): Result<Unit>
    suspend fun review(businessId: String, ownerUid: String, approve: Boolean, reason: String): Result<Unit>
}

/**
 * Manual review today. A provider such as Porichoy can implement this later
 * and be bound in DI without changing the submission screen.
 */
interface IdentityVerifier {
    val id: String
    suspend fun submit(packet: VerificationPacket): Result<IdentityOutcome>
}

interface ReminderRepository {
    fun observe(uid: String, businessId: String): Flow<List<Reminder>>
    suspend fun add(uid: String, businessId: String, reminder: Reminder): Result<Reminder>
    suspend fun markQueued(uid: String, businessId: String, id: String): Result<Unit>
}

interface ReminderScheduler {
    fun schedule(reminder: Reminder, uid: String, businessId: String)
    fun cancel(id: String)
}

interface BackupRepository {
    suspend fun exportJson(uid: String): Result<String>
    suspend fun restoreJson(uid: String, json: String): Result<Int>
}

interface SettingsStore {
    val smsMode: Flow<String>
    val notifications: Flow<Boolean>
    suspend fun setSmsMode(mode: String)
    suspend fun setNotifications(enabled: Boolean)
}

interface ScanBus {
    val payload: StateFlow<ScanPayload?>
    fun publish(items: List<LineItem>)
    fun consume(id: Long)
}

interface LocalNotifier {
    fun show(title: String, body: String, dedupeKey: String?)
}

interface TokenRepository {
    suspend fun save(uid: String, token: String)
}

interface MediaUploader {
    suspend fun upload(storagePath: String, jpeg: ByteArray): String
}

/** Client-side encryption for NID numbers. Key never leaves the device Keystore. */
interface FieldCipher {
    fun encrypt(plain: String): String
    fun decrypt(payload: String): String
}

interface LockStore {
    val snapshot: StateFlow<LockSnapshot>
    fun setPin(pin: String)
    fun checkPin(pin: String): Boolean
    fun setMode(mode: LockMode, timeoutSec: Int)
    fun clear()
    fun onBackground()
    fun onForeground()
    fun unlock()
    fun lockNow()
}

data class LockSnapshot(
    val mode: LockMode = LockMode.OFF,
    val pinSet: Boolean = false,
    val locked: Boolean = false,
    val timeoutSec: Int = 60,
    val biometricAvailable: Boolean = false,
)
