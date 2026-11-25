package com.fnb.d2c.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fnb.d2c.data.repository.FnbRepository
import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.DeliveryLocation
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.MenuItem
import com.fnb.d2c.domain.model.Order
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.PaymentMethod
import com.fnb.d2c.domain.model.ResolvedStoreStatus
import com.fnb.d2c.domain.model.SelectedOptionSnapshot
import com.fnb.d2c.domain.model.SelectionType
import com.fnb.d2c.domain.model.ShippingQuote
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.domain.service.ShippingQuoteService
import com.fnb.d2c.domain.util.CartKeyBuilder
import com.fnb.d2c.domain.util.OrderPriceCalculator
import com.fnb.d2c.domain.util.PriceBreakdown
import com.fnb.d2c.domain.util.ReorderHelper
import com.fnb.d2c.domain.util.StoreStatusResolver
import com.fnb.d2c.notification.OrderNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

class MainAppViewModel(
    val repository: FnbRepository,
    private val shippingQuoteService: ShippingQuoteService,
    private val notificationManager: OrderNotificationManager
) : ViewModel() {

    val currentUser = repository.currentUser
    val storeSettings = repository.storeSettings
    val storeSchedules = repository.storeSchedules
    val categories = repository.categories
    val menuItems = repository.menuItems
    val vouchers = repository.vouchers
    val orders = repository.orders
    val reviews = repository.reviews
    val isBellRinging = notificationManager.isBellRinging

    private val _clockTick = MutableStateFlow(Instant.now())
    val clockTick: StateFlow<Instant> = _clockTick.asStateFlow()

    // Demo toggle: allows testing the 45-min long-delivery risk alert after 15 seconds in class presentation
    private val _demoFastOverdueMode = MutableStateFlow(false)
    val demoFastOverdueMode: StateFlow<Boolean> = _demoFastOverdueMode.asStateFlow()

    val resolvedStoreStatus: StateFlow<ResolvedStoreStatus> = combine(
        storeSettings,
        storeSchedules,
        _clockTick
    ) { settings, schedules, tick ->
        StoreStatusResolver.resolve(
            settings = settings,
            schedules = schedules,
            now = ZonedDateTime.ofInstant(tick, StoreStatusResolver.vietnamZone)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StoreStatusResolver.resolve(storeSettings.value, storeSchedules.value)
    )

    // Customer Cart & Checkout State
    private val _cartItems = MutableStateFlow<List<CartLineItem>>(emptyList())
    val cartItems: StateFlow<List<CartLineItem>> = _cartItems.asStateFlow()

    private val _deliveryLocation = MutableStateFlow(
        DeliveryLocation(
            address = "135 Nam Kỳ Khởi Nghĩa, Quận 1, TP.HCM",
            lat = 10.776889,
            lng = 106.695278
        )
    )
    val deliveryLocation: StateFlow<DeliveryLocation> = _deliveryLocation.asStateFlow()

    private val _shippingQuote = MutableStateFlow(
        ShippingQuote(distanceKm = 2.6, shippingFee = 20_000L, sourceLabel = "Mặc định")
    )
    val shippingQuote: StateFlow<ShippingQuote> = _shippingQuote.asStateFlow()

    private val _isQuotingShipping = MutableStateFlow(false)
    val isQuotingShipping: StateFlow<Boolean> = _isQuotingShipping.asStateFlow()

    private val _addressSuggestions = MutableStateFlow<List<DeliveryLocation>>(emptyList())
    val addressSuggestions: StateFlow<List<DeliveryLocation>> = _addressSuggestions.asStateFlow()

    private val _selectedVoucherCode = MutableStateFlow<String?>(null)
    val selectedVoucherCode: StateFlow<String?> = _selectedVoucherCode.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow(PaymentMethod.WALLET)
    val selectedPaymentMethod: StateFlow<PaymentMethod> = _selectedPaymentMethod.asStateFlow()

    val priceBreakdown: StateFlow<PriceBreakdown> = combine(
        _cartItems,
        _shippingQuote,
        _selectedVoucherCode,
        vouchers
    ) { items, quote, code, allVouchers ->
        val matched = code?.trim()?.takeIf { it.isNotBlank() }?.let { c ->
            allVouchers.firstOrNull { it.code.equals(c, ignoreCase = true) }
        }
        val result = OrderPriceCalculator.calculate(
            items = items,
            shippingFee = quote.shippingFee,
            voucher = matched
        )
        if (!code.isNullOrBlank() && matched == null) {
            result.copy(voucherError = "Không tìm thấy mã giảm giá ${code.uppercase()}.")
        } else {
            result
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrderPriceCalculator.calculate(emptyList(), 20_000L, null)
    )

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        // Refresh time-sensitive store busy timer & long-delivery alert every 5 seconds
        viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                _clockTick.value = Instant.now()
            }
        }
        refreshShippingQuote()
    }

    fun emitMessage(msg: String) {
        _uiMessage.tryEmit(msg)
    }

    fun setDemoFastOverdueMode(enabled: Boolean) {
        _demoFastOverdueMode.value = enabled
    }

    // --- Auth ---
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.signIn(email, password)
                .onSuccess { emitMessage("Xin chào ${it.fullName}!") }
                .onFailure { emitMessage(it.message ?: "Đăng nhập thất bại.") }
            _isBusy.value = false
        }
    }

    fun signUp(email: String, password: String, fullName: String, phone: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.signUp(email, password, fullName, phone)
                .onSuccess { emitMessage("Đăng ký thành công tài khoản Khách hàng ${it.fullName}!") }
                .onFailure { emitMessage(it.message ?: "Đăng ký thất bại.") }
            _isBusy.value = false
        }
    }

    fun quickDemoLogin(role: UserRole) {
        viewModelScope.launch {
            repository.quickSwitchDemoRole(role)
                .onSuccess { emitMessage("Đã chuyển sang vai trò ${if (role == UserRole.CUSTOMER) "Khách hàng" else "Chủ quán / Bếp"}") }
                .onFailure { emitMessage(it.message ?: "Không thể chuyển nhanh vai trò.") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            notificationManager.stopMerchantBell()
            repository.signOut()
        }
    }

    fun topUpWallet(amount: Long = 200_000L) {
        viewModelScope.launch {
            repository.topUpDemoWallet(amount)
                .onSuccess { emitMessage("Đã nạp thêm +200.000đ vào Ví thử nghiệm!") }
                .onFailure { emitMessage(it.message ?: "Lỗi nạp ví.") }
        }
    }

    // --- Cart & Smart Line Splitting ---
    fun addCustomizedDishToCart(
        dish: MenuItem,
        selectedOptions: List<SelectedOptionSnapshot>,
        specialNote: String,
        quantity: Int
    ) {
        val key = CartKeyBuilder.buildCartKey(
            menuItemId = dish.id,
            optionIds = selectedOptions.map { it.optionId },
            note = specialNote
        )
        val incoming = CartLineItem(
            cartKey = key,
            menuItemId = dish.id,
            menuItemName = dish.name,
            imageUrl = dish.imageUrl,
            basePrice = dish.basePrice,
            selectedOptions = selectedOptions,
            specialNote = specialNote.trim(),
            quantity = quantity.coerceAtLeast(1)
        )
        _cartItems.value = CartKeyBuilder.mergeItem(_cartItems.value, incoming)
        emitMessage("Đã thêm ${incoming.quantity}x ${dish.name} vào giỏ hàng.")
    }

    fun updateCartLineQuantity(cartKey: String, delta: Int) {
        _cartItems.value = _cartItems.value.mapNotNull { line ->
            if (line.cartKey != cartKey) line
            else {
                val nextQty = line.quantity + delta
                if (nextQty <= 0) null else line.copy(quantity = nextQty)
            }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _selectedVoucherCode.value = null
    }

    // --- Delivery Location & Shipping Quote ---
    fun searchAddress(query: String) {
        viewModelScope.launch {
            _addressSuggestions.value = shippingQuoteService.searchAddressSuggestions(query)
        }
    }

    fun clearAddressSuggestions() {
        _addressSuggestions.value = emptyList()
    }

    fun selectDeliveryLocation(location: DeliveryLocation) {
        _deliveryLocation.value = location
        _addressSuggestions.value = emptyList()
        refreshShippingQuote()
    }

    fun refreshShippingQuote() {
        viewModelScope.launch {
            _isQuotingShipping.value = true
            val store = storeSettings.value
            val dest = _deliveryLocation.value
            _shippingQuote.value = shippingQuoteService.getQuote(
                storeLat = store.lat,
                storeLng = store.lng,
                customerLat = dest.lat,
                customerLng = dest.lng
            )
            _isQuotingShipping.value = false
        }
    }

    fun applyVoucherCode(code: String?) {
        _selectedVoucherCode.value = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _selectedPaymentMethod.value = method
    }

    fun placeOrder(onOrderCreated: (Order) -> Unit) {
        val status = resolvedStoreStatus.value
        if (!status.canAcceptOrder) {
            emitMessage("Không thể đặt đơn: ${status.badgeText} (${status.detailText})")
            return
        }
        val items = _cartItems.value
        if (items.isEmpty()) {
            emitMessage("Giỏ hàng đang trống.")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val dest = _deliveryLocation.value
            val quote = _shippingQuote.value
            val validVoucher = priceBreakdown.value.appliedVoucher?.code
            repository.placeOrder(
                deliveryAddress = dest.address,
                deliveryLat = dest.lat,
                deliveryLng = dest.lng,
                distanceKm = quote.distanceKm,
                shippingFee = quote.shippingFee,
                voucherCode = validVoucher,
                paymentMethod = _selectedPaymentMethod.value,
                cartItems = items
            ).onSuccess { order ->
                _cartItems.value = emptyList()
                _selectedVoucherCode.value = null
                emitMessage("Đặt món thành công! Mã đơn #${order.id.take(6).uppercase()}")
                onOrderCreated(order)
            }.onFailure { err ->
                emitMessage(err.message ?: "Đặt đơn thất bại.")
            }
            _isBusy.value = false
        }
    }

    // --- Reorder ("Đặt lại đơn này") ---
    fun reorderPastOrder(order: Order, onNavigateToCart: () -> Unit) {
        val result = ReorderHelper.buildReorderItems(order.items, menuItems.value)
        if (result.addedItems.isEmpty()) {
            emitMessage("Rất tiếc, tất cả các món trong đơn cũ hiện đã hết hàng.")
            return
        }
        var merged = _cartItems.value
        result.addedItems.forEach { line ->
            merged = CartKeyBuilder.mergeItem(merged, line)
        }
        _cartItems.value = merged

        if (result.skippedItemNames.isNotEmpty()) {
            emitMessage(
                "Đã nạp lại đơn cũ (bỏ qua món đã hết hàng: ${result.skippedItemNames.joinToString(", ")})."
            )
        } else {
            emitMessage("Đã nạp lại toàn bộ ${result.addedItems.sumOf { it.quantity }} phần ăn vào giỏ hàng!")
        }
        onNavigateToCart()
    }

    // --- Review ---
    fun submitOrderReview(orderId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            repository.submitReview(orderId, rating, comment)
                .onSuccess { emitMessage("Cảm ơn bạn đã đánh giá $rating sao!") }
                .onFailure { emitMessage(it.message ?: "Không thể gửi đánh giá.") }
        }
    }

    // --- Merchant Kitchen & Store Controls ---
    fun silenceBell() {
        notificationManager.stopMerchantBell()
    }

    fun confirmOrder(orderId: String) {
        notificationManager.stopMerchantBell()
        viewModelScope.launch {
            repository.confirmOrderPreparing(orderId)
                .onSuccess { emitMessage("Đã nhận đơn — chuyển sang Đang chuẩn bị món.") }
                .onFailure { emitMessage(it.message ?: "Lỗi xác nhận đơn.") }
        }
    }

    fun rejectOrder(orderId: String, reason: String) {
        notificationManager.stopMerchantBell()
        viewModelScope.launch {
            repository.rejectOrderAndRefund(orderId, reason)
                .onSuccess { emitMessage("Đã từ chối đơn & tự động xử lý hoàn tiền (nếu trả trước).") }
                .onFailure { emitMessage(it.message ?: "Lỗi từ chối đơn.") }
        }
    }

    fun dispatchShipper(orderId: String) {
        viewModelScope.launch {
            repository.requestShipperDispatch(orderId)
                .onSuccess { emitMessage("Đã gửi yêu cầu Gọi Shipper! Mở Mock Shipper để giả lập tài xế nhận đơn.") }
                .onFailure { emitMessage(it.message ?: "Lỗi gọi Shipper.") }
        }
    }

    fun advanceMockShipperMilestone(
        orderId: String,
        status: OrderStatus,
        driverName: String? = null,
        driverPhone: String? = null,
        driverPlate: String? = null
    ) {
        viewModelScope.launch {
            repository.updateDeliveryMilestone(
                orderId = orderId,
                newStatus = status,
                driverName = driverName,
                driverPhone = driverPhone,
                driverPlate = driverPlate
            ).onSuccess {
                emitMessage("Shipper cập nhật: ${status.labelVi}")
            }.onFailure {
                emitMessage(it.message ?: "Lỗi cập nhật tiến độ giao hàng.")
            }
        }
    }

    fun setStoreQuickStatus(manualStatus: StoreManualStatus, busyMinutes: Long?) {
        viewModelScope.launch {
            repository.setStoreQuickStatus(manualStatus, busyMinutes)
                .onSuccess {
                    _clockTick.value = Instant.now()
                    emitMessage("Đã cập nhật trạng thái hoạt động của quán.")
                }
                .onFailure { emitMessage(it.message ?: "Lỗi cập nhật trạng thái quán.") }
        }
    }

    fun saveWeeklySchedule(dayOfWeek: Int, openTime: String, closeTime: String, isClosed: Boolean) {
        viewModelScope.launch {
            repository.updateWeeklySchedule(dayOfWeek, openTime, closeTime, isClosed)
                .onSuccess { emitMessage("Đã lưu lịch hoạt động thường ngày.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu lịch.") }
        }
    }

    fun saveSpecialSchedule(date: String, openTime: String, closeTime: String, isClosed: Boolean) {
        viewModelScope.launch {
            repository.upsertSpecialSchedule(date, openTime, closeTime, isClosed)
                .onSuccess { emitMessage("Đã lưu lịch mở/đóng đặc biệt cho ngày $date.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu lịch đặc biệt.") }
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    // --- Merchant Catalog & Vouchers ---
    fun saveCategory(id: String?, name: String, sortOrder: Int) {
        viewModelScope.launch {
            repository.saveCategory(id, name, sortOrder)
                .onSuccess { emitMessage("Đã lưu danh mục $name.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu danh mục.") }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch { repository.deleteCategory(categoryId) }
    }

    fun saveDish(
        id: String?,
        categoryId: String,
        name: String,
        description: String,
        basePrice: Long,
        imageUrl: String,
        isAvailable: Boolean
    ) {
        viewModelScope.launch {
            repository.saveMenuItem(id, categoryId, name, description, basePrice, imageUrl, isAvailable)
                .onSuccess { emitMessage("Đã lưu món $name.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu món ăn.") }
        }
    }

    fun toggleDishStock(dishId: String, available: Boolean) {
        viewModelScope.launch {
            repository.toggleMenuItemAvailability(dishId, available)
        }
    }

    fun deleteDish(dishId: String) {
        viewModelScope.launch { repository.deleteMenuItem(dishId) }
    }

    fun saveOptionGroup(
        id: String?,
        menuItemId: String,
        name: String,
        selectionType: SelectionType,
        isRequired: Boolean
    ) {
        viewModelScope.launch {
            repository.saveOptionGroup(id, menuItemId, name, selectionType, isRequired)
                .onSuccess { emitMessage("Đã lưu nhóm tùy chọn $name.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu nhóm tùy chọn.") }
        }
    }

    fun deleteOptionGroup(groupId: String) {
        viewModelScope.launch { repository.deleteOptionGroup(groupId) }
    }

    fun saveOptionItem(
        id: String?,
        groupId: String,
        name: String,
        extraPrice: Long,
        isAvailable: Boolean
    ) {
        viewModelScope.launch {
            repository.saveMenuOption(id, groupId, name, extraPrice, isAvailable)
                .onSuccess { emitMessage("Đã lưu tùy chọn $name.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu tùy chọn.") }
        }
    }

    fun deleteOptionItem(optionId: String) {
        viewModelScope.launch { repository.deleteMenuOption(optionId) }
    }

    fun saveVoucher(
        id: String?,
        code: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderValue: Long,
        usageLimit: Int,
        endAtIso: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            repository.saveVoucher(id, code, discountType, discountValue, minOrderValue, usageLimit, endAtIso, isActive)
                .onSuccess { emitMessage("Đã lưu mã khuyến mãi ${code.uppercase()}.") }
                .onFailure { emitMessage(it.message ?: "Lỗi lưu voucher.") }
        }
    }

    fun toggleVoucher(voucherId: String, isActive: Boolean) {
        viewModelScope.launch { repository.toggleVoucherActive(voucherId, isActive) }
    }

    fun deleteVoucher(voucherId: String) {
        viewModelScope.launch { repository.deleteVoucher(voucherId) }
    }

    class Factory(
        private val repository: FnbRepository,
        private val shippingQuoteService: ShippingQuoteService,
        private val notificationManager: OrderNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainAppViewModel(repository, shippingQuoteService, notificationManager) as T
        }
    }
}
