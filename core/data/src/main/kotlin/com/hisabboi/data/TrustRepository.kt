package com.hisabboi.data

import com.google.firebase.firestore.FirebaseFirestore
import com.hisabboi.model.IdentityOutcome
import com.hisabboi.model.IdentityVerifier
import com.hisabboi.model.KycProfile
import com.hisabboi.model.KycRepository
import com.hisabboi.model.KycStatus
import com.hisabboi.model.MediaUploader
import com.hisabboi.model.PublicShop
import com.hisabboi.model.PublicShopRepository
import com.hisabboi.model.VerificationPacket
import com.hisabboi.model.VerificationRepository
import com.hisabboi.model.VerificationStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicShopRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : PublicShopRepository {
    override fun observe(id: String): Flow<PublicShop?> = callbackFlow {
        val reg = db.collection("publicShops").document(id).addSnapshotListener { snap, _ ->
            trySend(if (snap != null && snap.exists()) snap.toPublicShop() else null)
        }
        awaitClose { reg.remove() }
    }

    override fun observeAll(): Flow<List<PublicShop>> = callbackFlow {
        val reg = db.collection("publicShops").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.map { it.toPublicShop() })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun unpublish(id: String): Result<Unit> = runCatching {
        db.collection("publicShops").document(id).delete().await()
        Unit
    }.mapError()
}

@Singleton
class KycRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
    private val contacts: ContactRepositoryImpl,
    private val media: MediaUploader,
) : KycRepository {
    private fun col(uid: String, bid: String, cid: String) =
        db.biz(uid, bid).collection("contacts").document(cid).collection("kyc")

    override fun observe(uid: String, businessId: String, contactId: String): Flow<KycProfile> = callbackFlow {
        val reg = col(uid, businessId, contactId).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val profile = snap.documents.firstOrNull { it.id == "profile" }
            val images = snap.documents.filter { it.id != "profile" }.associate { it.id to it.getString("data").orEmpty() }
            trySend(kycFrom(profile, images))
        }
        awaitClose { reg.remove() }
    }

    override suspend fun save(uid: String, businessId: String, contactId: String, profile: KycProfile): Result<Unit> =
        runCatching {
            val base = "users/$uid/businesses/$businessId/contacts/$contactId/kyc"
            val photoPath = uploadIf(profile.photoBase64, "$base/photo.jpg")
            val frontPath = uploadIf(profile.nidFrontBase64, "$base/nid-front.jpg")
            val backPath = uploadIf(profile.nidBackBase64, "$base/nid-back.jpg")
            val now = System.currentTimeMillis()
            val batch = db.batch()
            val c = col(uid, businessId, contactId)
            batch.set(
                c.document("profile"),
                mapOf(
                    "nidEncrypted" to profile.nidEncrypted,
                    "nidLast4" to profile.nidLast4,
                    "address" to profile.address,
                    "referenceName" to profile.referenceName,
                    "referencePhone" to profile.referencePhone,
                    "photoPath" to photoPath,
                    "nidFrontPath" to frontPath,
                    "nidBackPath" to backPath,
                    "updatedAt" to now,
                ),
            )
            batch.set(c.document("photo"), mapOf("data" to profile.photoBase64))
            batch.set(c.document("nidFront"), mapOf("data" to profile.nidFrontBase64))
            batch.set(c.document("nidBack"), mapOf("data" to profile.nidBackBase64))
            batch.commit().await()
            val status = if (profile.isComplete()) KycStatus.COMPLETE else KycStatus.PARTIAL
            contacts.updateKycStatus(uid, businessId, contactId, status)
        }.mapError()

    override suspend fun delete(uid: String, businessId: String, contactId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        val c = col(uid, businessId, contactId)
        listOf("profile", "photo", "nidFront", "nidBack").forEach { batch.delete(c.document(it)) }
        batch.commit().await()
        contacts.updateKycStatus(uid, businessId, contactId, KycStatus.NONE)
    }.mapError()

    private suspend fun uploadIf(base64: String, path: String): String {
        if (base64.isBlank()) return ""
        val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return ""
        return runCatching { media.upload(path, bytes) }.getOrDefault("")
    }
}

