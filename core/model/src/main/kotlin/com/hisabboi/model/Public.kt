package com.hisabboi.model

/**
 * Allow-listed public projection.
 * KYC, NID, selfies, and verification files must never appear here.
 * [verified] is copied from the business document; security rules reject a mismatch.
 */
object PublicProjection {
    val SHOP_KEYS = setOf(
        "ownerUid", "name", "type", "phone", "address", "latitude", "longitude",
        "openingHours", "shopPhotoBase64", "verified", "payment", "updatedAt",
    )
    val PAYMENT_KEYS = setOf(
        "holderName", "bkash", "nagad", "rocket", "bankName", "bankAccount", "bankBranch", "banglaQr",
    )

    fun shop(business: Business, ownerUid: String, payment: PaymentProfile, now: Long): PublicShop =
        PublicShop(
            id = business.id,
            ownerUid = ownerUid,
            name = business.name,
            type = business.type,
            phone = business.phone,
            address = business.address,
            latitude = business.latitude,
            longitude = business.longitude,
            openingHours = business.openingHours,
            shopPhotoBase64 = business.shopPhotoBase64,
            verified = business.verified,
            payment = payment,
            updatedAt = now,
        )

    fun shopMap(shop: PublicShop): Map<String, Any?> = mapOf(
        "ownerUid" to shop.ownerUid,
        "name" to shop.name,
        "type" to shop.type.name.lowercase(),
        "phone" to shop.phone,
        "address" to shop.address,
        "latitude" to shop.latitude,
        "longitude" to shop.longitude,
        "openingHours" to shop.openingHours,
        "shopPhotoBase64" to shop.shopPhotoBase64,
        "verified" to shop.verified,
        "payment" to paymentMap(shop.payment),
        "updatedAt" to shop.updatedAt,
    )

    fun paymentMap(p: PaymentProfile): Map<String, Any?> = mapOf(
        "holderName" to p.holderName,
        "bkash" to p.bkash,
        "nagad" to p.nagad,
        "rocket" to p.rocket,
        "bankName" to p.bankName,
        "bankAccount" to p.bankAccount,
        "bankBranch" to p.bankBranch,
        "banglaQr" to p.banglaQr,
    )
}

/**
 * QR payload shown to a customer.
 * A Bangla QR / EMV merchant string is encoded as-is so the customer's own MFS or bank app can pay
 * the shopkeeper. Wallet numbers are encoded as readable text. This app never takes the payment.
 */
object PaymentQrContent {
    fun build(profile: PaymentProfile, shopName: String): String {
        if (profile.banglaQr.isNotBlank()) return profile.banglaQr.trim()
        return buildString {
            appendLine(shopName.ifBlank { "হিসাব বই" })
            if (profile.holderName.isNotBlank()) appendLine("নাম: ${profile.holderName}")
            if (profile.bkash.isNotBlank()) appendLine("bKash: ${profile.bkash}")
            if (profile.nagad.isNotBlank()) appendLine("Nagad: ${profile.nagad}")
            if (profile.rocket.isNotBlank()) appendLine("Rocket: ${profile.rocket}")
            if (profile.bankAccount.isNotBlank()) {
                appendLine("ব্যাংক: ${profile.bankName} ${profile.bankAccount}")
            }
            append("সরাসরি দোকানদারের অ্যাকাউন্টে পরিশোধ করুন")
        }.trim()
    }
}

object Templates {
    fun transaction(business: String, direction: Direction, amount: Double, balance: Double): String {
        val dir = direction.label()
        val side = if (balance >= 0) "পাবো" else "দেবো"
        return "$business: $dir ${Money.format(amount)}। বর্তমান ব্যালেন্স ${Money.format(kotlin.math.abs(balance))} ($side)। — হিসাব বই"
    }

    fun reminder(business: String, amount: Double): String =
        "$business: বকেয়া তাগাদা। আপনার কাছে পাবো ${Money.format(amount)}। অনুগ্রহ করে পরিশোধ করুন। — হিসাব বই"
}

object BnDate {
    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
    )

    fun format(millis: Long): String {
        val z = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        return Money.digits("${z.dayOfMonth} ${months[z.monthValue - 1]} ${z.year}")
    }

    fun formatTime(millis: Long): String {
        val z = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        val hour = z.hour
        val minute = z.minute.toString().padStart(2, '0')
        return Money.digits("$hour:$minute")
    }

    fun startOfDay(millis: Long = System.currentTimeMillis()): Long {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun endOfDay(millis: Long = System.currentTimeMillis()): Long = startOfDay(millis) + 86_400_000L - 1
}
