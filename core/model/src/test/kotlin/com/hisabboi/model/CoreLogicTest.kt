package com.hisabboi.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test
    fun banglaGroupingAndDigits() {
        assertEquals("১,০০০.৫০", Money.format(1000.5, withSymbol = false))
        assertEquals("১২,৩৪,৫৬৭.০০", Money.format(1234567.0, withSymbol = false))
        assertEquals(279800.0, Money.parse("২,৭৯,৮০০")!!, 0.001)
    }

    @Test
    fun totalsSplitReceiveAndPay() {
        val (receive, pay) = BalanceMath.totals(listOf(100.0, -40.0, 0.0, 25.5))
        assertEquals(125.5, receive, 0.001)
        assertEquals(40.0, pay, 0.001)
    }

    @Test
    fun gaveIncreasesReceivable() {
        assertEquals(50.0, BalanceMath.delta(Direction.GAVE, 50.0), 0.001)
        assertEquals(-20.0, BalanceMath.delta(Direction.RECEIVED, 20.0), 0.001)
        assertTrue(BalanceMath.exceedsCredit(80.0, Direction.GAVE, 30.0, 100.0))
        assertFalse(BalanceMath.exceedsCredit(80.0, Direction.RECEIVED, 30.0, 100.0))
    }

    @Test
    fun publicMapHasNoPrivateFields() {
        val shop = PublicProjection.shop(
            Business(id = "b1", name = "দোকান", verified = true, address = "ঢাকা"),
            ownerUid = "u1",
            payment = PaymentProfile(bkash = "01700000000", holderName = "রবিন"),
            now = 10L,
        )
        val map = PublicProjection.shopMap(shop)
        assertEquals(PublicProjection.SHOP_KEYS, map.keys)
        val payment = map.getValue("payment") as Map<*, *>
        assertEquals(PublicProjection.PAYMENT_KEYS, payment.keys)
        val blob = map.toString() + payment.toString()
        assertFalse(blob.contains("nid", ignoreCase = true))
        assertFalse(blob.contains("kyc", ignoreCase = true))
        assertFalse(blob.contains("selfie", ignoreCase = true))
    }

    @Test
    fun pinHashVerifiesAndRejects() {
        val salt = PinHash.salt()
        val hashed = PinHash.hash("1234", salt)
        assertTrue(PinHash.verify("1234", salt, hashed))
        assertFalse(PinHash.verify("9999", salt, hashed))
    }

    @Test
    fun banglaQrPassesThroughUntouched() {
        val emv = "00020101021226570012com.example"
        val text = PaymentQrContent.build(PaymentProfile(banglaQr = emv, bkash = "017"), "দোকান")
        assertEquals(emv, text)
    }
}
