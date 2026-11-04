package com.hisabboi.data

import com.google.firebase.firestore.DocumentSnapshot
import com.hisabboi.model.Business
import com.hisabboi.model.BusinessType
import com.hisabboi.model.CashEntry
import com.hisabboi.model.CashKind
import com.hisabboi.model.Contact
import com.hisabboi.model.ContactKind
import com.hisabboi.model.Direction
import com.hisabboi.model.KycProfile
import com.hisabboi.model.KycStatus
import com.hisabboi.model.LedgerTx
import com.hisabboi.model.LineItem
import com.hisabboi.model.PaymentProfile
import com.hisabboi.model.Product
import com.hisabboi.model.PublicShop
import com.hisabboi.model.Reminder
import com.hisabboi.model.ReminderChannel
import com.hisabboi.model.TxSource
import com.hisabboi.model.VerificationPacket
import com.hisabboi.model.VerificationStatus
import com.hisabboi.model.businessTypeOf
import com.hisabboi.model.cashKindOf
import com.hisabboi.model.contactKindOf
import com.hisabboi.model.directionOf
import com.hisabboi.model.kycOf
import com.hisabboi.model.verificationOf

internal fun Any?.asDouble(): Double = when (this) {
    is Double -> this
    is Float -> this.toDouble()
    is Long -> this.toDouble()
    is Int -> this.toDouble()
    is String -> this.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

internal fun Any?.asDoubleOrNull(): Double? = when (this) {
    null -> null
    is Double -> this
    is Float -> this.toDouble()
    is Long -> this.toDouble()
    is Int -> this.toDouble()
    else -> null
}

internal fun Any?.asLong(): Long = when (this) {
    is Long -> this
    is Int -> this.toLong()
    is Double -> this.toLong()
    else -> 0L
}

internal fun Any?.asBool(): Boolean = this as? Boolean ?: false

@Suppress("UNCHECKED_CAST")
internal fun itemsOf(raw: Any?): List<LineItem> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { row ->
        val map = row as? Map<*, *> ?: return@mapNotNull null
        val name = map["name"] as? String ?: return@mapNotNull null
        LineItem(name = name, barcode = map["barcode"] as? String, qty = (map["qty"] as? Number)?.toInt() ?: 1)
    }
}

internal fun itemsToMaps(items: List<LineItem>): List<Map<String, Any?>> =
    items.map { mapOf("name" to it.name, "barcode" to it.barcode, "qty" to it.qty) }

internal fun DocumentSnapshot.toBusiness(): Business = Business(
    id = id,
    name = getString("name").orEmpty(),
    ownerName = getString("ownerName").orEmpty(),
    phone = getString("phone").orEmpty(),
    photoBase64 = getString("photoBase64").orEmpty(),
    type = businessTypeOf(getString("type")),
    address = getString("address").orEmpty(),
    latitude = get("latitude").asDoubleOrNull(),
    longitude = get("longitude").asDoubleOrNull(),
    openingHours = getString("openingHours").orEmpty(),
    shopPhotoBase64 = getString("shopPhotoBase64").orEmpty(),
    publicOptIn = get("publicOptIn").asBool(),
    verified = get("verified").asBool(),
    verificationStatus = verificationOf(getString("verificationStatus")),
    verificationReason = getString("verificationReason").orEmpty(),
    deleted = get("deleted").asBool(),
    createdAt = get("createdAt").asLong(),
    updatedAt = get("updatedAt").asLong(),
)

internal fun Business.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "ownerName" to ownerName,
    "phone" to phone,
    "photoBase64" to photoBase64,
    "type" to type.name.lowercase(),
    "address" to address,
    "latitude" to latitude,
    "longitude" to longitude,
    "openingHours" to openingHours,
    "shopPhotoBase64" to shopPhotoBase64,
    "publicOptIn" to publicOptIn,
    "verified" to verified,
    "verificationStatus" to verificationStatus.name.lowercase(),
    "verificationReason" to verificationReason,
    "deleted" to deleted,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

