package com.fnb.d2c.ui.customer

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.DeliveryLocation
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.model.ResolvedStoreStatus
import com.fnb.d2c.domain.model.ShippingQuote
import com.fnb.d2c.domain.model.UserProfile
import com.fnb.d2c.domain.model.Voucher
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import com.fnb.d2c.domain.util.PriceBreakdown
import com.fnb.d2c.ui.theme.DangerAlertText
import com.fnb.d2c.ui.theme.HerbGreen
import com.fnb.d2c.ui.theme.WarningNoteBg
import com.fnb.d2c.ui.theme.WarningNoteBorder
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartAndCheckoutScreen(
    user: UserProfile,
    storeStatus: ResolvedStoreStatus,
    cartItems: List<CartLineItem>,
    deliveryLocation: DeliveryLocation,
    shippingQuote: ShippingQuote,
    isQuotingShipping: Boolean,
    addressSuggestions: List<DeliveryLocation>,
    availableVouchers: List<Voucher>,
    selectedVoucherCode: String?,
    priceBreakdown: PriceBreakdown,
    paymentMethod: PaymentMethod,
    isBusy: Boolean,
    onUpdateQuantity: (String, Int) -> Unit,
    onClearCart: () -> Unit,
    onSearchAddress: (String) -> Unit,
    onSelectLocation: (DeliveryLocation) -> Unit,
    onApplyVoucher: (String?) -> Unit,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onPlaceOrder: () -> Unit
) {
    var voucherInput by remember(selectedVoucherCode) { mutableStateOf(selectedVoucherCode ?: "") }
    var showMapDialog by remember { mutableStateOf(false) }

    if (cartItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🛒 Giỏ hàng của bạn đang trống",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hãy chọn món bên tab Thực đơn hoặc bấm Đặt lại đơn cũ nhé!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Giỏ hàng tách dòng thông minh",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cartItems.size} dòng món • ${cartItems.sumOf { it.quantity }} phần ăn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClearCart) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa hết")
                }
            }
        }

        items(cartItems, key = { it.cartKey }) { line ->
            CartLineCard(
                line = line,
                onIncrement = { onUpdateQuantity(line.cartKey, 1) },
                onDecrement = { onUpdateQuantity(line.cartKey, -1) }
            )
        }

        // Delivery Address + Automatic Distance & Shipping Quote
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Địa chỉ giao hàng & Báo giá Ship", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showMapDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chấm Bản đồ", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Text(
                        text = deliveryLocation.address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Khoảng cách: ${shippingQuote.distanceKm} km (${shippingQuote.sourceLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isQuotingShipping) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = CurrencyAndTimeFormatter.formatVnd(shippingQuote.shippingFee),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Voucher Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Mã khuyến mãi (Voucher)", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = voucherInput,
                            onValueChange = { voucherInput = it.uppercase() },
                            label = { Text("Nhập mã giảm giá") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onApplyVoucher(voucherInput) }) {
                            Text("Áp dụng")
                        }
                        if (!selectedVoucherCode.isNullOrBlank()) {
                            TextButton(onClick = {
                                voucherInput = ""
                                onApplyVoucher(null)
                            }) {
                                Text("Gỡ")
                            }
                        }
                    }

                    val activeVouchers = availableVouchers.filter { it.isActive && it.remainingUses > 0 }
                    if (activeVouchers.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeVouchers.forEach { v ->
                                val label = if (v.discountType == DiscountType.PERCENT) {
                                    "${v.code} (-${v.discountValue}%)"
                                } else {
                                    "${v.code} (-${CurrencyAndTimeFormatter.formatVnd(v.discountValue)})"
                                }
                                FilterChip(
                                    selected = selectedVoucherCode.equals(v.code, ignoreCase = true),
                                    onClick = {
                                        voucherInput = v.code
                                        onApplyVoucher(v.code)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    if (priceBreakdown.voucherError != null) {
                        Text(
                            text = "⚠️ ${priceBreakdown.voucherError}",
                            color = DangerAlertText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (priceBreakdown.appliedVoucher != null) {
                        Text(
                            text = "✅ Đã áp mã ${priceBreakdown.appliedVoucher.code}: giảm ${CurrencyAndTimeFormatter.formatVnd(priceBreakdown.discountAmount)} (chỉ trừ trên tiền món, không trừ phí ship).",
                            color = HerbGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Payment Method
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Phương thức thanh toán", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    PaymentMethod.entries.forEach { method ->
                        val subtitle = if (method == PaymentMethod.WALLET) {
                            "Số dư hiện tại: ${CurrencyAndTimeFormatter.formatVnd(user.walletBalance)} • Tự động hoàn tiền ngay nếu quán hết món"
                        } else {
                            "Thanh toán trực tiếp cho tài xế khi nhận đồ ăn"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPaymentMethod(method) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = paymentMethod == method,
                                onClick = { onSelectPaymentMethod(method) }
                            )
                            Column {
                                Text(method.labelVi, fontWeight = FontWeight.SemiBold)
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Transparent Total Calculation Summary
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Chi tiết thanh toán minh bạch", fontWeight = FontWeight.Bold)
                    SummaryRow("Tiền hàng (Món + Topping)", CurrencyAndTimeFormatter.formatVnd(priceBreakdown.subtotalAmount))
                    SummaryRow(
                        label = "Giảm giá Voucher (Tối đa = Tiền hàng)",
                        value = "-${CurrencyAndTimeFormatter.formatVnd(priceBreakdown.discountAmount)}",
                        valueColor = HerbGreen
                    )
                    SummaryRow(
                        label = "Phí giao hàng (${shippingQuote.distanceKm} km)",
                        value = "+${CurrencyAndTimeFormatter.formatVnd(priceBreakdown.shippingFee)}"
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tổng thanh toán", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = CurrencyAndTimeFormatter.formatVnd(priceBreakdown.totalAmount),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onPlaceOrder,
                        enabled = !isBusy && storeStatus.canAcceptOrder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (storeStatus.canAcceptOrder) {
                                "Xác nhận Đặt đơn • ${CurrencyAndTimeFormatter.formatVnd(priceBreakdown.totalAmount)}"
                            } else {
                                "Quán hiện ${storeStatus.badgeText}"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showMapDialog) {
        AddressAndMapPickerDialog(
            initialLocation = deliveryLocation,
            suggestions = addressSuggestions,
            onSearchQuery = onSearchAddress,
            onDismiss = { showMapDialog = false },
            onConfirm = { picked ->
                onSelectLocation(picked)
                showMapDialog = false
            }
        )
    }
}

@Composable
private fun CartLineCard(
    line: CartLineItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.menuItemName, fontWeight = FontWeight.Bold)
                    if (line.selectedOptions.isNotEmpty()) {
                        Text(
                            text = line.selectedOptions.joinToString(" • ") {
                                "${it.optionName} (${CurrencyAndTimeFormatter.formatSignedOptionPrice(it.extraPrice)})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (line.specialNote.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = WarningNoteBg,
                            border = BorderStroke(1.dp, WarningNoteBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "📝 Ghi chú riêng: ${line.specialNote}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = CurrencyAndTimeFormatter.formatVnd(line.lineTotal),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đơn giá: ${CurrencyAndTimeFormatter.formatVnd(line.unitPrice)} / phần",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Giảm")
                    }
                    Text(
                        text = "${line.quantity}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Tăng")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddressAndMapPickerDialog(
    initialLocation: DeliveryLocation,
    suggestions: List<DeliveryLocation>,
    onSearchQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (DeliveryLocation) -> Unit
) {
    var addressText by remember { mutableStateOf(initialLocation.address) }
    var pickedLat by remember { mutableDoubleStateOf(initialLocation.lat) }
    var pickedLng by remember { mutableDoubleStateOf(initialLocation.lng) }

    val quickLandmarks = remember {
        listOf(
            DeliveryLocation("ĐH Khoa học Tự nhiên (227 Nguyễn Văn Cừ, Q.5)", 10.7629, 106.6822),
            DeliveryLocation("Chợ Bến Thành (Quận 1, TP.HCM)", 10.7725, 106.6980),
            DeliveryLocation("Landmark 81 (Bình Thạnh, TP.HCM)", 10.7950, 106.7218),
            DeliveryLocation("Crescent Mall (Quận 7, TP.HCM)", 10.7287, 106.7189)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Chọn địa chỉ & Chấm tọa độ (OpenStreetMap)", fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = addressText,
                    onValueChange = {
                        addressText = it
                        onSearchQuery(it)
                    },
                    label = { Text("Nhập địa chỉ (Gợi ý tự động OSM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            suggestions.take(3).forEach { item ->
                                Text(
                                    text = "📍 ${item.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            addressText = item.address
                                            pickedLat = item.lat
                                            pickedLng = item.lng
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickLandmarks.forEach { lm ->
                        AssistChip(
                            onClick = {
                                addressText = lm.address
                                pickedLat = lm.lat
                                pickedLng = lm.lng
                            },
                            label = { Text(lm.address.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(pickedLat, pickedLng))

                                val marker = Marker(this).apply {
                                    position = GeoPoint(pickedLat, pickedLng)
                                    title = "Điểm nhận hàng"
                                }
                                overlays.add(marker)

                                overlays.add(object : Overlay() {
                                    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                                        if (e != null && mapView != null) {
                                            val proj = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                            pickedLat = proj.latitude
                                            pickedLng = proj.longitude
                                            marker.position = proj
                                            addressText = String.format(
                                                Locale.US,
                                                "Vị trí ghim bản đồ (%.5f, %.5f)",
                                                pickedLat,
                                                pickedLng
                                            )
                                            mapView.invalidate()
                                            return true
                                        }
                                        return false
                                    }
                                })
                            }
                        },
                        update = { mapView ->
                            val target = GeoPoint(pickedLat, pickedLng)
                            mapView.controller.setCenter(target)
                            mapView.overlays.filterIsInstance<Marker>().firstOrNull()?.position = target
                            mapView.invalidate()
                        }
                    )
                }

                Text(
                    text = String.format(Locale.US, "Tọa độ: (%.5f, %.5f) — Chạm bản đồ để đổi điểm giao", pickedLat, pickedLng),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(
                            DeliveryLocation(
                                address = addressText.ifBlank { "Điểm ghim bản đồ TP.HCM" },
                                lat = pickedLat,
                                lng = pickedLng
                            )
                        )
                    }) {
                        Text("Xác nhận vị trí")
                    }
                }
            }
        }
    }
}
