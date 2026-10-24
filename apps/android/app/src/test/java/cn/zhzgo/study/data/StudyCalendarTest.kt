package cn.zhzgo.study.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyCalendarTest {
    @Test fun calendarDoesNotDependOnDeviceTimeZone() {
        val instant=Instant.parse("2026-10-22T16:01:00Z")
        for(zone in listOf("UTC","America/Los_Angeles","Asia/Tokyo")) {
            assertEquals("2026-10-23",StudyCalendar.today(Clock.fixed(instant,ZoneId.of(zone))).toString())
        }
    }
    @Test fun chinaMidnightAndYearBoundaryAreCorrect() {
        assertEquals("2026-12-31",StudyCalendar.today(Clock.fixed(Instant.parse("2026-12-31T15:59:59Z"),ZoneId.of("UTC"))).toString())
        assertEquals("2027-01-01",StudyCalendar.today(Clock.fixed(Instant.parse("2026-12-31T16:00:00Z"),ZoneId.of("UTC"))).toString())
    }
}
