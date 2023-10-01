package com.dehnes.smarthome.utils

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

object DateTimeUtils {

    val zoneId = ZoneId.of("Europe/Oslo")

    fun Instant.roundToNearestFullHour(): Instant {
        val zonedDateTime = this.atZone(zoneId)
        val minute = zonedDateTime.minute

        return if (minute >= 30) {
            zonedDateTime
                .withMinute(0)
                .plusHours(1)
                .withSecond(0)
                .withNano(0)
        } else {
            zonedDateTime
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        }.toInstant()
    }

    fun Instant.plusDays(days: Long) = this.plus(days, ChronoUnit.DAYS)

}