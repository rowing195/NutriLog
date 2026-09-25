package com.watson.nutrilog

import com.watson.nutrilog.data.HealthConnectSync
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HydrationRecordTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val date = LocalDate.of(2026, 9, 25)
    private val now = Instant.parse("2026-09-25T04:00:00Z")

    @Test fun currentDayUsesMillilitersAndEndsAtNow() {
        val record = HealthConnectSync.hydrationRecord(date, 1250.5, now, zone)!!
        assertEquals(1250.5, record.volume.inMilliliters, 0.001)
        assertEquals(date.atStartOfDay(zone).toInstant(), record.startTime)
        assertEquals(now, record.endTime)
        assertEquals("nutrilog_water_2026-09-25", record.metadata.clientRecordId)
    }

    @Test fun editingUsesSameIdAndNewerVersion() {
        val first = HealthConnectSync.hydrationRecord(date, 500.0, now, zone)!!
        val updated = HealthConnectSync.hydrationRecord(date, 250.0, now.plusSeconds(1), zone)!!
        assertEquals(first.metadata.clientRecordId, updated.metadata.clientRecordId)
        assertTrue(updated.metadata.clientRecordVersion > first.metadata.clientRecordVersion)
        assertEquals(250.0, updated.volume.inMilliliters, 0.0)
    }

    @Test fun zeroNegativeFutureAndMidnightDoNotCreateRecords() {
        assertNull(HealthConnectSync.hydrationRecord(date, 0.0, now, zone))
        assertNull(HealthConnectSync.hydrationRecord(date, -250.0, now, zone))
        assertNull(HealthConnectSync.hydrationRecord(date.plusDays(1), 250.0, now, zone))
        assertNull(HealthConnectSync.hydrationRecord(date, 250.0, date.atStartOfDay(zone).toInstant(), zone))
    }

    @Test fun historicalDayUsesLocalBoundariesAcrossDaylightSaving() {
        val dstZone = ZoneId.of("America/New_York")
        val dstDate = LocalDate.of(2026, 3, 8)
        val record = HealthConnectSync.hydrationRecord(dstDate, 2000.0, now, dstZone)!!
        assertEquals(dstDate.atStartOfDay(dstZone).toInstant(), record.startTime)
        assertEquals(dstDate.plusDays(1).atStartOfDay(dstZone).toInstant(), record.endTime)
        assertEquals(23, Duration.between(record.startTime, record.endTime).toHours().toInt())
        assertNotEquals(record.startZoneOffset, record.endZoneOffset)
    }
}
