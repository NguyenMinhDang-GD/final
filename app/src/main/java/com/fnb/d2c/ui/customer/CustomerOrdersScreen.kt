package com.fnb.d2c.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import com.fnb.d2c.ui.theme.BrothAmber
import com.fnb.d2c.ui.theme.DangerAlertBg
import com.fnb.d2c.ui.theme.DangerAlertText
import com.fnb.d2c.ui.theme.HerbGreen

@Composable
fun CustomerOrdersScreen(
    orders: List<Order>,
    onReorder: (Order) -> Unit,
    onSubmitReview: (String, Int, String) -> Unit
) {
    if (orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Bạn chưa có đơn hàng nào.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(orders, key = { it.id }) { order ->
            CustomerOrderCard(
                order = order,
                onReorder = { onReorder(order) },
                onSubmitReview = { rating, comment -> onSubmitReview(order.id, rating, comment) }
            )
        }
    }
}

@Composable
private fun CustomerOrderCard(
    order: Order,
    onReorder: () -> Unit,
    onSubmitReview: (Int, String) -> Unit
) {
    val context = LocalContext.current
    var rating by remember(order.id) { mutableIntStateOf(5) }
    var comment by remember(order.id) { mutableStateOf("") }

    val statusColor = when {
        order.status == OrderStatus.COMPLETED -> HerbGreen
        order.status.isRejectedOrRefunded -> DangerAlertText
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(18.dp),
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
                        text = "Đơn #${order.id.take(8).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = CurrencyAndTimeFormatter.formatIsoTimestamp(order.createdAtIso),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = order.status.labelVi,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Step Progress Bar for active/completed orders
            if (!order.status.isRejectedOrRefunded) {
                val progress = ((order.status.stepIndex + 1).toFloat() / 7f).coerceIn(0.1f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = statusColor
                )
                Text(
                    text = "Tiến độ: Chờ xác nhận → Đang làm → Tìm Shipper → Đến quán → Đã lấy món → Đang giao → Hoàn thành",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    color = DangerAlertBg,
                    border = BorderStroke(1.dp, DangerAlertText.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Lý do từ chối: ${order.rejectReason ?: "Quán tạm hết nguyên liệu"}",
                            color = DangerAlertText,
                            fontWeight = FontWeight.Bold
                        )
                        if (order.status == OrderStatus.REFUNDED) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "💸 Đã hoàn trả tự động ${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)} về Ví ứng dụng của bạn.",
                                color = HerbGreen,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Driver Sync Card
            if (!order.driverName.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
                            Text("🛵 Tài xế: ${order.driverName}", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Biển số: ${order.driverPlate ?: "59X3-686.88"} • SĐT: ${order.driverPhone ?: ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!order.driverPhone.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.driverPhone}"))
                                    context.startActivity(dialIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HerbGreen)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gọi")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            order.items.forEach { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.quantity}x ${item.itemNameSnapshot}", fontWeight = FontWeight.SemiBold)
                        Text(CurrencyAndTimeFormatter.formatVnd(item.lineTotal))
                    }
                    if (item.selectedOptionsSnapshot.isNotEmpty()) {
                        Text(
                            text = item.selectedOptionsSnapshot.joinToString(", ") { it.optionName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.specialNote.isNotBlank()) {
                        Text(
                            text = "Ghi chú: ${item.specialNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrothAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${order.paymentMethod.labelVi} • ${order.paymentStatus.labelVi}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tổng: ${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Rating & Review on COMPLETED order
            if (order.status == OrderStatus.COMPLETED) {
                HorizontalDivider()
                if (order.review != null) {
                    Surface(
                        color = HerbGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = HerbGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Đánh giá của bạn: ${"★".repeat(order.review.rating)}${"☆".repeat(5 - order.review.rating)}",
                                    fontWeight = FontWeight.Bold,
                                    color = HerbGreen
                                )
                            }
                            if (order.review.comment.isNotBlank()) {
                                Text(order.review.comment, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Đánh giá chất lượng món ăn & giao hàng:", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                IconButton(onClick = { rating = star }) {
                                    Icon(
                                        imageVector = if (star <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "$star sao",
                                        tint = BrothAmber
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text("Nhận xét của bạn") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onSubmitReview(rating, comment) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Gửi đánh giá")
                        }
                    }
                }
            }

            // Reorder Button ("Đặt lại đơn này")
            if (order.status == OrderStatus.COMPLETED || order.status.isRejectedOrRefunded) {
                OutlinedButton(
                    onClick = onReorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Đặt lại đơn này (Giữ nguyên Topping & Ghi chú)")
                }
            }
        }
    }
}
