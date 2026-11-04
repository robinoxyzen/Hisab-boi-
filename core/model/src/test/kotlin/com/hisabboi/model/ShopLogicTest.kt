package com.hisabboi.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopLogicTest {
    @Test
    fun offlineFirestoreErrorBecomesTheFriendlyBanglaSentence() {
        val raw = "Failed to get document because the client is offline."
        val shown = UserFacingError.auth(raw)
        assertEquals(
            "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যাচ্ছে না। আবার চেষ্টা করুন।",
            shown,
        )
        assertFalse(shown.contains("Failed"))
        assertFalse(shown.contains("Exception"))
        assertFalse(shown.contains("offline"))
    }

    @Test
    fun otherRawExceptionsStayHidden() {
        assertEquals(UserFacingError.GENERIC, UserFacingError.auth("java.lang.IllegalStateException: boom"))
        assertEquals(UserFacingError.GENERIC, UserFacingError.auth(null))
        assertEquals(UserFacingError.DENIED, UserFacingError.auth("PERMISSION_DENIED: Missing or insufficient permissions."))
        val bangla = "গুগল ক্রেডেনশিয়াল পাওয়া যায়নি"
        assertEquals(bangla, UserFacingError.auth(bangla))
    }

    @Test
    fun profileReadRetriesThenUsesCacheOrContinues() {
        assertEquals(
            ProfileRead.Step.USE_SERVER,
            ProfileRead.next(serverSucceeded = true, serverExists = true, cacheExists = false, transient = false, attempt = 0),
        )
        assertEquals(
            ProfileRead.Step.CREATE_AND_CONTINUE,
            ProfileRead.next(serverSucceeded = true, serverExists = false, cacheExists = false, transient = false, attempt = 0),
        )
        assertEquals(
            ProfileRead.Step.USE_CACHE,
            ProfileRead.next(serverSucceeded = false, serverExists = false, cacheExists = true, transient = true, attempt = 0),
        )
        assertEquals(
            ProfileRead.Step.RETRY,
            ProfileRead.next(serverSucceeded = false, serverExists = false, cacheExists = false, transient = true, attempt = 0),
        )
        assertEquals(
            ProfileRead.Step.CONTINUE_WITHOUT_DOC,
            ProfileRead.next(serverSucceeded = false, serverExists = false, cacheExists = false, transient = true, attempt = 2),
        )
    }

    @Test
    fun avatarFallsBackFromUploadToGoogleToInitials() {
        val uploaded = "https://firebasestorage.googleapis.com/v0/b/hisab-boi-5af26.appspot.com/o/users%2Fu%2Fprofile.jpg?alt=media"
        val google = "https://lh3.googleusercontent.com/a/photo"
        assertEquals(uploaded, ProfilePhoto.resolve(uploaded, google, hideGoogle = false))
        assertEquals(google, ProfilePhoto.resolve(google, google, hideGoogle = false))
        assertEquals(google, ProfilePhoto.resolve(null, google, hideGoogle = false))
        assertNull(ProfilePhoto.resolve(null, google, hideGoogle = true))
        assertNull(ProfilePhoto.resolve(null, null, hideGoogle = false))
        assertTrue(ProfilePhoto.isUploaded(uploaded))
        assertFalse(ProfilePhoto.isUploaded(google))
    }

    @Test
    fun catalogFindsOwnBarcodeAndSearches() {
        val milk = Product(id = "1", barcode = "8941000100012", name = "দুধ", sellPrice = 80.0, stock = 4.0, unit = "পিস")
        val gone = milk.copy(id = "2", barcode = "111", name = "পুরনো", deleted = true)
        val rice = Product(id = "3", barcode = "8941000100099", name = "চাল", sellPrice = 70.0, stock = 2.5, unit = "কেজি")
        val all = listOf(rice, gone, milk)
        assertEquals("1", Catalog.find(all, "8941000100012")?.id)
        assertNull(Catalog.find(all, "111"))
        assertEquals(listOf("চাল", "দুধ"), Catalog.search(all, "").map { it.name })
        assertEquals(listOf("চাল"), Catalog.search(all, "চাল").map { it.name })
        assertEquals(listOf("দুধ"), Catalog.search(all, "8941000100012").map { it.name })
        assertEquals(0.0, Catalog.adjustedStock(1.0, -5.0), 0.001)
        assertEquals(6.0, Catalog.adjustedStock(4.0, 2.0), 0.001)
        assertEquals("স্টক ৪ পিস", Catalog.stockLabel(4.0, "পিস"))
    }

    @Test
    fun settingsSaveRequiresAChangeAndAMatchingPin() {
        val base = SettingsDraft.Input(
            sms = "device",
            notifications = true,
            mode = LockMode.PIN,
            pin = "",
            confirm = "",
            timeout = "60",
            savedSms = "device",
            savedNotifications = true,
            savedMode = LockMode.PIN,
            savedTimeout = 60,
            pinAlreadySet = true,
        )
        assertFalse(SettingsDraft.dirty(base))
        assertNull(SettingsDraft.validate(base))
        val changed = base.copy(sms = "gateway")
        assertTrue(SettingsDraft.dirty(changed))
        val badTimeout = base.copy(timeout = "2")
        assertEquals("টাইমআউট 15 থেকে 86400 সেকেন্ডের মধ্যে দিন", SettingsDraft.validate(badTimeout))
        val newPin = base.copy(pinAlreadySet = false, pin = "1234", confirm = "9999")
        assertEquals("পিন মিলছে না", SettingsDraft.validate(newPin))
        assertNull(SettingsDraft.validate(newPin.copy(confirm = "1234")))
    }

    @Test
    fun keypadEvaluatesLeftToRight() {
        assertEquals("3", AmountKeypad.push("1+2", "="))
        assertEquals("48", AmountKeypad.plain(AmountKeypad.evaluate("20-4×3")!!))
        assertNull(AmountKeypad.evaluate("8÷0"))
        assertEquals("5", AmountKeypad.push("0", "5"))
    }

    @Test
    fun cashBookKeepsARunningBalance() {
        val opening = CashEntry(id = "a", kind = CashKind.SALE, amount = 100.0, dateMillis = 1)
        val sale = CashEntry(id = "b", kind = CashKind.SALE, amount = 40.0, dateMillis = 10)
        val expense = CashEntry(id = "c", kind = CashKind.EXPENSE, amount = 15.0, dateMillis = 11)
        val rows = CashBook.todayWithRunning(listOf(opening, expense, sale), dayStart = 5)
        assertEquals(140.0, rows[0].second, 0.001)
        assertEquals(125.0, rows[1].second, 0.001)
    }

    @Test
    fun noticeToneMapsSaveAndFailure() {
        assertEquals(NoticeKind.SUCCESS, NoticeTone.of("সেভ হয়েছে"))
        assertEquals(NoticeKind.FAILED, NoticeTone.of("৪ থেকে ৬ সংখ্যার পিন দিন"))
        assertEquals(NoticeKind.PENDING, NoticeTone.of("ছবি আপলোড হচ্ছে…"))
        assertEquals(NoticeKind.SUCCESS, NoticeTone.verification(VerificationStatus.VERIFIED).first)
        assertEquals("ব্যর্থ", NoticeTone.verification(VerificationStatus.REJECTED).second)
        assertEquals(NoticeKind.DONE, NoticeTone.reminder("sent").first)
    }

    @Test
    fun centerSquareCropAndEdge() {
        assertEquals(Triple(50, 0, 100), ImageFrame.centerSquare(200, 100))
        assertEquals(Triple(0, 40, 80), ImageFrame.centerSquare(80, 160))
        assertEquals(512, ImageFrame.targetEdge(2000, 512))
        assertEquals(80, ImageFrame.targetEdge(80, 512))
    }
}
