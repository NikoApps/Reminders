package io.github.nikoapps.reminders.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Picture
import io.github.nikoapps.commons.R as CommonsR

/**
 * The built-in pictures as a grid of big labelled tiles, two across. Tapping one picks it. The
 * helper's choice is marked with a thick border, and TalkBack says "chosen".
 */
@Composable
fun PicturePicker(chosen: Picture?, onPick: (Picture) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(
            title = stringResource(R.string.picture_title),
            backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
            backLabel = stringResource(CommonsR.string.cancel),
            onBack = onCancel,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.picture_help),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Picture.entries.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { picture ->
                        PictureTile(picture, picture == chosen, onClick = { onPick(picture) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PictureTile(picture: Picture, isChosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val name = stringResource(picture.label)
    val label = if (isChosen) stringResource(R.string.picture_chosen, name) else name
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = if (isChosen) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.onBackground)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        },
        modifier = modifier
            .sizeIn(minWidth = 96.dp, minHeight = 96.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                selected = isChosen
                onClick { onClick(); true }
            },
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BuiltInPicture(picture, Modifier.fillMaxWidth().aspectRatio(1f))
            BasicText(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
