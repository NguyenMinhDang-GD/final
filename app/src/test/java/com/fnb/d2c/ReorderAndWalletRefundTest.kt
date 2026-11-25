package com.fnb.d2c

import com.fnb.d2c.data.repository.DemoFnbRepository
import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.model.PaymentStatus
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.domain.util.ReorderHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderAndWalletRefundTest {

    @Test
    fun `prepaid wallet order deducts balance and rejecting order refunds 100 percent`() = runBlocking {
        val repo = DemoFnbRepository()
        val customer = repo.quickSwitchDemoRole(UserRole.CUSTOMER).getOrThrow()
        assertEquals(500_000L, customer.walletBalance)

        val cartLine = CartLineItem(
            cartKey = "dish-bun-rieu||không hành",
            menuItemId = "dish-bun-rieu",
            menuItemName = "Bún Riêu Cua Đặc Biệt",
            imageUrl = "",
            basePrice = 55_000L,
            selectedOptions = emptyList(),
            specialNote = "không hành",
            quantity = 2
        )

        val order = repo.placeOrder(
            deliveryAddress = "227 Nguyễn Văn Cừ, Q5",
            deliveryLat = 10.76,
            deliveryLng = 106.68,
            distanceKm = 1.5,
            shippingFee = 15_000L,
            voucherCode = "BUNRIEU15", // 15% of 110,000 = 16,500 -> total = 93,500 + 15,000 = 108,500
            paymentMethod = PaymentMethod.WALLET,
            cartItems = listOf(cartLine)
        ).getOrThrow()

        assertEquals(108_500L, order.totalAmount)
        assertEquals(PaymentStatus.PAID, order.paymentStatus)
        assertEquals(391_500L, repo.currentUser.value?.walletBalance)

        // Merchant rejects due to out-of-stock -> automatic full wallet refund!
        repo.rejectOrderAndRefund(order.id, "Hết riêu cua đồng").getOrThrow()

        val updatedOrder = repo.orders.value.first { it.id == order.id }
        assertEquals(OrderStatus.REFUNDED, updatedOrder.status)
        assertEquals(PaymentStatus.REFUNDED, updatedOrder.paymentStatus)
        assertEquals(500_000L, repo.currentUser.value?.walletBalance)
    }

    @Test
    fun `reorder skips out-of-stock dishes and keeps available dishes with notes`() = runBlocking {
        val repo = DemoFnbRepository()
        val seedOrder = repo.orders.value.first() // Has Bún Riêu + Trà Tắc

        // Mark Trà Tắc out of stock
        repo.toggleMenuItemAvailability("dish-tra-tac", false)

        val reorderResult = ReorderHelper.buildReorderItems(
            pastOrderItems = seedOrder.items,
            catalog = repo.menuItems.value
        )

        assertEquals(1, reorderResult.addedItems.size)
        assertEquals("Bún Riêu Cua Đặc Biệt", reorderResult.addedItems.first().menuItemName)
        assertEquals("Ít cay, không hành lá", reorderResult.addedItems.first().specialNote)
        assertTrue(reorderResult.skippedItemNames.any { it.contains("Trà Tắc") })
    }
}
