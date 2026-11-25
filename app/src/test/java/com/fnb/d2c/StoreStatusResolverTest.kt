package com.fnb.d2c

import com.fnb.d2c.domain.model.ScheduleType
import com.fnb.d2c.domain.model.StoreAvailabilityState
import com.fnb.d2c.domain.model.StoreManualStatus
import com.fnb.d2c.domain.model.StoreSchedule
import com.fnb.d2c.domain.model.StoreSettings
import com.fnb.d2c.domain.util.StoreStatusResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StoreStatusResolverTest {

    private val vnZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val tuesdayNoon = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, vnZone) // Tuesday = 2

    private val weeklySchedules = listOf(
        StoreSchedule(
            id = "w2",
            type = ScheduleType.WEEKLY,
            dayOfWeek = 2,
            openTime = "06:30",
            closeTime = "21:30",
            isClosed = false
        )
    )

    @Test
    fun `manual CLOSED overrides everything`() {
        val settings = StoreSettings(manualStatus = StoreManualStatus.CLOSED)
        val status = StoreStatusResolver.resolve(settings, weeklySchedules, tuesdayNoon)
        assertEquals(StoreAvailabilityState.CLOSED, status.state)
        assertFalse(status.canAcceptOrder)
    }

    @Test
    fun `busy_until blocks orders before expiry and auto-reopens after expiry without cron`() {
        val busyUntil1230 = tuesdayNoon.plusMinutes(30).toInstant().toString()
        val settings = StoreSettings(manualStatus = StoreManualStatus.OPEN, busyUntilIso = busyUntil1230)

        val duringBusy = StoreStatusResolver.resolve(settings, weeklySchedules, tuesdayNoon)
        assertEquals(StoreAvailabilityState.BUSY, duringBusy.state)
        assertFalse(duringBusy.canAcceptOrder)

        val afterBusy = StoreStatusResolver.resolve(settings, weeklySchedules, tuesdayNoon.plusMinutes(31))
        assertEquals(StoreAvailabilityState.OPEN, afterBusy.state)
        assertTrue(afterBusy.canAcceptOrder)
    }

    @Test
    fun `special holiday schedule overrides weekly schedule`() {
        val holiday = StoreSchedule(
            id = "sp1",
            type = ScheduleType.SPECIAL,
            specificDate = "2026-11-24",
            openTime = "08:00",
            closeTime = "12:00",
            isClosed = true
        )
        val status = StoreStatusResolver.resolve(
            settings = StoreSettings(manualStatus = StoreManualStatus.OPEN),
            schedules = weeklySchedules + holiday,
            now = tuesdayNoon
        )
        assertEquals(StoreAvailabilityState.CLOSED, status.state)
    }
}