internal fun DocumentSnapshot.toContact(): Contact = Contact(
    id = id,
    name = getString("name").orEmpty(),
    phone = getString("phone").orEmpty(),
    photoBase64 = getString("photoBase64").orEmpty(),
    kind = contactKindOf(getString("kind")),
    balance = get("balance").asDouble(),
    creditLimit = get("creditLimit").asDoubleOrNull(),
    address = getString("address").orEmpty(),
    referenceName = getString("referenceName").orEmpty(),
    referencePhone = getString("referencePhone").orEmpty(),
    kycStatus = kycOf(getString("kycStatus")),
    deleted = get("deleted").asBool(),
    createdAt = get("createdAt").asLong(),
    updatedAt = get("updatedAt").asLong(),
)

internal fun Contact.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "phone" to phone,
    "photoBase64" to photoBase64,
    "kind" to kind.name.lowercase(),
    "balance" to balance,
    "creditLimit" to creditLimit,
    "address" to address,
    "referenceName" to referenceName,
    "referencePhone" to referencePhone,
    "kycStatus" to kycStatus.name.lowercase(),
    "deleted" to deleted,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

internal fun DocumentSnapshot.toTx(): LedgerTx = LedgerTx(
    id = id,
    contactId = getString("contactId").orEmpty(),
    contactName = getString("contactName").orEmpty(),
    direction = directionOf(getString("direction")),
    amount = get("amount").asDouble(),
    description = getString("description").orEmpty(),
    items = itemsOf(get("items")),
    dateMillis = get("dateMillis").asLong(),
    photoBase64 = getString("photoBase64").orEmpty(),
    smsRequested = get("smsRequested").asBool(),
    source = when (getString("source")) {
        "qr_confirm" -> TxSource.QR_CONFIRM
        "webhook" -> TxSource.WEBHOOK
        else -> TxSource.MANUAL
    },
    deleted = get("deleted").asBool(),
    createdAt = get("createdAt").asLong(),
)

internal fun LedgerTx.toMap(): Map<String, Any?> = mapOf(
    "contactId" to contactId,
    "contactName" to contactName,
    "direction" to direction.name.lowercase(),
    "amount" to amount,
    "description" to description,
    "items" to itemsToMaps(items),
    "dateMillis" to dateMillis,
    "photoBase64" to photoBase64,
    "smsRequested" to smsRequested,
    "source" to when (source) {
        TxSource.QR_CONFIRM -> "qr_confirm"
        TxSource.WEBHOOK -> "webhook"
        TxSource.MANUAL -> "manual"
    },
    "deleted" to deleted,
    "createdAt" to createdAt,
)

internal fun DocumentSnapshot.toCash(): CashEntry = CashEntry(
    id = id,
    kind = cashKindOf(getString("kind")),
    amount = get("amount").asDouble(),
    note = getString("note").orEmpty(),
    items = itemsOf(get("items")),
    dateMillis = get("dateMillis").asLong(),
    deleted = get("deleted").asBool(),
    createdAt = get("createdAt").asLong(),
)

internal fun CashEntry.toMap(): Map<String, Any?> = mapOf(
    "kind" to kind.name.lowercase(),
    "amount" to amount,
    "note" to note,
    "items" to itemsToMaps(items),
    "dateMillis" to dateMillis,
    "deleted" to deleted,
    "createdAt" to createdAt,
)

internal fun DocumentSnapshot.toProduct(): Product = Product(
    id = id,
    barcode = getString("barcode").orEmpty(),
    name = getString("name").orEmpty(),
    deleted = get("deleted").asBool(),
    updatedAt = get("updatedAt").asLong(),
)

internal fun paymentOf(data: Map<String, Any?>?): PaymentProfile {
    if (data == null) return PaymentProfile()
    return PaymentProfile(
        holderName = data["holderName"] as? String ?: "",
        bkash = data["bkash"] as? String ?: "",
        nagad = data["nagad"] as? String ?: "",
        rocket = data["rocket"] as? String ?: "",
        bankName = data["bankName"] as? String ?: "",
        bankAccount = data["bankAccount"] as? String ?: "",
        bankBranch = data["bankBranch"] as? String ?: "",
        banglaQr = data["banglaQr"] as? String ?: "",
    )
}

internal fun DocumentSnapshot.toPayment(): PaymentProfile = paymentOf(
    data?.mapValues { it.value },
)

