package com.fnb.d2c

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fnb.d2c.domain.model.OrderStatus
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.ui.auth.AuthScreen
import com.fnb.d2c.ui.customer.CartAndCheckoutScreen
import com.fnb.d2c.ui.customer.CustomerMenuScreen
import com.fnb.d2c.ui.customer.CustomerOrdersScreen
import com.fnb.d2c.ui.merchant.MerchantCatalogAndVoucherScreen
import com.fnb.d2c.ui.merchant.MerchantKitchenScreen
import com.fnb.d2c.ui.merchant.StoreScheduleScreen
import com.fnb.d2c.ui.shipper.MockShipperSheet
import com.fnb.d2c.ui.theme.CoBaFnbTheme
import com.fnb.d2c.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FnbApplication

        setContent {
            CoBaFnbTheme {
                val vm: MainAppViewModel = viewModel(
                    factory = MainAppViewModel.Factory(
                        repository = app.repository,
                        shippingQuoteService = app.shippingQuoteService,
                        notificationManager = app.notificationManager
                    )
                )
                D2CFnbRootApp(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D2CFnbRootApp(vm: MainAppViewModel) {
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val storeSettings by vm.storeSettings.collectAsStateWithLifecycle()
    val storeSchedules by vm.storeSchedules.collectAsStateWithLifecycle()
    val resolvedStatus by vm.resolvedStoreStatus.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val menuItems by vm.menuItems.collectAsStateWithLifecycle()
    val vouchers by vm.vouchers.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val cartItems by vm.cartItems.collectAsStateWithLifecycle()
    val deliveryLocation by vm.deliveryLocation.collectAsStateWithLifecycle()
    val shippingQuote by vm.shippingQuote.collectAsStateWithLifecycle()
    val isQuotingShipping by vm.isQuotingShipping.collectAsStateWithLifecycle()
    val addressSuggestions by vm.addressSuggestions.collectAsStateWithLifecycle()
    val selectedVoucherCode by vm.selectedVoucherCode.collectAsStateWithLifecycle()
    val priceBreakdown by vm.priceBreakdown.collectAsStateWithLifecycle()
    val paymentMethod by vm.selectedPaymentMethod.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val isBellRinging by vm.isBellRinging.collectAsStateWithLifecycle()
    val clockTick by vm.clockTick.collectAsStateWithLifecycle()
    val demoFastOverdueMode by vm.demoFastOverdueMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var customerTab by remember { mutableIntStateOf(0) }
    var merchantTab by remember { mutableIntStateOf(0) }
    var showMockShipper by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(vm) {
        vm.uiMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentProfile = user
    if (currentProfile == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AuthScreen(
                    storeName = storeSettings.name,
                    backendModeLabel = vm.repository.backendModeLabel,
                    isBusy = isBusy,
                    onSignIn = vm::signIn,
                    onSignUp = vm::signUp,
                    onQuickDemoRole = vm::quickDemoLogin
                )
            }
        }
        return
    }

    val pendingCount = orders.count { it.status == OrderStatus.PENDING }
    val activeShipperCount = orders.count { it.status.isInDeliveryFlow }
    val customerOrders = orders.filter { it.customerId == currentProfile.id }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Text(
                        text = if (currentProfile.role == UserRole.CUSTOMER) {
                            "Khách: ${currentProfile.fullName}"
                        } else {
                            "Quản lý Quán / Bếp"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // 1-tap Demo Role Switcher (Customer <-> Merchant)
                    TextButton(
                        onClick = {
                            val target = if (currentProfile.role == UserRole.CUSTOMER) {
                                UserRole.MERCHANT
                            } else {
                                UserRole.CUSTOMER
                            }
                            vm.quickDemoLogin(target)
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Đổi vai trò",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (currentProfile.role == UserRole.CUSTOMER) "Sang Quán" else "Sang Khách",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Mock Shipper Drawer Trigger
                    IconButton(onClick = { showMockShipper = true }) {
                        BadgedBox(
                            badge = {
                                if (activeShipperCount > 0) {
                                    Badge { Text("$activeShipperCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeliveryDining, contentDescription = "Mock Shipper")
                        }
                    }

                    IconButton(onClick = vm::signOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Đăng xuất")
                    }
                }
            )
        },
        bottomBar = {
            if (currentProfile.role == UserRole.CUSTOMER) {
                val totalCartQty = cartItems.sumOf { it.quantity }
                NavigationBar {
                    NavigationBarItem(
                        selected = customerTab == 0,
                        onClick = { customerTab = 0 },
                        icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null) },
                        label = { Text("Thực đơn") }
                    )
                    NavigationBarItem(
                        selected = customerTab == 1,
                        onClick = { customerTab = 1 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (totalCartQty > 0) Badge { Text("$totalCartQty") }
                                }
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            }
                        },
                        label = { Text("Giỏ hàng") }
                    )
                    NavigationBarItem(
                        selected = customerTab == 2,
                        onClick = { customerTab = 2 },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Đơn của tôi (${customerOrders.size})") }
                    )
                }
            } else {
                NavigationBar {
                    NavigationBarItem(
                        selected = merchantTab == 0,
                        onClick = { merchantTab = 0 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) Badge { Text("$pendingCount") }
                                }
                            ) {
                                Icon(Icons.Default.Kitchen, contentDescription = null)
                            }
                        },
                        label = { Text("Bếp & Đơn") }
                    )
                    NavigationBarItem(
                        selected = merchantTab == 1,
                        onClick = { merchantTab = 1 },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        label = { Text("Menu & Mã") }
                    )
                    NavigationBarItem(
                        selected = merchantTab == 2,
                        onClick = { merchantTab = 2 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        label = { Text("Giờ Quán") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentProfile.role == UserRole.CUSTOMER) {
                when (customerTab) {
                    0 -> CustomerMenuScreen(
                        user = currentProfile,
                        storeSettings = storeSettings,
                        storeStatus = resolvedStatus,
                        categories = categories,
                        menuItems = menuItems,
                        cartItemCount = cartItems.sumOf { it.quantity },
                        cartSubtotal = priceBreakdown.subtotalAmount,
                        onTopUpWallet = { vm.topUpWallet(200_000L) },
                        onAddDishToCart = vm::addCustomizedDishToCart,
                        onGoToCart = { customerTab = 1 }
                    )

                    1 -> CartAndCheckoutScreen(
                        user = currentProfile,
                        storeStatus = resolvedStatus,
                        cartItems = cartItems,
                        deliveryLocation = deliveryLocation,
                        shippingQuote = shippingQuote,
                        isQuotingShipping = isQuotingShipping,
                        addressSuggestions = addressSuggestions,
                        availableVouchers = vouchers,
                        selectedVoucherCode = selectedVoucherCode,
                        priceBreakdown = priceBreakdown,
                        paymentMethod = paymentMethod,
                        isBusy = isBusy,
                        onUpdateQuantity = vm::updateCartLineQuantity,
                        onClearCart = vm::clearCart,
                        onSearchAddress = vm::searchAddress,
                        onSelectLocation = vm::selectDeliveryLocation,
                        onApplyVoucher = vm::applyVoucherCode,
                        onSelectPaymentMethod = vm::setPaymentMethod,
                        onPlaceOrder = {
                            vm.placeOrder {
                                customerTab = 2
                            }
                        }
                    )

                    2 -> CustomerOrdersScreen(
                        orders = customerOrders,
                        onReorder = { pastOrder ->
                            vm.reorderPastOrder(pastOrder) {
                                customerTab = 1
                            }
                        },
                        onSubmitReview = vm::submitOrderReview
                    )
                }
            } else {
                when (merchantTab) {
                    0 -> MerchantKitchenScreen(
                        orders = orders,
                        isBellRinging = isBellRinging,
                        nowTick = clockTick,
                        demoFastOverdueMode = demoFastOverdueMode,
                        onToggleDemoOverdue = vm::setDemoFastOverdueMode,
                        onSilenceBell = vm::silenceBell,
                        onConfirmOrder = vm::confirmOrder,
                        onRejectOrder = vm::rejectOrder,
                        onDispatchShipper = vm::dispatchShipper,
                        onOpenMockShipper = { showMockShipper = true }
                    )

                    1 -> MerchantCatalogAndVoucherScreen(
                        categories = categories,
                        menuItems = menuItems,
                        vouchers = vouchers,
                        onSaveCategory = vm::saveCategory,
                        onDeleteCategory = vm::deleteCategory,
                        onSaveDish = vm::saveDish,
                        onToggleDishStock = vm::toggleDishStock,
                        onDeleteDish = vm::deleteDish,
                        onSaveOptionGroup = vm::saveOptionGroup,
                        onDeleteOptionGroup = vm::deleteOptionGroup,
                        onSaveOptionItem = vm::saveOptionItem,
                        onDeleteOptionItem = vm::deleteOptionItem,
                        onSaveVoucher = vm::saveVoucher,
                        onToggleVoucher = vm::toggleVoucher,
                        onDeleteVoucher = vm::deleteVoucher
                    )

                    2 -> StoreScheduleScreen(
                        resolvedStatus = resolvedStatus,
                        schedules = storeSchedules,
                        onSetQuickStatus = vm::setStoreQuickStatus,
                        onSaveWeeklySchedule = vm::saveWeeklySchedule,
                        onSaveSpecialSchedule = vm::saveSpecialSchedule,
                        onDeleteSchedule = vm::deleteSchedule
                    )
                }
            }
        }
    }

    if (showMockShipper) {
        MockShipperSheet(
            orders = orders,
            onDismiss = { showMockShipper = false },
            onAdvanceMilestone = vm::advanceMockShipperMilestone
        )
    }
}
