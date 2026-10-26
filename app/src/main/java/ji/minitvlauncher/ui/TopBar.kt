package ji.minitvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ji.minitvlauncher.R
import ji.minitvlauncher.system.NetKind
import ji.minitvlauncher.system.NetStatus
import ji.minitvlauncher.ui.theme.Manrope
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx

/** One round button in the top bar. */
data class TopButton(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val warn: Boolean = false,
    val badge: String? = null,
    /** Drawn over the icon's corner, e.g. the no-Internet warning dot. */
    val alertDot: Boolean = false,
)

/**
 * Right-aligned row of round buttons; the focused one shows its label in a tooltip underneath,
 * aligned to the button's right edge so it never runs off screen.
 */
@Composable
fun TopBar(buttons: List<TopButton>, modifier: Modifier = Modifier) {
    val rightEdges = remember { HashMap<String, Int>() }
    // Focus is read as state from each button's interaction source rather than from change
    // events, which fire again (and wrongly) when a button is rebuilt with a new label.
    var focused: String? = null

    Box(modifier.fillMaxWidth().padding(top = 64.px, end = SIDE.px), contentAlignment = Alignment.TopEnd) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.px)) {
            buttons.forEach { b ->
                key(b.id) {
                    val interaction = remember { MutableInteractionSource() }
                    if (interaction.collectIsFocusedAsState().value) focused = b.id
                    RoundButton(
                        b,
                        placement = Modifier.onPlaced { rightEdges[b.id] = (it.positionInParent().x + it.size.width).toInt() },
                        interaction = interaction,
                    )
                }
            }
        }
        val current = buttons.firstOrNull { it.id == focused }
        if (current != null) {
            val rowWidth = rightEdges.values.maxOrNull() ?: 0
            val right = rightEdges[current.id] ?: rowWidth
            Text(
                current.label,
                style = Tooltip,
                color = Palette.Background,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = 80.px)
                    .layout { measurable, constraints ->
                        // Anchor the tooltip's right edge to the button's.
                        val p = measurable.measure(constraints.copy(minWidth = 0))
                        layout(p.width, p.height) { p.place(-(rowWidth - right), 0) }
                    }
                    .background(Palette.TextBright, RoundedCornerShape(10.px))
                    .padding(horizontal = 16.px, vertical = 8.px),
            )
        }
    }
}

private val Tooltip = TextStyle(fontFamily = Manrope, fontSize = 20.tx, fontWeight = FontWeight.Medium)
private val Badge = TextStyle(fontFamily = Manrope, fontSize = 15.tx, fontWeight = FontWeight.SemiBold)

@Composable
private fun RoundButton(b: TopButton, placement: Modifier, interaction: MutableInteractionSource) {
    Box(placement) {
        Surface(
            onClick = b.onClick,
            interactionSource = interaction,
            modifier = Modifier.size(64.px).semantics { contentDescription = b.label },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LocalBackdrop.current.copy(alpha = 0.55f),
                contentColor = if (b.warn) Palette.Warning else Palette.TextSecondary,
                focusedContainerColor = Palette.TextBright,
                focusedContentColor = Palette.Background,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.5.px, Palette.BorderStrong), shape = CircleShape),
                focusedBorder = Border(BorderStroke(1.5.px, Palette.TextBright), shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.size(64.px), contentAlignment = Alignment.Center) {
                Image(rememberVectorPainter(b.icon), null, Modifier.size(30.px), colorFilter = ColorFilter.tint(LocalContentColor.current))
                if (b.alertDot) {
                    Box(
                        Modifier
                            .offset(x = 9.px, y = 9.px)
                            .size(11.px)
                            .background(Palette.Warning, CircleShape),
                    )
                }
            }
        }
        if (b.badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.px, y = (-4).px)
                    .sizeIn(minWidth = 26.px, minHeight = 26.px)
                    .background(Palette.TextBright, CircleShape)
                    .border(2.px, Palette.Background, CircleShape)
                    .padding(horizontal = 7.px),
                contentAlignment = Alignment.Center,
            ) {
                Text(b.badge, style = Badge, color = Palette.Background)
            }
        }
    }
}

/** Icon, warning tint and tooltip text for the network button. */
@Composable
fun networkButtonLook(status: NetStatus): Triple<ImageVector, Boolean, String> {
    val wifiName = status.name ?: stringResource(R.string.net_wifi)
    return when (status.kind) {
        NetKind.Wifi -> Triple(Icons.Wifi, false, wifiName)
        NetKind.WifiWeak -> Triple(Icons.WifiWeak, false, stringResource(R.string.net_weak, wifiName))
        NetKind.Ethernet -> Triple(Icons.Ethernet, false, stringResource(R.string.net_ethernet))
        NetKind.Offline -> Triple(Icons.WifiOff, true, stringResource(R.string.net_offline))
        NetKind.NoInternet -> Triple(Icons.WifiFaint, true, stringResource(R.string.net_no_internet, wifiName))
    }
}

internal val ScrimColor = Color(0x99050608)
