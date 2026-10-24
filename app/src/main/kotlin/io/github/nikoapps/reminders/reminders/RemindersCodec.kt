package io.github.nikoapps.reminders.reminders

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64

/**
 * Turns the reminders into one short string for DataStore and back. Six reminders don't need a
 * database or a serialisation library.
 *
 * Each reminder is `place;id;name;minuteOfDay;repeat;photoVersion;recordingVersion;activeFrom;picture`,
 * with the text fields in URL-safe Base64 so any script or punctuation is safe. The repeat is `d`
 * for every day, `w` and ISO day numbers for chosen days (`w135` is Monday, Wednesday and
 * Friday), or `o` and a date for once (`o2026-10-24`). Reminders are joined with commas. Records
 * that don't read back are skipped rather than losing every reminder. The picture is the
 * [Picture.key], empty for none; records saved before pictures have no such field.
 */
object RemindersCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(reminders: List<Reminder>): String =
        reminders.joinToString(",") { r ->
            listOf(
                r.place.toString(),
                b64(r.id),
                b64(r.name),
                (r.time.hour * 60 + r.time.minute).toString(),
                encodeRepeat(r.repeat),
                r.photoVersion.toString(),
                r.recordingVersion.toString(),
                r.activeFrom.toString(),
                r.picture?.key.orEmpty(),
            ).joinToString(";")
        }

    fun decode(text: String?): List<Reminder> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(",").mapNotNull { record ->
            val fields = record.split(";")
            // Fields a later version adds are left alone rather than losing the reminder.
            if (fields.size < 8) return@mapNotNull null
            runCatching {
                val minute = fields[3].toInt()
                Reminder(
                    id = unb64(fields[1]),
                    name = unb64(fields[2]),
                    time = LocalTime.of(minute / 60, minute % 60),
                    repeat = decodeRepeat(fields[4]) ?: return@mapNotNull null,
                    place = fields[0].toInt(),
                    photoVersion = fields[5].toInt().coerceAtLeast(0),
                    recordingVersion = fields[6].toInt().coerceAtLeast(0),
                    activeFrom = fields[7].toLong(),
                    picture = Picture.of(fields.getOrNull(8)),
                )
            }.getOrNull()
        }
    }

    internal fun encodeRepeat(repeat: Repeat): String = when (repeat) {
        Repeat.EveryDay -> "d"
        is Repeat.Days -> "w" + repeat.days.map { it.value }.sorted().joinToString("")
        is Repeat.Once -> "o" + repeat.date
    }

    internal fun decodeRepeat(text: String): Repeat? = when {
        text == "d" -> Repeat.EveryDay
        text.startsWith("w") -> text.drop(1).map { DayOfWeek.of(it.digitToInt()) }.toSet()
            .takeIf { it.isNotEmpty() }
            ?.let(Repeat::Days)
        text.startsWith("o") -> Repeat.Once(LocalDate.parse(text.drop(1)))
        else -> null
    }

    private fun b64(s: String): String = encoder.encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(decoder.decode(s), Charsets.UTF_8)
}
