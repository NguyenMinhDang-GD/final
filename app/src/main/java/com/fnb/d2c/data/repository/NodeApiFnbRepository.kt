package com.fnb.d2c.data.repository

import android.content.Context
import com.fnb.d2c.domain.model.CartLineItem
import com.fnb.d2c.domain.model.Category
import com.fnb.d2c.domain.model.DiscountType
import com.fnb.d2c.domain.model.MenuItem
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Connects Android App directly to our Node.js + Express + WebSocket Server (`backend/`).
 * - HTTP REST API with Bearer JWT authentication
 * - Live WebSocket (`/ws?token=<jwt>`) pushing ORDER_CREATED / ORDER_UPDATED / STORE_UPDATED
 * - Automatic graceful fallback to offline DemoFnbRepository if the Node.js server isn't started yet
 */
class NodeApiFnbRepository(
    context: Context,
    baseUrl: String,
    private val onNewOrderCreated: (Order) -> Unit = {},
    private val onOrderStatusUpdated: (Order) -> Unit = {}
) : FnbRepository {

    private val cleanBaseUrl = baseUrl.trim().trimEnd('/')
    private val wsBaseUrl = cleanBaseUrl
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")

    private val prefs = context.getSharedPreferences("coba_node_auth", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // Fallback seed data if the user opens the app before running `npm start`
    private val offlineFallback = DemoFnbRepository(
        onNewOrderCreated = onNewOrderCreated,
        onOrderStatusUpdated = onOrderStatusUpdated
    )

    @Volatile
    private var isServerOnline = false

    @Volatile
    private var jwtToken: String? = prefs.getString("jwt_token", null)

    private var activeWebSocket: WebSocket? = null

    override val backendModeLabel: String
        get() = if (isServerOnline) {
            "Node.js Express + WebSocket ($cleanBaseUrl)"
        } else {
            "Node.js Server ($cleanBaseUrl • Tự động dự phòng Offline)"
        }

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _storeSettings = MutableStateFlow(offlineFallback.storeSettings.value)
    override val storeSettings: StateFlow<StoreSettings> = _storeSettings.asStateFlow()

    private val _storeSchedules = MutableStateFlow(offlineFallback.storeSchedules.value)
    override val storeSchedules: StateFlow<List<StoreSchedule>> = _storeSchedules.asStateFlow()

    private val _categories = MutableStateFlow(offlineFallback.categories.value)
    override val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _menuItems = MutableStateFlow(offlineFallback.menuItems.value)
    override val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _vouchers = MutableStateFlow(offlineFallback.vouchers.value)
    override val vouchers: StateFlow<List<Voucher>> = _vouchers.asStateFlow()

    private val _orders = MutableStateFlow(offlineFallback.orders.value)
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _reviews = MutableStateFlow(offlineFallback.reviews.value)
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    @Serializable
    private data class BootstrapResponse(
        val storeSettings: StoreSettings,
        val storeSchedules: List<StoreSchedule>,
        val categories: List<Category>,
        val menuItems: List<MenuItem>,
        val vouchers: List<Voucher>
    )

    @Serializable
    private data class AuthResponse(
        val token: String,
        val user: UserProfile
    )

    @Serializable
    private data class CatalogResponse(
        val id: String? = null,
        val categories: List<Category>,
        val menuItems: List<MenuItem>,
        val vouchers: List<Voucher>
    )

    init {
        scope.launch {
            refreshAll()
            if (!jwtToken.isNullOrBlank()) {
                runCatching { restoreSession() }
            }
            connectWebSocket()
        }
    }

    private suspend fun restoreSession() {
        val raw = requestJson("GET", "/api/auth/me")
        val userEl = raw.jsonObject["user"] ?: return
        _currentUser.value = json.decodeFromJsonElement<UserProfile>(userEl)
        fetchOrders()
    }

    private fun saveToken(token: String?) {
        jwtToken = token
        prefs.edit().putString("jwt_token", token).apply()
    }

    private fun connectWebSocket() {
        activeWebSocket?.cancel()
        val tokenQuery = jwtToken?.let { "?token=$it" } ?: ""
        val req = Request.Builder()
            .url("$wsBaseUrl/ws$tokenQuery")
            .build()

        activeWebSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isServerOnline = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleWsMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    delay(4_000L)
                    connectWebSocket()
                }
            }
        })
    }

    private suspend fun handleWsMessage(rawText: String) {
        val obj = runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull() ?: return
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        when (type) {
            "ORDER_CREATED" -> {
                val orderEl = obj["order"]
                fetchOrders()
                refreshCurrentUserBalance()
                if (orderEl != null) {
                    val ord = json.decodeFromJsonElement<Order>(orderEl)
                    onNewOrderCreated(ord)
                }
            }

            "ORDER_UPDATED" -> {
                val orderEl = obj["order"]
                fetchOrders()
                refreshCurrentUserBalance()
                if (orderEl != null) {
                    val ord = json.decodeFromJsonElement<Order>(orderEl)
                    onOrderStatusUpdated(ord)
                }
            }

            "STORE_UPDATED" -> runCatching { fetchStore() }
            "CATALOG_UPDATED" -> runCatching { fetchCatalog() }
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        bodyElement: JsonElement? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url("$cleanBaseUrl$path")
        jwtToken?.takeIf { it.isNotBlank() }?.let { token ->
            reqBuilder.header("Authorization", "Bearer $token")
        }

        val bodyStr = bodyElement?.let { json.encodeToString(JsonElement.serializer(), it) }
        val reqBody = bodyStr?.toRequestBody(jsonMediaType) ?: "".toRequestBody(jsonMediaType)

        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "DELETE" -> reqBuilder.delete()
            "POST" -> reqBuilder.post(reqBody)
            "PUT" -> reqBuilder.put(reqBody)
            "PATCH" -> reqBuilder.patch(reqBody)
            else -> reqBuilder.method(method.uppercase(), reqBody)
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            isServerOnline = true
            val respText = resp.body?.string().orEmpty()
            val parsed = if (respText.isNotBlank()) {
                json.parseToJsonElement(respText)
            } else {
                buildJsonObject {}
            }
            if (!resp.isSuccessful) {
                val errMsg = parsed.jsonObject["error"]?.jsonPrimitive?.content
                    ?: "Lỗi máy chủ Node.js (HTTP ${resp.code})"
                throw IllegalStateException(errMsg)
            }
            parsed
        }
    }

    private fun syncFromOfflineFallback() {
        _currentUser.value = offlineFallback.currentUser.value
        _storeSettings.value = offlineFallback.storeSettings.value
        _storeSchedules.value = offlineFallback.storeSchedules.value
        _categories.value = offlineFallback.categories.value
        _menuItems.value = offlineFallback.menuItems.value
        _vouchers.value = offlineFallback.vouchers.value
        _orders.value = offlineFallback.orders.value
        _reviews.value = offlineFallback.reviews.value
    }

    override suspend fun signIn(email: String, password: String): Result<UserProfile> {
        return try {
            val resEl = requestJson(
                "POST",
                "/api/auth/login",
                buildJsonObject {
                    put("email", email.trim())
                    put("password", password)
                }
            )
            val authRes = json.decodeFromJsonElement<AuthResponse>(resEl)
            saveToken(authRes.token)
            _currentUser.value = authRes.user
            connectWebSocket()
            refreshAll()
            Result.success(authRes.user)
        } catch (io: IOException) {
            isServerOnline = false
            offlineFallback.signIn(email, password).also { syncFromOfflineFallback() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String
    ): Result<UserProfile> {
        return try {
            val resEl = requestJson(
                "POST",
                "/api/auth/register",
                buildJsonObject {
                    put("email", email.trim())
                    put("password", password)
                    put("full_name", fullName.trim())
                    put("phone", phone.trim())
                }
            )
            val authRes = json.decodeFromJsonElement<AuthResponse>(resEl)
            saveToken(authRes.token)
            _currentUser.value = authRes.user
            connectWebSocket()
            refreshAll()
            Result.success(authRes.user)
        } catch (io: IOException) {
            isServerOnline = false
            offlineFallback.signUp(email, password, fullName, phone).also { syncFromOfflineFallback() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun quickSwitchDemoRole(role: UserRole): Result<UserProfile> {
        return try {
            val resEl = requestJson(
                "POST",
                "/api/auth/demo-login",
                buildJsonObject { put("role", role.name) }
            )
            val authRes = json.decodeFromJsonElement<AuthResponse>(resEl)
            saveToken(authRes.token)
            _currentUser.value = authRes.user
            connectWebSocket()
            refreshAll()
            Result.success(authRes.user)
        } catch (io: IOException) {
            isServerOnline = false
            offlineFallback.quickSwitchDemoRole(role).also { syncFromOfflineFallback() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        saveToken(null)
        _currentUser.value = null
        offlineFallback.signOut()
        connectWebSocket()
    }

    override suspend fun topUpDemoWallet(amount: Long): Result<Unit> {
        return try {
            val resEl = requestJson(
                "POST",
                "/api/auth/wallet/topup",
                buildJsonObject { put("amount", amount) }
            )
            resEl.jsonObject["user"]?.let {
                _currentUser.value = json.decodeFromJsonElement<UserProfile>(it)
            }
            Result.success(Unit)
        } catch (io: IOException) {
            isServerOnline = false
            offlineFallback.topUpDemoWallet(amount).also { syncFromOfflineFallback() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveCategory(id: String?, name: String, sortOrder: Int): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                val resEl = requestJson(
                    "POST",
                    "/api/catalog/categories",
                    buildJsonObject {
                        if (id != null) put("id", id)
                        put("name", name.trim())
                        put("sort_order", sortOrder)
                    }
                )
                applyCatalogResponse(resEl)
            },
            offlineBlock = { offlineFallback.saveCategory(id, name, sortOrder).getOrThrow() }
        )

    override suspend fun deleteCategory(categoryId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                applyCatalogResponse(requestJson("DELETE", "/api/catalog/categories/$categoryId"))
            },
            offlineBlock = { offlineFallback.deleteCategory(categoryId).getOrThrow() }
        )

    override suspend fun saveMenuItem(
        id: String?,
        categoryId: String,
        name: String,
        description: String,
        basePrice: Long,
        imageUrl: String,
        isAvailable: Boolean
    ): Result<String> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/catalog/menu-items",
                buildJsonObject {
                    if (id != null) put("id", id)
                    put("category_id", categoryId)
                    put("name", name.trim())
                    put("description", description.trim())
                    put("base_price", basePrice)
                    put("image_url", imageUrl.trim())
                    put("is_available", isAvailable)
                }
            )
            val parsed = applyCatalogResponse(resEl)
            parsed.id ?: id.orEmpty()
        },
        offlineBlock = {
            offlineFallback.saveMenuItem(id, categoryId, name, description, basePrice, imageUrl, isAvailable)
                .getOrThrow()
        }
    )

    override suspend fun toggleMenuItemAvailability(menuItemId: String, isAvailable: Boolean): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                val resEl = requestJson(
                    "PATCH",
                    "/api/catalog/menu-items/$menuItemId/availability",
                    buildJsonObject { put("is_available", isAvailable) }
                )
                applyCatalogResponse(resEl)
            },
            offlineBlock = { offlineFallback.toggleMenuItemAvailability(menuItemId, isAvailable).getOrThrow() }
        )

    override suspend fun deleteMenuItem(menuItemId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                applyCatalogResponse(requestJson("DELETE", "/api/catalog/menu-items/$menuItemId"))
            },
            offlineBlock = { offlineFallback.deleteMenuItem(menuItemId).getOrThrow() }
        )

    override suspend fun saveOptionGroup(
        id: String?,
        menuItemId: String,
        name: String,
        selectionType: SelectionType,
        isRequired: Boolean
    ): Result<String> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/catalog/option-groups",
                buildJsonObject {
                    if (id != null) put("id", id)
                    put("menu_item_id", menuItemId)
                    put("name", name.trim())
                    put("selection_type", selectionType.name)
                    put("is_required", isRequired)
                }
            )
            applyCatalogResponse(resEl).id ?: id.orEmpty()
        },
        offlineBlock = {
            offlineFallback.saveOptionGroup(id, menuItemId, name, selectionType, isRequired).getOrThrow()
        }
    )

    override suspend fun deleteOptionGroup(groupId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                applyCatalogResponse(requestJson("DELETE", "/api/catalog/option-groups/$groupId"))
            },
            offlineBlock = { offlineFallback.deleteOptionGroup(groupId).getOrThrow() }
        )

    override suspend fun saveMenuOption(
        id: String?,
        groupId: String,
        name: String,
        extraPrice: Long,
        isAvailable: Boolean
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/catalog/options",
                buildJsonObject {
                    if (id != null) put("id", id)
                    put("group_id", groupId)
                    put("name", name.trim())
                    put("extra_price", extraPrice)
                    put("is_available", isAvailable)
                }
            )
            applyCatalogResponse(resEl)
        },
        offlineBlock = {
            offlineFallback.saveMenuOption(id, groupId, name, extraPrice, isAvailable).getOrThrow()
        }
    )

    override suspend fun deleteMenuOption(optionId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                applyCatalogResponse(requestJson("DELETE", "/api/catalog/options/$optionId"))
            },
            offlineBlock = { offlineFallback.deleteMenuOption(optionId).getOrThrow() }
        )

    override suspend fun saveVoucher(
        id: String?,
        code: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderValue: Long,
        usageLimit: Int,
        endAtIso: String,
        isActive: Boolean
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/catalog/vouchers",
                buildJsonObject {
                    if (id != null) put("id", id)
                    put("code", code.trim().uppercase())
                    put("discount_type", discountType.name)
                    put("discount_value", discountValue)
                    put("min_order_value", minOrderValue)
                    put("usage_limit", usageLimit)
                    put("end_at", endAtIso)
                    put("is_active", isActive)
                }
            )
            applyCatalogResponse(resEl)
        },
        offlineBlock = {
            offlineFallback.saveVoucher(
                id, code, discountType, discountValue, minOrderValue, usageLimit, endAtIso, isActive
            ).getOrThrow()
        }
    )

    override suspend fun toggleVoucherActive(voucherId: String, isActive: Boolean): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                val resEl = requestJson(
                    "PATCH",
                    "/api/catalog/vouchers/$voucherId/active",
                    buildJsonObject { put("is_active", isActive) }
                )
                applyCatalogResponse(resEl)
            },
            offlineBlock = { offlineFallback.toggleVoucherActive(voucherId, isActive).getOrThrow() }
        )

    override suspend fun deleteVoucher(voucherId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                applyCatalogResponse(requestJson("DELETE", "/api/catalog/vouchers/$voucherId"))
            },
            offlineBlock = { offlineFallback.deleteVoucher(voucherId).getOrThrow() }
        )

    override suspend fun placeOrder(
        deliveryAddress: String,
        deliveryLat: Double,
        deliveryLng: Double,
        distanceKm: Double,
        shippingFee: Long,
        voucherCode: String?,
        paymentMethod: PaymentMethod,
        cartItems: List<CartLineItem>
    ): Result<Order> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/orders",
                buildJsonObject {
                    put("delivery_address", deliveryAddress)
                    put("delivery_lat", deliveryLat)
                    put("delivery_lng", deliveryLng)
                    put("distance_km", distanceKm)
                    put("shipping_fee", shippingFee)
                    put("voucher_code", voucherCode?.trim()?.uppercase() ?: "")
                    put("payment_method", paymentMethod.name)
                    put(
                        "items",
                        buildJsonArray {
                            cartItems.forEach { line ->
                                add(
                                    buildJsonObject {
                                        put("menu_item_id", line.menuItemId)
                                        put("item_name_snapshot", line.menuItemName)
                                        put("unit_price_snapshot", line.unitPrice)
                                        put(
                                            "selected_options_snapshot",
                                            json.encodeToJsonElement(line.selectedOptions)
                                        )
                                        put("quantity", line.quantity)
                                        put("special_note", line.specialNote.trim())
                                    }
                                )
                            }
                        }
                    )
                }
            )
            resEl.jsonObject["user"]?.let {
                _currentUser.value = json.decodeFromJsonElement<UserProfile>(it)
            }
            val created = json.decodeFromJsonElement<Order>(resEl.jsonObject.getValue("order"))
            fetchOrders()
            fetchCatalog()
            created
        },
        offlineBlock = {
            offlineFallback.placeOrder(
                deliveryAddress, deliveryLat, deliveryLng, distanceKm, shippingFee, voucherCode, paymentMethod, cartItems
            ).getOrThrow()
        }
    )

    override suspend fun confirmOrderPreparing(orderId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                requestJson("POST", "/api/orders/$orderId/confirm")
                fetchOrders()
            },
            offlineBlock = { offlineFallback.confirmOrderPreparing(orderId).getOrThrow() }
        )

    override suspend fun rejectOrderAndRefund(orderId: String, reason: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                requestJson(
                    "POST",
                    "/api/orders/$orderId/reject",
                    buildJsonObject { put("reason", reason) }
                )
                fetchOrders()
                fetchCatalog()
                refreshCurrentUserBalance()
            },
            offlineBlock = { offlineFallback.rejectOrderAndRefund(orderId, reason).getOrThrow() }
        )

    override suspend fun setStoreQuickStatus(
        manualStatus: StoreManualStatus,
        busyDurationMinutes: Long?
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "PATCH",
                "/api/store/status",
                buildJsonObject {
                    put("manual_status", manualStatus.name)
                    if (busyDurationMinutes != null) put("busy_duration_minutes", busyDurationMinutes)
                }
            )
            resEl.jsonObject["storeSettings"]?.let {
                _storeSettings.value = json.decodeFromJsonElement<StoreSettings>(it)
            }
            Unit
        },
        offlineBlock = { offlineFallback.setStoreQuickStatus(manualStatus, busyDurationMinutes).getOrThrow() }
    )

    override suspend fun updateWeeklySchedule(
        dayOfWeek: Int,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "PUT",
                "/api/store/schedules/weekly",
                buildJsonObject {
                    put("day_of_week", dayOfWeek)
                    put("open_time", openTime)
                    put("close_time", closeTime)
                    put("is_closed", isClosed)
                }
            )
            resEl.jsonObject["storeSchedules"]?.let {
                _storeSchedules.value = json.decodeFromJsonElement<List<StoreSchedule>>(it)
            }
            Unit
        },
        offlineBlock = {
            offlineFallback.updateWeeklySchedule(dayOfWeek, openTime, closeTime, isClosed).getOrThrow()
        }
    )

    override suspend fun upsertSpecialSchedule(
        specificDate: String,
        openTime: String,
        closeTime: String,
        isClosed: Boolean
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            val resEl = requestJson(
                "POST",
                "/api/store/schedules/special",
                buildJsonObject {
                    put("specific_date", specificDate)
                    put("open_time", openTime)
                    put("close_time", closeTime)
                    put("is_closed", isClosed)
                }
            )
            resEl.jsonObject["storeSchedules"]?.let {
                _storeSchedules.value = json.decodeFromJsonElement<List<StoreSchedule>>(it)
            }
            Unit
        },
        offlineBlock = {
            offlineFallback.upsertSpecialSchedule(specificDate, openTime, closeTime, isClosed).getOrThrow()
        }
    )

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                val resEl = requestJson("DELETE", "/api/store/schedules/$scheduleId")
                resEl.jsonObject["storeSchedules"]?.let {
                    _storeSchedules.value = json.decodeFromJsonElement<List<StoreSchedule>>(it)
                }
                Unit
            },
            offlineBlock = { offlineFallback.deleteSchedule(scheduleId).getOrThrow() }
        )

    override suspend fun requestShipperDispatch(orderId: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                requestJson("POST", "/api/orders/$orderId/dispatch-shipper")
                fetchOrders()
            },
            offlineBlock = { offlineFallback.requestShipperDispatch(orderId).getOrThrow() }
        )

    override suspend fun updateDeliveryMilestone(
        orderId: String,
        newStatus: OrderStatus,
        driverName: String?,
        driverPhone: String?,
        driverPlate: String?
    ): Result<Unit> = runNodeOrOffline(
        nodeBlock = {
            requestJson(
                "PATCH",
                "/api/orders/$orderId/milestone",
                buildJsonObject {
                    put("status", newStatus.name)
                    if (driverName != null) put("driver_name", driverName)
                    if (driverPhone != null) put("driver_phone", driverPhone)
                    if (driverPlate != null) put("driver_plate", driverPlate)
                }
            )
            fetchOrders()
        },
        offlineBlock = {
            offlineFallback.updateDeliveryMilestone(orderId, newStatus, driverName, driverPhone, driverPlate)
                .getOrThrow()
        }
    )

    override suspend fun submitReview(orderId: String, rating: Int, comment: String): Result<Unit> =
        runNodeOrOffline(
            nodeBlock = {
                requestJson(
                    "POST",
                    "/api/orders/$orderId/review",
                    buildJsonObject {
                        put("rating", rating)
                        put("comment", comment)
                    }
                )
                fetchOrders()
            },
            offlineBlock = { offlineFallback.submitReview(orderId, rating, comment).getOrThrow() }
        )

    override suspend fun refreshAll() {
        runCatching {
            val bootEl = requestJson("GET", "/api/bootstrap")
            val boot = json.decodeFromJsonElement<BootstrapResponse>(bootEl)
            _storeSettings.value = boot.storeSettings
            _storeSchedules.value = boot.storeSchedules
            _categories.value = boot.categories
            _menuItems.value = boot.menuItems
            _vouchers.value = boot.vouchers
            if (!jwtToken.isNullOrBlank()) {
                fetchOrders()
                refreshCurrentUserBalance()
            }
        }
    }

    private suspend fun fetchStore() {
        val el = requestJson("GET", "/api/store")
        el.jsonObject["storeSettings"]?.let {
            _storeSettings.value = json.decodeFromJsonElement<StoreSettings>(it)
        }
        el.jsonObject["storeSchedules"]?.let {
            _storeSchedules.value = json.decodeFromJsonElement<List<StoreSchedule>>(it)
        }
    }

    private suspend fun fetchCatalog() {
        applyCatalogResponse(requestJson("GET", "/api/catalog"))
    }

    private fun applyCatalogResponse(el: JsonElement): CatalogResponse {
        val parsed = json.decodeFromJsonElement<CatalogResponse>(el)
        _categories.value = parsed.categories
        _menuItems.value = parsed.menuItems
        _vouchers.value = parsed.vouchers
        return parsed
    }

    private suspend fun fetchOrders() {
        if (jwtToken.isNullOrBlank()) return
        val el = requestJson("GET", "/api/orders")
        val list = el.jsonObject["orders"]?.let {
            json.decodeFromJsonElement<List<Order>>(it)
        }.orEmpty()
        _orders.value = list
        _reviews.value = list.mapNotNull { it.review }
    }

    private suspend fun refreshCurrentUserBalance() {
        if (jwtToken.isNullOrBlank()) return
        runCatching {
            val el = requestJson("GET", "/api/auth/me")
            el.jsonObject["user"]?.let {
                _currentUser.value = json.decodeFromJsonElement<UserProfile>(it)
            }
        }
    }

    private suspend fun <T> runNodeOrOffline(
        nodeBlock: suspend () -> T,
        offlineBlock: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(nodeBlock())
        } catch (io: IOException) {
            isServerOnline = false
            runCatching { offlineBlock() }.also { syncFromOfflineFallback() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
