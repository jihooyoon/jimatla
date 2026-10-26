package ji.minitvlauncher.ui

import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ji.minitvlauncher.R
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.TILE_RADIUS
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx

/** Covers the whole window; for full-screen popups (picker, search). */
internal object FullScreen : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * The launcher's own photo picker, for TVs without a system one: every image the media store
 * knows, internal storage and USB drives alike, newest first.
 */
@Composable
fun ImagePicker(images: List<Uri>, thumbnail: suspend (Uri) -> ImageBitmap?, onPick: (Uri) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Popup(popupPositionProvider = FullScreen, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.fillMaxSize().background(Palette.Background).padding(start = 120.px, end = 120.px, top = 88.px),
            verticalArrangement = Arrangement.spacedBy(40.px),
        ) {
            PanelHeader(stringResource(R.string.wallpaper), stringResource(R.string.picker_title))
            if (images.isEmpty()) {
                Text(stringResource(R.string.picker_empty), style = TextStyle(fontFamily = appFont(26), fontSize = 26.tx), color = Palette.TextMuted)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(32.px),
                verticalArrangement = Arrangement.spacedBy(32.px),
                contentPadding = PaddingValues(top = 8.px, bottom = 88.px),
            ) {
                itemsIndexed(images, key = { _, uri -> uri.toString() }) { i, uri ->
                    val image by produceState<ImageBitmap?>(null, uri) { value = thumbnail(uri) }
                    val shape = RoundedCornerShape(TILE_RADIUS.px)
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Surface(
                        onClick = { onPick(uri) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusRing(focused)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.Surface),
                        interactionSource = interaction,
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(images.isNotEmpty()) { if (images.isNotEmpty()) runCatching { first.requestFocus() } }
    }
}
