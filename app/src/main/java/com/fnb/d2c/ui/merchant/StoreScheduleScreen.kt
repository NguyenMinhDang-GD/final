package com.fnb.d2c.ui.merchant

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fnb.d2c.domain.model.ResolvedStoreStatus
import com.fnb.d2c.domain.model.ScheduleType
import com.fnb.d2c.domain.model.StoreAvailabilityState
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.StoreSchedule
import com.fnb.d2c.domain.util.StoreStatusResolver
import com.fnb.d2c.ui.theme.BrothAmber
import com.fnb.d2c.ui.theme.DangerAlertText
import com.fnb.d2c.ui.theme.HerbGreen
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreScheduleScreen(
    resolvedStatus: ResolvedStoreStatus,
    schedules: List<StoreSchedule>,
    onSetQuickStatus: (StoreManualStatus, Long?) -> Unit,
    onSaveWeeklySchedule: (Int, String, String, Boolean) -> Unit,
    onSaveSpecialSchedule: (String, String, String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var editingWeeklyDow by remember { mutableStateOf<Int?>(null) }
    var showSpecialDialog by remember { mutableStateOf(false) }

    val weeklyByDow = schedules
        .filter { it.type == ScheduleType.WEEKLY && it.dayOfWeek != null }
        .associateBy { it.dayOfWeek!! }

    val specialList = schedules
        .filter { it.type == ScheduleType.SPECIAL }
        .sortedBy { it.specificDate }

    val statusColor = when (resolvedStatus.state) {
        StoreAvailabilityState.OPEN -> HerbGreen
        StoreAvailabilityState.BUSY -> BrothAmber
        StoreAvailabilityState.CLOSED -> DangerAlertText
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Status Switcher (Open / Busy Timer / Closed)
        item {
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
                        Text("Trạng thái hoạt động tức thì", fontWeight = FontWeight.Bold)
                        Surface(color = statusColor, shape = RoundedCornerShape(50)) {
                            Text(
                                text = resolvedStatus.badgeText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = resolvedStatus.detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSetQuickStatus(StoreManualStatus.OPEN, null) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = HerbGreen)
                        ) {
                            Text("Mở cửa ngay")
                        }
                        OutlinedButton(
                            onClick = { onSetQuickStatus(StoreManualStatus.CLOSED, null) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerAlertText)
                        ) {
                            Text("Đóng cửa")
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = "⏱️ Chế độ Tạm bận (Tự động mở lại khi hết giờ, không cần Cron Job):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Bận 15 phút" to 15L,
                            "Bận 30 phút" to 30L,
                            "Bận 1 tiếng" to 60L,
                            "Nghỉ 1 ngày" to 1440L
                        ).forEach { (label, mins) ->
                            AssistChip(
                                onClick = { onSetQuickStatus(StoreManualStatus.OPEN, mins) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Weekly Schedule (Mon - Sun)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📅 Lịch hoạt động thường ngày (Thứ 2 – Chủ Nhật)", fontWeight = FontWeight.Bold)
                    (1..7).forEach { dow ->
                        val sched = weeklyByDow[dow]
                        val open = sched?.openTime?.take(5) ?: "06:30"
                        val close = sched?.closeTime?.take(5) ?: "22:00"
                        val isClosed = sched?.isClosed ?: false

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(StoreStatusResolver.dayOfWeekLabelVi(dow), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isClosed) "Nghỉ bán" else "$open – $close",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isClosed) DangerAlertText else HerbGreen
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { editingWeeklyDow = dow }) {
                                    Text("Đổi giờ")
                                }
                                Switch(
                                    checked = !isClosed,
                                    onCheckedChange = { openToday ->
                                        onSaveWeeklySchedule(dow, open, close, !openToday)
                                    }
                                )
                            }
                        }
                        if (dow < 7) HorizontalDivider()
                    }
                }
            }
        }

        // Special Date Schedules
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎉 Lịch mở/đóng tùy chỉnh theo ngày lễ", fontWeight = FontWeight.Bold)
                Button(onClick = { showSpecialDialog = true }) {
                    Text("+ Thêm ngày")
                }
            }
        }

        items(specialList, key = { it.id }) { sp ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ngày ${sp.specificDate}", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (sp.isClosed) "Nghỉ bán cả ngày" else "Mở cửa: ${sp.openTime.take(5)} – ${sp.closeTime.take(5)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sp.isClosed) DangerAlertText else HerbGreen
                        )
                    }
                    IconButton(onClick = { onDeleteSchedule(sp.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa lịch")
                    }
                }
            }
        }
    }

    editingWeeklyDow?.let { dow ->
        val existing = weeklyByDow[dow]
        var openStr by remember(dow) { mutableStateOf(existing?.openTime?.take(5) ?: "06:30") }
        var closeStr by remember(dow) { mutableStateOf(existing?.closeTime?.take(5) ?: "22:00") }
        AlertDialog(
            onDismissRequest = { editingWeeklyDow = null },
            title = { Text("Giờ mở cửa ${StoreStatusResolver.dayOfWeekLabelVi(dow)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = openStr, onValueChange = { openStr = it }, label = { Text("Giờ mở (HH:mm)") })
                    OutlinedTextField(value = closeStr, onValueChange = { closeStr = it }, label = { Text("Giờ đóng (HH:mm)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveWeeklySchedule(dow, openStr, closeStr, existing?.isClosed ?: false)
                    editingWeeklyDow = null
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { editingWeeklyDow = null }) { Text("Hủy") } }
        )
    }

    if (showSpecialDialog) {
        var dateStr by remember { mutableStateOf(LocalDate.now().toString()) }
        var openStr by remember { mutableStateOf("08:00") }
        var closeStr by remember { mutableStateOf("20:00") }
        var closedAllDay by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showSpecialDialog = false },
            title = { Text("Lịch ngày đặc biệt / Nghỉ lễ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Ngày (yyyy-MM-dd)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = closedAllDay, onCheckedChange = { closedAllDay = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Nghỉ bán trọn ngày này")
                    }
                    if (!closedAllDay) {
                        OutlinedTextField(value = openStr, onValueChange = { openStr = it }, label = { Text("Giờ mở (HH:mm)") })
                        OutlinedTextField(value = closeStr, onValueChange = { closeStr = it }, label = { Text("Giờ đóng (HH:mm)") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveSpecialSchedule(dateStr, openStr, closeStr, closedAllDay)
                    showSpecialDialog = false
                }) { Text("Lưu lịch") }
            },
            dismissButton = { TextButton(onClick = { showSpecialDialog = false }) { Text("Hủy") } }
        )
    }
}
