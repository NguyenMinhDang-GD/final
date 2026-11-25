package com.fnb.d2c.domain.util

import com.fnb.d2c.domain.model.CartLineItem

object CartKeyBuilder {

    /**
     * Generates a deterministic line-splitting cart key.
     * Two bowls of the same dish with different options OR different notes
     * (e.g. "không hành" vs "nhiều hành") produce distinct keys and stay on separate lines.
     */
    fun buildCartKey(menuItemId: String, optionIds: List<String>, note: String): String {
        val sortedOptions = optionIds.sorted().joinToString(",")
        val normalizedNote = normalizeNote(note)
        return "$menuItemId|$sortedOptions|$normalizedNote"
    }

    fun normalizeNote(note: String): String =
        note.trim().lowercase().replace(Regex("\\s+"), " ")

    fun mergeItem(existing: List<CartLineItem>, incoming: CartLineItem): List<CartLineItem> {
        val idx = existing.indexOfFirst { it.cartKey == incoming.cartKey }
        if (idx < 0) return existing + incoming
        return existing.mapIndexed { index, item ->
            if (index == idx) item.copy(quantity = item.quantity + incoming.quantity) else item
        }
    }
}
