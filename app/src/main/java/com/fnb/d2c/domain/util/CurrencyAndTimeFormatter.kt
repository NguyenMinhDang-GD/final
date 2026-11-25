package com.fnb.d2c.domain.util

import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderStatus
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object CurrencyAndTimeFormatter {

    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("vi-VN")).apply {
        groupingSeparator = '.'
    }
    private val formatter = DecimalFormat("#,###", symbols)
    private val dtFormatter = DateTimeFormatter.ofPattern("HH:mm • dd/MM/yyyy")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    fun formatVnd(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        return "$sign${formatter.format(abs(amount))}đ"
    }

    fun formatSignedOptionPrice(extraPrice: Long): String = when {
        extraPrice > 0 -> "+${formatVnd(extraPrice)}"
        extraPrice < 0 -> formatVnd(extraPrice)
        else -> "+0đ"
    }

    fun formatIsoTimestamp(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { dtFormatter.format(Instant.parse(iso)) }.getOrDefault(iso)
    }

    /**
     * Checks whether an active delivery order has exceeded the safe delivery threshold
     * (Default: 45 minutes = 2700 seconds; Demo fast-forward mode: 15 seconds).
     */
    fun getDeliveryElapsedSeconds(order: Order, now: Instant = Instant.now()): Long {
        if (order.status != OrderStatus.PICKED_UP && order.status != OrderStatus.DELIVERING) {
            return 0L
        }
        val baseIso = order.pickedUpAtIso ?: order.createdAtIso
        val start = runCatching { Instant.parse(baseIso) }.getOrNull() ?: return 0L
        return Duration.between(start, now).seconds.coerceAtLeast(0L)
    }

    fun isDeliveryOverdue(
        order: Order,
        thresholdSeconds: Long = 45 * 60L,
        now: Instant = Instant.now()
    ): Boolean = getDeliveryElapsedSeconds(order, now) >= thresholdSeconds
}
