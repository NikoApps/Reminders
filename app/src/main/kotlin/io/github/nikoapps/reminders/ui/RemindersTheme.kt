package io.github.nikoapps.reminders.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import io.github.nikoapps.commons.theme.NikoAccent
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.reminders.settings.ThemeChoice

/**
 * Niko's theme with Reminders' coral, light or dark as the helper chose. The status bar and
 * navigation bar icons follow it, not the phone, so they stay readable when a helper picks Light
 * on a dark phone or Dark on a light one.
 */
@Composable
fun RemindersTheme(activity: ComponentActivity, theme: ThemeChoice?, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
        ThemeChoice.Phone, null -> isSystemInDarkTheme()
    }
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
    NikoTheme(darkTheme = dark, accent = NikoAccent.Reminders) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
