package ji.minitvlauncher.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ji.minitvlauncher.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import ji.minitvlauncher.system.InputSignal
import ji.minitvlauncher.system.NotificationItem
import ji.minitvlauncher.system.TvInputItem
import ji.minitvlauncher.ui.theme.Manrope
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx

private object AtRightEdge : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(windowSize.width - popupContentSize.width, 0)
}

/** 640-wide sheet sliding in from the right in its own focusable window; Back closes it. */
@Composable
fun SidePanel(onDismiss: () -> Unit, content: @Composable ColumnScope.(first: FocusRequester) -> Unit) {
    val first = remember { FocusRequester() }
    val enter = remember { Animatable(0f) }
    val slide = with(LocalDensity.current) { 40.px.toPx() }
    Popup(popupPositionProvider = AtRightEdge, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .width(640.px)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = (1f - enter.value) * slide
                    alpha = enter.value
                }
                .background(Palette.Panel)
                .drawBehind { drawLine(Palette.PanelEdge, Offset.Zero, Offset(0f, size.height), 1.px.toPx()) }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.px, vertical = 72.px),
            verticalArrangement = Arrangement.spacedBy(36.px),
        ) {
            content(first)
        }
        LaunchedEffect(Unit) {
            runCatching { first.requestFocus() }
            enter.animateTo(1f, tween(200))
        }
    }
}

private val Eyebrow = TextStyle(fontFamily = Manrope, fontSize = 20.tx, letterSpacing = 3.tx)
private val PanelTitle = TextStyle(fontFamily = Manrope, fontSize = 44.tx, fontWeight = FontWeight.Medium)
private val RowTitle = TextStyle(fontFamily = Manrope, fontSize = 26.tx, fontWeight = FontWeight.Medium)
private val RowTitleLarge = TextStyle(fontFamily = Manrope, fontSize = 28.tx, fontWeight = FontWeight.Medium)
private val RowSub = TextStyle(fontFamily = Manrope, fontSize = 20.tx)
private val RowMeta = TextStyle(fontFamily = Manrope, fontSize = 18.tx)
private val Ghost = TextStyle(fontFamily = Manrope, fontSize = 20.tx, fontWeight = FontWeight.Medium)

@Composable
fun PanelHeader(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
        Text(eyebrow.uppercase(), style = Eyebrow, color = Palette.TextDim)
        Text(title, style = PanelTitle, color = Palette.Text)
    }
}

/** List row as in the design's panels: soft fill, light when focused. */
@Composable
fun PanelRow(onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.px)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (filled) Palette.Surface else Color.Transparent,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.TextBright,
            focusedContentColor = Palette.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 24.px, vertical = 20.px), verticalArrangement = Arrangement.spacedBy(6.px), content = content)
    }
}

@Composable
fun InputPanel(inputs: List<TvInputItem>, lastInput: String?, onPick: (TvInputItem) -> Unit, onDismiss: () -> Unit) {
    SidePanel(onDismiss) { first ->
        PanelHeader(stringResource(R.string.inputs), stringResource(R.string.inputs_title))
        val focusIndex = inputs.indexOfFirst { it.id == lastInput }.coerceAtLeast(0)
        Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
            inputs.forEachIndexed { i, input ->
                val active = input.id == lastInput
                PanelRow(
                    onClick = { onPick(input) },
                    modifier = if (i == focusIndex) Modifier.focusRequester(first) else Modifier,
                    filled = active,
                ) {
                    val color = LocalContentColor.current
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.px)) {
                            Text(input.label, style = RowTitleLarge, color = color)
                            Text(
                                stringResource(
                                    when (input.signal) {
                                        InputSignal.Connected -> if (input.isPassthrough) R.string.input_connected else R.string.input_tuner
                                        InputSignal.Standby -> R.string.input_standby
                                        InputSignal.None -> R.string.input_no_signal
                                    },
                                ),
                                style = RowSub,
                                color = color.copy(alpha = 0.6f),
                            )
                        }
                        if (active) {
                            Image(
                                rememberVectorPainter(Icons.Check), stringResource(R.string.input_last_used),
                                Modifier.size(28.px), colorFilter = ColorFilter.tint(color),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPanel(
    items: List<NotificationItem>,
    now: Long,
    onOpen: (NotificationItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    SidePanel(onDismiss) { first ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            PanelHeader(
                stringResource(R.string.notifications),
                if (items.isEmpty()) stringResource(R.string.notifications_none)
                else pluralStringResource(R.plurals.notifications_new, items.size, items.size),
            )
            if (items.any { it.clearable }) {
                Surface(
                    onClick = onClearAll,
                    modifier = Modifier.height(52.px),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.px)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Palette.Text,
                        focusedContainerColor = Palette.TextBright,
                        focusedContentColor = Palette.Background,
                    ),
                    border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.5.px, Palette.BorderStrong))),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Text(
                        stringResource(R.string.notifications_clear_all),
                        style = Ghost,
                        color = LocalContentColor.current,
                        modifier = Modifier.padding(horizontal = 24.px, vertical = 13.px),
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.notifications_empty), style = TextStyle(fontFamily = Manrope, fontSize = 24.tx), color = Palette.TextDim)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
            items.forEachIndexed { i, n ->
                PanelRow(onClick = { onOpen(n) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    val color = LocalContentColor.current
                    val time = DateUtils.getRelativeTimeSpanString(n.postTime, now, DateUtils.MINUTE_IN_MILLIS).toString()
                    Text("${n.appLabel}  ·  $time", style = RowMeta, color = color.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.title.isNotEmpty()) Text(n.title, style = RowTitle, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotEmpty()) Text(n.text, style = RowSub, color = color.copy(alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
