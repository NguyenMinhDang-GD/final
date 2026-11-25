package com.fnb.d2c.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class UserRole {
    CUSTOMER,
    MERCHANT
}

@Serializable
data class UserProfile(
    val id: String,
    val email: String = "",
    @SerialName("full_name") val fullName: String,
    val phone: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    @SerialName("wallet_balance") val walletBalance: Long = 500_000L
)

@Serializable
enum class StoreManualStatus {
    OPEN,
    CLOSED
}

@Serializable
data class StoreSettings(
    val id: Int = 1,
    val name: String = "Bún Riêu & Bún Chả Cá Cô Ba",
    val address: String = "227 Nguyễn Văn Cừ, Phường 4, Quận 5, TP.HCM",
    val lat: Double = 10.762622,
    val lng: Double = 106.682172,
    @SerialName("manual_status") val manualStatus: StoreManualStatus = StoreManualStatus.OPEN,
    @SerialName("busy_until") val busyUntilIso: String? = null
)

@Serializable
enum class ScheduleType {
    WEEKLY,
    SPECIAL
}

@Serializable
data class StoreSchedule(
    val id: String,
    val type: ScheduleType,
    @SerialName("day_of_week") val dayOfWeek: Int? = null, // 1 (Mon) .. 7 (Sun)
    @SerialName("specific_date") val specificDate: String? = null, // yyyy-MM-dd
    @SerialName("open_time") val openTime: String = "06:30", // HH:mm
    @SerialName("close_time") val closeTime: String = "22:00", // HH:mm
    @SerialName("is_closed") val isClosed: Boolean = false
)

enum class StoreAvailabilityState {
    OPEN,
    BUSY,
    CLOSED
}

