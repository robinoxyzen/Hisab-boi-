package com.hisabboi.model

/**
 * Shopkeeper-facing copy. Screens must not show raw exception text.
 */
object UserFacingError {
    const val OFFLINE = "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যাচ্ছে না। আবার চেষ্টা করুন।"
    const val GENERIC = "এখন সম্পন্ন হয়নি। আবার চেষ্টা করুন।"
    const val DENIED = "এই তথ্য দেখার অনুমতি নেই।"

    fun auth(raw: String?): String {
        if (raw.isNullOrBlank()) return GENERIC
        if (isOffline(raw)) return OFFLINE
        if (raw.contains("PERMISSION_DENIED", ignoreCase = true)) return DENIED
        if (isFriendlyBangla(raw)) return raw
        return GENERIC
    }

    fun isOffline(raw: String): Boolean {
        val m = raw.lowercase()
        return m.contains("offline")
            || m.contains("unavailable")
            || m.contains("failed to get document")
            || m.contains("unable to resolve host")
            || m.contains("network")
            || m.contains("connection abort")
            || m.contains("connection reset")
            || m.contains("failed_precondition")
            || m.contains("deadline")
            || m.contains("timed out")
            || m.contains("timeout")
            || m.contains("enetunreach")
            || m.contains("econnrefused")
    }

    fun isFriendlyBangla(raw: String): Boolean {
        if (raw.length > 220 || raw.contains('\n')) return false
        if (raw.contains("Exception") || raw.contains("Firebase") || raw.contains("http")) return false
        return raw.any { it in '\u0980'..'\u09FF' }
    }
}

/**
 * What to do when the one-shot user-document read fails.
 * An offline or just-created database must not block sign-in.
 */
object ProfileRead {
    enum class Step { USE_SERVER, USE_CACHE, RETRY, CREATE_AND_CONTINUE, CONTINUE_WITHOUT_DOC }

    fun isTransient(message: String?): Boolean = !message.isNullOrBlank() && UserFacingError.isOffline(message)

    fun next(
        serverSucceeded: Boolean,
        serverExists: Boolean,
        cacheExists: Boolean,
        transient: Boolean,
        attempt: Int,
        maxAttempts: Int = 3,
    ): Step = when {
        serverSucceeded && serverExists -> Step.USE_SERVER
        serverSucceeded && !serverExists -> Step.CREATE_AND_CONTINUE
        cacheExists -> Step.USE_CACHE
        transient && attempt + 1 < maxAttempts -> Step.RETRY
        else -> Step.CONTINUE_WITHOUT_DOC
    }
}

/** Avatar URL: uploaded storage photo, then the Google photo, then initials (null). */
object ProfilePhoto {
    fun isUploaded(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("firebasestorage.googleapis.com") || url.contains(".appspot.com")
    }

    fun resolve(stored: String?, google: String?, hideGoogle: Boolean): String? {
        val uploaded = stored?.takeIf { isUploaded(it) }
        if (!uploaded.isNullOrBlank()) return uploaded
        if (hideGoogle) return null
        return google?.takeIf { it.isNotBlank() }
    }
}

object Catalog {
    val UNITS = listOf("পিস", "কেজি", "লিটার", "প্যাকেট")

    fun find(products: List<Product>, barcode: String): Product? {
        val code = barcode.trim()
        if (code.isEmpty()) return null
        return products.firstOrNull { !it.deleted && it.barcode == code }
    }

    fun search(products: List<Product>, query: String): List<Product> {
        val q = query.trim()
        return products
            .filter { product ->
                !product.deleted && (
                    q.isEmpty()
                        || product.name.contains(q, ignoreCase = true)
                        || product.barcode.contains(q, ignoreCase = true)
                    )
            }
            .sortedBy { it.name }
    }

    fun adjustedStock(current: Double, delta: Double): Double = (current + delta).coerceAtLeast(0.0)

