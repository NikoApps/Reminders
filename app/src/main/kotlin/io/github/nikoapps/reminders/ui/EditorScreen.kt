package io.github.nikoapps.reminders.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nikoapps.commons.photo.PhotoCropScreen
import io.github.nikoapps.commons.photo.Photos
import io.github.nikoapps.commons.ui.ChoiceRow
import io.github.nikoapps.commons.ui.ProvideInfoStrings
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SolidButton
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.EditorProblem
import io.github.nikoapps.reminders.reminders.Picture
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.ReminderFiles
import io.github.nikoapps.reminders.reminders.RepeatKind
import io.github.nikoapps.reminders.reminders.Repeat
import io.github.nikoapps.reminders.reminders.editorProblems
import io.github.nikoapps.reminders.reminders.kind
import io.github.nikoapps.reminders.reminders.placeChoices
import io.github.nikoapps.reminders.reminders.repeatOf
import io.github.nikoapps.reminders.reminders.sameTime
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import io.github.nikoapps.commons.R as CommonsR

/** The days of the week in the order the phone's calendar starts them. */
private fun weekDays(locale: Locale): List<DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}

/**
 * Adds a reminder at [place], or changes the one already there: photo, recorded message, name,
 * time, days and place. This helper screen may scroll; the main screen never does.
 *
 * [onSave] gets the edit only once it's valid. A new photo or recording is made here and only
 * kept once the reminder is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    reminders: List<Reminder>,
    place: Int,
    remindersOnScreen: Int,
    onSave: (ReminderEdit) -> Unit,
    onRemove: (Reminder) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val existing = remember(reminders, place) { reminders.firstOrNull { it.place == place } }
    // Kept by id, so the screen still knows what it's editing if it's moved while open.
    val editingId by rememberSaveable { mutableStateOf(existing?.id) }

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var minuteOfDay by rememberSaveable { mutableIntStateOf(existing?.time?.let { it.hour * 60 + it.minute } ?: DEFAULT_MINUTE) }
    var repeatKind by rememberSaveable { mutableStateOf(existing?.repeat?.kind() ?: RepeatKind.EveryDay) }
    var dayNumbers by rememberSaveable {
        mutableStateOf((existing?.repeat as? Repeat.Days)?.days?.map { it.value }?.sorted() ?: emptyList())
    }
    var dateEpochDay by rememberSaveable {
        mutableLongStateOf((existing?.repeat as? Repeat.Once)?.date?.toEpochDay() ?: LocalDate.now().toEpochDay())
    }
    var chosenPlace by rememberSaveable { mutableIntStateOf(place) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var placeTaken by rememberSaveable { mutableStateOf(false) }
    // A new photo, cropped and waiting in the cache until Save.
    var newPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    // A new recording, waiting in the cache until Save, or the saved one marked to go.
    var newRecording by rememberSaveable { mutableStateOf<String?>(null) }
    var recordingRemoved by rememberSaveable { mutableStateOf(false) }
    // The photo being cropped. While set, the crop screen shows instead of the form.
    var cropSource by rememberSaveable { mutableStateOf<String?>(null) }
    var photoMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    // A built-in picture, for a reminder without a photo. Choosing one removes the photo, and a new photo removes it.
    var pictureKey by rememberSaveable { mutableStateOf(existing?.picture?.key) }
    var pickingPicture by rememberSaveable { mutableStateOf(false) }

    // Once Save hands the new photo and recording over, the view model moves them into place.
    var saved by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Leaving the editor: the camera's picture and anything not saved go, so they don't pile up
    // in the cache. While the activity is only being made again, such as when the phone turns,
    // they stay, because the editor comes back with them.
    DisposableEffect(Unit) {
        onDispose {
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@onDispose
            listOfNotNull(newPhoto.takeUnless { saved }, newRecording.takeUnless { saved }).forEach { File(it).delete() }
            Photos.deleteCameraPicture(context)
        }
    }

    val editing = reminders.firstOrNull { it.id == editingId }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val days = dayNumbers.map(DayOfWeek::of).toSet()
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val repeat = repeatOf(repeatKind, days, date)
    val problems = editorProblems(name, repeatKind, days, date, time, LocalDateTime.now())
    val choices = remember(reminders, editingId, remindersOnScreen) {
        (placeChoices(reminders, editingId, remindersOnScreen) + place).distinct().sorted()
    }
    val clashes = sameTime(reminders, editingId, time, repeat, LocalDate.now())

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cropSource = Photos.cameraPicture(context).toString()
    }
    // Uses the phone's photo picker, or the older document picker on phones without it. Neither needs a permission.
    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropSource = uri.toString()
    }
    val hasPhoto = newPhoto != null || (!photoRemoved && editing?.hasPhoto == true)
    val picture = Picture.of(pictureKey)

    val save = {
        when {
            problems.isNotEmpty() -> showProblems = true
            chosenPlace !in choices -> placeTaken = true
            else -> {
                saved = true
                onSave(
                    ReminderEdit(
                        id = editingId,
                        name = name.trim(),
                        time = time,
                        repeat = repeat,
                        place = chosenPlace,
                        photo = when {
                            newPhoto != null -> FileEdit.Replace(newPhoto!!)
                            photoRemoved -> FileEdit.Remove
                            else -> FileEdit.Keep
                        },
                        picture = picture,
                        recording = when {
                            newRecording != null -> FileEdit.Replace(newRecording!!)
                            recordingRemoved -> FileEdit.Remove
                            else -> FileEdit.Keep
                        },
                    ),
                )
                onBack()
            }
        }
    }

    cropSource?.let { source ->
        PhotoCropScreen(
            source = source,
            title = stringResource(R.string.crop_title),
            previewLabel = stringResource(R.string.crop_preview),
            onCropped = { file ->
                newPhoto?.let { File(it).delete() }
                newPhoto = file.path
                photoRemoved = false
                photoMessage = null
                pictureKey = null
                cropSource = null
            },
            onCancel = { cropSource = null },
            onUnreadable = {
                photoMessage = R.string.editor_photo_unreadable
                cropSource = null
            },
        )
        return
    }

    if (pickingPicture) {
        PicturePicker(
            chosen = picture,
            onPick = {
                pictureKey = it.key
                newPhoto?.let { path -> File(path).delete() }
                newPhoto = null
                photoRemoved = editing?.hasPhoto == true
                photoMessage = null
                pickingPicture = false
            },
            onCancel = { pickingPicture = false },
        )
        return
    }

    ProvideInfoStrings {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = stringResource(if (editing == null) R.string.editor_add_title else R.string.editor_change_title),
                backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
                backLabel = stringResource(CommonsR.string.back),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                val side = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)

                // Photo
                Heading(stringResource(R.string.editor_photo), side)
                Note(stringResource(R.string.editor_photo_how), modifier = side)
                EditorPhoto(editing = editing, newPhoto = newPhoto, removed = photoRemoved, picture = picture, modifier = side)
                photoMessage?.let { Note(stringResource(it), warning = true, modifier = side) }
                SecondaryButton(
                    text = stringResource(R.string.editor_photo_take),
                    onClick = {
                        try {
                            takePhoto.launch(Photos.cameraUri(context))
                        } catch (_: ActivityNotFoundException) {
                            photoMessage = R.string.editor_no_camera_app
                        }
                    },
                    modifier = side,
                )
                SecondaryButton(
                    text = stringResource(R.string.editor_photo_choose),
                    onClick = {
                        try {
                            choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (_: ActivityNotFoundException) {
                            photoMessage = R.string.editor_no_photo_app
                        }
                    },
                    modifier = side,
                )
                SecondaryButton(
                    text = stringResource(R.string.editor_picture_choose),
                    onClick = { pickingPicture = true },
                    modifier = side,
                )
                if (picture != null && !hasPhoto) {
                    SecondaryButton(
                        text = stringResource(R.string.editor_picture_remove),
                        onClick = { pictureKey = null },
                        modifier = side,
                    )
                }
                if (hasPhoto) {
                    SecondaryButton(
                        text = stringResource(R.string.editor_photo_remove),
                        onClick = {
                            newPhoto?.let { File(it).delete() }
                            newPhoto = null
                            photoRemoved = true
                        },
                        modifier = side,
                    )
                }

                // Message
                val savedClip = editing?.takeIf { it.hasRecording && !recordingRemoved }?.let { ReminderFiles.recording(context, it.id) }
                MessageSection(
                    clip = newRecording?.let(::File) ?: savedClip,
                    isNew = newRecording != null,
                    removed = recordingRemoved,
                    onRecorded = { file ->
                        newRecording?.let { File(it).delete() }
                        newRecording = file.path
                        recordingRemoved = false
                    },
                    onDelete = {
                        newRecording?.let { File(it).delete() }
                        newRecording = null
                        recordingRemoved = editing?.hasRecording == true
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                // Name
                Heading(stringResource(R.string.editor_name), side)
                val nameMissing = showProblems && EditorProblem.NoName in problems
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    isError = nameMissing,
                    supportingText = { Text(stringResource(if (nameMissing) R.string.editor_name_missing else R.string.editor_name_help)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = side.fillMaxWidth(),
                )

                // Time and days
                Heading(stringResource(R.string.editor_when), side)
                ValueRow(
                    title = stringResource(R.string.editor_time),
                    value = timeText(time),
                    trailing = painterResource(CommonsR.drawable.ic_chevron_right),
                    onClick = { pickingTime = true },
                )
                if (clashes.isNotEmpty()) {
                    Note(stringResource(R.string.editor_same_time, clashes.joinToString { it.name }), modifier = side)
                }
                ChoiceRow(
                    title = stringResource(R.string.editor_days),
                    options = RepeatKind.entries,
                    selected = repeatKind,
                    optionLabel = { stringResource(it.label()) },
                    onSelect = { repeatKind = it },
                    cancelLabel = stringResource(CommonsR.string.cancel),
                )
                when (repeatKind) {
                    RepeatKind.EveryDay -> Unit
                    RepeatKind.ChosenDays -> {
                        DayButtons(
                            days = weekDays(locale),
                            chosen = days,
                            locale = locale,
                            onToggle = { day ->
                                dayNumbers = (if (day in days) days - day else days + day).map { it.value }.sorted()
                            },
                            modifier = side,
                        )
                        if (showProblems && EditorProblem.NoDays in problems) Note(stringResource(R.string.editor_no_days), warning = true, modifier = side)
                    }
                    RepeatKind.Once -> {
                        ValueRow(
                            title = stringResource(R.string.editor_date),
                            value = dateText(date),
                            trailing = painterResource(CommonsR.drawable.ic_chevron_right),
                            onClick = { pickingDate = true },
                        )
                        if (showProblems && EditorProblem.Past in problems) Note(stringResource(R.string.editor_past), warning = true, modifier = side)
                    }
                }

                // Place
                if (choices.size > 1) {
                    ChoiceRow(
                        title = stringResource(R.string.editor_place),
                        options = choices,
                        selected = chosenPlace,
                        optionLabel = { placeName(it, remindersOnScreen) },
                        onSelect = {
                            chosenPlace = it
                            placeTaken = false
                        },
                        cancelLabel = stringResource(CommonsR.string.cancel),
                    )
                }
                if (placeTaken) Note(stringResource(R.string.editor_place_taken), warning = true, modifier = side)

                if (showProblems && problems.isNotEmpty()) Note(stringResource(R.string.editor_fix_first), warning = true, modifier = side)
                SolidButton(text = stringResource(R.string.editor_save), onClick = save, modifier = side.fillMaxWidth())
                if (editing != null) {
                    SecondaryButton(text = stringResource(R.string.editor_remove), onClick = { confirmRemove = true }, modifier = side)
                }
                SecondaryButton(text = stringResource(CommonsR.string.cancel), onClick = onBack, modifier = side)
            }
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = time,
            onPicked = {
                minuteOfDay = it.hour * 60 + it.minute
                pickingTime = false
            },
            onCancel = { pickingTime = false },
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = date,
            onPicked = {
                dateEpochDay = it.toEpochDay()
                pickingDate = false
            },
            onCancel = { pickingDate = false },
        )
    }
    if (confirmRemove && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.editor_remove_title, editing.name)) },
            text = { Text(stringResource(R.string.editor_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove(editing)
                }, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.editor_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.editor_remove_keep))
                }
            },
        )
    }
}

/** The picture as it will be on the tile: the new photo, the saved one, the built-in picture, or the bell. */
@Composable
private fun EditorPhoto(editing: Reminder?, newPhoto: String?, removed: Boolean, picture: Picture?, modifier: Modifier) {
    val context = LocalContext.current
    val square = Modifier.size(160.dp)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val photo = when {
            newPhoto != null -> rememberPhoto("new#$newPhoto") { File(newPhoto) }
            !removed && editing?.hasPhoto == true -> rememberPhoto(PhotoCache.key(editing)) { ReminderFiles.photo(context, editing.id) }
            else -> null
        }
        if (photo != null) {
            Photo(photo, square, contentDescription = stringResource(R.string.editor_photo_label))
        } else {
            val placeholder = Reminder("", "", LocalTime.MIDNIGHT, Repeat.EveryDay, 0, picture = picture)
            ReminderPicture(placeholder, square)
        }
    }
}