@Singleton
class VerificationRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
    private val media: MediaUploader,
) : VerificationRepository {
    override fun observeMine(uid: String, businessId: String): Flow<VerificationPacket> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("verification")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val meta = snap.documents.firstOrNull { it.id == "submission" }
                val images = snap.documents.filter { it.id != "submission" }
                    .associate { it.id to it.getString("data").orEmpty() }
                trySend(meta?.toVerification(images) ?: VerificationPacket(businessId = businessId, ownerUid = uid))
            }
        awaitClose { reg.remove() }
    }

    override fun observeQueue(): Flow<List<VerificationPacket>> = callbackFlow {
        val reg = db.collection("verificationRequests").addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap.documents.map { it.toVerification() }.sortedByDescending { it.submittedAt })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun submit(packet: VerificationPacket): Result<Unit> = runCatching {
        val uid = packet.ownerUid
        val bid = packet.businessId
        val now = System.currentTimeMillis()
        val base = "users/$uid/businesses/$bid/verification"
        val front = upload(packet.nidFrontBase64, "$base/nid-front.jpg")
        val back = upload(packet.nidBackBase64, "$base/nid-back.jpg")
        val selfie = upload(packet.selfieBase64, "$base/selfie.jpg")
        val trade = upload(packet.tradeLicenseBase64, "$base/trade.jpg")
        val col = db.biz(uid, bid).collection("verification")
        val batch = db.batch()
        batch.set(
            col.document("submission"),
            mapOf(
                "businessId" to bid,
                "ownerUid" to uid,
                "businessName" to packet.businessName,
                "nidEncrypted" to packet.nidEncrypted,
                "nidLast4" to packet.nidLast4,
                "nidFrontPath" to front,
                "nidBackPath" to back,
                "selfiePath" to selfie,
                "tradePath" to trade,
                "status" to "pending",
                "reason" to "",
                "submittedAt" to now,
                "reviewedAt" to 0L,
            ),
        )
        batch.set(col.document("nidFront"), mapOf("data" to packet.nidFrontBase64))
        batch.set(col.document("nidBack"), mapOf("data" to packet.nidBackBase64))
        batch.set(col.document("selfie"), mapOf("data" to packet.selfieBase64))
        if (packet.tradeLicenseBase64.isNotBlank()) {
            batch.set(col.document("trade"), mapOf("data" to packet.tradeLicenseBase64))
        }
        batch.commit().await()
        db.collection("verificationRequests").document(bid).set(
            mapOf(
                "ownerUid" to uid,
                "businessId" to bid,
                "businessName" to packet.businessName,
                "status" to "pending",
                "reason" to "",
                "submittedAt" to now,
                "reviewedAt" to 0L,
            ),
        ).await()
        val businessRef = db.biz(uid, bid)
        val current = businessRef.get().await().toBusiness()
        businessRef.set(
            current.copy(
                verificationStatus = VerificationStatus.PENDING,
                verificationReason = "",
                verified = false,
                updatedAt = now,
            ).toMap(),
        ).await()
        Unit
    }.mapError()

    override suspend fun review(businessId: String, ownerUid: String, approve: Boolean, reason: String): Result<Unit> =
        runCatching {
            val now = System.currentTimeMillis()
            val status = if (approve) VerificationStatus.VERIFIED else VerificationStatus.REJECTED
            val businessRef = db.biz(ownerUid, businessId)
            val current = businessRef.get().await().toBusiness()
            businessRef.set(
                current.copy(
                    verified = approve,
                    verificationStatus = status,
                    verificationReason = if (approve) "" else reason,
                    updatedAt = now,
                ).toMap(),
            ).await()
            db.biz(ownerUid, businessId).collection("verification").document("submission")
                .update(
                    mapOf(
                        "status" to status.name.lowercase(),
                        "reason" to if (approve) "" else reason,
                        "reviewedAt" to now,
                    ),
                ).await()
            db.collection("verificationRequests").document(businessId).set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "businessId" to businessId,
                    "businessName" to current.name,
                    "status" to status.name.lowercase(),
                    "reason" to if (approve) "" else reason,
                    "submittedAt" to current.updatedAt,
                    "reviewedAt" to now,
                ),
            ).await()
            if (current.publicOptIn) {
                val payment = db.biz(ownerUid, businessId).collection("paymentProfile").document("default").get().await()
                val profile = if (payment.exists()) payment.toPayment() else com.hisabboi.model.PaymentProfile()
                val shop = com.hisabboi.model.PublicProjection.shop(
                    current.copy(verified = approve, verificationStatus = status),
                    ownerUid,
                    profile,
                    now,
                )
                db.collection("publicShops").document(businessId)
                    .set(com.hisabboi.model.PublicProjection.shopMap(shop)).await()
            }
        }.mapError()

    private suspend fun upload(base64: String, path: String): String {
        if (base64.isBlank()) return ""
        val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return ""
        return runCatching { media.upload(path, bytes) }.getOrDefault("")
    }
}

/**
 * Default identity provider: park the submission for a platform admin.
 * To plug in Porichoy (or similar) later, implement [IdentityVerifier] and bind it instead of this class.
 * Do not call a NID vendor from the phone with a secret in the APK.
 */
@Singleton
class ManualAdminVerifier @Inject constructor(
    private val repository: VerificationRepository,
) : IdentityVerifier {
    override val id: String = "manual-admin"
    override suspend fun submit(packet: VerificationPacket): Result<IdentityOutcome> =
        repository.submit(packet).map { IdentityOutcome(VerificationStatus.PENDING) }
}

/** Extension point. Not registered in DI. */
class PorichoyVerifier : IdentityVerifier {
    override val id: String = "porichoy"
    override suspend fun submit(packet: VerificationPacket): Result<IdentityOutcome> =
        Result.failure(
            UnsupportedOperationException(
                "Porichoy is not configured. Shop verification stays in the admin queue.",
            ),
        )
}