internal fun DocumentSnapshot.toPublicShop(): PublicShop {
    @Suppress("UNCHECKED_CAST")
    val payment = get("payment") as? Map<String, Any?>
    return PublicShop(
        id = id,
        ownerUid = getString("ownerUid").orEmpty(),
        name = getString("name").orEmpty(),
        type = businessTypeOf(getString("type")),
        phone = getString("phone").orEmpty(),
        address = getString("address").orEmpty(),
        latitude = get("latitude").asDoubleOrNull(),
        longitude = get("longitude").asDoubleOrNull(),
        openingHours = getString("openingHours").orEmpty(),
        shopPhotoBase64 = getString("shopPhotoBase64").orEmpty(),
        verified = get("verified").asBool(),
        payment = paymentOf(payment),
        updatedAt = get("updatedAt").asLong(),
    )
}

internal fun DocumentSnapshot.toReminder(): Reminder = Reminder(
    id = id,
    contactId = getString("contactId").orEmpty(),
    contactName = getString("contactName").orEmpty(),
    phone = getString("phone").orEmpty(),
    channel = ReminderChannel.entries.firstOrNull { it.name.equals(getString("channel"), true) }
        ?: ReminderChannel.DEVICE,
    message = getString("message").orEmpty(),
    amount = get("amount").asDouble(),
    scheduledAt = get("scheduledAt").asLong(),
    sentAt = get("sentAt").asLong(),
    status = getString("status").orEmpty(),
    createdAt = get("createdAt").asLong(),
)

internal fun Reminder.toMap(): Map<String, Any?> = mapOf(
    "contactId" to contactId,
    "contactName" to contactName,
    "phone" to phone,
    "channel" to channel.name.lowercase(),
    "message" to message,
    "amount" to amount,
    "scheduledAt" to scheduledAt,
    "sentAt" to sentAt,
    "status" to status,
    "createdAt" to createdAt,
)

internal fun kycFrom(profile: DocumentSnapshot?, images: Map<String, String>): KycProfile = KycProfile(
    nidEncrypted = profile?.getString("nidEncrypted").orEmpty(),
    nidLast4 = profile?.getString("nidLast4").orEmpty(),
    address = profile?.getString("address").orEmpty(),
    referenceName = profile?.getString("referenceName").orEmpty(),
    referencePhone = profile?.getString("referencePhone").orEmpty(),
    photoBase64 = images["photo"].orEmpty(),
    nidFrontBase64 = images["nidFront"].orEmpty(),
    nidBackBase64 = images["nidBack"].orEmpty(),
    photoPath = profile?.getString("photoPath").orEmpty(),
    nidFrontPath = profile?.getString("nidFrontPath").orEmpty(),
    nidBackPath = profile?.getString("nidBackPath").orEmpty(),
    updatedAt = profile?.get("updatedAt").asLong(),
)

internal fun DocumentSnapshot.toVerification(images: Map<String, String> = emptyMap()): VerificationPacket = VerificationPacket(
    businessId = getString("businessId").orEmpty().ifBlank { id },
    ownerUid = getString("ownerUid").orEmpty(),
    businessName = getString("businessName").orEmpty(),
    nidEncrypted = getString("nidEncrypted").orEmpty(),
    nidLast4 = getString("nidLast4").orEmpty(),
    nidFrontBase64 = images["nidFront"] ?: getString("nidFrontBase64").orEmpty(),
    nidBackBase64 = images["nidBack"] ?: getString("nidBackBase64").orEmpty(),
    selfieBase64 = images["selfie"] ?: getString("selfieBase64").orEmpty(),
    tradeLicenseBase64 = images["trade"] ?: getString("tradeLicenseBase64").orEmpty(),
    nidFrontPath = getString("nidFrontPath").orEmpty(),
    nidBackPath = getString("nidBackPath").orEmpty(),
    selfiePath = getString("selfiePath").orEmpty(),
    tradePath = getString("tradePath").orEmpty(),
    status = verificationOf(getString("status")),
    reason = getString("reason").orEmpty(),
    submittedAt = get("submittedAt").asLong(),
    reviewedAt = get("reviewedAt").asLong(),
)

internal fun Throwable.bangla(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("PERMISSION_DENIED", true) -> "এই তথ্য দেখার অনুমতি নেই"
        raw.contains("UNAVAILABLE", true) -> "অফলাইন। তথ্য পরে সিঙ্ক হবে"
        raw.contains("placeholder", true) -> raw
        raw.isBlank() -> "কিছু একটা ভুল হয়েছে"
        else -> raw
    }
}
