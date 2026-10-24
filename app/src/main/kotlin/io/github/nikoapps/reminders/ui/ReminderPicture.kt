package io.github.nikoapps.reminders.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.photo.Photos
import io.github.nikoapps.commons.theme.NikoColors
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.ReminderFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos already read from their files, so the grid and the reminder screen show them at once.
 * Each one is 512 pixels square, about 1MB, so a dozen covers every tile and a few more.
 */
object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(12)

    fun key(reminder: Reminder): String = "${reminder.id}#${reminder.photoVersion}"

    operator fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, photo: ImageBitmap) {
        cache.put(key, photo)
    }

    /** Forgets every version of one reminder's photo, after it's changed or removed. */
    fun forget(reminderId: String) {
        cache.snapshot().keys.filter { it.startsWith("$reminderId#") }.forEach(cache::remove)
    }
}

/** A photo file read off the main thread, or null while it's loading or if it can't be read. Kept in [PhotoCache] under [key]. */
@Composable
fun rememberPhoto(key: String, file: () -> File): ImageBitmap? {
    val photo by produceState(PhotoCache[key], key) {
        value = PhotoCache[key] ?: withContext(Dispatchers.IO) {
            Photos.load(file())?.asImageBitmap()?.also { PhotoCache.put(key, it) }
        }
    }
    return photo
}

/**
 * The reminder's photo, or a bell on Reminders' coral when it has none or the photo can't be
 * read. Purely a picture: the name next to it carries the meaning, so it has no TalkBack label.
 */
@Composable
fun ReminderPicture(reminder: Reminder, modifier: Modifier = Modifier) {
    if (!reminder.hasPhoto) {
        Bell(modifier)
        return
    }
    val context = LocalContext.current.applicationContext
    val photo = rememberPhoto(PhotoCache.key(reminder)) { ReminderFiles.photo(context, reminder.id) }
    if (photo == null) Bell(modifier) else Photo(photo, modifier)
}

/** A square photo with rounded corners. */
@Composable
fun Photo(photo: ImageBitmap, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        bitmap = photo,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun Bell(modifier: Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(NikoColors.Apps.Reminders),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notification),
            contentDescription = null,
            tint = NikoColors.Ink,
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}
