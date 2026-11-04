package com.hisabboi.model

enum class BusinessType { GROCERY, PHARMACY, CLOTHING, ELECTRONICS, RESTAURANT, WHOLESALE, OTHER }

fun BusinessType.label(): String = when (this) {
    BusinessType.GROCERY -> "মুদি"
    BusinessType.PHARMACY -> "ফার্মেসি"
    BusinessType.CLOTHING -> "কাপড়"
    BusinessType.ELECTRONICS -> "ইলেকট্রনিক্স"
    BusinessType.RESTAURANT -> "রেস্তোরাঁ"
    BusinessType.WHOLESALE -> "পাইকারি"
    BusinessType.OTHER -> "অন্যান্য"
}

fun businessTypeOf(raw: String?): BusinessType =
    BusinessType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BusinessType.GROCERY

enum class ContactKind { CUSTOMER, SUPPLIER, BOTH }

fun ContactKind.label(): String = when (this) {
    ContactKind.CUSTOMER -> "কাস্টমার"
    ContactKind.SUPPLIER -> "সাপ্লায়ার"
    ContactKind.BOTH -> "উভয়"
}

fun contactKindOf(raw: String?): ContactKind = when (raw?.lowercase()) {
    "supplier" -> ContactKind.SUPPLIER
    "both" -> ContactKind.BOTH
    else -> ContactKind.CUSTOMER
}

enum class Direction { GAVE, RECEIVED }

fun Direction.label(): String = if (this == Direction.GAVE) "দিলাম" else "পেলাম"

fun directionOf(raw: String?): Direction =
    if (raw.equals("received", ignoreCase = true)) Direction.RECEIVED else Direction.GAVE

enum class CashKind { SALE, PURCHASE, EXPENSE, OWNER_IN, OWNER_OUT, QR_IN }

fun CashKind.label(): String = when (this) {
    CashKind.SALE -> "ক্যাশ বিক্রি"
    CashKind.PURCHASE -> "ক্যাশ কেনা"
    CashKind.EXPENSE -> "খরচ"
    CashKind.OWNER_IN -> "মালিক জমা"
    CashKind.OWNER_OUT -> "মালিক তোলা"
    CashKind.QR_IN -> "QR গ্রহণ (নিজে নিশ্চিত)"
}

fun CashKind.signed(amount: Double): Double = when (this) {
    CashKind.SALE, CashKind.OWNER_IN, CashKind.QR_IN -> amount
    CashKind.PURCHASE, CashKind.EXPENSE, CashKind.OWNER_OUT -> -amount
}

fun cashKindOf(raw: String?): CashKind =
    CashKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CashKind.SALE

enum class VerificationStatus { NONE, PENDING, VERIFIED, REJECTED }

fun verificationOf(raw: String?): VerificationStatus =
    VerificationStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VerificationStatus.NONE

enum class KycStatus { NONE, PARTIAL, COMPLETE }

fun kycOf(raw: String?): KycStatus =
    KycStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: KycStatus.NONE

enum class LockMode { OFF, PIN, PIN_AND_BIOMETRIC }

enum class ReminderChannel { GATEWAY, DEVICE, WHATSAPP, IMAGE }

fun ReminderChannel.label(): String = when (this) {
    ReminderChannel.GATEWAY -> "এসএমএস গেটওয়ে"
    ReminderChannel.DEVICE -> "ফোনের এসএমএস"
    ReminderChannel.WHATSAPP -> "হোয়াটসঅ্যাপ"
    ReminderChannel.IMAGE -> "ছবি কার্ড"
}

enum class TxSource { MANUAL, QR_CONFIRM, WEBHOOK }

data class SessionUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAdmin: Boolean,
)

data class Business(
    val id: String = "",
    val name: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val photoBase64: String = "",
    val type: BusinessType = BusinessType.GROCERY,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openingHours: String = "",
    val shopPhotoBase64: String = "",
    val publicOptIn: Boolean = false,
    val verified: Boolean = false,
    val verificationStatus: VerificationStatus = VerificationStatus.NONE,
    val verificationReason: String = "",
    val deleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Contact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val photoBase64: String = "",
    val kind: ContactKind = ContactKind.CUSTOMER,
    val balance: Double = 0.0,
    val creditLimit: Double? = null,
    val address: String = "",
    val referenceName: String = "",
    val referencePhone: String = "",
    val kycStatus: KycStatus = KycStatus.NONE,
    val deleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val kycVerified: Boolean get() = kycStatus == KycStatus.COMPLETE
}