    fun stockLabel(stock: Double, unit: String): String {
        val n = if (stock % 1.0 == 0.0) stock.toLong().toString() else String.format(java.util.Locale.US, "%.2f", stock)
        val u = unit.ifBlank { "পিস" }
        return "স্টক ${Money.digits(n)} $u"
    }
}

/** Center-square crop used before a profile or product photo is compressed. */
object ImageFrame {
    fun centerSquare(width: Int, height: Int): Triple<Int, Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val side = minOf(w, h)
        return Triple((w - side) / 2, (h - side) / 2, side)
    }

    fun targetEdge(side: Int, maxEdge: Int = 512): Int = minOf(side.coerceAtLeast(1), maxEdge.coerceAtLeast(1))
}

enum class NoticeKind { SUCCESS, DONE, FAILED, PENDING, INFO }

object NoticeTone {
    fun of(text: String): NoticeKind = when {
        text.contains("ভুল") || text.contains("হয়নি") || text.contains("যায়নি") || text.contains("পারেনি")
            || text.contains("মিলছে না") || text.contains("লাগবে") || text.contains("অনুমতি নেই")
            || (text.contains("পিন") && text.contains("দিন")) -> NoticeKind.FAILED
        text.contains("সম্পন্ন") || text.contains("পাঠানো হয়েছে") -> NoticeKind.DONE
        text.contains("অপেক্ষা") || text.contains("আপলোড হচ্ছে") || text.contains("অফলাইন") -> NoticeKind.PENDING
        text.contains("সেভ") || text.contains("সফল") || text.contains("আপডেট") || text.contains("যোগ হয়েছে")
            || text.contains("ফিরে এসেছে") || text.contains("সক্রিয়") -> NoticeKind.SUCCESS
        else -> NoticeKind.INFO
    }

    fun verification(status: VerificationStatus): Pair<NoticeKind, String> = when (status) {
        VerificationStatus.NONE -> NoticeKind.INFO to "জমা হয়নি"
        VerificationStatus.PENDING -> NoticeKind.PENDING to "অপেক্ষমাণ"
        VerificationStatus.VERIFIED -> NoticeKind.SUCCESS to "সফল"
        VerificationStatus.REJECTED -> NoticeKind.FAILED to "ব্যর্থ"
    }

    fun reminder(status: String): Pair<NoticeKind, String> = when (status.lowercase()) {
        "sent", "done", "complete" -> NoticeKind.DONE to "সম্পন্ন"
        "failed", "error" -> NoticeKind.FAILED to "ব্যর্থ"
        else -> NoticeKind.PENDING to "অপেক্ষমাণ"
    }
}

object SettingsDraft {
    const val TIMEOUT_MIN = 15
    const val TIMEOUT_MAX = 24 * 3600

    data class Input(
        val sms: String,
        val notifications: Boolean,
        val mode: LockMode,
        val pin: String,
        val confirm: String,
        val timeout: String,
        val savedSms: String,
        val savedNotifications: Boolean,
        val savedMode: LockMode,
        val savedTimeout: Int,
        val pinAlreadySet: Boolean,
    )

    fun dirty(input: Input): Boolean {
        val timeout = input.timeout.toIntOrNull()
        val pinDirty = input.mode != LockMode.OFF && (input.pin.isNotBlank() || input.confirm.isNotBlank())
        return input.sms != input.savedSms
            || input.notifications != input.savedNotifications
            || input.mode != input.savedMode
            || timeout != input.savedTimeout
            || pinDirty
    }

    /** Null when the draft can be saved. */
    fun validate(input: Input): String? {
        val timeout = input.timeout.toIntOrNull()
        if (timeout == null || timeout !in TIMEOUT_MIN..TIMEOUT_MAX) {
            return "টাইমআউট ${TIMEOUT_MIN} থেকে ${TIMEOUT_MAX} সেকেন্ডের মধ্যে দিন"
        }
        if (input.mode == LockMode.OFF) return null
        val pin = input.pin.filter { it.isDigit() }
        val confirm = input.confirm.filter { it.isDigit() }
        val typing = pin.isNotBlank() || confirm.isNotBlank()
        val mustSet = !input.pinAlreadySet
        if (!typing && !mustSet) return null
        if (pin.length !in 4..6) return "৪ থেকে ৬ সংখ্যার পিন দিন"
        if (confirm != pin) return "পিন মিলছে না"
        return null
    }
}

