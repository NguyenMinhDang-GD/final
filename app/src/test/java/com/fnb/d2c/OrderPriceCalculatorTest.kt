package com.fnb.d2c

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.model.Voucher
import com.fnb.d2c.domain.util.OrderPriceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderPriceCalculatorTest {

    private val now = Instant.parse("2026-11-24T12:00:00Z")

    @Test
    fun `supports negative kid-bowl option price and percentage voucher`() {
        val kidBowl = CartLineItem(
            cartKey = "k1",
            menuItemId = "dish-1",
            menuItemName = "Bún Riêu",
            imageUrl = "",
            basePrice = 55_000L,
            selectedOptions = listOf(
                SelectedOptionSnapshot("opt-kid", "Size", "Tô trẻ em", -5_000L),
                SelectedOptionSnapshot("opt-cha", "Topping", "Thêm chả", 15_000L)
            ),
            specialNote = "",
            quantity = 2
        )
        // Unit price = 55k - 5k + 15k = 65k; x2 = 130k
        val voucher10Pct = Voucher(
            id = "v1",
            code = "GIAM10",
            discountType = DiscountType.PERCENT,
            discountValue = 10L,
            minOrderValue = 100_000L,
            usageLimit = 10,
            usedCount = 2,
            endAtIso = now.plus(5, ChronoUnit.DAYS).toString(),
            isActive = true
        )

        val breakdown = OrderPriceCalculator.calculate(
            items = listOf(kidBowl),
            shippingFee = 20_000L,
            voucher = voucher10Pct,
            now = now
        )

        assertNull(breakdown.voucherError)
        assertEquals(130_000L, breakdown.subtotalAmount)
        assertEquals(13_000L, breakdown.discountAmount)
        assertEquals(20_000L, breakdown.shippingFee)
        assertEquals(137_000L, breakdown.totalAmount)
    }

    @Test
    fun `voucher larger than subtotal caps at subtotal and NEVER deducts shipping fee`() {
        val singleBowl = CartLineItem(
            cartKey = "k2",
            menuItemId = "dish-1",
            menuItemName = "Bún Chả Cá",
            imageUrl = "",
            basePrice = 50_000L,
            selectedOptions = emptyList(),
            specialNote = "",
            quantity = 1
        )
        val hugeVoucher = Voucher(
            id = "v2",
            code = "SIEUVIP100K",
            discountType = DiscountType.FIXED,
            discountValue = 100_000L,
            minOrderValue = 40_000L,
            usageLimit = 10,
            usedCount = 0,
            endAtIso = now.plus(5, ChronoUnit.DAYS).toString(),
            isActive = true
        )

        val breakdown = OrderPriceCalculator.calculate(
            items = listOf(singleBowl),
            shippingFee = 25_000L,
            voucher = hugeVoucher,
            now = now
        )

        assertEquals(50_000L, breakdown.subtotalAmount)
        assertEquals(50_000L, breakdown.discountAmount) // Capped at subtotal!
        assertEquals(25_000L, breakdown.shippingFee)
        assertEquals(25_000L, breakdown.totalAmount) // Shipping fee intact!
    }

    @Test
    fun `rejects voucher when usage limit exhausted or below min order`() {
        val bowl = CartLineItem("k", "d", "Bún", "", 50_000L, emptyList(), "", 1)
        val exhaustedVoucher = Voucher(
            id = "v3",
            code = "HETMA",
            discountType = DiscountType.FIXED,
            discountValue = 15_000L,
            minOrderValue = 30_000L,
            usageLimit = 5,
            usedCount = 5,
            endAtIso = now.plus(2, ChronoUnit.DAYS).toString(),
            isActive = true
        )

        val result = OrderPriceCalculator.calculate(listOf(bowl), 15_000L, exhaustedVoucher, now)
        assertNotNull(result.voucherError)
        assertEquals(0L, result.discountAmount)
        assertEquals(65_000L, result.totalAmount)
    }
}
