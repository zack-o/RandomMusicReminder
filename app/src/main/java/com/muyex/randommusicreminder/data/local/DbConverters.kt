package com.muyex.randommusicreminder.data.local

import androidx.room.TypeConverter
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalTime

class DbConverters {
    @TypeConverter fun timingModeToString(value: com.muyex.randommusicreminder.domain.model.TimingMode): String = value.name
    @TypeConverter fun stringToTimingMode(value: String): com.muyex.randommusicreminder.domain.model.TimingMode = com.muyex.randommusicreminder.domain.model.TimingMode.valueOf(value)
    @TypeConverter fun missingDayPolicyToString(value: com.muyex.randommusicreminder.domain.model.MissingDayPolicy): String = value.name
    @TypeConverter fun stringToMissingDayPolicy(value: String): com.muyex.randommusicreminder.domain.model.MissingDayPolicy = com.muyex.randommusicreminder.domain.model.MissingDayPolicy.valueOf(value)
    @TypeConverter
    fun holidayPolicyToString(value: com.muyex.randommusicreminder.domain.calendar.HolidayPolicy): String = value.name

    @TypeConverter
    fun stringToHolidayPolicy(value: String): com.muyex.randommusicreminder.domain.calendar.HolidayPolicy =
        com.muyex.randommusicreminder.domain.calendar.HolidayPolicy.valueOf(value)

    @TypeConverter
    fun occurrenceKindToString(value: OccurrenceKind): String = value.name

    @TypeConverter
    fun stringToOccurrenceKind(value: String): OccurrenceKind = OccurrenceKind.valueOf(value)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun scheduleTypeToString(value: ScheduleType): String = value.name

    @TypeConverter
    fun stringToScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun musicPoolModeToString(value: MusicPoolMode): String = value.name

    @TypeConverter
    fun stringToMusicPoolMode(value: String): MusicPoolMode = MusicPoolMode.valueOf(value)

    @TypeConverter
    fun alertModeToString(value: AlertMode): String = value.name

    @TypeConverter
    fun stringToAlertMode(value: String): AlertMode = AlertMode.valueOf(value)

    @TypeConverter
    fun occurrenceStateToString(value: OccurrenceState): String = value.name

    @TypeConverter
    fun stringToOccurrenceState(value: String): OccurrenceState = OccurrenceState.valueOf(value)

    @TypeConverter
    fun scheduleModeToString(value: ScheduleMode): String = value.name

    @TypeConverter
    fun stringToScheduleMode(value: String): ScheduleMode = ScheduleMode.valueOf(value)

    @TypeConverter
    fun ringSessionStateToString(value: RingSessionState): String = value.name

    @TypeConverter
    fun stringToRingSessionState(value: String): RingSessionState = RingSessionState.valueOf(value)

    @TypeConverter
    fun ringStopReasonToString(value: RingStopReason?): String? = value?.name

    @TypeConverter
    fun stringToRingStopReason(value: String?): RingStopReason? = value?.let(RingStopReason::valueOf)

    @TypeConverter
    fun longListToString(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun stringToLongList(value: String): List<Long> {
        if (value.isBlank()) return emptyList()
        return value.split(',').mapNotNull(String::toLongOrNull)
    }
}
