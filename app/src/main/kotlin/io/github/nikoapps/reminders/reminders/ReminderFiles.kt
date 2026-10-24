package io.github.nikoapps.reminders.reminders

import android.content.Context
import io.github.nikoapps.commons.files.copyIntoPlace
import io.github.nikoapps.commons.files.safeFileStem
import io.github.nikoapps.reminders.settings.deviceStorage
import java.io.File

/**
 * Each reminder's photo, a 512 pixel square JPEG, and its recorded message, a mono WAV, named
 * after its id. They live in device-protected storage, so a reminder shows its photo and plays
 * its message before the first unlock after a restart. Nothing goes to shared storage.
 *
 * Everything here reads or writes files, so call it off the main thread.
 */
object ReminderFiles {

    fun photo(context: Context, id: String): File = File(File(context.deviceStorage().filesDir, "photos"), safeFileStem(id) + ".jpg")

    fun recording(context: Context, id: String): File = File(File(context.deviceStorage().filesDir, "recordings"), safeFileStem(id) + ".wav")

    /** A new recording in the editor, not saved yet. The phone may clear these. */
    fun newRecording(context: Context, name: String): File = File(File(context.cacheDir, "recordings").apply { mkdirs() }, name)

    fun savePhoto(context: Context, id: String, cropped: File): Boolean = copyIntoPlace(cropped, photo(context, id))

    fun saveRecording(context: Context, id: String, recording: File): Boolean = copyIntoPlace(recording, recording(context, id))

    fun deletePhoto(context: Context, id: String) {
        photo(context, id).delete()
    }

    fun deleteRecording(context: Context, id: String) {
        recording(context, id).delete()
    }
}
