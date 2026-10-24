package cn.zhzgo.study.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The API uses a China calendar day; device time zones must not move practice between days. */
object StudyCalendar {
    private val zone = ZoneId.of("Asia/Shanghai")
    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(zone))
}
