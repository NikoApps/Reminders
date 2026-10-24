package io.github.nikoapps.reminders.reminders

import androidx.compose.runtime.Immutable
import io.github.nikoapps.reminders.settings.KeepReminding
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

/** What happened to one reminder at one time. */
enum class Outcome {
    /** It's playing, and plays again until Done or the keep reminding time is over. */
    Due,

    /** Done was pressed. */
    Done,

    /** It played for the whole keep reminding time without Done, or the phone was off then. */
    Missed,
}

/**
 * One time a reminder played. History keeps these for 7 days, so a helper can check on a visit
 * that reminders play and get done. It records only the reminder's name, when it played and
 * when Done was pressed.
 */
@Immutable
data class Played(
    val reminderId: String,
    /** The reminder's name when it played, so history still reads right after it's renamed or removed. */
    val name: String,
    /** The time it was set for. With [reminderId] it names this one time. */
    val planned: LocalDateTime,
    val outcome: Outcome,
    /** How many times it has played so far. */
    val plays: Int = 0,
    /** When Done was pressed, for [Outcome.Done]. */
    val doneAt: LocalDateTime? = null,
) {
    fun isFor(id: String, time: LocalDateTime): Boolean = reminderId == id && planned == time
}

/** How many days history keeps, today included. */
const val HISTORY_DAYS = 7L

/** History without anything older than [HISTORY_DAYS] days before [today]. */
fun List<Played>.keptOn(today: LocalDate): List<Played> {
    val oldest = today.minusDays(HISTORY_DAYS - 1)
    return filter { it.planned.toLocalDate() >= oldest }
}

/**
 * The time of reminder [id] that its tile, its screen and Done are about: today's latest, or one
 * from yesterday that's still playing or whose plays ran past midnight, such as 23:50 with 30
 * minutes of reminding. Null when it hasn't played today.
 */
fun List<Played>.current(id: String, now: LocalDateTime, keep: KeepReminding): Played? {
    val today = now.toLocalDate()
    return filter { played ->
        played.reminderId == id &&
            (played.planned.toLocalDate() == today || played.outcome == Outcome.Due || lastPlay(played.planned, keep).toLocalDate() == today)
    }.maxByOrNull { it.planned }
}

/**
 * Marks the reminder [id]'s [current] time done at [now], if it's still due or missed, so a late
 * dose can still be marked. Anything else is left as it is.
 */
fun List<Played>.markDone(id: String, now: LocalDateTime, keep: KeepReminding): List<Played> {
    val latest = current(id, now, keep) ?: return this
    if (latest.outcome == Outcome.Done) return this
    return map { if (it === latest) it.copy(outcome = Outcome.Done, doneAt = now) else it }
}

/**
 * Turns history into one string for DataStore and back: one record per time played,
 * `id;name;planned;outcome;plays;doneAt`, with id and name in URL-safe Base64, times as ISO
 * local date-times and an empty doneAt when not done. Records that don't read back are skipped.
 */
object HistoryCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(history: List<Played>): String = history.joinToString(",") { p ->
        listOf(b64(p.reminderId), b64(p.name), p.planned.toString(), p.outcome.name, p.plays.toString(), p.doneAt?.toString().orEmpty())
            .joinToString(";")
    }

    fun decode(text: String?): List<Played> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(",").mapNotNull { record ->
            val f = record.split(";")
            if (f.size < 6) return@mapNotNull null
            runCatching {
                Played(
                    reminderId = unb64(f[0]),
                    name = unb64(f[1]),
                    planned = LocalDateTime.parse(f[2]),
                    outcome = Outcome.valueOf(f[3]),
                    plays = f[4].toInt().coerceAtLeast(0),
                    doneAt = f[5].takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
                )
            }.getOrNull()
        }
    }

    private fun b64(s: String): String = encoder.encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(decoder.decode(s), Charsets.UTF_8)
}