/**
 * The seven days as big buttons, two rows, each labelled with the day's short name and read in
 * full by TalkBack, with whether it's chosen.
 */
@Composable
private fun DayButtons(days: List<DayOfWeek>, chosen: Set<DayOfWeek>, locale: Locale, onToggle: (DayOfWeek) -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { day ->
                    val on = day in chosen
                    val full = day.getDisplayName(TextStyle.FULL, locale)
                    val label = stringResource(if (on) R.string.day_chosen else R.string.day_not_chosen, full)
                    val buttonModifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .semantics {
                            contentDescription = label
                            selected = on
                        }
                    if (on) {
                        androidx.compose.material3.Button(onClick = { onToggle(day) }, modifier = buttonModifier) {
                            Text(day.getDisplayName(TextStyle.SHORT, locale), textAlign = TextAlign.Center, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onToggle(day) }, modifier = buttonModifier) {
                            Text(day.getDisplayName(TextStyle.SHORT, locale), textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
                // Keeps the last row's buttons the same size as the first.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** The clock face from Material, 12-hour with part of day, with a switch to typing for helpers who prefer it (spec decision 6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var typing by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.editor_time)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (typing) TimeInput(state = state) else TimePicker(state = state)
                SecondaryButton(
                    text = stringResource(if (typing) R.string.editor_time_clock else R.string.editor_time_type),
                    onClick = { typing = !typing },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(CommonsR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(CommonsR.string.cancel)) }
        },
    )
}

/** Material's calendar for a once reminder. Days before today can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(initial, today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (picked != null) onPicked(picked) else onCancel()
                },
                modifier = Modifier.heightIn(min = 64.dp),
            ) { Text(stringResource(CommonsR.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(CommonsR.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 16.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String, modifier: Modifier, warning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth().then(if (warning) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    )
}

private fun RepeatKind.label(): Int = when (this) {
    RepeatKind.EveryDay -> R.string.repeat_every_day
    RepeatKind.ChosenDays -> R.string.repeat_chosen_days
    RepeatKind.Once -> R.string.repeat_once
}

/** 8:00 in the morning: a common first medicine time. */
private const val DEFAULT_MINUTE = 8 * 60

/** Long enough for "Blood pressure pill after breakfast", short enough to stay readable on a tile. */
private const val MAX_NAME_LENGTH = 40
