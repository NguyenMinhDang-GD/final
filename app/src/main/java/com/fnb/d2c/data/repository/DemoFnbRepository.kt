package com.fnb.d2c.data.repository

import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.Category
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.MenuOption
import com.fnb.d2c.domain.model.OptionGroup
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderItem
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.model.PaymentStatus
import com.fnb.d2c.domain.model.Review
import com.fnb.d2c.domain.model.ScheduleType
import com.fnb.d2c.domain.model.SelectionType
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.StoreSchedule
import com.fnb.d2c.domain.model.StoreSettings
import com.fnb.d2c.domain.model.UserProfile
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.domain.model.Voucher
import com.fnb.d2c.domain.util.OrderPriceCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Rich, reactive local repository pre-populated with "Bún Riêu & Bún Chả Cá Cô Ba" seed data.
 * Enables 100% instant testing of Customer <-> Merchant <-> Mock Shipper Realtime workflows
 * even before configuring Supabase keys, and mirrors every PostgreSQL RPC constraint identically.
 */
class DemoFnbRepository(
    private val onNewOrderCreated: (Order) -> Unit = {},
    private val onOrderStatusUpdated: (Order) -> Unit = {}
) : FnbRepository {

    override val backendModeLabel: String = "Chế độ Demo Cục bộ (Đồng bộ tức thì)"

    private val mutex = Mutex()

    private val accounts = mutableMapOf(
        "khach@coba.vn" to ("123456" to UserProfile(
            id = "cust-demo-001",
            email = "khach@coba.vn",
            fullName = "Nguyễn Minh Anh",
            phone = "0908123456",
            role = UserRole.CUSTOMER,
            walletBalance = 500_000L
        )),
        "quan@coba.vn" to ("123456" to UserProfile(
            id = "merch-demo-001",
            email = "quan@coba.vn",
            fullName = "Cô Ba Chủ Quán",
            phone = "0901999888",
            role = UserRole.MERCHANT,
            walletBalance = 0L
        ))
    )

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _storeSettings = MutableStateFlow(
        StoreSettings(
            id = 1,
            name = "Bún Riêu & Bún Chả Cá Cô Ba",
            address = "227 Nguyễn Văn Cừ, Phường 4, Quận 5, TP.HCM",
            lat = 10.762622,
            lng = 106.682172,
            manualStatus = StoreManualStatus.OPEN,
            busyUntilIso = null
        )
    )
    override val storeSettings: StateFlow<StoreSettings> = _storeSettings.asStateFlow()

    private val _storeSchedules = MutableStateFlow(seedSchedules())
    override val storeSchedules: StateFlow<List<StoreSchedule>> = _storeSchedules.asStateFlow()

    private val _categories = MutableStateFlow(seedCategories())
    override val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _menuItems = MutableStateFlow(seedMenuItems())
    override val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _vouchers = MutableStateFlow(seedVouchers())
    override val vouchers: StateFlow<List<Voucher>> = _vouchers.asStateFlow()

    private val _reviews = MutableStateFlow(seedReviews())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _orders = MutableStateFlow(seedOrders())
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<UserProfile> = mutex.withLock {
        val key = email.trim().lowercase()
        val entry = accounts[key]
            ?: return Result.failure(IllegalArgumentException("Không tìm thấy tài khoản $key. Dùng khach@coba.vn hoặc quan@coba.vn (MK: 123456)."))
        if (entry.first != password) {
            return Result.failure(IllegalArgumentException("Mật khẩu không chính xác."))
        }
        _currentUser.value = entry.second
        Result.success(entry.second)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        role: UserRole
    ): Result<UserProfile> = mutex.withLock {
        val key = email.trim().lowercase()
        if (key.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Email không hợp lệ hoặc mật khẩu dưới 6 ký tự."))
        }
        if (accounts.containsKey(key)) {
            return Result.failure(IllegalArgumentException("Email $key đã được đăng ký."))
        }
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            email = key,
            fullName = fullName.ifBlank { key.substringBefore("@") },
            phone = phone.ifBlank { "0909000111" },
            role = role,
            walletBalance = if (role == UserRole.CUSTOMER) 500_000L else 0L
        )
        accounts[key] = password to profile
        _currentUser.value = profile
        Result.success(profile)
    }

    override suspend fun quickSwitchDemoRole(role: UserRole): Result<UserProfile> = mutex.withLock {
        val key = if (role == UserRole.CUSTOMER) "khach@coba.vn" else "quan@coba.vn"
        val profile = accounts.getValue(key).second
        _currentUser.value = profile
        Result.success(profile)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    override suspend fun topUpDemoWallet(amount: Long): Result<Unit> = mutex.withLock {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("Chưa đăng nhập."))
        val updated = user.copy(walletBalance = user.walletBalance + amount.coerceAtLeast(0L))
        updateAccountProfile(updated)
        Result.success(Unit)
    }

    private fun updateAccountProfile(updated: UserProfile) {
        val existingPair = accounts[updated.email.lowercase()]
        if (existingPair != null) {
            accounts[updated.email.lowercase()] = existingPair.first to updated
        } else {
            val matchKey = accounts.entries.firstOrNull { it.value.second.id == updated.id }?.key
            if (matchKey != null) {
                accounts[matchKey] = accounts.getValue(matchKey).first to updated
            }
        }
        if (_currentUser.value?.id == updated.id) {
            _currentUser.value = updated
        }
    }

    override suspend fun saveCategory(id: String?, name: String, sortOrder: Int): Result<Unit> = mutex.withLock {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Tên danh mục không được để trống."))
        val list = _categories.value.toMutableList()
        if (id == null) {
            list += Category(id = UUID.randomUUID().toString(), name = trimmed, sortOrder = sortOrder)
        } else {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(name = trimmed, sortOrder = sortOrder)
        }
        _categories.value = list.sortedBy { it.sortOrder }
        Result.success(Unit)
    }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> = mutex.withLock {
        _categories.value = _categories.value.filterNot { it.id == categoryId }
        _menuItems.value = _menuItems.value.filterNot { it.categoryId == categoryId }
        Result.success(Unit)
    }

    override suspend fun saveMenuItem(
        id: String?,
        categoryId: String,
        name: String,
        description: String,
        basePrice: Long,
        imageUrl: String,
        isAvailable: Boolean
    ): Result<String> = mutex.withLock {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Tên món không được để trống."))
        if (basePrice < 0) return Result.failure(IllegalArgumentException("Giá món không hợp lệ."))
        val list = _menuItems.value.toMutableList()
        val finalId = id ?: UUID.randomUUID().toString()
        val idx = list.indexOfFirst { it.id == finalId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                categoryId = categoryId,
                name = trimmed,
                description = description.trim(),
                basePrice = basePrice,
                imageUrl = imageUrl.trim(),
                isAvailable = isAvailable
            )
        } else {
            list += MenuItem(
                id = finalId,
                categoryId = categoryId,
                name = trimmed,
                description = description.trim(),
                basePrice = basePrice,
                imageUrl = imageUrl.trim(),
                isAvailable = isAvailable,
                optionGroups = emptyList()
            )
        }
        _menuItems.value = list
        Result.success(finalId)
    }

    override suspend fun toggleMenuItemAvailability(menuItemId: String, isAvailable: Boolean): Result<Unit> =
        mutex.withLock {
            _menuItems.value = _menuItems.value.map {
                if (it.id == menuItemId) it.copy(isAvailable = isAvailable) else it
            }
            Result.success(Unit)
        }

    override suspend fun deleteMenuItem(menuItemId: String): Result<Unit> = mutex.withLock {
        _menuItems.value = _menuItems.value.filterNot { it.id == menuItemId }
        Result.success(Unit)
    }

    override suspend fun saveOptionGroup(
        id: String?,
        menuItemId: String,
        name: String,
        selectionType: SelectionType,
        isRequired: Boolean
    ): Result<String> = mutex.withLock {
        val groupId = id ?: UUID.randomUUID().toString()
        _menuItems.value = _menuItems.value.map { dish ->
            if (dish.id != menuItemId) return@map dish
            val groups = dish.optionGroups.toMutableList()
            val idx = groups.indexOfFirst { it.id == groupId }
            if (idx >= 0) {
                groups[idx] = groups[idx].copy(
                    name = name.trim(),
                    selectionType = selectionType,
                    isRequired = isRequired
                )
            } else {
                groups += OptionGroup(
                    id = groupId,
                    menuItemId = menuItemId,
                    name = name.trim(),
                    selectionType = selectionType,
                    isRequired = isRequired,
                    options = emptyList()
                )
            }
            dish.copy(optionGroups = groups)
        }
        Result.success(groupId)
    }

    override suspend fun deleteOptionGroup(groupId: String): Result<Unit> = mutex.withLock {
        _menuItems.value = _menuItems.value.map { dish ->
            dish.copy(optionGroups = dish.optionGroups.filterNot { it.id == groupId })
        }
        Result.success(Unit)
    }

    override suspend fun saveMenuOption(
        id: String?,
        groupId: String,
        name: String,
        extraPrice: Long,
        isAvailable: Boolean
    ): Result<Unit> = mutex.withLock {
        val optId = id ?: UUID.randomUUID().toString()
        _menuItems.value = _menuItems.value.map { dish ->
            dish.copy(
                optionGroups = dish.optionGroups.map { group ->
                    if (group.id != groupId) return@map group
                    val opts = group.options.toMutableList()
                    val idx = opts.indexOfFirst { it.id == optId }
                    if (idx >= 0) {
                        opts[idx] = opts[idx].copy(
                            name = name.trim(),
                            extraPrice = extraPrice,
                            isAvailable = isAvailable
                        )
                    } else {
                        opts += MenuOption(
                            id = optId,
                            groupId = groupId,
                            name = name.trim(),
                            extraPrice = extraPrice,
                            isAvailable = isAvailable
                        )
                    }
                    group.copy(options = opts)
                }
            )
        }
        Result.success(Unit)
    }

    override suspend fun deleteMenuOption(optionId: String): Result<Unit> = mutex.withLock {
        _menuItems.value = _menuItems.value.map { dish ->
            dish.copy(
                optionGroups = dish.optionGroups.map { group ->
                    group.copy(options = group.options.filterNot { it.id == optionId })
                }
            )
        }
        Result.success(Unit)
    }

    override suspend fun saveVoucher(
        id: String?,
        code: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderValue: Long,
        usageLimit: Int,
        endAtIso: String,
        isActive: Boolean
    ): Result<Unit> = mutex.withLock {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) return Result.failure(IllegalArgumentException("Mã giảm giá không được để trống."))
        val list = _vouchers.value.toMutableList()
        val finalId = id ?: UUID.randomUUID().toString()
        val idx = list.indexOfFirst { it.id == finalId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                code = cleanCode,
                discountType = discountType,
                discountValue = discountValue,
                minOrderValue = minOrderValue,
                usageLimit = usageLimit.coerceAtLeast(1),
                endAtIso = endAtIso,
                isActive = isActive
            )
        } else {
            list += Voucher(
                id = finalId,
                code = cleanCode,
                discountType = discountType,
                discountValue = discountValue,
                minOrderValue = minOrderValue,
                usageLimit = usageLimit.coerceAtLeast(1),
                usedCount = 0,
                endAtIso = endAtIso,
                isActive = isActive
            )
        }
        _vouchers.value = list
        Result.success(Unit)
    }

    override suspend fun toggleVoucherActive(voucherId: String, isActive: Boolean): Result<Unit> = mutex.withLock {
        _vouchers.value = _vouchers.value.map {
            if (it.id == voucherId) it.copy(isActive = isActive) else it
        }
        Result.success(Unit)
    }

    override suspend fun deleteVoucher(voucherId: String): Result<Unit> = mutex.withLock {
        _vouchers.value = _vouchers.value.filterNot { it.id == voucherId }
        Result.success(Unit)
    }

    override suspend fun placeOrder(
        deliveryAddress: String,
        deliveryLat: Double,
        deliveryLng: Double,
        distanceKm: Double,
        shippingFee: Long,
        voucherCode: String?,
        paymentMethod: PaymentMethod,
        cartItems: List<CartLineItem>
    ): Result<Order> {
        val createdOrder: Order = mutex.withLock {
            val user = _currentUser.value
                ?: return Result.failure(IllegalStateException("Vui lòng đăng nhập để đặt món."))
            if (cartItems.isEmpty()) {
                return Result.failure(IllegalArgumentException("Giỏ hàng đang trống."))
            }

            val cleanCode = voucherCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            val matchedVoucher = cleanCode?.let { code ->
                _vouchers.value.firstOrNull { it.code.equals(code, ignoreCase = true) }
                    ?: return Result.failure(IllegalArgumentException("Mã giảm giá $code không tồn tại."))
            }

            val pricing = OrderPriceCalculator.calculate(
                items = cartItems,
                shippingFee = shippingFee,
                voucher = matchedVoucher
            )
            if (pricing.voucherError != null) {
                return Result.failure(IllegalArgumentException(pricing.voucherError))
            }

            if (paymentMethod == PaymentMethod.WALLET && user.walletBalance < pricing.totalAmount) {
                return Result.failure(
                    IllegalStateException("Số dư Ví không đủ. Vui lòng nạp thêm hoặc chọn Tiền mặt (COD).")
                )
            }

            // Atomically update voucher used_count
            if (matchedVoucher != null) {
                _vouchers.value = _vouchers.value.map {
                    if (it.id == matchedVoucher.id) it.copy(usedCount = it.usedCount + 1) else it
                }
            }

            // Atomically deduct wallet if WALLET
            val paymentStatus = if (paymentMethod == PaymentMethod.WALLET) {
                updateAccountProfile(user.copy(walletBalance = user.walletBalance - pricing.totalAmount))
                PaymentStatus.PAID
            } else {
                PaymentStatus.UNPAID
            }

            val orderId = UUID.randomUUID().toString()
            val nowIso = Instant.now().toString()
            val orderItems = cartItems.map { line ->
                OrderItem(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    menuItemId = line.menuItemId,
                    itemNameSnapshot = line.menuItemName,
                    unitPriceSnapshot = line.unitPrice,
                    selectedOptionsSnapshot = line.selectedOptions,
                    quantity = line.quantity,
                    specialNote = line.specialNote.trim()
                )
            }

            val newOrder = Order(
                id = orderId,
                customerId = user.id,
                customerName = user.fullName,
                customerPhone = user.phone,
                voucherCode = cleanCode,
                status = OrderStatus.PENDING,
                deliveryAddress = deliveryAddress,
                deliveryLat = deliveryLat,
                deliveryLng = deliveryLng,
                distanceKm = distanceKm,
                subtotalAmount = pricing.subtotalAmount,
                discountAmount = pricing.discountAmount,
                shippingFee = pricing.shippingFee,
                totalAmount = pricing.totalAmount,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                createdAtIso = nowIso,
                items = orderItems
            )
            _orders.value = listOf(newOrder) + _orders.value
            newOrder
        }

        onNewOrderCreated(createdOrder)
        return Result.success(createdOrder)
    }

    override suspend fun confirmOrderPreparing(orderId: String): Result<Unit> {
        val updated = mutex.withLock {
            val current = _orders.value.firstOrNull { it.id == orderId }
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy đơn hàng."))
            val next = current.copy(status = OrderStatus.PREPARING)
            _orders.value = _orders.value.map { if (it.id == orderId) next else it }
            next
        }
        onOrderStatusUpdated(updated)
        return Result.success(Unit)
    }

    override suspend fun rejectOrderAndRefund(orderId: String, reason: String): Result<Unit> {
        val updated = mutex.withLock {
            val current = _orders.value.firstOrNull { it.id == orderId }
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy đơn hàng."))
            val cleanReason = reason.trim().ifBlank { "Quán tạm hết nguyên liệu" }

            // Restore voucher usage
            if (!current.voucherCode.isNullOrBlank()) {
                _vouchers.value = _vouchers.value.map {
                    if (it.code.equals(current.voucherCode, ignoreCase = true)) {
                        it.copy(usedCount = (it.usedCount - 1).coerceAtLeast(0))
                    } else it
                }
            }

            val next = if (current.paymentMethod == PaymentMethod.WALLET && current.paymentStatus == PaymentStatus.PAID) {
                // Refund to customer's wallet
                val custEntry = accounts.values.firstOrNull { it.second.id == current.customerId }?.second
                if (custEntry != null) {
                    updateAccountProfile(custEntry.copy(walletBalance = custEntry.walletBalance + current.totalAmount))
                }
                current.copy(
                    status = OrderStatus.REFUNDED,
                    paymentStatus = PaymentStatus.REFUNDED,
                    rejectReason = cleanReason
                )
            } else {
                current.copy(
                    status = OrderStatus.REJECTED,
                    rejectReason = cleanReason
                )
            }

            _orders.value = _orders.value.map { if (it.id == orderId) next else it }
            next
        }
        onOrderStatusUpdated(updated)
        return Result.success(Unit)
    }

    override suspend fun setStoreQuickStatus(
        manualStatus: StoreManualStatus,
        busyDurationMinutes: Long?
    ): Result<Unit> = mutex.withLock {
        val busyUntil = busyDurationMinutes?.let {
            Instant.now().plus(it, ChronoUnit.MINUTES).toString()
        }
        _storeSettings.value = _storeSettings.value.copy(
            manualStatus = manualStatus,
            busyUntilIso = busyUntil
        )
        Result.success(Unit)
    }

    override suspend fun updateWeeklySchedule(
        dayOfWeek: Int,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit> = mutex.withLock {
        val list = _storeSchedules.value.toMutableList()
        val idx = list.indexOfFirst { it.type == ScheduleType.WEEKLY && it.dayOfWeek == dayOfWeek }
        if (idx >= 0) {
            list[idx] = list[idx].copy(openTime = openTime, closeTime = closeTime, isClosed = isClosed)
        } else {
            list += StoreSchedule(
                id = UUID.randomUUID().toString(),
                type = ScheduleType.WEEKLY,
                dayOfWeek = dayOfWeek,
                openTime = openTime,
                closeTime = closeTime,
                isClosed = isClosed
            )
        }
        _storeSchedules.value = list
        Result.success(Unit)
    }

    override suspend fun upsertSpecialSchedule(
        specificDate: String,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit> = mutex.withLock {
        val list = _storeSchedules.value.toMutableList()
        val idx = list.indexOfFirst { it.type == ScheduleType.SPECIAL && it.specificDate == specificDate }
        if (idx >= 0) {
            list[idx] = list[idx].copy(openTime = openTime, closeTime = closeTime, isClosed = isClosed)
        } else {
            list += StoreSchedule(
                id = UUID.randomUUID().toString(),
                type = ScheduleType.SPECIAL,
                specificDate = specificDate,
                openTime = openTime,
                closeTime = closeTime,
                isClosed = isClosed
            )
        }
        _storeSchedules.value = list
        Result.success(Unit)
    }

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> = mutex.withLock {
        _storeSchedules.value = _storeSchedules.value.filterNot { it.id == scheduleId }
        Result.success(Unit)
    }

    override suspend fun requestShipperDispatch(orderId: String): Result<Unit> =
        updateDeliveryMilestone(orderId = orderId, newStatus = OrderStatus.FINDING_DRIVER)

    override suspend fun updateDeliveryMilestone(
        orderId: String,
        newStatus: OrderStatus,
        driverName: String?,
        driverPhone: String?,
        driverPlate: String?
    ): Result<Unit> {
        val updated = mutex.withLock {
            val current = _orders.value.firstOrNull { it.id == orderId }
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy đơn hàng."))
            val pickedUpIso = when {
                newStatus == OrderStatus.PICKED_UP && current.pickedUpAtIso == null -> Instant.now().toString()
                else -> current.pickedUpAtIso
            }
            val next = current.copy(
                status = newStatus,
                driverName = driverName ?: current.driverName,
                driverPhone = driverPhone ?: current.driverPhone,
                driverPlate = driverPlate ?: current.driverPlate,
                pickedUpAtIso = pickedUpIso
            )
            _orders.value = _orders.value.map { if (it.id == orderId) next else it }
            next
        }
        onOrderStatusUpdated(updated)
        return Result.success(Unit)
    }

    override suspend fun submitReview(orderId: String, rating: Int, comment: String): Result<Unit> =
        mutex.withLock {
            val order = _orders.value.firstOrNull { it.id == orderId }
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy đơn hàng."))
            val review = Review(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                customerId = order.customerId,
                customerName = order.customerName,
                rating = rating.coerceIn(1, 5),
                comment = comment.trim(),
                createdAtIso = Instant.now().toString()
            )
            _reviews.value = listOf(review) + _reviews.value.filterNot { it.orderId == orderId }
            _orders.value = _orders.value.map {
                if (it.id == orderId) it.copy(review = review) else it
            }
            Result.success(Unit)
        }

    override suspend fun refreshAll() {
        // StateFlows are already live in memory.
    }

    private fun seedSchedules(): List<StoreSchedule> = (1..7).map { dow ->
        StoreSchedule(
            id = "sched-week-$dow",
            type = ScheduleType.WEEKLY,
            dayOfWeek = dow,
            openTime = if (dow == 7) "07:00" else "06:00",
            closeTime = "23:30",
            isClosed = false
        )
    }

    private fun seedCategories(): List<Category> = listOf(
        Category(id = "cat-main", name = "Món chính", sortOrder = 1),
        Category(id = "cat-side", name = "Món thêm", sortOrder = 2),
        Category(id = "cat-drink", name = "Thức uống", sortOrder = 3)
    )

    private fun seedMenuItems(): List<MenuItem> = listOf(
        MenuItem(
            id = "dish-bun-rieu",
            categoryId = "cat-main",
            name = "Bún Riêu Cua Đặc Biệt",
            description = "Riêu cua đồng nguyên chất, đậu hũ chiên giòn, chả lụa, huyết mềm, rau sống tươi.",
            basePrice = 55_000L,
            imageUrl = "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=800",
            isAvailable = true,
            optionGroups = listOf(
                OptionGroup(
                    id = "grp-br-size",
                    menuItemId = "dish-bun-rieu",
                    name = "Kích cỡ tô",
                    selectionType = SelectionType.SINGLE,
                    isRequired = true,
                    options = listOf(
                        MenuOption("opt-br-reg", "grp-br-size", "Tô thường", 0L, true),
                        MenuOption("opt-br-lg", "grp-br-size", "Tô lớn đặc biệt", 10_000L, true),
                        MenuOption("opt-br-kid", "grp-br-size", "Tô trẻ em", -5_000L, true)
                    )
                ),
                OptionGroup(
                    id = "grp-br-top",
                    menuItemId = "dish-bun-rieu",
                    name = "Món ăn kèm",
                    selectionType = SelectionType.MULTI,
                    isRequired = false,
                    options = listOf(
                        MenuOption("opt-br-cha", "grp-br-top", "Thêm chả cá", 15_000L, true),
                        MenuOption("opt-br-gio", "grp-br-top", "Thêm giò heo", 20_000L, true),
                        MenuOption("opt-br-mamtom", "grp-br-top", "Mắm tôm để riêng", 0L, true)
                    )
                )
            )
        ),
        MenuItem(
            id = "dish-bun-cha-ca",
            categoryId = "cat-main",
            name = "Bún Chả Cá Nha Trang",
            description = "Chả cá thu chiên & hấp dai ngọt chuẩn vị Nha Trang, nước dùng thanh trong hầm xương cá.",
            basePrice = 50_000L,
            imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800",
            isAvailable = true,
            optionGroups = listOf(
                OptionGroup(
                    id = "grp-bcc-size",
                    menuItemId = "dish-bun-cha-ca",
                    name = "Kích cỡ tô",
                    selectionType = SelectionType.SINGLE,
                    isRequired = true,
                    options = listOf(
                        MenuOption("opt-bcc-reg", "grp-bcc-size", "Tô thường", 0L, true),
                        MenuOption("opt-bcc-lg", "grp-bcc-size", "Tô lớn nhiều chả", 12_000L, true)
                    )
                ),
                OptionGroup(
                    id = "grp-bcc-top",
                    menuItemId = "dish-bun-cha-ca",
                    name = "Topping thêm",
                    selectionType = SelectionType.MULTI,
                    isRequired = false,
                    options = listOf(
                        MenuOption("opt-bcc-sua", "grp-bcc-top", "Thêm sứa giòn", 15_000L, true),
                        MenuOption("opt-bcc-rau", "grp-bcc-top", "Rau sống thêm", 5_000L, true)
                    )
                )
            )
        ),
        MenuItem(
            id = "dish-bun-gio",
            categoryId = "cat-main",
            name = "Bún Riêu Giò Heo Chả Cá",
            description = "Tô đầy đủ riêu cua đồng béo thơm, khoanh giò heo rút xương mềm rục và chả cá thu.",
            basePrice = 65_000L,
            imageUrl = "https://images.unsplash.com/photo-1555126634-323283e090fa?w=800",
            isAvailable = true,
            optionGroups = listOf(
                OptionGroup(
                    id = "grp-bg-size",
                    menuItemId = "dish-bun-gio",
                    name = "Kích cỡ tô",
                    selectionType = SelectionType.SINGLE,
                    isRequired = true,
                    options = listOf(
                        MenuOption("opt-bg-reg", "grp-bg-size", "Tô tiêu chuẩn", 0L, true),
                        MenuOption("opt-bg-xl", "grp-bg-size", "Tô đại 2 khoanh giò", 18_000L, true)
                    )
                )
            )
        ),
        MenuItem(
            id = "dish-cha-ca-them",
            categoryId = "cat-side",
            name = "Chén Chả Cá Thu Chiên Nóng",
            description = "100g chả cá thu quết tay chiên phồng chấm mắm ớt tỏi.",
            basePrice = 25_000L,
            imageUrl = "https://images.unsplash.com/photo-1541696432-82c6da8ce7bf?w=800",
            isAvailable = true
        ),
        MenuItem(
            id = "dish-tra-tac",
            categoryId = "cat-drink",
            name = "Trà Tắc Xí Muội Nhà Làm",
            description = "Giải nhiệt thanh mát với tắc tươi và xí muội mặn ngọt.",
            basePrice = 18_000L,
            imageUrl = "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=800",
            isAvailable = true
        ),
        MenuItem(
            id = "dish-sua-dau-nanh",
            categoryId = "cat-drink",
            name = "Sữa Đậu Nành Lá Dứa",
            description = "Nấu mới mỗi sáng từ đậu nành quê thơm mùi lá dứa.",
            basePrice = 15_000L,
            imageUrl = "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800",
            isAvailable = true,
            optionGroups = listOf(
                OptionGroup(
                    id = "grp-sdn-temp",
                    menuItemId = "dish-sua-dau-nanh",
                    name = "Cách dùng",
                    selectionType = SelectionType.SINGLE,
                    isRequired = true,
                    options = listOf(
                        MenuOption("opt-sdn-cold", "grp-sdn-temp", "Uống đá", 0L, true),
                        MenuOption("opt-sdn-hot", "grp-sdn-temp", "Uống nóng", 0L, true)
                    )
                )
            )
        )
    )

    private fun seedVouchers(): List<Voucher> = listOf(
        Voucher(
            id = "vch-1",
            code = "GIAM20K",
            discountType = DiscountType.FIXED,
            discountValue = 20_000L,
            minOrderValue = 100_000L,
            usageLimit = 50,
            usedCount = 4,
            endAtIso = Instant.now().plus(60, ChronoUnit.DAYS).toString(),
            isActive = true
        ),
        Voucher(
            id = "vch-2",
            code = "BUNRIEU15",
            discountType = DiscountType.PERCENT,
            discountValue = 15L,
            minOrderValue = 50_000L,
            usageLimit = 100,
            usedCount = 12,
            endAtIso = Instant.now().plus(30, ChronoUnit.DAYS).toString(),
            isActive = true
        ),
        Voucher(
            id = "vch-3",
            code = "SIEUVIP100K",
            discountType = DiscountType.FIXED,
            discountValue = 100_000L,
            minOrderValue = 40_000L,
            usageLimit = 10,
            usedCount = 1,
            endAtIso = Instant.now().plus(30, ChronoUnit.DAYS).toString(),
            isActive = true
        )
    )

    private fun seedReviews(): List<Review> = listOf(
        Review(
            id = "rev-seed-1",
            orderId = "ord-completed-seed",
            customerId = "cust-demo-001",
            customerName = "Nguyễn Minh Anh",
            rating = 5,
            comment = "Nước lèo đậm đà, mắm tôm để riêng sạch sẽ, giao nhanh còn nóng hổi!",
            createdAtIso = Instant.now().minus(2, ChronoUnit.DAYS).toString()
        )
    )

    private fun seedOrders(): List<Order> {
        val sampleReview = seedReviews().first()
        return listOf(
            Order(
                id = "ord-completed-seed",
                customerId = "cust-demo-001",
                customerName = "Nguyễn Minh Anh",
                customerPhone = "0908123456",
                voucherCode = "BUNRIEU15",
                status = OrderStatus.COMPLETED,
                deliveryAddress = "135 Nam Kỳ Khởi Nghĩa, Quận 1, TP.HCM",
                deliveryLat = 10.776889,
                deliveryLng = 106.695278,
                distanceKm = 2.6,
                subtotalAmount = 88_000L,
                discountAmount = 13_200L,
                shippingFee = 20_000L,
                totalAmount = 94_800L,
                paymentMethod = PaymentMethod.WALLET,
                paymentStatus = PaymentStatus.PAID,
                driverName = "Trần Quốc Bảo",
                driverPhone = "0903555777",
                driverPlate = "59P2-456.78",
                pickedUpAtIso = Instant.now().minus(2, ChronoUnit.DAYS).minus(20, ChronoUnit.MINUTES).toString(),
                createdAtIso = Instant.now().minus(2, ChronoUnit.DAYS).minus(35, ChronoUnit.MINUTES).toString(),
                items = listOf(
                    OrderItem(
                        id = "oi-seed-1",
                        orderId = "ord-completed-seed",
                        menuItemId = "dish-bun-rieu",
                        itemNameSnapshot = "Bún Riêu Cua Đặc Biệt",
                        unitPriceSnapshot = 70_000L,
                        selectedOptionsSnapshot = listOf(
                            SelectedOptionSnapshot("opt-br-reg", "Kích cỡ tô", "Tô thường", 0L),
                            SelectedOptionSnapshot("opt-br-cha", "Món ăn kèm", "Thêm chả cá", 15_000L),
                            SelectedOptionSnapshot("opt-br-mamtom", "Món ăn kèm", "Mắm tôm để riêng", 0L)
                        ),
                        quantity = 1,
                        specialNote = "Ít cay, không hành lá"
                    ),
                    OrderItem(
                        id = "oi-seed-2",
                        orderId = "ord-completed-seed",
                        menuItemId = "dish-tra-tac",
                        itemNameSnapshot = "Trà Tắc Xí Muội Nhà Làm",
                        unitPriceSnapshot = 18_000L,
                        selectedOptionsSnapshot = emptyList(),
                        quantity = 1,
                        specialNote = "Ít đá"
                    )
                ),
                review = sampleReview
            )
        )
    }
}
