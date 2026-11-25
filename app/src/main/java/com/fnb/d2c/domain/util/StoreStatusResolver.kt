package com.fnb.d2c.domain.util

import com.fnb.d2c.domain.model.ResolvedStoreStatus
import com.fnb.d2c.domain.model.ScheduleType
import com.fnb.d2c.domain.model.StoreAvailabilityState
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.StoreSchedule
import com.fnb.d2c.domain.model.StoreSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object StoreStatusResolver {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val vietnamZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    /**
     * Resolves real-time store status without Cron Jobs using the 4-level priority:
     * 1. manual_status == CLOSED -> Closed
     * 2. busy_until != null && now < busy_until -> Busy (auto-recovers once time passes)
     * 3. SPECIAL schedule matching today's date -> Special hours / holiday closed
     * 4. WEEKLY schedule matching today's day-of-week -> Regular hours
     */
    fun resolve(
        settings: StoreSettings,
        schedules: List<StoreSchedule>,
        now: ZonedDateTime = ZonedDateTime.now(vietnamZone)
    ): ResolvedStoreStatus {
        // Priority 1: Manual CLOSED
        if (settings.manualStatus == StoreManualStatus.CLOSED) {
            return ResolvedStoreStatus(
                state = StoreAvailabilityState.CLOSED,
                badgeText = "Đã đóng cửa",
                detailText = "Quán hiện đang đóng cửa thủ công. Hẹn gặp lại bạn sau!"
            )
        }

        // Priority 2: Temporary BUSY timer
        val busyInstant = settings.busyUntilIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (busyInstant != null && now.toInstant().isBefore(busyInstant)) {
            val reopenLocal = busyInstant.atZone(now.zone).toLocalTime().format(timeFormatter)
            val reopenDate = busyInstant.atZone(now.zone).toLocalDate()
            val reopenText = if (reopenDate == now.toLocalDate()) {
                "lúc $reopenLocal hôm nay"
            } else {
                "lúc $reopenLocal ngày ${reopenDate.dayOfMonth}/${reopenDate.monthValue}"
            }
            return ResolvedStoreStatus(
                state = StoreAvailabilityState.BUSY,
                badgeText = "Quán đang bận",
                detailText = "Bếp đang quá tải đơn, quán sẽ nhận đơn lại $reopenText."
            )
        }

        val todayStr = now.toLocalDate().toString() // yyyy-MM-dd
        val currentTime = now.toLocalTime()

        // Priority 3: Special date override
        val specialToday = schedules.firstOrNull {
            it.type == ScheduleType.SPECIAL && it.specificDate == todayStr
        }
        if (specialToday != null) {
            return evaluateWindow(
                schedule = specialToday,
                currentTime = currentTime,
                sourceLabel = "Lịch ngày đặc biệt ($todayStr)"
            )
        }

        // Priority 4: Weekly schedule (1 = Monday .. 7 = Sunday)
        val dow = now.dayOfWeek.value
        val weeklyToday = schedules.firstOrNull {
            it.type == ScheduleType.WEEKLY && it.dayOfWeek == dow
        }
        if (weeklyToday != null) {
            return evaluateWindow(
                schedule = weeklyToday,
                currentTime = currentTime,
                sourceLabel = "Giờ mở cửa hôm nay"
            )
        }

        return ResolvedStoreStatus(
            state = StoreAvailabilityState.OPEN,
            badgeText = "Đang mở cửa",
            detailText = "Sẵn sàng phục vụ món nóng hổi"
        )
    }

    private fun evaluateWindow(
        schedule: StoreSchedule,
        currentTime: LocalTime,
        sourceLabel: String
    ): ResolvedStoreStatus {
        val open = parseLocalTimeSafe(schedule.openTime, LocalTime.of(6, 30))
        val close = parseLocalTimeSafe(schedule.closeTime, LocalTime.of(22, 0))
        val openLabel = open.format(timeFormatter)
        val closeLabel = close.format(timeFormatter)

        if (schedule.isClosed) {
            return ResolvedStoreStatus(
                state = StoreAvailabilityState.CLOSED,
                badgeText = "Nghỉ bán hôm nay",
                detailText = "$sourceLabel: Quán nghỉ bán trong ngày hôm nay."
            )
        }

        val withinHours = !currentTime.isBefore(open) && !currentTime.isAfter(close)
        return if (withinHours) {
            ResolvedStoreStatus(
                state = StoreAvailabilityState.OPEN,
                badgeText = "Đang mở cửa",
                detailText = "$sourceLabel: $openLabel – $closeLabel"
            )
        } else {
            ResolvedStoreStatus(
                state = StoreAvailabilityState.CLOSED,
                badgeText = "Ngoài giờ phục vụ",
                detailText = "$sourceLabel: $openLabel – $closeLabel"
            )
        }
    }

    private fun parseLocalTimeSafe(raw: String, fallback: LocalTime): LocalTime {
        val trimmed = raw.trim().take(5)
        return runCatching { LocalTime.parse(trimmed, timeFormatter) }.getOrDefault(fallback)
    }

    fun dayOfWeekLabelVi(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Thứ Hai"
        2 -> "Thứ Ba"
        3 -> "Thứ Tư"
        4 -> "Thứ Năm"
        5 -> "Thứ Sáu"
        6 -> "Thứ Bảy"
        7 -> "Chủ Nhật"
        else -> "Ngày $dayOfWeek"
    }

    fun parseDateSafe(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
}
