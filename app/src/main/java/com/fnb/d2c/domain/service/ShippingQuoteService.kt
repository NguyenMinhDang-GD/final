package com.fnb.d2c.domain.service

import com.fnb.d2c.domain.model.DeliveryLocation
import com.fnb.d2c.domain.model.ShippingQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates real road distance & shipping fee using OSRM Routing API,
 * plus address search via OpenStreetMap Nominatim, with deterministic Haversine fallback.
 */
class ShippingQuoteService {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val BASE_FEE_VND = 15_000L
        const val BASE_DISTANCE_KM = 2.0
        const val EXTRA_PER_KM_VND = 5_000L

        fun calculateFeeFromDistance(distanceKm: Double): Long {
            val safeKm = distanceKm.coerceAtLeast(0.1)
            if (safeKm <= BASE_DISTANCE_KM) return BASE_FEE_VND
            val extraKm = ceil(safeKm - BASE_DISTANCE_KM).toLong()
            return BASE_FEE_VND + extraKm * EXTRA_PER_KM_VND
        }

        fun haversineRoadEstimateKm(
            lat1: Double,
            lng1: Double,
            lat2: Double,
            lng2: Double
        ): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            // Multiply straight-line distance by 1.28 urban road factor
            val estimated = earthRadiusKm * c * 1.28
            return (round(estimated * 10.0) / 10.0).coerceAtLeast(0.5)
        }
    }

    suspend fun getQuote(
        storeLat: Double,
        storeLng: Double,
        customerLat: Double,
        customerLng: Double
    ): ShippingQuote = withContext(Dispatchers.IO) {
        val osrmKm = runCatching {
            val urlStr =
                "https://router.project-osrm.org/route/v1/driving/$storeLng,$storeLat;$customerLng,$customerLat?overview=false"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "CoBaD2CFnbApp/1.0")
            }
            conn.inputStream.bufferedReader().use { reader ->
                val root = json.parseToJsonElement(reader.readText()).jsonObject
                val meters = root["routes"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("distance")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                meters?.let { (round((it / 1000.0) * 10.0) / 10.0).coerceAtLeast(0.3) }
            }
        }.getOrNull()

        if (osrmKm != null) {
            ShippingQuote(
                distanceKm = osrmKm,
                shippingFee = calculateFeeFromDistance(osrmKm),
                sourceLabel = "Lộ trình đường bộ OSRM"
            )
        } else {
            val fallbackKm = haversineRoadEstimateKm(storeLat, storeLng, customerLat, customerLng)
            ShippingQuote(
                distanceKm = fallbackKm,
                shippingFee = calculateFeeFromDistance(fallbackKm),
                sourceLabel = "Ước tính tọa độ GPS"
            )
        }
    }

    suspend fun searchAddressSuggestions(query: String): List<DeliveryLocation> =
        withContext(Dispatchers.IO) {
            if (query.trim().length < 3) return@withContext emptyList()
            runCatching {
                val encoded = URLEncoder.encode("${query.trim()}, Hồ Chí Minh, Việt Nam", "UTF-8")
                val urlStr =
                    "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&countrycodes=vn"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("User-Agent", "CoBaD2CFnbStudentProject/1.0")
                    setRequestProperty("Accept-Language", "vi")
                }
                conn.inputStream.bufferedReader().use { reader ->
                    val arr = json.parseToJsonElement(reader.readText()).jsonArray
                    arr.mapNotNull { el ->
                        val obj = el.jsonObject
                        val displayName = obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                        val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                        DeliveryLocation(
                            address = displayName,
                            lat = lat,
                            lng = lon
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
}
