package com.fnb.d2c.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fnb.d2c.MainActivity
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.util.CurrencyAndTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles:
 * 1. MERCHANT_NEW_ORDER channel + repeating bell chime until Merchant confirms/rejects.
 * 2. CUSTOMER_ORDER_UPDATE channel + vibration pattern [0, 400, 200, 400] on status/refund events.
 */
class OrderNotificationManager(private val appContext: Context) {

    companion object {
        const val CHANNEL_MERCHANT_NEW_ORDER = "MERCHANT_NEW_ORDER"
        const val CHANNEL_CUSTOMER_ORDER_UPDATE = "CUSTOMER_ORDER_UPDATE"
        val CUSTOMER_VIBRATION_PATTERN = longArrayOf(0L, 400L, 200L, 400L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ringingJob: Job? = null

    private val _isBellRinging = MutableStateFlow(false)
    val isBellRinging: StateFlow<Boolean> = _isBellRinging.asStateFlow()

    init {
        createChannels()
    }

    private fun createChannels() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val merchantChannel = NotificationChannel(
            CHANNEL_MERCHANT_NEW_ORDER,
            "Đơn hàng mới tại Quán",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Chuông báo khi khách vừa đặt đơn hàng mới"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 500L, 200L, 500L)
        }

        val customerChannel = NotificationChannel(
            CHANNEL_CUSTOMER_ORDER_UPDATE,
            "Cập nhật tiến độ đơn hàng",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo kèm rung khi đơn hàng đổi trạng thái hoặc hoàn tiền"
            enableVibration(true)
            vibrationPattern = CUSTOMER_VIBRATION_PATTERN
        }

        nm.createNotificationChannel(merchantChannel)
        nm.createNotificationChannel(customerChannel)
    }

    fun triggerMerchantNewOrderAlert(order: Order) {
        startMerchantBellLoop()
        postNotification(
            channelId = CHANNEL_MERCHANT_NEW_ORDER,
            notificationId = order.id.hashCode(),
            title = "🔔 Có đơn mới #${order.id.take(6).uppercase()}!",
            body = "${order.customerName} vừa đặt ${order.items.sumOf { it.quantity }} phần (${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)}). Xác nhận ngay!"
        )
    }

    fun startMerchantBellLoop() {
        if (ringingJob?.isActive == true) return
        _isBellRinging.value = true
        ringingJob = scope.launch {
            val toneGen = runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
            }.getOrNull()
            try {
                repeat(12) {
                    if (!isActive) return@launch
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
                    vibratePattern(longArrayOf(0L, 300L, 150L, 300L))
                    delay(2200L)
                }
            } finally {
                runCatching { toneGen?.release() }
                _isBellRinging.value = false
            }
        }
    }

    fun stopMerchantBell() {
        ringingJob?.cancel()
        ringingJob = null
        _isBellRinging.value = false
    }

    fun notifyCustomerOrderChanged(order: Order) {
        vibratePattern(CUSTOMER_VIBRATION_PATTERN)
        val shortId = "#${order.id.take(6).uppercase()}"
        val (title, message) = when (order.status) {
            com.fnb.d2c.domain.model.OrderStatus.PREPARING ->
                "🍲 Quán đang chuẩn bị món ($shortId)" to "Bếp đã nhận đơn của bạn và đang chế biến nóng hổi."
            com.fnb.d2c.domain.model.OrderStatus.REJECTED ->
                "⚠️ Quán từ chối đơn ($shortId)" to "Lý do: ${order.rejectReason ?: "Tạm hết nguyên liệu"}."
            com.fnb.d2c.domain.model.OrderStatus.REFUNDED ->
                "💸 Đã hoàn tiền về Ví ($shortId)" to "Lý do từ chối: ${order.rejectReason ?: "Hết món"}. Đã hoàn ${CurrencyAndTimeFormatter.formatVnd(order.totalAmount)} vào Ví của bạn."
            com.fnb.d2c.domain.model.OrderStatus.FINDING_DRIVER ->
                "🛵 Đang tìm tài xế giao hàng ($shortId)" to "Món đã đóng gói xong, hệ thống đang điều phối Shipper."
            com.fnb.d2c.domain.model.OrderStatus.DRIVER_AT_STORE ->
                "📍 Tài xế đã đến quán ($shortId)" to "Tài xế ${order.driverName ?: ""} (${order.driverPlate ?: ""}) đang nhận món tại quán."
            com.fnb.d2c.domain.model.OrderStatus.PICKED_UP ->
                "📦 Tài xế đã lấy món ($shortId)" to "Tài xế ${order.driverName ?: ""} vừa rời quán cùng đơn hàng của bạn."
            com.fnb.d2c.domain.model.OrderStatus.DELIVERING ->
                "🚀 Đơn đang trên đường giao ($shortId)" to "Tài xế đang di chuyển tới ${order.deliveryAddress}."
            com.fnb.d2c.domain.model.OrderStatus.COMPLETED ->
                "✅ Giao hàng thành công ($shortId)" to "Chúc bạn ngon miệng! Hãy chấm sao đánh giá món ăn nhé."
            com.fnb.d2c.domain.model.OrderStatus.PENDING ->
                "📝 Đã gửi đơn hàng ($shortId)" to "Đang chờ quán xác nhận đơn của bạn."
        }

        postNotification(
            channelId = CHANNEL_CUSTOMER_ORDER_UPDATE,
            notificationId = (order.id + order.status.name).hashCode(),
            title = title,
            body = message
        )
    }

    private fun vibratePattern(pattern: LongArray) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun postNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(CUSTOMER_VIBRATION_PATTERN)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
        }
    }
}
