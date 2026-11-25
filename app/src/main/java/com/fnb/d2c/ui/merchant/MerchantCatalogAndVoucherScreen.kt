package com.fnb.d2c.ui.merchant

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fnb.d2c.domain.model.Category
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.OptionGroup
import com.fnb.d2c.domain.model.SelectionType
import com.fnb.d2c.domain.model.Voucher
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun MerchantCatalogAndVoucherScreen(
    categories: List<Category>,
    menuItems: List<MenuItem>,
    vouchers: List<Voucher>,
    onSaveCategory: (String?, String, Int) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveDish: (String?, String, String, String, Long, String, Boolean) -> Unit,
    onToggleDishStock: (String, Boolean) -> Unit,
    onDeleteDish: (String) -> Unit,
    onSaveOptionGroup: (String?, String, String, SelectionType, Boolean) -> Unit,
    onDeleteOptionGroup: (String) -> Unit,
    onSaveOptionItem: (String?, String, String, Long, Boolean) -> Unit,
    onDeleteOptionItem: (String) -> Unit,
    onSaveVoucher: (String?, String, DiscountType, Long, Long, Int, String, Boolean) -> Unit,
    onToggleVoucher: (String, Boolean) -> Unit,
    onDeleteVoucher: (String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<MenuItem?>(null) }
    var showNewDishDialog by remember { mutableStateOf(false) }
    var groupDialogDishId by remember { mutableStateOf<String?>(null) }
    var optionDialogGroup by remember { mutableStateOf<OptionGroup?>(null) }
    var showVoucherDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text("Thực đơn & Option (${menuItems.size})") }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text("Khuyến mãi Voucher (${vouchers.size})") }
            )
        }

        if (tabIndex == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm Danh mục")
                        }
                        Button(
                            onClick = { showNewDishDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = categories.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm Món ăn")
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Danh mục hiện có:", fontWeight = FontWeight.Bold)
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ${cat.name} (Thứ tự: ${cat.sortOrder})")
                                    IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa danh mục")
                                    }
                                }
                            }
                        }
                    }
                }

                items(menuItems, key = { it.id }) { dish ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Giá gốc: ${CurrencyAndTimeFormatter.formatVnd(dish.basePrice)}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (dish.isAvailable) "Còn" else "Hết",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Switch(
                                        checked = dish.isAvailable,
                                        onCheckedChange = { onToggleDishStock(dish.id, it) }
                                    )
                                    IconButton(onClick = { editingDish = dish }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Sửa món")
                                    }
                                    IconButton(onClick = { onDeleteDish(dish.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa món")
                                    }
                                }
                            }

                            // Option Groups
                            dish.optionGroups.forEach { grp ->
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Nhóm: ${grp.name} (${grp.selectionType.name}${if (grp.isRequired) ", Bắt buộc" else ""})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row {
                                        TextButton(onClick = { optionDialogGroup = grp }) {
                                            Text("+ Option")
                                        }
                                        IconButton(
                                            onClick = { onDeleteOptionGroup(grp.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                grp.options.forEach { opt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "- ${opt.name} (${CurrencyAndTimeFormatter.formatSignedOptionPrice(opt.extraPrice)})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        IconButton(
                                            onClick = { onDeleteOptionItem(opt.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { groupDialogDishId = dish.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Thêm Nhóm Tùy chỉnh (Kích cỡ / Topping)")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { showVoucherDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tạo Mã Khuyến Mãi (Voucher) Mới")
                    }
                }

                items(vouchers, key = { it.id }) { v ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(v.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    val desc = if (v.discountType == DiscountType.PERCENT) {
                                        "Giảm ${v.discountValue}% • Đơn tối thiểu ${CurrencyAndTimeFormatter.formatVnd(v.minOrderValue)}"
                                    } else {
                                        "Giảm ${CurrencyAndTimeFormatter.formatVnd(v.discountValue)} • Đơn tối thiểu ${CurrencyAndTimeFormatter.formatVnd(v.minOrderValue)}"
                                    }
                                    Text(desc, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "Đã dùng: ${v.usedCount}/${v.usageLimit} • HSD: ${CurrencyAndTimeFormatter.formatIsoTimestamp(v.endAtIso)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = v.isActive,
                                        onCheckedChange = { onToggleVoucher(v.id, it) }
                                    )
                                    IconButton(onClick = { onDeleteVoucher(v.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa mã")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var orderStr by remember { mutableStateOf("${categories.size + 1}") }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Thêm Danh mục mới") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("Tên danh mục") })
                    OutlinedTextField(
                        value = orderStr,
                        onValueChange = { orderStr = it },
                        label = { Text("Thứ tự hiển thị") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveCategory(null, catName, orderStr.toIntOrNull() ?: 1)
                    showCategoryDialog = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Hủy") } }
        )
    }

    if (showNewDishDialog || editingDish != null) {
        val target = editingDish
        var catId by remember { mutableStateOf(target?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
        var name by remember { mutableStateOf(target?.name.orEmpty()) }
        var desc by remember { mutableStateOf(target?.description.orEmpty()) }
        var priceStr by remember { mutableStateOf(target?.basePrice?.toString() ?: "50000") }
        var imageUrl by remember { mutableStateOf(target?.imageUrl.orEmpty()) }

        AlertDialog(
            onDismissRequest = {
                showNewDishDialog = false
                editingDish = null
            },
            title = { Text(if (target == null) "Thêm Món Ăn Mới" else "Chỉnh sửa Món Ăn") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = catId == cat.id,
                                onClick = { catId = cat.id },
                                label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên món") })
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Giá gốc (VNĐ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Mô tả") })
                    OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("URL Ảnh (tùy chọn)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveDish(
                        target?.id,
                        catId,
                        name,
                        desc,
                        priceStr.toLongOrNull() ?: 0L,
                        imageUrl,
                        target?.isAvailable ?: true
                    )
                    showNewDishDialog = false
                    editingDish = null
                }) { Text("Lưu món") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewDishDialog = false
                    editingDish = null
                }) { Text("Hủy") }
            }
        )
    }

    groupDialogDishId?.let { dishId ->
        var grpName by remember { mutableStateOf("Kích cỡ tô") }
        var selType by remember { mutableStateOf(SelectionType.SINGLE) }
        var required by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { groupDialogDishId = null },
            title = { Text("Thêm Nhóm Tùy Chỉnh") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = grpName, onValueChange = { grpName = it }, label = { Text("Tên nhóm (VD: Kích cỡ, Topping)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selType == SelectionType.SINGLE,
                            onClick = {
                                selType = SelectionType.SINGLE
                                required = true
                            },
                            label = { Text("Chọn 1 (SINGLE)") }
                        )
                        FilterChip(
                            selected = selType == SelectionType.MULTI,
                            onClick = {
                                selType = SelectionType.MULTI
                                required = false
                            },
                            label = { Text("Chọn nhiều (MULTI)") }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = required, onCheckedChange = { required = it })
                        Text("Bắt buộc chọn")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveOptionGroup(null, dishId, grpName, selType, required)
                    groupDialogDishId = null
                }) { Text("Lưu nhóm") }
            },
            dismissButton = { TextButton(onClick = { groupDialogDishId = null }) { Text("Hủy") } }
        )
    }

    optionDialogGroup?.let { grp ->
        var optName by remember { mutableStateOf("") }
        var extraStr by remember { mutableStateOf("10000") }
        AlertDialog(
            onDismissRequest = { optionDialogGroup = null },
            title = { Text("Thêm Option vào '${grp.name}'") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = optName, onValueChange = { optName = it }, label = { Text("Tên tùy chọn (VD: Tô lớn, Thêm chả)") })
                    OutlinedTextField(
                        value = extraStr,
                        onValueChange = { extraStr = it },
                        label = { Text("Giá cộng/trừ VNĐ (VD: 10000 hoặc -5000)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveOptionItem(null, grp.id, optName, extraStr.toLongOrNull() ?: 0L, true)
                    optionDialogGroup = null
                }) { Text("Thêm") }
            },
            dismissButton = { TextButton(onClick = { optionDialogGroup = null }) { Text("Hủy") } }
        )
    }

    if (showVoucherDialog) {
        var code by remember { mutableStateOf("") }
        var discountType by remember { mutableStateOf(DiscountType.FIXED) }
        var valueStr by remember { mutableStateOf("20000") }
        var minOrderStr by remember { mutableStateOf("80000") }
        var limitStr by remember { mutableStateOf("50") }
        var daysValidStr by remember { mutableStateOf("30") }

        AlertDialog(
            onDismissRequest = { showVoucherDialog = false },
            title = { Text("Tạo Mã Khuyến Mãi (Voucher)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Mã code (VD: KHAITRUONG)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = discountType == DiscountType.FIXED,
                            onClick = { discountType = DiscountType.FIXED },
                            label = { Text("Số tiền (VNĐ)") }
                        )
                        FilterChip(
                            selected = discountType == DiscountType.PERCENT,
                            onClick = { discountType = DiscountType.PERCENT },
                            label = { Text("Phần trăm (%)") }
                        )
                    }
                    OutlinedTextField(
                        value = valueStr,
                        onValueChange = { valueStr = it },
                        label = { Text(if (discountType == DiscountType.PERCENT) "Mức giảm (%)" else "Số tiền giảm (VNĐ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = minOrderStr,
                        onValueChange = { minOrderStr = it },
                        label = { Text("Đơn tối thiểu (VNĐ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = limitStr,
                            onValueChange = { limitStr = it },
                            label = { Text("Số lượt tối đa") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = daysValidStr,
                            onValueChange = { daysValidStr = it },
                            label = { Text("Hạn (ngày)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val days = (daysValidStr.toLongOrNull() ?: 30L).coerceAtLeast(1L)
                    val endIso = Instant.now().plus(days, ChronoUnit.DAYS).toString()
                    onSaveVoucher(
                        null,
                        code,
                        discountType,
                        valueStr.toLongOrNull() ?: 10000L,
                        minOrderStr.toLongOrNull() ?: 0L,
                        limitStr.toIntOrNull() ?: 50,
                        endIso,
                        true
                    )
                    showVoucherDialog = false
                }) { Text("Tạo Voucher") }
            },
            dismissButton = { TextButton(onClick = { showVoucherDialog = false }) { Text("Hủy") } }
        )
    }
}
