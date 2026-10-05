package com.ritual.app

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reglas de hora para que los avisos semanales no se adelanten ni se pierdan. */
class ReminderTimeTest {
    private val zone = ZoneId.of("America/Mexico_City")

    @Test
    fun reminderUsesTheLeadTimeOnTheUpcomingSameDay() {
        val now = ZonedDateTime.of(2026, 6, 1, 14, 0, 0, 0, zone) // lunes
        val expected = ZonedDateTime.of(2026, 6, 1, 14, 50, 0, 0, zone)

        assertEquals(
            expected.toInstant().toEpochMilli(),
            ReminderTime.nextTriggerMillis(now.toInstant().toEpochMilli(), 0, "15:00", 10, zone),
        )
    }

    @Test
    fun missedReminderMovesToTheFollowingWeek() {
        val now = ZonedDateTime.of(2026, 6, 1, 14, 55, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 6, 8, 14, 50, 0, 0, zone)

        assertEquals(
            expected.toInstant().toEpochMilli(),
            ReminderTime.nextTriggerMillis(now.toInstant().toEpochMilli(), 0, "15:00", 10, zone),
        )
    }

    @Test
    fun reminderForNextWeekdayKeepsTheLocalTime() {
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone) // lunes
        val expected = ZonedDateTime.of(2026, 6, 3, 6, 30, 0, 0, zone) // miércoles

        assertEquals(
            expected.toInstant().toEpochMilli(),
            ReminderTime.nextTriggerMillis(now.toInstant().toEpochMilli(), 2, "07:00", 30, zone),
        )
    }

    @Test
    fun mondayJustAfterMidnightCanReceiveSundayConfiguredHabitReminder() {
        val now = ZonedDateTime.of(2026, 6, 8, 0, 0, 0, 0, zone) // lunes
        val expected = ZonedDateTime.of(2026, 6, 13, 23, 50, 0, 0, zone) // sábado anterior

        assertEquals(
            expected.toInstant().toEpochMilli(),
            ReminderTime.nextTriggerMillis(now.toInstant().toEpochMilli(), 6, "00:00", 10, zone),
        )
    }

    @Test
    fun completedHabitSkipsOnlyItsCurrentDayOccurrence() {
        val now = ZonedDateTime.of(2026, 6, 1, 14, 0, 0, 0, zone) // lunes
        val scheduled = ReminderTime.nextTriggerMillis(now.toInstant().toEpochMilli(), 0, "15:00", 10, zone)
        val actual = ReminderTime.skipCompletedOccurrenceIfToday(
            scheduled, now.toInstant().toEpochMilli(), 0, true, zone,
        )
        val expected = ZonedDateTime.of(2026, 6, 8, 14, 50, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), actual)
    }
}
