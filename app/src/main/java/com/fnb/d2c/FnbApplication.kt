package com.fnb.d2c

import android.app.Application
import com.fnb.d2c.data.repository.FnbRepository
import com.fnb.d2c.data.repository.NodeApiFnbRepository
import com.fnb.d2c.domain.model.UserRole
import com.fnb.d2c.domain.service.ShippingQuoteService
import com.fnb.d2c.notification.OrderNotificationManager
import org.osmdroid.config.Configuration

class FnbApplication : Application() {

    lateinit var notificationManager: OrderNotificationManager
        private set

    lateinit var shippingQuoteService: ShippingQuoteService
        private set

    lateinit var repository: FnbRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName

        notificationManager = OrderNotificationManager(applicationContext)
        shippingQuoteService = ShippingQuoteService()

        repository = NodeApiFnbRepository(
            context = applicationContext,
            baseUrl = BuildConfig.NODE_API_BASE_URL,
            onNewOrderCreated = { order ->
                notificationManager.triggerMerchantNewOrderAlert(order)
            },
            onOrderStatusUpdated = { order ->
                val user = repository.currentUser.value
                if (user == null || user.role == UserRole.CUSTOMER) {
                    notificationManager.notifyCustomerOrderChanged(order)
                }
            }
        )
    }
}