/** Left-to-right shop calculator used by the amount keypad. */
object AmountKeypad {
    private val ops = setOf('+', '-', '×', '÷')

    fun push(expression: String, key: String): String = when (key) {
        "C" -> ""
        "⌫" -> expression.dropLast(1)
        "=" -> evaluate(expression)?.let(::plain) ?: expression
        "+", "-", "×", "÷" -> when {
            expression.isEmpty() -> expression
            expression.last() in ops -> expression.dropLast(1) + key
            else -> expression + key
        }
        "." -> {
            val chunk = expression.takeLastWhile { it !in ops }
            when {
                chunk.contains('.') -> expression
                expression.isEmpty() || expression.last() in ops -> expression + "0."
                else -> expression + "."
            }
        }
        "00" -> if (expression.isEmpty() || expression.last() in ops) expression + "0" else expression + "00"
        else -> if (key.length == 1 && key[0].isDigit()) {
            if (expression == "0") key else expression + key
        } else expression
    }

    fun evaluate(expression: String): Double? {
        if (expression.isBlank()) return null
        val cleaned = expression.trimEnd { it in ops || it == '.' }
        if (cleaned.isBlank()) return null
        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        for (ch in cleaned) {
            if (ch in ops) {
                if (number.isEmpty()) return null
                tokens += number.toString()
                tokens += ch.toString()
                number.clear()
            } else number.append(ch)
        }
        if (number.isNotEmpty()) tokens += number.toString()
        var acc = tokens.firstOrNull()?.toDoubleOrNull() ?: return null
        var index = 1
        while (index + 1 < tokens.size) {
            val op = tokens[index]
            val next = tokens[index + 1].toDoubleOrNull() ?: return null
            acc = when (op) {
                "+" -> acc + next
                "-" -> acc - next
                "×" -> acc * next
                "÷" -> if (next == 0.0) return null else acc / next
                else -> return null
            }
            index += 2
        }
        if (index != tokens.size) return null
        if (!acc.isFinite()) return null
        return acc
    }

    fun plain(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", rounded)
    }
}

object CashBook {
    fun opening(entries: List<CashEntry>, dayStart: Long): Double =
        entries.filter { !it.deleted && it.dateMillis < dayStart }.sumOf { it.kind.signed(it.amount) }

    fun todayWithRunning(entries: List<CashEntry>, dayStart: Long): List<Pair<CashEntry, Double>> {
        var running = opening(entries, dayStart)
        return entries.filter { !it.deleted && it.dateMillis >= dayStart }
            .sortedBy { it.dateMillis }
            .map { entry ->
                running += entry.kind.signed(entry.amount)
                entry to running
            }
    }
}

/**
 * Platform biometric status codes. Kept here so the decision can be tested without Android.
 * Values match androidx.biometric.BiometricManager.
 */
object BiometricRead {
    const val SUCCESS = 0
    const val NONE_ENROLLED = 11
    const val NO_HARDWARE = 12
    const val UNSUPPORTED = -2

    data class Status(val hardware: Boolean, val enrolled: Boolean, val useStrong: Boolean)

    fun status(strong: Int, weak: Int): Status {
        fun sensor(code: Int) = code != NO_HARDWARE && code != UNSUPPORTED
        return Status(
            hardware = sensor(strong) || sensor(weak),
            enrolled = strong == SUCCESS || weak == SUCCESS,
            useStrong = strong == SUCCESS,
        )
    }
}

object LedgerList {
    fun lastDates(transactions: List<LedgerTx>): Map<String, Long> =
        transactions.filter { !it.deleted && it.contactId.isNotBlank() }
            .groupBy { it.contactId }
            .mapValues { (_, rows) -> rows.maxOf { it.dateMillis } }
}
