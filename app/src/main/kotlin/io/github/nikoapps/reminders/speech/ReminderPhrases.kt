package io.github.nikoapps.reminders.speech

import java.util.Locale

/**
 * The sentences the phone's voice says, hand-written as whole sentences for each language, as in
 * Niko Clock and Dialer. Names are typed by the helper in any script and go in as they are. No
 * numbers, so the voice never reads digits in the wrong language.
 */
data class ReminderPhrases(
    val locale: Locale,
    /** Said when a reminder has no recording: "It's time: White pill." */
    private val timeFor: (String) -> String,
    /** Said after Done is pressed. */
    val done: String,
    /** A pretend reminder for the voice test in settings. */
    val testName: String,
) {
    fun reminder(name: String): String = timeFor(name.trim().trimEnd('.', '।', '۔'))
}

/**
 * The voice language choices in settings, with null for "Same as phone", in the same order and
 * with the same tags as Niko Dialer. Each has its sentences in [phrasesFor]; add a language
 * here only together with them.
 */
val VOICE_LANGUAGES: List<String?> = listOf(null, "en", "ne-NP", "hi-IN", "bn", "ur", "ar", "es", "pt-BR", "fr", "id")

/**
 * The sentences for the voice language: the helper's choice, or the phone's language. Null when
 * Reminders has none for it, so it says nothing rather than reading another language's words
 * with this language's voice.
 */
fun phrasesFor(voiceLanguage: String?, phone: Locale): ReminderPhrases? {
    val locale = voiceLanguage?.let(Locale::forLanguageTag) ?: phone
    return when (locale.language) {
        "en" -> ReminderPhrases(Locale.ENGLISH, { "It's time: $it." }, "Done.", "Drink a glass of water")
        "ne" -> ReminderPhrases(Locale.forLanguageTag("ne-NP"), { "समय भयो: $it।" }, "सकियो।", "एक गिलास पानी पिउनुहोस्")
        "hi" -> ReminderPhrases(Locale.forLanguageTag("hi-IN"), { "समय हो गया है: $it।" }, "हो गया।", "एक गिलास पानी पिएँ")
        "bn" -> ReminderPhrases(Locale.forLanguageTag("bn-BD"), { "সময় হয়েছে: $it।" }, "হয়ে গেছে।", "এক গ্লাস পানি খান")
        "ur" -> ReminderPhrases(Locale.forLanguageTag("ur-PK"), { "وقت ہو گیا ہے: $it۔" }, "ہو گیا۔", "ایک گلاس پانی پیئیں")
        "ar" -> ReminderPhrases(Locale.forLanguageTag("ar"), { "حان الوقت: $it." }, "تم.", "اشرب كوبًا من الماء")
        "es" -> ReminderPhrases(Locale.forLanguageTag("es"), { "Es la hora: $it." }, "Hecho.", "Beber un vaso de agua")
        "pt" -> ReminderPhrases(Locale.forLanguageTag("pt-BR"), { "Está na hora: $it." }, "Feito.", "Beber um copo de água")
        "fr" -> ReminderPhrases(Locale.forLanguageTag("fr-FR"), { "C'est l'heure : $it." }, "C'est fait.", "Boire un verre d'eau")
        // Android still calls Indonesian "in"; newer code says "id".
        "in", "id" -> ReminderPhrases(Locale.forLanguageTag("id-ID"), { "Sudah waktunya: $it." }, "Selesai.", "Minum segelas air")
        else -> null
    }
}
