package com.fnb.d2c

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.util.CartKeyBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CartKeyBuilderTest {

    @Test
    fun `same dish with different notes splits into two separate cart lines`() {
        val keyNoOnion = CartKeyBuilder.buildCartKey(
            menuItemId = "dish-bun-rieu",
            optionIds = listOf("opt-size-reg", "opt-cha"),
            note = "không hành"
        )
        val keyExtraOnion = CartKeyBuilder.buildCartKey(
            menuItemId = "dish-bun-rieu",
            optionIds = listOf("opt-size-reg", "opt-cha"),
            note = "nhiều hành"
        )
        assertNotEquals(keyNoOnion, keyExtraOnion)

        val line1 = CartLineItem(
            cartKey = keyNoOnion,
            menuItemId = "dish-bun-rieu",
            menuItemName = "Bún Riêu Cua",
            imageUrl = "",
            basePrice = 55_000L,
            selectedOptions = emptyList(),
            specialNote = "không hành",
            quantity = 1
        )
        val line2 = line1.copy(cartKey = keyExtraOnion, specialNote = "nhiều hành")

        val merged = CartKeyBuilder.mergeItem(listOf(line1), line2)
        assertEquals(2, merged.size)
        assertEquals(1, merged[0].quantity)
        assertEquals(1, merged[1].quantity)
    }

    @Test
    fun `identical dish options and normalized note merges quantity into single line`() {
        val keyA = CartKeyBuilder.buildCartKey(
            menuItemId = "dish-bun-rieu",
            optionIds = listOf("opt-gio", "opt-size-lg"),
            note = "  KHÔNG   Cay  "
        )
        val keyB = CartKeyBuilder.buildCartKey(
            menuItemId = "dish-bun-rieu",
            optionIds = listOf("opt-size-lg", "opt-gio"),
            note = "không cay"
        )
        assertEquals(keyA, keyB)

        val first = CartLineItem(
            cartKey = keyA,
            menuItemId = "dish-bun-rieu",
            menuItemName = "Bún Riêu Cua",
            imageUrl = "",
            basePrice = 55_000L,
            selectedOptions = listOf(
                SelectedOptionSnapshot("opt-size-lg", "Size", "Tô lớn", 10_000L)
            ),
            specialNote = "không cay",
            quantity = 1
        )
        val merged = CartKeyBuilder.mergeItem(listOf(first), first.copy(quantity = 2))
        assertEquals(1, merged.size)
        assertEquals(3, merged.first().quantity)
    }
}
