package com.fnb.d2c.ui.shipper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import com.fnb.d2c.ui.theme.HerbGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MockShipperSheet(
    orders: List<Order>,
    onDismiss: () -> Unit,
    onAdvanceMilestone: (
        orderId: String,
        newStatus: OrderStatus,
        driverName: String?,
        driverPhone: String?,
        driverPlate: String?
    ) -> Unit
) {
    val activeDeliveryOrders = orders.filter {
        it.status == OrderStatus.FINDING_DRIVER ||
            it.status == OrderStatus.DRIVER_AT_STORE ||
            it.status == OrderStatus.PICKED_UP ||
            it.status == OrderStatus.DELIVERING
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🛵 Bảng Giả lập Tài xế Lalamove (Mock Shipper)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Dùng khi thuyết trình: bấm chuyển mốc giao hàng để đồng bộ tài xế và kích hoạt thông báo rung trên màn hình Khách hàng ngay lập tức.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (activeDeliveryOrders.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Chưa có đơn nào đang gọi Shipper. Hãy vào vai Chủ quán -> Xác nhận đơn -> bấm 'Gọi Shipper' trước nhé!",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                activeDeliveryOrders.forEach { ord ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Đơn #${ord.id.take(6).uppercase()} • ${ord.customerName}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ord.status.labelVi,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Giao đến: ${ord.deliveryAddress}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (ord.paymentMethod == PaymentMethod.COD) {
                                    "Thu hộ COD: ${CurrencyAndTimeFormatter.formatVnd(ord.totalAmount)}"
                                } else {
                                    "Thu hộ COD: 0đ (Khách đã trả qua Ví)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            HorizontalDivider()

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onAdvanceMilestone(
                                            ord.id,
                                            OrderStatus.DRIVER_AT_STORE,
                                            "Nguyễn Văn Tài",
                                            "0909123456",
                                            "59X3-686.88"
                                        )
                                    }
                                ) {
                                    Text("1. Nhận đơn & Đến quán")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onAdvanceMilestone(
                                            ord.id,
                                            OrderStatus.PICKED_UP,
                                            ord.driverName ?: "Nguyễn Văn Tài",
                                            ord.driverPhone ?: "0909123456",
                                            ord.driverPlate ?: "59X3-686.88"
                                        )
                                    }
                                ) {
                                    Text("2. Đã lấy món")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onAdvanceMilestone(
                                            ord.id,
                                            OrderStatus.DELIVERING,
                                            ord.driverName ?: "Nguyễn Văn Tài",
                                            ord.driverPhone ?: "0909123456",
                                            ord.driverPlate ?: "59X3-686.88"
                                        )
                                    }
                                ) {
                                    Text("3. Đang giao")
                                }
                                Button(
                                    onClick = {
                                        onAdvanceMilestone(
                                            ord.id,
                                            OrderStatus.COMPLETED,
                                            ord.driverName ?: "Nguyễn Văn Tài",
                                            ord.driverPhone ?: "0909123456",
                                            ord.driverPlate ?: "59X3-686.88"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = HerbGreen)
                                ) {
                                    Text("4. Giao Hoàn thành ✓")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
