package io.github.nikoapps.reminders.reminders

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nikoapps.reminders.settings.deviceDataStore
import io.github.nikoapps.reminders.settings.forgetUnreadable
import io.github.nikoapps.reminders.settings.isUnreadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "reminders"

/**
 * Reads and writes the reminders and their history. Both are in one DataStore, so the alarm can
 * read the reminders and record what played in one step, and an edit never races a reminder
 * that's playing. Change reminders with the functions in Places.kt inside [updateReminders],
 * such as `updateReminders { removeReminder(it, id) }`, so nothing else moves.
 */
class RemindersRepository(context: Context) {
    private val app = context.applicationContext
    private val store = app.deviceDataStore(STORE_NAME)

    val reminders: Flow<List<Reminder>> = store.data.map { RemindersCodec.decode(it[Keys.reminders]) }

    val history: Flow<List<Played>> = store.data.map { HistoryCodec.decode(it[Keys.history]) }

    /** True while there are no reminders because the saved ones couldn't be read. Reads a file, so collect it off the main thread. */
    val unreadable: Flow<Boolean> = reminders.map { it.isEmpty() && app.isUnreadable(STORE_NAME) }

    suspend fun currentReminders(): List<Reminder> = reminders.first()

    suspend fun updateReminders(transform: (List<Reminder>) -> List<Reminder>) {
        store.edit { prefs -> prefs.writeReminders(transform(RemindersCodec.decode(prefs[Keys.reminders]))) }
    }

    suspend fun updateHistory(transform: (List<Played>) -> List<Played>) {
        store.edit { prefs -> prefs.writeHistory(transform(HistoryCodec.decode(prefs[Keys.history]))) }
    }

    /**
     * Reads the reminders and history, and saves the history [transform] returns, in one step.
     * Returns whatever [transform] worked out along with it.
     */
    suspend fun <T> withHistory(transform: (List<Reminder>, List<Played>) -> Pair<List<Played>, T>): T {
        var result: T? = null
        store.edit { prefs ->
            val (history, extra) = transform(RemindersCodec.decode(prefs[Keys.reminders]), HistoryCodec.decode(prefs[Keys.history]))
            prefs.writeHistory(history)
            result = extra
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** The helper has added reminders again, so the unreadable file and its message go. Call it off the main thread. */
    fun forgetUnreadable() {
        app.forgetUnreadable(STORE_NAME)
    }

    private fun MutablePreferences.writeReminders(reminders: List<Reminder>) {
        if (reminders.isEmpty()) remove(Keys.reminders) else this[Keys.reminders] = RemindersCodec.encode(reminders)
    }

    private fun MutablePreferences.writeHistory(history: List<Played>) {
        if (history.isEmpty()) remove(Keys.history) else this[Keys.history] = HistoryCodec.encode(history)
    }

    private object Keys {
        val reminders = stringPreferencesKey("reminders")
        val history = stringPreferencesKey("history")
    }
}
