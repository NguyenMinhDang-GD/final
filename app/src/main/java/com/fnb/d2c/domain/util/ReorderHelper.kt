package com.fnb.d2c.domain.util

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.OrderItem
import com.fnb.d2c.domain.model.ReorderResult
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.model.SelectionType

object ReorderHelper {

    /**
     * Rebuilds CartLineItems from a past order's snapshot against the live menu catalog.
     * Automatically skips out-of-stock dishes or items whose required option is unavailable,
     * preserving original toppings and special notes (smart line-splitting intact).
     */
    fun buildReorderItems(
        pastOrderItems: List<OrderItem>,
        catalog: List<MenuItem>
    ): ReorderResult {
        val menuById = catalog.associateBy { it.id }
        val menuByName = catalog.associateBy { it.name.trim().lowercase() }

        var cartLines = emptyList<CartLineItem>()
        val skippedNames = mutableListOf<String>()

        for (pastItem in pastOrderItems) {
            val liveDish = pastItem.menuItemId?.let { menuById[it] }
                ?: menuByName[pastItem.itemNameSnapshot.trim().lowercase()]

            if (liveDish == null || !liveDish.isAvailable) {
                skippedNames += pastItem.itemNameSnapshot
                continue
            }

            val allLiveOptionsById = liveDish.optionGroups
                .flatMap { group -> group.options.map { opt -> opt.id to (group to opt) } }
                .toMap()
            val allLiveOptionsByName = liveDish.optionGroups
                .flatMap { group -> group.options.map { opt -> opt.name.trim().lowercase() to (group to opt) } }
                .toMap()

            val matchedSnapshots = mutableListOf<SelectedOptionSnapshot>()
            for (snap in pastItem.selectedOptionsSnapshot) {
                val match = allLiveOptionsById[snap.optionId]
                    ?: allLiveOptionsByName[snap.optionName.trim().lowercase()]
                if (match != null && match.second.isAvailable) {
                    matchedSnapshots += SelectedOptionSnapshot(
                        optionId = match.second.id,
                        groupName = match.first.name,
                        optionName = match.second.name,
                        extraPrice = match.second.extraPrice
                    )
                }
            }

            // Ensure every required SINGLE group has a valid available option
            var missingRequired = false
            for (group in liveDish.optionGroups.filter { it.isRequired && it.selectionType == SelectionType.SINGLE }) {
                val alreadyPicked = matchedSnapshots.any { picked ->
                    group.options.any { it.id == picked.optionId && it.isAvailable }
                }
                if (!alreadyPicked) {
                    val fallbackOpt = group.options.firstOrNull { it.isAvailable }
                    if (fallbackOpt != null) {
                        matchedSnapshots += SelectedOptionSnapshot(
                            optionId = fallbackOpt.id,
                            groupName = group.name,
                            optionName = fallbackOpt.name,
                            extraPrice = fallbackOpt.extraPrice
                        )
                    } else {
                        missingRequired = true
                    }
                }
            }

            if (missingRequired) {
                skippedNames += pastItem.itemNameSnapshot
                continue
            }

            val key = CartKeyBuilder.buildCartKey(
                menuItemId = liveDish.id,
                optionIds = matchedSnapshots.map { it.optionId },
                note = pastItem.specialNote
            )
            val line = CartLineItem(
                cartKey = key,
                menuItemId = liveDish.id,
                menuItemName = liveDish.name,
                imageUrl = liveDish.imageUrl,
                basePrice = liveDish.basePrice,
                selectedOptions = matchedSnapshots,
                specialNote = pastItem.specialNote,
                quantity = pastItem.quantity
            )
            cartLines = CartKeyBuilder.mergeItem(cartLines, line)
        }

        return ReorderResult(
            addedItems = cartLines,
            skippedItemNames = skippedNames.distinct()
        )
    }
}
