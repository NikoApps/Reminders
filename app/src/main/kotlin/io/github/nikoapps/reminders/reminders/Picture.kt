package io.github.nikoapps.reminders.reminders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.nikoapps.reminders.R

/**
 * Built-in pictures for a reminder without a photo, so it can be recognised without reading. A
 * photo of the real thing still works best, so a photo always replaces the picture.
 *
 * [key] is what's stored and must never change once released. [background] is the colour
 * behind the picture, from the Niko app colours, so pictures that sit side by side differ.
 */
enum class Picture(val key: String, @DrawableRes val drawable: Int, @StringRes val label: Int, val background: Long) {
    Pill("pill", R.drawable.picture_pill, R.string.picture_pill, 0xFFFF9A93),
    Bottle("bottle", R.drawable.picture_bottle, R.string.picture_bottle, 0xFFFFC7C2),
    EyeDrops("eye_drops", R.drawable.picture_eye_drops, R.string.picture_eye_drops, 0xFF9CC8FF),
    Injection("injection", R.drawable.picture_injection, R.string.picture_injection, 0xFFFF9A93),
    Water("water", R.drawable.picture_water, R.string.picture_water, 0xFF9CC8FF),
    Meal("meal", R.drawable.picture_meal, R.string.picture_meal, 0xFFFFB067),
    Prayer("prayer", R.drawable.picture_prayer, R.string.picture_prayer, 0xFFC9B8FF),
    Call("call", R.drawable.picture_call, R.string.picture_call, 0xFF7BE3A6),
    Doctor("doctor", R.drawable.picture_doctor, R.string.picture_doctor, 0xFFFFC7C2),
    Walk("walk", R.drawable.picture_walk, R.string.picture_walk, 0xFF7BE3A6),
    Sleep("sleep", R.drawable.picture_sleep, R.string.picture_sleep, 0xFFC9B8FF),
    Wash("wash", R.drawable.picture_wash, R.string.picture_wash, 0xFF9CC8FF),
    ;

    companion object {
        fun of(key: String?): Picture? = entries.firstOrNull { it.key == key }
    }
}
