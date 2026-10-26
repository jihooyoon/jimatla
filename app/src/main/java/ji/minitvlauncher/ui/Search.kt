package ji.minitvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ji.minitvlauncher.AppEntry
import ji.minitvlauncher.R
import ji.minitvlauncher.appCategory
import ji.minitvlauncher.matchesQuery
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.TILE_RADIUS
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx

/** Keys a remote may send for its microphone button; most systems handle Assist ones before apps see them. */
private val VoiceKeys = setOf(Key.Search, Key.VoiceAssist, Key.Assist)
private const val VOICE_KEY_WINDOW_MS = 3_000L

private val PillText = TextStyle(fontFamily = appFont(24), fontSize = 24.tx, fontWeight = FontWeight.Medium)
private val Query = TextStyle(fontFamily = appFont(28), fontSize = 28.tx, color = Palette.TextBright)
private val ResultTitle = TextStyle(fontFamily = appFont(26), fontSize = 26.tx, fontWeight = FontWeight.Medium)
private val NoResults = TextStyle(fontFamily = appFont(30), fontSize = 30.tx)
private val ResultLabel = TextStyle(fontFamily = appFont(22), fontSize = 22.tx, fontWeight = FontWeight.Medium)

/** The "Search" pill at the top left; the home screen slides it beside the shrunken clock. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.px),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(32.px)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LocalBackdrop.current.copy(alpha = 0.55f),
            contentColor = Palette.TextSecondary,
            focusedContainerColor = Palette.TextBright,
            focusedContentColor = Palette.Background,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.5.px, Palette.BorderStrong), shape = RoundedCornerShape(32.px)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            Modifier.padding(start = 24.px, end = 32.px).height(64.px),
            horizontalArrangement = Arrangement.spacedBy(14.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = LocalContentColor.current
            Image(rememberVectorPainter(Icons.Search), null, Modifier.size(28.px), colorFilter = ColorFilter.tint(color))
            Text(stringResource(R.string.search), style = PillText, color = color)
        }
    }
}

/**
 * Full-screen app search in its own window: type (or speak) part of a name, accents optional.
 * [voiceQuery] arrives from the system recognizer and replaces the text.
 */
@Composable
fun SearchScreen(
    apps: List<AppEntry>,
    images: AppImageSource,
    voiceQuery: String?,
    onVoice: (() -> Unit)?,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val query = field.text
    LaunchedEffect(voiceQuery) { if (voiceQuery != null) field = TextFieldValue(voiceQuery, TextRange(voiceQuery.length)) }
    val results = remember(apps, query) { apps.filter { it.matchesQuery(query) } }
    val fieldFocus = remember { FocusRequester() }
    val backdrop = LocalBackdrop.current

    // The remote's voice key opens the system Assistant over us. Close search when that happens so
    // dismissing the Assistant lands on the home screen. Only a voice key seen just before the pause
    // counts: notifications, dialogs or input switches also pause us and must not close search.
    var voiceKeyAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && SystemClock.uptimeMillis() - voiceKeyAt < VOICE_KEY_WINDOW_MS) onDismiss()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Popup(popupPositionProvider = FullScreen, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val focusManager = LocalFocusManager.current // the popup's own window
        Column(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key in VoiceKeys) voiceKeyAt = SystemClock.uptimeMillis()
                    false // let the system open the Assistant as usual
                }
                .background(backdrop)
                .padding(start = 120.px, top = 88.px),
            verticalArrangement = Arrangement.spacedBy(40.px),
        ) {
            Row(
                Modifier.padding(end = 120.px),
                horizontalArrangement = Arrangement.spacedBy(20.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onVoice != null) CircleButton(Icons.Mic, stringResource(R.string.search_voice), onVoice)
                var fieldFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .weight(1f)
                        .height(64.px)
                        .background(Palette.Surface, RoundedCornerShape(32.px))
                        .border(BorderStroke(1.5.px, if (fieldFocused) Palette.TextBright else Palette.BorderStrong), RoundedCornerShape(32.px))
                        .padding(horizontal = 28.px),
                    horizontalArrangement = Arrangement.spacedBy(16.px),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(rememberVectorPainter(Icons.Search), null, Modifier.size(28.px), colorFilter = ColorFilter.tint(Palette.TextMuted))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = Query, color = Palette.TextDim)
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it },
                            singleLine = true,
                            textStyle = Query,
                            cursorBrush = SolidColor(Palette.TextBright),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { results.singleOrNull()?.let(onLaunch) }),
                            modifier = Modifier
                                .focusRequester(fieldFocus)
                                .onFocusChanged { fieldFocused = it.isFocused }
                                // The remote's arrows leave the field once the cursor can't move further.
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val sel = field.selection
                                    val direction = when (event.key) {
                                        Key.DirectionLeft -> FocusDirection.Left.takeIf { sel.collapsed && sel.start == 0 }
                                        Key.DirectionRight -> FocusDirection.Right.takeIf { sel.collapsed && sel.end == field.text.length }
                                        Key.DirectionDown -> FocusDirection.Down
                                        Key.DirectionUp -> FocusDirection.Up
                                        else -> null
                                    }
                                    direction != null && focusManager.moveFocus(direction)
                                },
                        )
                    }
                }
                CircleButton(Icons.Close, stringResource(R.string.close), onDismiss)
            }

            val trimmed = query.trim()
            Text(
                when {
                    trimmed.isEmpty() -> stringResource(R.string.search_all, results.size)
                    results.isEmpty() -> stringResource(R.string.search_none, trimmed)
                    else -> pluralStringResource(R.plurals.search_matches, results.size, results.size)
                },
                style = if (trimmed.isNotEmpty() && results.isEmpty()) NoResults else ResultTitle,
                color = Palette.TextMuted,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(32.px),
                verticalArrangement = Arrangement.spacedBy(40.px),
                contentPadding = PaddingValues(top = 8.px, end = 72.px, bottom = 88.px),
            ) {
                items(results, key = { it.component.flattenToString() }) { app -> ResultTile(app, images) { onLaunch(app) } }
            }
        }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.px),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.TextSecondary,
            focusedContainerColor = Palette.TextBright,
            focusedContentColor = Palette.Background,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.5.px, Palette.BorderStrong), shape = CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(rememberVectorPainter(icon), label, Modifier.size(30.px), colorFilter = ColorFilter.tint(LocalContentColor.current))
        }
    }
}

@Composable
private fun ResultTile(app: AppEntry, images: AppImageSource, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(TILE_RADIUS.px)
    val fill = app.appCategory.fill
    Column(verticalArrangement = Arrangement.spacedBy(16.px)) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(320.px, 180.px).focusRing(focused),
            shape = CardDefaults.shape(shape),
            colors = CardDefaults.colors(containerColor = fill, focusedContainerColor = fill),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
            interactionSource = interaction,
        ) {
            TileImage(app, images)
        }
        Text(app.label, style = ResultLabel, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(320.px))
    }
}
