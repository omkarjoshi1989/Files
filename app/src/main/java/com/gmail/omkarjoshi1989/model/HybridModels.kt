package com.gmail.omkarjoshi1989.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Mirrors Supabase enum `Days of The Week`. */
enum class HybridDay(val dbValue: String) {
    Sunday("Sunday"), Monday("Monday"), Tuesday("Tuesday"), Wednesday("Wednesday"),
    Thursday("Thursday"), Friday("Friday"), Saturday("Saturday");

    companion object {
        fun fromDb(v: String?): HybridDay? = entries.firstOrNull { it.dbValue == v }
        fun of(date: LocalDate): HybridDay = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> Sunday
            DayOfWeek.MONDAY -> Monday
            DayOfWeek.TUESDAY -> Tuesday
            DayOfWeek.WEDNESDAY -> Wednesday
            DayOfWeek.THURSDAY -> Thursday
            DayOfWeek.FRIDAY -> Friday
            DayOfWeek.SATURDAY -> Saturday
        }
    }
}

/** Mirrors Supabase enum `HomeOfficeWeekend`. */
enum class HomeOfficeWeekend(val dbValue: String) {
    H("H"), O("O"), W("w");

    companion object {
        fun fromDb(v: String?): HomeOfficeWeekend? = entries.firstOrNull { it.dbValue == v }
    }
}

/** Mirrors Supabase enum `PlannedHybridModes`. [how] is the matching `H_O_W` code (null = empty). */
enum class PlannedHybridMode(val dbValue: String, val how: HomeOfficeWeekend?) {
    HOME("HOME", HomeOfficeWeekend.H),
    OFFICE("OFFICE", HomeOfficeWeekend.O),
    LEAVE("LEAVE", HomeOfficeWeekend.O),
    HOLIDAY("HOLIDAY", HomeOfficeWeekend.O),
    WEEKEND("weekend", HomeOfficeWeekend.W),
    COMP_OFF("comp-off", null);

    companion object {
        fun fromDb(v: String?): PlannedHybridMode? = entries.firstOrNull { it.dbValue == v }
    }
}

/** One row of the Supabase `Hybrid` table. */
data class HybridEntry(
    val date: LocalDate,
    val day: HybridDay,
    val mode: PlannedHybridMode,
    val how: HomeOfficeWeekend?,
    /** Stored value of `office_or_home_serial_number` (managed by the app, not user-editable). */
    val serial: String?,
    val status: String?
)

