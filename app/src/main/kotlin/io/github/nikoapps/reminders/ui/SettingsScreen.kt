package io.github.nikoapps.reminders.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.ui.HealthRows
import io.github.nikoapps.commons.ui.PhoneMakerRow
import io.github.nikoapps.commons.ui.PinRows
import io.github.nikoapps.commons.ui.ProvideInfoStrings
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SettingsGroup
import io.github.nikoapps.commons.ui.SpeechSpeedRow
import io.github.nikoapps.commons.ui.TestVoiceRow
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.commons.ui.VoiceLanguageRow
import io.github.nikoapps.commons.ui.VoiceProblemRow
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.settings.Settings
import io.github.nikoapps.reminders.speech.VOICE_LANGUAGES
import java.time.LocalDate
import io.github.nikoapps.commons.R as CommonsR

/**
 * Settings for helpers, as a plain list like Android's own settings (Hub ADR 0023). The title
 * bar stays put while the list scrolls. Each row shows its current value; choices open a dialog.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    reminders: List<Reminder>,
    history: List<Played>,
    voice: VoiceState,
    voiceNeeded: Boolean,
    problems: List<HealthCheck>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onFix: (HealthCheck) -> Unit,
    onEditPlace: (Int) -> Unit,
    onTestVoice: () -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit,
    onStartSetup: () -> Unit,
    onBack: () -> Unit,
) {
    ProvideInfoStrings {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = stringResource(R.string.settings_open),
                backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
                backLabel = stringResource(CommonsR.string.back),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                // Only while something is wrong. The voice only matters while a reminder has no recording.
                val voiceProblem = voiceNeeded && voice.voice.isProblem
                if (problems.isNotEmpty() || voiceProblem) {
                    SettingsGroup(stringResource(R.string.section_needs_attention)) {
                        if (voiceProblem) VoiceProblemRow(voice.voice)
                        HealthRows(problems, onFix)
                    }
                }

                SettingsGroup(stringResource(R.string.section_reminders)) {
                    ReminderRows(reminders, settings.remindersOnScreen, onEditPlace)
                }

                SettingsGroup(stringResource(R.string.section_screen)) {
                    RemindersOnScreenRow(settings, reminders, onUpdate)
                    ThemeRow(settings, onUpdate)
                }

                SettingsGroup(stringResource(R.string.section_sound)) {
                    VolumeRow(settings, onUpdate)
                    KeepRemindingRow(settings, onUpdate)
                    VibrateRow(settings, onUpdate)
                }

                SettingsGroup(stringResource(R.string.section_history)) {
                    HistoryRows(history, LocalDate.now())
                }

                SettingsGroup(stringResource(R.string.section_voice)) {
                    VoiceLanguageRow(VOICE_LANGUAGES, settings.voiceLanguage) { tag -> onUpdate { it.copy(voiceLanguage = tag) } }
                    SpeechSpeedRow(settings.speechSpeed) { speed -> onUpdate { it.copy(speechRate = speed.rate) } }
                    TestVoiceRow(voice.engine, onTestVoice)
                }

                SettingsGroup(stringResource(R.string.section_helper)) {
                    PinRows(settings.helperPinHash != null, stringResource(R.string.info_pin), onSetPin, onRemovePin)
                    ValueRow(
                        title = stringResource(R.string.setup_again),
                        value = stringResource(R.string.setup_again_value),
                        trailing = painterResource(CommonsR.drawable.ic_chevron_right),
                        onClick = onStartSetup,
                    )
                    PhoneMakerRow(stringResource(R.string.info_phone_maker))
                }
            }
        }
    }
}
