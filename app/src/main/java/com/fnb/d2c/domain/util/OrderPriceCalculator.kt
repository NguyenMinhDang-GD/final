package com.fnb.d2c.domain.util

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.Voucher
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class PriceBreakdown(
    val subtotalAmount: Long,
    val discountAmount: Long,
    val shippingFee: Long,
    val totalAmount: Long,
    val appliedVoucher: Voucher? = null,
    val voucherError: String? = null
)

object OrderPriceCalculator {

    /**
     * Strict Pricing Formula:
     *   subtotal = sum(max(0, basePrice + sum(optionExtraPrice)) * quantity)
     *   discount = min(subtotal, rawVoucherDiscount)  <-- NEVER touches shippingFee!
     *   total    = (subtotal - discount) + shippingFee
     */
    fun calculate(
        items: List<CartLineItem>,
        shippingFee: Long,
        voucher: Voucher?,
        now: Instant = Instant.now()
    ): PriceBreakdown {
        val safeShipping = max(0L, shippingFee)
        val subtotal = items.sumOf { line ->
            val unitPrice = max(0L, line.basePrice + line.selectedOptions.sumOf { it.extraPrice })
            unitPrice * max(0, line.quantity)
        }

        if (voucher == null) {
            return PriceBreakdown(
                subtotalAmount = subtotal,
                discountAmount = 0L,
                shippingFee = safeShipping,
                totalAmount = subtotal + safeShipping
            )
        }

        val validationError = validateVoucher(voucher, subtotal, now)
        if (validationError != null) {
            return PriceBreakdown(
                subtotalAmount = subtotal,
                discountAmount = 0L,
                shippingFee = safeShipping,
                totalAmount = subtotal + safeShipping,
                appliedVoucher = null,
                voucherError = validationError
            )
        }

        val rawDiscount = when (voucher.discountType) {
            DiscountType.PERCENT -> ((subtotal.toDouble() * voucher.discountValue.toDouble()) / 100.0).roundToLong()
            DiscountType.FIXED -> voucher.discountValue
        }

        val cappedDiscount = min(subtotal, max(0L, rawDiscount))
        val total = (subtotal - cappedDiscount) + safeShipping

        return PriceBreakdown(
            subtotalAmount = subtotal,
            discountAmount = cappedDiscount,
            shippingFee = safeShipping,
            totalAmount = total,
            appliedVoucher = voucher,
            voucherError = null
        )
    }

    fun validateVoucher(voucher: Voucher, subtotalAmount: Long, now: Instant = Instant.now()): String? {
        if (!voucher.isActive) {
            return "Mã ${voucher.code} hiện đang tạm ngưng."
        }
        val endInstant = runCatching { Instant.parse(voucher.endAtIso) }.getOrNull()
        if (endInstant != null && !endInstant.isAfter(now)) {
            return "Mã ${voucher.code} đã hết hạn sử dụng."
        }
        if (voucher.usedCount >= voucher.usageLimit) {
            return "Mã ${voucher.code} đã hết lượt sử dụng (${voucher.usageLimit}/${voucher.usageLimit})."
        }
        if (subtotalAmount < voucher.minOrderValue) {
            return "Đơn tối thiểu ${CurrencyAndTimeFormatter.formatVnd(voucher.minOrderValue)} mới áp dụng được mã ${voucher.code}."
        }
        return null
    }
}
