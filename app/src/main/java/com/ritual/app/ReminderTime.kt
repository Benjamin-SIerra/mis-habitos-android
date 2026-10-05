package com.ritual.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Calcula la próxima alarma semanal usando la zona horaria local del teléfono. */
object ReminderTime {
    fun nextTriggerMillis(
        nowMillis: Long,
        dayIndex: Int,
        startTime: String,
        leadMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(dayIndex in 0..6) { "El día debe estar entre lunes (0) y domingo (6)." }
        require(leadMinutes >= 0) { "El adelanto del recordatorio no puede ser negativo." }

        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val habitDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayIndex + 1)))
        var trigger = habitDate.atTime(LocalTime.parse(startTime)).atZone(zone).minusMinutes(leadMinutes.toLong())

        // Si la hora de aviso de esta semana ya pasó, el hábito se repite la próxima semana.
        if (!trigger.isAfter(now)) trigger = trigger.plusWeeks(1)
        return trigger.toInstant().toEpochMilli()
    }

    /** Salta solo el aviso de hoy si el hábito ya se marcó como completado. */
    fun skipCompletedOccurrenceIfToday(
        triggerAtMillis: Long,
        nowMillis: Long,
        dayIndex: Int,
        completedToday: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (!completedToday || dayIndex !in 0..6) return triggerAtMillis
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val trigger = Instant.ofEpochMilli(triggerAtMillis).atZone(zone)
        val todayIndex = now.dayOfWeek.value - 1
        return if (todayIndex == dayIndex && trigger.toLocalDate() == now.toLocalDate()) {
            trigger.plusWeeks(1).toInstant().toEpochMilli()
        } else {
            triggerAtMillis
        }
    }
}
