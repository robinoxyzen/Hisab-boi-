package com.hisabboi.model

import java.util.Locale
import kotlin.math.abs

/** Bangla digits and Bangladeshi grouping (12,34,567.89). */
object Money {
    private val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun digits(input: String): String = buildString {
        input.forEach { ch ->
            if (ch in '0'..'9') append(bn[ch - '0']) else append(ch)
        }
    }

    fun format(amount: Double, withSymbol: Boolean = true): String {
        val neg = amount < 0
        val raw = String.format(Locale.US, "%.2f", abs(amount))
        val parts = raw.split('.')
        val text = digits(group(parts[0]) + "." + parts[1])
        val sign = if (neg) "-" else ""
        return if (withSymbol) "$sign৳$text" else "$sign$text"
    }

    fun group(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val last3 = intPart.takeLast(3)
        var rest = intPart.dropLast(3)
        val chunks = ArrayDeque<String>()
        while (rest.length > 2) {
            chunks.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) chunks.addFirst(rest)
        return chunks.joinToString(",") + "," + last3
    }

    fun parse(raw: String): Double? {
        val normalized = buildString {
            raw.forEach { ch ->
                val i = "০১২৩৪৫৬৭৮৯".indexOf(ch)
                when {
                    i >= 0 -> append('0' + i)
                    ch == ',' || ch == '৳' || ch.isWhitespace() -> Unit
                    else -> append(ch)
                }
            }
        }
        return normalized.toDoubleOrNull()
    }
}

object BalanceMath {
    fun delta(direction: Direction, amount: Double): Double =
        if (direction == Direction.GAVE) amount else -amount

    /** Positive balances are receivable (পাবো). Negative are payable (দেবো). */
    fun totals(balances: List<Double>): Pair<Double, Double> {
        var receive = 0.0
        var pay = 0.0
        for (b in balances) {
            if (b > 0.0001) receive += b else if (b < -0.0001) pay += -b
        }
        return receive to pay
    }

    fun exceedsCredit(balance: Double, direction: Direction, amount: Double, limit: Double?): Boolean {
        if (limit == null) return false
        return balance + delta(direction, amount) > limit + 0.0001
    }
}

object Geo {
    fun km(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return 2 * r * Math.asin(Math.sqrt(s))
    }
}

object PinHash {
    fun salt(): ByteArray {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    fun hash(pin: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return toHex(factory.generateSecret(spec).encoded)
    }

    fun verify(pin: String, salt: ByteArray, expected: String): Boolean = hash(pin, salt) == expected

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
