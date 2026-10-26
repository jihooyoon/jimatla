package ji.minitvlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import ji.minitvlauncher.R

/*
 * The design is drawn on a 1920×1080 px canvas; Android TV reports 960×540 dp,
 * so one design pixel is half a dp. Write sizes as in the design: 120.px, 24.tx.
 */
val Number.px: Dp get() = (toFloat() / 2f).dp
val Number.tx: TextUnit get() = (toFloat() / 2f).sp

/** Corner radius of every app/picture tile, focused or not (design: 16 px = 8 dp). */
const val TILE_RADIUS = 16

object Palette {
    val Background = Color(0xFF0E0F11)
    val Panel = Color(0xFF17181C)
    val PanelEdge = Color(0xFF26272B)
    val Surface = Color(0xFF1F2025)
    val SurfaceSelected = Color(0xFF24262B)
    val Border = Color(0xFF2E3035)
    val BorderStrong = Color(0xFF3A3B3F)

    val TextBright = Color(0xFFF4F2EE)
    val Text = Color(0xFFE8E6E1)
    val TextSecondary = Color(0xFFC9C7C1)
    val TextMuted = Color(0xFF9A9892)
    val TextDim = Color(0xFF8A8882)

    val Warning = Color(0xFFE8A04B)
    val Ok = Color(0xFF9FD3B5)
}

/*
 * Google Sans Flex, one variable file (wght 300–700, opsz 14–128). Browsers pick the optical size
 * from the font size; Android doesn't, so each text size gets its own family with opsz = size.
 */
private val families = HashMap<Pair<Int, Int>, FontFamily>()

private fun flex(weight: Int, opsz: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz.toFloat())),
)

/** The UI face at [sizePx] design pixels, in every weight the design uses. */
fun appFont(sizePx: Int): FontFamily {
    val opsz = sizePx.coerceIn(14, 128)
    return families.getOrPut(0 to opsz) { FontFamily((300..700 step 100).map { flex(it, opsz) }) }
}

/** A single weight at a given optical size, for the clock's continuous weight change. */
fun appFont(weight: Int, opsz: Int): FontFamily =
    families.getOrPut(weight to opsz.coerceIn(14, 128)) { FontFamily(flex(weight, opsz.coerceIn(14, 128))) }

object Type {
    val RowTitle = TextStyle(fontFamily = appFont(30), fontSize = 30.tx, lineHeight = 40.tx, fontWeight = FontWeight.Medium)
    val RowMeta = TextStyle(fontFamily = appFont(22), fontSize = 22.tx)
    val TileLabel = TextStyle(fontFamily = appFont(24), fontSize = 24.tx, lineHeight = 32.tx, fontWeight = FontWeight.Medium)
    val Date = TextStyle(fontFamily = appFont(28), fontSize = 28.tx, lineHeight = 36.tx)
    val Hint = TextStyle(fontFamily = appFont(24), fontSize = 24.tx)
    val MenuItem = TextStyle(fontFamily = appFont(24), fontSize = 24.tx, fontWeight = FontWeight.Medium)
    val Pill = TextStyle(fontFamily = appFont(22), fontSize = 22.tx)
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = Palette.Background, surface = Palette.Surface),
        content = content,
    )
}
