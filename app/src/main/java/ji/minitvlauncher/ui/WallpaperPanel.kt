package ji.minitvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ji.minitvlauncher.R
import ji.minitvlauncher.wallpaper.WallpaperChoice
import ji.minitvlauncher.wallpaper.WallpaperPipeline
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.TILE_RADIUS
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx

private val Sub = TextStyle(fontFamily = appFont(20), fontSize = 20.tx)
private val Caption = TextStyle(fontFamily = appFont(20), fontSize = 20.tx)
private val PickLabel = TextStyle(fontFamily = appFont(22), fontSize = 22.tx, fontWeight = FontWeight.Medium)

/** Samples, no picture, the custom picture if any, and the button to pick a new one. */
@Composable
fun WallpaperPanel(
    current: WallpaperChoice,
    hasCustom: Boolean,
    thumbnail: suspend (WallpaperChoice) -> ImageBitmap?,
    onSelect: (WallpaperChoice) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    SidePanel(onDismiss) { first ->
        Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
            PanelHeader(stringResource(R.string.wall_eyebrow), stringResource(R.string.wallpaper))
            Text(stringResource(R.string.wall_hint), style = Sub, color = Palette.TextMuted)
        }
        val choices = WallpaperChoice.entries.filter { it != WallpaperChoice.Custom || hasCustom }
        // Two columns; the pick button joins the grid as a cell when a custom picture exists.
        val cells: List<WallpaperChoice?> = choices + if (hasCustom) listOf(null) else emptyList()
        Column(verticalArrangement = Arrangement.spacedBy(28.px)) {
            cells.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.px)) {
                    pair.forEach { choice ->
                        Box(Modifier.weight(1f)) {
                            if (choice == null) {
                                PickButton(stringResource(R.string.wall_replace), Modifier.fillMaxWidth().aspectRatio(16f / 9f), onPickImage)
                            } else {
                                WallpaperThumb(
                                    choice, selected = choice == current, thumbnail = thumbnail, onClick = { onSelect(choice) },
                                    modifier = if (choice == current) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            if (!hasCustom) PickButton("+  " + stringResource(R.string.wall_pick), Modifier.fillMaxWidth().height(64.px), onPickImage)
        }
    }
}

@Composable
private fun WallpaperThumb(
    choice: WallpaperChoice,
    selected: Boolean,
    thumbnail: suspend (WallpaperChoice) -> ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val image by produceState<ImageBitmap?>(null, choice) { value = thumbnail(choice) }
    val shape = RoundedCornerShape(TILE_RADIUS.px)
    Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (choice == WallpaperChoice.None) Color(WallpaperPipeline.NEUTRAL) else Palette.Surface,
                focusedContainerColor = if (choice == WallpaperChoice.None) Color(WallpaperPipeline.NEUTRAL) else Palette.Surface,
            ),
            border = ClickableSurfaceDefaults.border(
                border = if (choice == WallpaperChoice.None) Border(BorderStroke(1.5.px, Palette.BorderStrong), shape = shape) else Border.None,
                focusedBorder = Border(BorderStroke(4.px, Palette.TextBright), inset = (-4).px, shape = shape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        ) {
            Box(Modifier.fillMaxSize()) {
                image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.px).size(36.px).background(Palette.TextBright, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(rememberVectorPainter(Icons.Check), null, Modifier.size(22.px), colorFilter = ColorFilter.tint(Palette.Background))
                    }
                }
            }
        }
        Text(stringResource(choice.label), style = Caption, color = if (selected) Palette.TextBright else Palette.Text)
    }
}

@Composable
private fun PickButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TILE_RADIUS.px)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.TextBright,
            focusedContentColor = Palette.Background,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.5.px, Palette.BorderStrong), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.px), contentAlignment = Alignment.Center) {
            Text(label, style = PickLabel, color = LocalContentColor.current, textAlign = TextAlign.Center)
        }
    }
}
