package com.fnb.d2c.ui.merchant

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import com.fnb.d2c.ui.theme.BrothAmber
import com.fnb.d2c.ui.theme.DangerAlertBg
import com.fnb.d2c.ui.theme.DangerAlertText
import com.fnb.d2c.ui.theme.HerbGreen
import com.fnb.d2c.ui.theme.WarningNoteBg
import com.fnb.d2c.ui.theme.WarningNoteBorder
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantKitchenScreen(
    orders: List<Order>,
    isBellRinging: Boolean,
    nowTick: Instant,
    demoFastOverdueMode: Boolean,
    onToggleDemoOverdue: (Boolean) -> Unit,
    onSilenceBell: () -> Unit,
    onConfirmOrder: (String) -> Unit,
    onRejectOrder: (String, String) -> Unit,
    onDispatchShipper: (String) -> Unit,
    onOpenMockShipper: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var rejectingOrder by remember { mutableStateOf<Order?>(null) }

    val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
    val preparingOrders = orders.filter { it.status == OrderStatus.PREPARING }
    val deliveringOrders = orders.filter { it.status.isInDeliveryFlow }
    val historyOrders = orders.filter {
        it.status == OrderStatus.COMPLETED || it.status.isRejectedOrRefunded
    }

    val displayedOrders = when (selectedTab) {
        0 -> pendingOrders
        1 -> preparingOrders
        2 -> deliveringOrders
        else -> historyOrders
    }

    val overdueThresholdSeconds = if (demoFastOverdueMode) 15L else 45 * 60L

    Column(modifier = Modifier.fillMaxSize()) {
        if (isBellRinging || pendingOrders.isNotEmpty()) {
            Surface(
                color = DangerAlertText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Có ${pendingOrders.size} đơn mới đang chờ xác nhận!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isBellRinging) {
                        OutlinedButton(
                            onClick = onSilenceBell,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Text("Tắt chuông")
                        }
                    }
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Đơn mới (${pendingOrders.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Bếp đang làm (${preparingOrders.size})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Đang giao (${deliveringOrders.size})") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Lịch sử (${historyOrders.size})") }
            )
        }

        if (selectedTab == 2) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chế độ Demo cảnh báo giao lâu (15 giây thay vì 45 phút)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = demoFastOverdueMode,
                        onCheckedChange = onToggleDemoOverdue
                    )
                }
            }
        }

        if (displayedOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Không có đơn hàng nào trong mục này.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(displayedOrders, key = { it.id }) { order ->
                    KitchenOrderCard(
                        order = order,
                        isOverdue = CurrencyAndTimeFormatter.isDeliveryOverdue(
                            order = order,
                            thresholdSeconds = overdueThresholdSeconds,
                            now = nowTick
                        ),
                        elapsedSeconds = CurrencyAndTimeFormatter.getDeliveryElapsedSeconds(order, nowTick),
                        onConfirm = { onConfirmOrder(order.id) },
                        onReject = { rejectingOrder = order },
                        onDispatchShipper = { onDispatchShipper(order.id) },
                        onOpenMockShipper = onOpenMockShipper
                    )
                }
            }
        }
    }

    rejectingOrder?.let { target ->
        RejectOrderDialog(
            order = target,
            onDismiss = { rejectingOrder = null },
            onConfirmReject = { reason ->
                onRejectOrder(target.id, reason)
                rejectingOrder = null
            }
        )
    }
}

@Composable
private fun KitchenOrderCard(
    order: Order,
    isOverdue: Boolean,
    elapsedSeconds: Long,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onDispatchShipper: () -> Unit,
    onOpenMockShipper: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Column {
                    Text(
                        text = "Phiếu bếp #${order.id.take(6).uppercase()} • ${order.customerName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "📍 ${order.deliveryAddress} (${order.distanceKm} km)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = order.status.labelVi,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Long-Delivery Risk Alert Badge
            if (isOverdue) {
                Surface(
                    color = DangerAlertBg,
                    border = BorderStroke(1.5.dp, DangerAlertText),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🚨 GIAO QUÁ LÂU - CẦN KIỂM TRA (${elapsedSeconds}s)",
                                color = DangerAlertText,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tài xế: ${order.driverName ?: "Chưa rõ"} • ${order.driverPlate ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerAlertText
                            )
                        }
                        if (!order.driverPhone.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.driverPhone}"))
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerAlertText)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gọi TX")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Kitchen Display Items + Prominent Special Note Highlight
            order.items.forEach { item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.itemNameSnapshot}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyAndTimeFormatter.formatVnd(item.lineTotal),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (item.selectedOptionsSnapshot.isNotEmpty()) {
                        Text(
                            text = "   + " + item.selectedOptionsSnapshot.joinToString(" | ") { it.optionName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.specialNote.isNotBlank()) {
                        Surface(
                            color = WarningNoteBg,
                            border = BorderStroke(1.5.dp, DangerAlertText),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DangerAlertText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "LƯU Ý BẾP: ${item.specialNote.uppercase()}",
                                    color = DangerAlertText,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (order.paymentMethod == PaymentMethod.COD) {
                        "Thu hộ COD: ${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)}"
                    } else {
                        "Đã trả trước qua Ví (${order.paymentStatus.labelVi})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CurrencyAndTimeFormatter.formatVnd(order.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Action Buttons per Status
            when (order.status) {
                OrderStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerAlertText),
                            border = BorderStroke(1.dp, DangerAlertText)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Từ chối đơn")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = HerbGreen)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Xác nhận nhận")
                        }
                    }
                }

                OrderStatus.PREPARING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerAlertText)
                        ) {
                            Text("Báo hết món")
                        }
                        Button(
                            onClick = onDispatchShipper,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeliveryDining, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Đóng gói xong • Gọi Shipper")
                        }
                    }
                }

                OrderStatus.FINDING_DRIVER,
                OrderStatus.DRIVER_AT_STORE,
                OrderStatus.PICKED_UP,
                OrderStatus.DELIVERING -> {
                    OutlinedButton(
                        onClick = onOpenMockShipper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mở Bảng điều khiển Tài xế (Mock Shipper)")
                    }
                }

                else -> {
                    if (order.review != null) {
                        Text(
                            text = "⭐ Khách đánh giá ${order.review.rating}/5: \"${order.review.comment}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrothAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RejectOrderDialog(
    order: Order,
    onDismiss: () -> Unit,
    onConfirmReject: (String) -> Unit
) {
    var reason by remember { mutableStateOf("Tạm hết chả cá thu tươi") }
    val presetReasons = listOf(
        "Tạm hết chả cá thu tươi",
        "Hết nước dùng riêu cua",
        "Bếp quá tải, không kịp giao",
        "Khu vực giao hàng đang ngập nước"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Từ chối đơn #${order.id.take(6).uppercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.paymentMethod == PaymentMethod.WALLET) {
                    Text(
                        text = "💸 Khách đã thanh toán trước ${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)} qua Ví. Hệ thống sẽ TỰ ĐỘNG HOÀN TIỀN 100% về Ví khách ngay khi từ chối.",
                        color = HerbGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetReasons.forEach { preset ->
                        AssistChip(
                            onClick = { reason = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Lý do gửi đến khách hàng") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReject(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = DangerAlertText)
            ) {
                Text("Xác nhận Từ chối")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
