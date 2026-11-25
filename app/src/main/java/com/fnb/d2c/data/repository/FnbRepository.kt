package com.fnb.d2c.data.repository

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.Category
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.MenuOption
import com.fnb.d2c.domain.model.OptionGroup
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.model.Review
import com.fnb.d2c.domain.model.SelectionType
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.StoreSchedule
import com.fnb.d2c.domain.model.StoreSettings
import com.fnb.d2c.domain.model.UserProfile
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.domain.model.Voucher
import kotlinx.coroutines.flow.StateFlow

interface FnbRepository {
    val backendModeLabel: String
    val currentUser: StateFlow<UserProfile?>
    val storeSettings: StateFlow<StoreSettings>
    val storeSchedules: StateFlow<List<StoreSchedule>>
    val categories: StateFlow<List<Category>>
    val menuItems: StateFlow<List<MenuItem>>
    val vouchers: StateFlow<List<Voucher>>
    val orders: StateFlow<List<Order>>
    val reviews: StateFlow<List<Review>>

    // 0. Auth & Wallet
    suspend fun signIn(email: String, password: String): Result<UserProfile>
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String
    ): Result<UserProfile>
    suspend fun quickSwitchDemoRole(role: UserRole): Result<UserProfile>
    suspend fun signOut()
    suspend fun topUpDemoWallet(amount: Long): Result<Unit>

    // 1. Merchant Catalog & Vouchers
    suspend fun saveCategory(id: String?, name: String, sortOrder: Int): Result<Unit>
    suspend fun deleteCategory(categoryId: String): Result<Unit>

    suspend fun saveMenuItem(
        id: String?,
        categoryId: String,
        name: String,
        description: String,
        basePrice: Long,
        imageUrl: String,
        isAvailable: Boolean
    ): Result<String>
    suspend fun toggleMenuItemAvailability(menuItemId: String, isAvailable: Boolean): Result<Unit>
    suspend fun deleteMenuItem(menuItemId: String): Result<Unit>

    suspend fun saveOptionGroup(
        id: String?,
        menuItemId: String,
        name: String,
        selectionType: SelectionType,
        isRequired: Boolean
    ): Result<String>
    suspend fun deleteOptionGroup(groupId: String): Result<Unit>

    suspend fun saveMenuOption(
        id: String?,
        groupId: String,
        name: String,
        extraPrice: Long,
        isAvailable: Boolean
    ): Result<Unit>
    suspend fun deleteMenuOption(optionId: String): Result<Unit>

    suspend fun saveVoucher(
        id: String?,
        code: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderValue: Long,
        usageLimit: Int,
        endAtIso: String,
        isActive: Boolean
    ): Result<Unit>
    suspend fun toggleVoucherActive(voucherId: String, isActive: Boolean): Result<Unit>
    suspend fun deleteVoucher(voucherId: String): Result<Unit>

    // 2. Customer Order Placement
    suspend fun placeOrder(
        deliveryAddress: String,
        deliveryLat: Double,
        deliveryLng: Double,
        distanceKm: Double,
        shippingFee: Long,
        voucherCode: String?,
        paymentMethod: PaymentMethod,
        cartItems: List<CartLineItem>
    ): Result<Order>

    // 3. Kitchen Processing & Store Status / Schedules
    suspend fun confirmOrderPreparing(orderId: String): Result<Unit>
    suspend fun rejectOrderAndRefund(orderId: String, reason: String): Result<Unit>
    suspend fun setStoreQuickStatus(manualStatus: StoreManualStatus, busyDurationMinutes: Long?): Result<Unit>
    suspend fun updateWeeklySchedule(
        dayOfWeek: Int,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit>
    suspend fun upsertSpecialSchedule(
        specificDate: String,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit>
    suspend fun deleteSchedule(scheduleId: String): Result<Unit>

    // 4 & 5. Automated Delivery Dispatch, Tracking & Reviews
    suspend fun requestShipperDispatch(orderId: String): Result<Unit>
    suspend fun updateDeliveryMilestone(
        orderId: String,
        newStatus: OrderStatus,
        driverName: String? = null,
        driverPhone: String? = null,
        driverPlate: String? = null
    ): Result<Unit>
    suspend fun submitReview(orderId: String, rating: Int, comment: String): Result<Unit>
    suspend fun refreshAll()
}
