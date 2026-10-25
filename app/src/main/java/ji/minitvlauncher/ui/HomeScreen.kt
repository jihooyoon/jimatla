package ji.minitvlauncher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import ji.minitvlauncher.AppEntry
import ji.minitvlauncher.AppImage
import ji.minitvlauncher.HomeRow
import ji.minitvlauncher.R
import ji.minitvlauncher.RowKey
import ji.minitvlauncher.appCategory
import ji.minitvlauncher.ui.theme.Manrope
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/** Where the home screen gets card images; the app's repository on device, fakes in tests. */
interface AppImageSource {
    fun cachedImage(entry: AppEntry): AppImage?
    suspend fun loadImage(entry: AppEntry, widthPx: Int, heightPx: Int): AppImage?
}

// Layout from the design, in 1920×1080 design pixels.
private const val VIEWPORT_TOP = 160
private const val FIRST_ROW_TOP = 370 // inside the viewport; the focused row is scrolled here
private const val COLLAPSE_DISTANCE = 220 // scroll over which the clock shrinks
private const val SIDE = 120
private const val TILE_W = 320
private const val TILE_H = 180
private const val FOCUSED_SCALE = 352f / TILE_W

/** Bumped by the activity when Home is pressed while already home. */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    images: AppImageSource,
    onLaunch: (AppEntry) -> Unit,
    homePresses: Int = 0,
    now: () -> Date = ::Date,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { COLLAPSE_DISTANCE.px.toPx() }
    // Smoothstep of scroll progress; read only inside draw/layer blocks so scrolling never recomposes.
    val collapse = {
        val p = (scroll.value / collapseDistancePx).coerceIn(0f, 1f)
        p * p * (3 - 2 * p)
    }
    val firstTile = remember { FocusRequester() }
    val firstRowWithApps = rows.indexOfFirst { it.apps.isNotEmpty() }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        RowsViewport(rows, images, onLaunch, scroll, firstTile, firstRowWithApps)
        ViewportEdges(collapse)
        Clock(collapse, now)
    }

    LaunchedEffect(firstRowWithApps >= 0, homePresses) {
        if (firstRowWithApps < 0) return@LaunchedEffect
        if (homePresses > 0) scroll.animateScrollTo(0)
        runCatching { firstTile.requestFocus() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Keeps the focused tile at least [padPx] away from either edge of its row. */
@OptIn(ExperimentalFoundationApi::class)
private class EdgePaddingSpec(private val padPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - padPx
        val trailing = offset + size - (containerSize - padPx)
        return when {
            leading < 0 -> leading
            trailing > 0 -> minOf(trailing, leading)
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowsViewport(
    rows: List<HomeRow>,
    images: AppImageSource,
    onLaunch: (AppEntry) -> Unit,
    scroll: ScrollState,
    firstTile: FocusRequester,
    firstRowWithApps: Int,
) {
    val scope = rememberCoroutineScope()
    val rowTops = remember { HashMap<RowKey, Int>() }
    val rowSpec = with(LocalDensity.current) { remember(this) { EdgePaddingSpec(SIDE.px.toPx()) } }

    // The design scrolls whole rows: the focused row always sits where the first row starts.
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        Column(
            Modifier
                .padding(top = VIEWPORT_TOP.px)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = FIRST_ROW_TOP.px, bottom = 360.px),
            // 72 between a row's labels and the next title, minus the 18 the strip keeps for the focused tile.
            verticalArrangement = Arrangement.spacedBy(54.px),
        ) {
            rows.forEachIndexed { index, row ->
                key(row.key) {
                    AppRow(
                        row = row,
                        images = images,
                        onLaunch = onLaunch,
                        rowSpec = rowSpec,
                        firstTile = if (index == firstRowWithApps) firstTile else null,
                        onFocused = {
                            rowTops[row.key]?.let { top ->
                                scope.launch { scroll.animateScrollTo(top, tween(280, easing = FastOutSlowInEasing)) }
                            }
                        },
                        modifier = Modifier.onPlaced { rowTops[row.key] = it.positionInParent().y.toInt() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    row: HomeRow,
    images: AppImageSource,
    onLaunch: (AppEntry) -> Unit,
    rowSpec: BringIntoViewSpec,
    firstTile: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val active = focusedIndex >= 0
    val count = row.apps.size

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.px)) {
        Row(Modifier.padding(horizontal = SIDE.px), horizontalArrangement = Arrangement.spacedBy(20.px)) {
            Text(
                text = when (val key = row.key) {
                    RowKey.Favorites -> stringResource(R.string.row_favorites)
                    is RowKey.Category -> stringResource(key.category.title)
                },
                style = Type.RowTitle,
                color = if (active) Palette.TextBright else Palette.TextMuted,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = if (active) stringResource(R.string.row_position, focusedIndex + 1, count)
                else pluralStringResource(R.plurals.row_app_count, count, count),
                style = Type.RowMeta,
                color = Palette.TextDim,
                modifier = Modifier.alignByBaseline(),
            )
        }

        if (row.apps.isEmpty()) {
            EmptyFavorites()
            return@Column
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.hasFocus) focusedIndex = -1 },
                contentPadding = PaddingValues(start = SIDE.px, end = (SIDE + 88).px, top = 24.px, bottom = 18.px),
                horizontalArrangement = Arrangement.spacedBy(32.px),
            ) {
                itemsIndexed(row.apps, key = { _, app -> app.component.flattenToString() }) { index, app ->
                    AppTile(
                        app = app,
                        images = images,
                        onClick = { onLaunch(app) },
                        onFocused = {
                            focusedIndex = index
                            onFocused()
                        },
                        modifier = if (index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites() {
    val dash = with(LocalDensity.current) { 10.px.toPx() }
    Box(
        Modifier
            .padding(start = SIDE.px, top = 24.px, bottom = 18.px + 18.px + 32.px)
            .size(672.px, TILE_H.px)
            .drawBehind {
                val stroke = 2.px.toPx()
                drawRoundRect(
                    color = Palette.BorderStrong,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.px.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .padding(horizontal = 40.px),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.favorites_empty),
            style = Type.Hint,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    images: AppImageSource,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.px)
    val fill = app.appCategory.fill

    Column(Modifier.width(TILE_W.px), verticalArrangement = Arrangement.spacedBy(18.px)) {
        Card(
            onClick = onClick,
            modifier = modifier
                .size(TILE_W.px, TILE_H.px)
                .onFocusChanged { if (it.isFocused) onFocused() },
            interactionSource = interaction,
            shape = CardDefaults.shape(shape),
            colors = CardDefaults.colors(containerColor = fill, focusedContainerColor = fill),
            scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(4.px, Palette.TextBright), inset = (-4).px, shape = shape),
            ),
        ) {
            TileImage(app, images)
        }
        TileLabel(app.label, focused)
    }
}

@Composable
private fun TileImage(app: AppEntry, images: AppImageSource) {
    val density = LocalDensity.current
    val image by produceState(images.cachedImage(app), app) {
        if (value != null) return@produceState
        // Rendered at the focused size so the scaled-up tile stays sharp.
        val w = with(density) { (TILE_W * FOCUSED_SCALE).px.roundToPx() }
        value = images.loadImage(app, w, w * 9 / 16)
    }
    val current = image ?: return
    if (current.isBanner) {
        Image(current.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(current.bitmap, contentDescription = null)
        }
    }
}

/** Long labels fade out at the end; on the focused tile they scroll, fading at both edges. */
@Composable
private fun TileLabel(text: String, focused: Boolean) {
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = Type.TileLabel,
        color = if (focused) Palette.TextBright else Palette.TextMuted,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        // A marquee measures unbounded, so overflow is only meaningful while not scrolling.
        onTextLayout = { if (!focused) overflows = it.hasVisualOverflow },
        modifier = Modifier
            .width(TILE_W.px)
            .then(
                when {
                    !overflows -> Modifier
                    focused -> Modifier
                        .fadeEdges(start = 28.px, end = 28.px)
                        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 400, repeatDelayMillis = 1200)
                    else -> Modifier.fadeEdges(start = 0.dp, end = 40.px)
                },
            ),
    )
}

private fun Modifier.fadeEdges(start: Dp, end: Dp) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val s = start.toPx() / size.width
        val e = 1f - end.toPx() / size.width
        drawRect(
            Brush.horizontalGradient(
                0f to if (s > 0f) Color.Transparent else Color.Black,
                s to Color.Black,
                e to Color.Black,
                1f to if (e < 1f) Color.Transparent else Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Rows fade in under the clock and out at the bottom. Painted in the background color rather than
 * masked, so no offscreen layer is needed while scrolling; the top one only shows once scrolled,
 * which keeps it off the wallpaper.
 */
@Composable
private fun ViewportEdges(collapse: () -> Float) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(top = VIEWPORT_TOP.px)
                .fillMaxWidth()
                .height(72.px)
                .graphicsLayer { alpha = collapse() }
                .background(Brush.verticalGradient(listOf(Palette.Background, Palette.Background.copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(64.px)
                .background(Brush.verticalGradient(listOf(Palette.Background.copy(alpha = 0f), Palette.Background))),
        )
    }
}

private val ClockLarge = TextStyle(
    fontFamily = Manrope,
    fontSize = 128.tx,
    lineHeight = 128.tx,
    fontWeight = FontWeight(300),
    letterSpacing = (-4).tx,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)
private val ClockSmall = ClockLarge.copy(fontSize = 44.tx, lineHeight = 44.tx, fontWeight = FontWeight(600), letterSpacing = (-0.5).tx)

/**
 * Big clock that shrinks into the top-left corner as rows scroll up. Instead of re-laying out text at
 * every size and weight, a light large rendering and a bold small one are scaled and cross-faded.
 */
@Composable
private fun Clock(collapse: () -> Float, now: () -> Date) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale) }
    val dateFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }

    val date by produceState(now()) {
        while (true) {
            value = now()
            delay(60_000 - value.time % 60_000)
        }
    }
    val time = timeFormat.format(date)

    fun size(e: Float) = lerp(128f, 44f, e)
    fun top(e: Float) = lerp(232f, 74f, e)

    Box(Modifier.fillMaxSize()) {
        ClockText(time, ClockLarge, collapse) { e -> Triple(top(e), size(e) / 128f, 1f - e) }
        ClockText(time, ClockSmall, collapse) { e -> Triple(top(e), size(e) / 44f, e) }
        Text(
            text = dateFormat.format(date),
            style = Type.Date,
            color = Palette.TextMuted,
            modifier = Modifier.graphicsLayer {
                val e = collapse()
                translationX = SIDE.px.toPx()
                translationY = (top(e) + size(e) + 14f - e * 16f).px.toPx()
                alpha = (1f - e * 2.2f).coerceAtLeast(0f)
            },
        )
    }
}

@Composable
private fun ClockText(time: String, style: TextStyle, collapse: () -> Float, place: (Float) -> Triple<Float, Float, Float>) {
    Text(
        text = time,
        style = style,
        color = Palette.Text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            val (top, scale, a) = place(collapse())
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = SIDE.px.toPx()
            translationY = top.px.toPx()
            scaleX = scale
            scaleY = scale
            alpha = a
        },
    )
}
