package io.github.nikoapps.reminders.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A time in digits, as the phone shows times: "8:00 AM" or "20:00". Numbers are digits on screen; only the voice uses words. */
@Composable
fun timeText(time: LocalTime): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "H:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, pattern), locale))
}

/** A date the way the phone writes it, such as "24 Oct 2026". */
@Composable
fun dateText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
