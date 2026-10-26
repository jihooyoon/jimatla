package ji.minitvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import ji.minitvlauncher.ui.theme.Manrope
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

enum class MenuAction { Open, AddFavorite, RemoveFavorite, Arrange, AppInfo }

/**
 * Long-press menu for an app tile. Placed inside the tile so the popup anchors to it: below the
 * (scaled-up) tile, or above when it would run off the bottom. Its own window takes focus, and
 * Back dismisses it.
 */
@Composable
fun AppMenu(title: String, isFavorite: Boolean, onAction: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val position = remember(density) { MenuPosition(density) }
    val first = remember { FocusRequester() }
    val items = buildList {
        add(Triple(MenuAction.Open, Icons.Play, R.string.menu_open))
        if (isFavorite) {
            add(Triple(MenuAction.Arrange, Icons.Arrange, R.string.menu_arrange))
            add(Triple(MenuAction.RemoveFavorite, Icons.StarOff, R.string.menu_remove_favorite))
        } else {
            add(Triple(MenuAction.AddFavorite, Icons.Star, R.string.menu_add_favorite))
        }
        add(Triple(MenuAction.AppInfo, Icons.Info, R.string.menu_app_info))
    }

    // The menu opens while OK is still held: its repeats and release land here, on "Open".
    // Ignore OK until a fresh press starts inside the menu.
    var armed by remember { mutableStateOf(false) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    if (event.key !in OkKeys) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                    !armed
                }
                .width(380.px)
                .background(Palette.Surface, RoundedCornerShape(20.px))
                .border(BorderStroke(1.px, Palette.Border), RoundedCornerShape(20.px))
                .padding(8.px),
            verticalArrangement = Arrangement.spacedBy(2.px),
        ) {
            Text(
                title,
                style = TextStyle(fontFamily = Manrope, fontSize = 20.tx),
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.px, end = 20.px, top = 12.px, bottom = 14.px),
            )
            items.forEachIndexed { i, (action, icon, label) ->
                MenuItem(
                    icon = icon,
                    label = stringResource(label),
                    onClick = { onAction(action) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.px)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.TextBright,
            focusedContentColor = Palette.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        val color = androidx.tv.material3.LocalContentColor.current
        Row(
            Modifier.padding(horizontal = 20.px, vertical = 16.px),
            horizontalArrangement = Arrangement.spacedBy(18.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(rememberVectorPainter(icon), null, Modifier.size(26.px), colorFilter = ColorFilter.tint(color))
            Text(label, style = Type.MenuItem, color = color)
        }
    }
}

private class MenuPosition(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = with(density) {
        val margin = 40.px.roundToPx()
        val gap = 14.px.roundToPx()
        // Anchored to the tile's own bounds, which already include its focused growth.
        val x = anchorBounds.left.coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        IntOffset(x, y)
    }
}
