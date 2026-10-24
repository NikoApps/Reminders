package io.github.nikoapps.reminders.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

private val stores = mutableMapOf<String, DataStore<Preferences>>()

/**
 * The phone's device-protected storage, which can be read before the first unlock after a
 * restart. Reminders keeps everything there, its photos and recordings too, so a reminder plays
 * and shows on a locked phone after an overnight restart (Hub ADR 0036, spec decision 8).
 */
fun Context.deviceStorage(): Context = applicationContext.createDeviceProtectedStorageContext()

/**
 * Reminders' DataStore called [name], in device-protected storage. One per process. A file that
 * can't be read any more starts empty rather than crashing every alarm, but the old file is kept
 * next to it as `<name>-unreadable`, so [isUnreadable] can say so.
 */
internal fun Context.deviceDataStore(name: String): DataStore<Preferences> = synchronized(stores) {
    stores.getOrPut(name) {
        val dir = deviceStorage().filesDir
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler {
                runCatching { storeFile(dir, name).copyTo(unreadableFile(dir, name), overwrite = true) }
                emptyPreferences()
            },
            produceFile = { storeFile(dir, name) },
        )
    }
}

/** True while the copy of an unreadable store file is kept. Reads a file, so call it off the main thread. */
internal fun Context.isUnreadable(name: String): Boolean = unreadableFile(deviceStorage().filesDir, name).exists()

/** The helper has added reminders again, so the unreadable copy and its message go. Call it off the main thread. */
internal fun Context.forgetUnreadable(name: String) {
    unreadableFile(deviceStorage().filesDir, name).delete()
}

private fun storeFile(filesDir: File, name: String) = File(filesDir, "datastore/$name.preferences_pb")

private fun unreadableFile(filesDir: File, name: String) = File(filesDir, "datastore/$name-unreadable.preferences_pb")