data class LineItem(
    val name: String,
    val barcode: String? = null,
    val qty: Int = 1,
)

fun List<LineItem>.asNote(): String = joinToString("\n") { item ->
    if (item.qty > 1) "${item.name} × ${Money.digits(item.qty.toString())}" else item.name
}

data class LedgerTx(
    val id: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val direction: Direction = Direction.GAVE,
    val amount: Double = 0.0,
    val description: String = "",
    val items: List<LineItem> = emptyList(),
    val dateMillis: Long = 0L,
    val photoBase64: String = "",
    val smsRequested: Boolean = false,
    val source: TxSource = TxSource.MANUAL,
    val deleted: Boolean = false,
    val createdAt: Long = 0L,
)

data class CashEntry(
    val id: String = "",
    val kind: CashKind = CashKind.SALE,
    val amount: Double = 0.0,
    val note: String = "",
    val items: List<LineItem> = emptyList(),
    val dateMillis: Long = 0L,
    val deleted: Boolean = false,
    val createdAt: Long = 0L,
)

data class Product(
    val id: String = "",
    val barcode: String = "",
    val name: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long = 0L,
)

/**
 * The shopkeeper's own receiving details.
 * Customers pay this account directly. হিসাব বই never receives, holds, routes, or settles money.
 */
data class PaymentProfile(
    val holderName: String = "",
    val bkash: String = "",
    val nagad: String = "",
    val rocket: String = "",
    val bankName: String = "",
    val bankAccount: String = "",
    val bankBranch: String = "",
    val banglaQr: String = "",
) {
    fun hasAny(): Boolean = listOf(bkash, nagad, rocket, bankAccount, banglaQr, holderName).any { it.isNotBlank() }
}

data class PublicShop(
    val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val type: BusinessType = BusinessType.GROCERY,
    val phone: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openingHours: String = "",
    val shopPhotoBase64: String = "",
    val verified: Boolean = false,
    val payment: PaymentProfile = PaymentProfile(),
    val updatedAt: Long = 0L,
)

data class KycProfile(
    val nidEncrypted: String = "",
    val nidLast4: String = "",
    val address: String = "",
    val referenceName: String = "",
    val referencePhone: String = "",
    val photoBase64: String = "",
    val nidFrontBase64: String = "",
    val nidBackBase64: String = "",
    val photoPath: String = "",
    val nidFrontPath: String = "",
    val nidBackPath: String = "",
    val updatedAt: Long = 0L,
) {
    fun isComplete(): Boolean =
        nidEncrypted.isNotBlank() && nidFrontBase64.isNotBlank() && nidBackBase64.isNotBlank() && photoBase64.isNotBlank()
}

data class VerificationPacket(
    val businessId: String = "",
    val ownerUid: String = "",
    val businessName: String = "",
    val nidEncrypted: String = "",
    val nidLast4: String = "",
    val nidFrontBase64: String = "",
    val nidBackBase64: String = "",
    val selfieBase64: String = "",
    val tradeLicenseBase64: String = "",
    val nidFrontPath: String = "",
    val nidBackPath: String = "",
    val selfiePath: String = "",
    val tradePath: String = "",
    val status: VerificationStatus = VerificationStatus.NONE,
    val reason: String = "",
    val submittedAt: Long = 0L,
    val reviewedAt: Long = 0L,
)

data class Reminder(
    val id: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val phone: String = "",
    val channel: ReminderChannel = ReminderChannel.DEVICE,
    val message: String = "",
    val amount: Double = 0.0,
    val scheduledAt: Long = 0L,
    val sentAt: Long = 0L,
    val status: String = "pending",
    val createdAt: Long = 0L,
)

data class TrashBundle(
    val contacts: List<Contact> = emptyList(),
    val transactions: List<LedgerTx> = emptyList(),
    val cash: List<CashEntry> = emptyList(),
    val businesses: List<Business> = emptyList(),
)

data class ScanPayload(val id: Long, val items: List<LineItem>)

/** Result of a pluggable identity check. Manual admin review is the default provider. */
data class IdentityOutcome(
    val status: VerificationStatus,
    val reason: String = "",
)
