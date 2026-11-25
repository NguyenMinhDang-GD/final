package com.fnb.d2c.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fnb.d2c.domain.model.Category
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.ResolvedStoreStatus
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.model.SelectionType
import com.fnb.d2c.domain.model.StoreAvailabilityState
import com.fnb.d2c.domain.model.StoreSettings
import com.fnb.d2c.domain.model.UserProfile
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import com.fnb.d2c.ui.theme.BrothAmber
import com.fnb.d2c.ui.theme.DangerAlertText
import com.fnb.d2c.ui.theme.HerbGreen
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMenuScreen(
    user: UserProfile,
    storeSettings: StoreSettings,
    storeStatus: ResolvedStoreStatus,
    categories: List<Category>,
    menuItems: List<MenuItem>,
    cartItemCount: Int,
    cartSubtotal: Long,
    onTopUpWallet: () -> Unit,
    onAddDishToCart: (MenuItem, List<SelectedOptionSnapshot>, String, Int) -> Unit,
    onGoToCart: () -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var customizingDish by remember { mutableStateOf<MenuItem?>(null) }

    val filteredDishes = remember(selectedCategoryId, menuItems) {
        if (selectedCategoryId == null) menuItems
        else menuItems.filter { it.categoryId == selectedCategoryId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 92.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StoreHeroBanner(
                    user = user,
                    storeSettings = storeSettings,
                    storeStatus = storeStatus,
                    onTopUpWallet = onTopUpWallet
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Tất cả (${menuItems.size})") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            items(filteredDishes, key = { it.id }) { dish ->
                MenuDishCard(
                    dish = dish,
                    canOrder = storeStatus.canAcceptOrder && dish.isAvailable,
                    onClick = {
                        if (dish.isAvailable) {
                            customizingDish = dish
                        }
                    }
                )
            }
        }

        if (cartItemCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onGoToCart() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Xem giỏ hàng ($cartItemCount phần)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = CurrencyAndTimeFormatter.formatVnd(cartSubtotal),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    customizingDish?.let { dish ->
        ItemCustomizationBottomSheet(
            dish = dish,
            canOrder = storeStatus.canAcceptOrder,
            storeClosedReason = storeStatus.detailText,
            onDismiss = { customizingDish = null },
            onConfirm = { options, note, qty ->
                onAddDishToCart(dish, options, note, qty)
                customizingDish = null
            }
        )
    }
}

@Composable
private fun StoreHeroBanner(
    user: UserProfile,
    storeSettings: StoreSettings,
    storeStatus: ResolvedStoreStatus,
    onTopUpWallet: () -> Unit
) {
    val badgeColor = when (storeStatus.state) {
        StoreAvailabilityState.OPEN -> HerbGreen
        StoreAvailabilityState.BUSY -> BrothAmber
        StoreAvailabilityState.CLOSED -> DangerAlertText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = badgeColor, shape = RoundedCornerShape(50)) {
                    Text(
                        text = "● ${storeStatus.badgeText}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Ví: ${CurrencyAndTimeFormatter.formatVnd(user.walletBalance)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onTopUpWallet, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("+Nạp")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = storeSettings.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "📍 ${storeSettings.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = storeStatus.detailText,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MenuDishCard(
    dish: MenuItem,
    canOrder: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(if (dish.isAvailable) 1f else 0.55f)
            .clickable(enabled = dish.isAvailable, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = dish.imageUrl.ifBlank { "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=400" },
                contentDescription = dish.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (dish.description.isNotBlank()) {
                    Text(
                        text = dish.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CurrencyAndTimeFormatter.formatVnd(dish.basePrice),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (!dish.isAvailable) {
                        Surface(
                            color = DangerAlertText.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Tạm hết hàng",
                                color = DangerAlertText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = if (canOrder) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "+ Chọn món",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCustomizationBottomSheet(
    dish: MenuItem,
    canOrder: Boolean,
    storeClosedReason: String,
    onDismiss: () -> Unit,
    onConfirm: (List<SelectedOptionSnapshot>, String, Int) -> Unit
) {
    // groupId -> selected optionIds
    val singleSelections = remember(dish.id) {
        mutableStateMapOf<String, String>().apply {
            dish.optionGroups
                .filter { it.selectionType == SelectionType.SINGLE && it.isRequired }
                .forEach { grp ->
                    grp.options.firstOrNull { it.isAvailable }?.let { firstOpt ->
                        put(grp.id, firstOpt.id)
                    }
                }
        }
    }
    val multiSelections = remember(dish.id) { mutableStateMapOf<String, Boolean>() }
    var specialNote by remember(dish.id) { mutableStateOf("") }
    var quantity by remember(dish.id) { mutableIntStateOf(1) }

    val missingRequiredGroups = dish.optionGroups.filter { grp ->
        grp.isRequired && grp.selectionType == SelectionType.SINGLE && singleSelections[grp.id].isNullOrBlank()
    }

    val chosenSnapshots: List<SelectedOptionSnapshot> = buildList {
        dish.optionGroups.forEach { grp ->
            if (grp.selectionType == SelectionType.SINGLE) {
                val chosenId = singleSelections[grp.id]
                val opt = grp.options.firstOrNull { it.id == chosenId && it.isAvailable }
                if (opt != null) {
                    add(SelectedOptionSnapshot(opt.id, grp.name, opt.name, opt.extraPrice))
                }
            } else {
                grp.options.filter { it.isAvailable && multiSelections[it.id] == true }.forEach { opt ->
                    add(SelectedOptionSnapshot(opt.id, grp.name, opt.name, opt.extraPrice))
                }
            }
        }
    }

    val unitPrice = max(0L, dish.basePrice + chosenSnapshots.sumOf { it.extraPrice })
    val totalLinePrice = unitPrice * quantity

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = dish.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Giá gốc: ${CurrencyAndTimeFormatter.formatVnd(dish.basePrice)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            dish.optionGroups.forEach { group ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (group.isRequired) "Bắt buộc chọn 1" else "Tùy chọn nhiều",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (group.isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                group.options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (opt.isAvailable) 1f else 0.45f)
                            .clickable(enabled = opt.isAvailable) {
                                if (group.selectionType == SelectionType.SINGLE) {
                                    singleSelections[group.id] = opt.id
                                } else {
                                    multiSelections[opt.id] = !(multiSelections[opt.id] ?: false)
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (group.selectionType == SelectionType.SINGLE) {
                                RadioButton(
                                    selected = singleSelections[group.id] == opt.id,
                                    enabled = opt.isAvailable,
                                    onClick = { singleSelections[group.id] = opt.id }
                                )
                            } else {
                                Checkbox(
                                    checked = multiSelections[opt.id] == true,
                                    enabled = opt.isAvailable,
                                    onCheckedChange = { multiSelections[opt.id] = it }
                                )
                            }
                            Text(
                                text = if (opt.isAvailable) opt.name else "${opt.name} (Hết)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = CurrencyAndTimeFormatter.formatSignedOptionPrice(opt.extraPrice),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (opt.extraPrice < 0) HerbGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider()
            OutlinedTextField(
                value = specialNote,
                onValueChange = { specialNote = it },
                label = { Text("Ghi chú riêng cho phần này (VD: không hành, dị ứng tôm)") },
                placeholder = { Text("Khác ghi chú sẽ tự động tách dòng trong giỏ hàng") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Số lượng:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (quantity > 1) quantity-- }) {
                        Icon(Icons.Default.Remove, contentDescription = "Giảm")
                    }
                    Text(
                        text = "$quantity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    IconButton(onClick = { quantity++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Tăng")
                    }
                }
            }

            if (!canOrder) {
                Text(
                    text = "⚠️ Hiện chưa thể đặt món: $storeClosedReason",
                    color = DangerAlertText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (missingRequiredGroups.isNotEmpty()) {
                Text(
                    text = "Vui lòng chọn: ${missingRequiredGroups.joinToString { it.name }}",
                    color = DangerAlertText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { onConfirm(chosenSnapshots, specialNote, quantity) },
                enabled = canOrder && missingRequiredGroups.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Thêm vào giỏ • ${CurrencyAndTimeFormatter.formatVnd(totalLinePrice)}",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