data class ResolvedStoreStatus(
    val state: StoreAvailabilityState,
    val badgeText: String,
    val detailText: String
) {
    val canAcceptOrder: Boolean get() = state == StoreAvailabilityState.OPEN
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
enum class SelectionType {
    SINGLE,
    MULTI
}

@Serializable
data class MenuOption(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val name: String,
    @SerialName("extra_price") val extraPrice: Long = 0L,
    @SerialName("is_available") val isAvailable: Boolean = true
)

@Serializable
data class OptionGroup(
    val id: String,
    @SerialName("menu_item_id") val menuItemId: String,
    val name: String,
    @SerialName("selection_type") val selectionType: SelectionType,
    @SerialName("is_required") val isRequired: Boolean = false,
    val options: List<MenuOption> = emptyList()
)

@Serializable
data class MenuItem(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String = "",
    @SerialName("base_price") val basePrice: Long,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("is_available") val isAvailable: Boolean = true,
    val optionGroups: List<OptionGroup> = emptyList()
)

@Serializable
enum class DiscountType {
    PERCENT,
    FIXED
}

@Serializable
data class Voucher(
    val id: String,
    val code: String,
    @SerialName("discount_type") val discountType: DiscountType,
    @SerialName("discount_value") val discountValue: Long,
    @SerialName("min_order_value") val minOrderValue: Long = 0L,
    @SerialName("usage_limit") val usageLimit: Int = 100,
    @SerialName("used_count") val usedCount: Int = 0,
    @SerialName("end_at") val endAtIso: String,
    @SerialName("is_active") val isActive: Boolean = true
) {
    val remainingUses: Int get() = max(0, usageLimit - usedCount)
}

@Serializable
data class SelectedOptionSnapshot(
    @SerialName("option_id") val optionId: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("option_name") val optionName: String,
    @SerialName("extra_price") val extraPrice: Long
)

data class CartLineItem(
    val cartKey: String,
    val menuItemId: String,
    val menuItemName: String,
    val imageUrl: String,
    val basePrice: Long,
    val selectedOptions: List<SelectedOptionSnapshot>,
    val specialNote: String,
    val quantity: Int
) {
    val unitPrice: Long
        get() = max(0L, basePrice + selectedOptions.sumOf { it.extraPrice })

    val lineTotal: Long
        get() = unitPrice * quantity
}

@Serializable
enum class OrderStatus(val labelVi: String, val stepIndex: Int) {
    PENDING("Chờ quán xác nhận", 0),
    PREPARING("Quán đang chuẩn bị món", 1),
    FINDING_DRIVER("Đang tìm tài xế", 2),
    DRIVER_AT_STORE("Tài xế đã đến quán", 3),
    PICKED_UP("Tài xế đã lấy món", 4),
    DELIVERING("Đang giao hàng", 5),
    COMPLETED("Hoàn thành", 6),
    REJECTED("Quán từ chối đơn", -1),
    REFUNDED("Đã từ chối & Hoàn tiền", -2);

    val isRejectedOrRefunded: Boolean
        get() = this == REJECTED || this == REFUNDED

    val isInDeliveryFlow: Boolean
        get() = this == FINDING_DRIVER || this == DRIVER_AT_STORE || this == PICKED_UP || this == DELIVERING
}

@Serializable
enum class PaymentMethod(val labelVi: String) {
    COD("Tiền mặt (COD)"),
    WALLET("Ví ứng dụng (Trả trước)")
}

@Serializable
enum class PaymentStatus(val labelVi: String) {
    UNPAID("Chưa thanh toán (Thu hộ)"),
    PAID("Đã thanh toán qua Ví"),
    REFUNDED("Đã hoàn tiền về Ví")
}

@Serializable
data class OrderItem(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("menu_item_id") val menuItemId: String? = null,
    @SerialName("item_name_snapshot") val itemNameSnapshot: String,
    @SerialName("unit_price_snapshot") val unitPriceSnapshot: Long,
    @SerialName("selected_options_snapshot") val selectedOptionsSnapshot: List<SelectedOptionSnapshot> = emptyList(),
    val quantity: Int,
    @SerialName("special_note") val specialNote: String = ""
) {
    val lineTotal: Long get() = unitPriceSnapshot * quantity
}

@Serializable
data class Review(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("customer_id") val customerId: String,
    val customerName: String = "Khách hàng",
    val rating: Int,
    val comment: String = "",
    @SerialName("created_at") val createdAtIso: String
)

@Serializable
data class Order(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    val customerName: String = "Khách hàng",
    val customerPhone: String = "0909111222",
    @SerialName("voucher_code") val voucherCode: String? = null,
    val status: OrderStatus = OrderStatus.PENDING,
    @SerialName("delivery_address") val deliveryAddress: String,
    @SerialName("delivery_lat") val deliveryLat: Double,
    @SerialName("delivery_lng") val deliveryLng: Double,
    @SerialName("distance_km") val distanceKm: Double = 0.0,
    @SerialName("subtotal_amount") val subtotalAmount: Long,
    @SerialName("discount_amount") val discountAmount: Long = 0L,
    @SerialName("shipping_fee") val shippingFee: Long,
    @SerialName("total_amount") val totalAmount: Long,
    @SerialName("payment_method") val paymentMethod: PaymentMethod,
    @SerialName("payment_status") val paymentStatus: PaymentStatus,
    @SerialName("reject_reason") val rejectReason: String? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_phone") val driverPhone: String? = null,
    @SerialName("driver_plate") val driverPlate: String? = null,
    @SerialName("picked_up_at") val pickedUpAtIso: String? = null,
    @SerialName("created_at") val createdAtIso: String,
    val items: List<OrderItem> = emptyList(),
    val review: Review? = null
)

data class DeliveryLocation(
    val address: String,
    val lat: Double,
    val lng: Double
)

data class ShippingQuote(
    val distanceKm: Double,
    val shippingFee: Long,
    val sourceLabel: String
)

data class ReorderResult(
    val addedItems: List<CartLineItem>,
    val skippedItemNames: List<String>
)
