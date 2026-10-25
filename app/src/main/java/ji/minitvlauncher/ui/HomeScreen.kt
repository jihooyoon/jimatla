package ji.minitvlauncher.ui

import android.content.ComponentName
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import ji.minitvlauncher.moved
import ji.minitvlauncher.system.NetKind
import ji.minitvlauncher.system.NetStatus
import ji.minitvlauncher.system.NotificationItem
import ji.minitvlauncher.system.TvInputItem
import ji.minitvlauncher.wallpaper.Backdrop
import ji.minitvlauncher.weather.Weather
import ji.minitvlauncher.wallpaper.WallpaperChoice
import ji.minitvlauncher.wallpaper.WallpaperPipeline
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/** Where the home screen gets card images; the app's repository on device, fakes in tests. */
interface AppImageSource {
    fun cachedImage(entry: AppEntry): AppImage?
    suspend fun loadImage(entry: AppEntry, widthPx: Int, heightPx: Int): AppImage?
}

/** What the home screen asks of the system; the activity on device, fakes in tests. */
interface HomeActions {
    fun launch(app: AppEntry)
    fun setFavorites(favorites: List<ComponentName>)
    fun showAppInfo(app: AppEntry)
    fun openSystem(screen: SystemScreen)
    /** Tries the system's notification panel; false if this TV has none the launcher may open. */
    fun openSystemNotifications(): Boolean
    fun switchInput(input: TvInputItem)
    fun openNotification(item: NotificationItem)
    fun clearNotifications()
    fun selectWallpaper(choice: WallpaperChoice)
    /** The system picker where there is one, else the launcher's own. */
    fun pickWallpaperImage()
    suspend fun wallpaperThumbnail(choice: WallpaperChoice): ImageBitmap?
    /** Null when the TV has no speech recognizer. */
    val voiceSearch: ((onText: (String) -> Unit) -> Unit)?
}

/** The chosen wallpaper and what to paint for it. */
data class WallpaperState(
    val choice: WallpaperChoice = WallpaperChoice.None,
    val hasCustom: Boolean = false,
    val backdrop: Backdrop = Backdrop(WallpaperPipeline.NEUTRAL),
)

/** Backdrop color of the current wallpaper, for surfaces that blend into it. */
val LocalBackdrop = staticCompositionLocalOf { Palette.Background }

enum class SystemScreen { Bluetooth, Network, Settings }

/** Live system state behind the top bar. */
data class SystemStatus(
    val network: NetStatus = NetStatus(NetKind.Wifi),
    /** Null when the launcher may not read notifications. */
    val notifications: List<NotificationItem>? = null,
    val hasSystemNotificationPanel: Boolean = false,
    val inputs: List<TvInputItem> = emptyList(),
)

private enum class Panel { Inputs, Notifications, Wallpaper }

// Layout from the design, in 1920×1080 design pixels.
private const val VIEWPORT_TOP = 160
private const val FIRST_ROW_TOP = 370 // inside the viewport; the focused row is scrolled here
private const val COLLAPSE_DISTANCE = 220 // scroll over which the clock shrinks
internal const val SIDE = 120
private const val TILE_W = 320
private const val TILE_H = 180
internal const val FOCUSED_SCALE = 352f / TILE_W
// "20:45" at 44 px semibold is about 2.7 em; the search pill sits 32 px past it once collapsed.
private const val CLOCK_SMALL_WIDTH = 119

/** Transient home-screen state: which tile has its menu open, is being moved, or should take focus. */
@Stable
private class HomeUi {
    var menuFor by mutableStateOf<ComponentName?>(null)
    var panel by mutableStateOf<Panel?>(null)
    var searching by mutableStateOf(false)
    var voiceQuery by mutableStateOf<String?>(null)
    var arranging by mutableStateOf<ComponentName?>(null)
    var pendingFocus by mutableStateOf<ComponentName?>(null)
    var toast by mutableStateOf<Int?>(null)
    var toastSerial by mutableIntStateOf(0)

    fun showToast(@StringRes text: Int) {
        toast = text
        toastSerial++
    }
}

/** [homePresses] is bumped by the activity when Home is pressed while already home. */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    images: AppImageSource,
    actions: HomeActions,
    status: SystemStatus = SystemStatus(),
    wallpaper: WallpaperState = WallpaperState(),
    weather: Weather? = null,
    homePresses: Int = 0,
    now: () -> Date = ::Date,
) {
    val ui = remember { HomeUi() }
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
    val favorites = rows.first { it.key == RowKey.Favorites }.apps.map { it.component }

    val onMenuAction: (AppEntry, MenuAction) -> Unit = { app, action ->
        val c = app.component
        ui.menuFor = null
        when (action) {
            MenuAction.Open -> actions.launch(app)
            MenuAction.AddFavorite -> {
                actions.setFavorites(favorites + c)
                ui.pendingFocus = c
                ui.showToast(R.string.toast_added_favorite)
            }
            MenuAction.RemoveFavorite -> {
                actions.setFavorites(favorites - c)
                ui.pendingFocus = c
                ui.showToast(R.string.toast_removed_favorite)
            }
            MenuAction.Arrange -> ui.arranging = c
            MenuAction.AppInfo -> actions.showAppInfo(app)
        }
    }
    val onMove: (AppEntry, Int) -> Unit = { app, delta -> actions.setFavorites(favorites.moved(app.component, delta)) }

    val backdrop = Color(wallpaper.backdrop.color)
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize().background(backdrop)) {
        BackdropImage(wallpaper.backdrop.frame, collapse)
        RowsViewport(rows, images, actions, ui, onMenuAction, onMove, scroll, firstTile, firstRowWithApps)
        ViewportEdges(collapse)
        Clock(collapse, now, weather)
        val pillShift = with(density) { (CLOCK_SMALL_WIDTH + 32).px.toPx() }
        SearchPill(
            onClick = { ui.voiceQuery = null; ui.searching = true },
            modifier = Modifier
                .padding(start = SIDE.px, top = 64.px)
                .graphicsLayer { translationX = pillShift * collapse() },
        )
        TopBar(topButtons(status, actions, ui))
        if (ui.menuFor != null) Box(Modifier.fillMaxSize().background(Color(0x59050608)))
        if (ui.panel != null) Box(Modifier.fillMaxSize().background(ScrimColor))
        BottomPill(ui, rows)
    }

    if (ui.searching) {
        val voice = actions.voiceSearch
        SearchScreen(
            apps = remember(rows) { rows.flatMap { it.apps }.sortedWith(compareBy(java.text.Collator.getInstance()) { it.label }) },
            images = images,
            voiceQuery = ui.voiceQuery,
            onVoice = voice?.let { start -> { start { text -> ui.voiceQuery = text } } },
            onLaunch = { ui.searching = false; actions.launch(it) },
            onDismiss = { ui.searching = false },
        )
    }

    when (ui.panel) {
        Panel.Wallpaper -> WallpaperPanel(
            current = wallpaper.choice,
            hasCustom = wallpaper.hasCustom,
            thumbnail = actions::wallpaperThumbnail,
            onSelect = actions::selectWallpaper,
            onPickImage = { ui.panel = null; actions.pickWallpaperImage() },
            onDismiss = { ui.panel = null },
        )
        Panel.Inputs -> InputPanel(
            status.inputs,
            onPick = { ui.panel = null; actions.switchInput(it) },
            onDismiss = { ui.panel = null },
        )
        Panel.Notifications -> NotificationPanel(
            status.notifications.orEmpty(),
            now = now().time,
            onOpen = { ui.panel = null; actions.openNotification(it) },
            onClearAll = { actions.clearNotifications() },
            onDismiss = { ui.panel = null },
        )
        null -> Unit
    }
    }

    LaunchedEffect(firstRowWithApps >= 0, homePresses) {
        if (firstRowWithApps < 0) return@LaunchedEffect
        if (homePresses > 0) {
            ui.menuFor = null
            ui.panel = null
            ui.searching = false
            ui.arranging = null
            scroll.animateScrollTo(0)
        }
        runCatching { firstTile.requestFocus() }
    }
    LaunchedEffect(ui.toastSerial) {
        delay(1800)
        ui.toast = null
    }
}

/** Inputs only on TVs that have them; the bell only when there is something it can open. */
@Composable
private fun topButtons(status: SystemStatus, actions: HomeActions, ui: HomeUi): List<TopButton> = buildList {
    if (status.inputs.isNotEmpty()) {
        add(TopButton("input", Icons.Input, stringResource(R.string.inputs), onClick = { ui.panel = Panel.Inputs }))
    }
    add(TopButton("bt", Icons.Bluetooth, stringResource(R.string.bluetooth), onClick = { actions.openSystem(SystemScreen.Bluetooth) }))
    val (netIcon, netWarn, netLabel) = networkButtonLook(status.network)
    add(
        TopButton(
            "net", netIcon, netLabel,
            onClick = { actions.openSystem(SystemScreen.Network) },
            warn = netWarn,
            alertDot = status.network.kind == NetKind.NoInternet,
        ),
    )
    val notifications = status.notifications
    if (notifications != null || status.hasSystemNotificationPanel) {
        val count = notifications?.size ?: 0
        add(
            TopButton(
                "notif", Icons.Bell,
                if (count > 0) pluralStringResource(R.plurals.notifications_new, count, count) else stringResource(R.string.notifications),
                onClick = { if (!actions.openSystemNotifications() && notifications != null) ui.panel = Panel.Notifications },
                badge = if (count > 0) (if (count > 9) "9+" else count.toString()) else null,
            ),
        )
    }
    add(TopButton("wall", Icons.Wallpaper, stringResource(R.string.wallpaper), onClick = { ui.panel = Panel.Wallpaper }))
    add(TopButton("settings", Icons.Settings, stringResource(R.string.settings), onClick = { actions.openSystem(SystemScreen.Settings) }))
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
    actions: HomeActions,
    ui: HomeUi,
    onMenuAction: (AppEntry, MenuAction) -> Unit,
    onMove: (AppEntry, Int) -> Unit,
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
                        actions = actions,
                        ui = ui,
                        onMenuAction = onMenuAction,
                        onMove = onMove,
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
    actions: HomeActions,
    ui: HomeUi,
    onMenuAction: (AppEntry, MenuAction) -> Unit,
    onMove: (AppEntry, Int) -> Unit,
    rowSpec: BringIntoViewSpec,
    firstTile: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    // Tracked by app rather than position, so the count stays right while a favorite is moved.
    var focusedApp by remember { mutableStateOf<ComponentName?>(null) }
    val focusedIndex = row.apps.indexOfFirst { it.component == focusedApp }
    val active = focusedIndex >= 0
    val count = row.apps.size
    val isFavorites = row.key == RowKey.Favorites
    val listState = rememberLazyListState()

    // A tile that should take focus must be composed first: scroll the row to it.
    LaunchedEffect(ui.pendingFocus, row.apps) {
        val index = row.apps.indexOfFirst { it.component == ui.pendingFocus }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    }

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
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.hasFocus) focusedApp = null },
                contentPadding = PaddingValues(start = SIDE.px, end = (SIDE + 88).px, top = 24.px, bottom = 18.px),
                horizontalArrangement = Arrangement.spacedBy(32.px),
            ) {
                itemsIndexed(row.apps, key = { _, app -> app.component.flattenToString() }) { index, app ->
                    val arranging = ui.arranging == app.component
                    AppTile(
                        app = app,
                        index = index,
                        images = images,
                        ui = ui,
                        isFavorite = isFavorites,
                        canMoveLeft = arranging && index > 0,
                        canMoveRight = arranging && index < count - 1,
                        onClick = { actions.launch(app) },
                        onMenuAction = { onMenuAction(app, it) },
                        onMove = { onMove(app, it) },
                        onFocused = {
                            focusedApp = app.component
                            onFocused()
                        },
                        extraFocus = if (index == 0) firstTile else null,
                        modifier = if (arranging) Modifier.zIndex(1f) else Modifier,
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

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    index: Int,
    images: AppImageSource,
    ui: HomeUi,
    isFavorite: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onMenuAction: (MenuAction) -> Unit,
    onMove: (Int) -> Unit,
    onFocused: () -> Unit,
    extraFocus: FocusRequester?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(12.px)
    val fill = app.appCategory.fill
    val arranging = ui.arranging == app.component
    val dimmed = ui.arranging != null && !arranging

    LaunchedEffect(ui.pendingFocus) {
        if (ui.pendingFocus == app.component) {
            ui.pendingFocus = null
            runCatching { focus.requestFocus() }
        }
    }
    // Moving a favorite reorders the row; keep the moved tile on screen.
    LaunchedEffect(index, arranging) { if (arranging) bringIntoView.bringIntoView() }

    Box(modifier) {
        Column(
            Modifier
                .width(TILE_W.px)
                .graphicsLayer { alpha = if (dimmed) 0.4f else 1f },
            verticalArrangement = Arrangement.spacedBy(18.px),
        ) {
            Card(
                onClick = onClick,
                onLongClick = { if (ui.arranging == null) ui.menuFor = app.component },
                modifier = Modifier
                    .size(TILE_W.px, TILE_H.px)
                    .focusRequester(focus)
                    .then(if (extraFocus != null) Modifier.focusRequester(extraFocus) else Modifier)
                    .bringIntoViewRequester(bringIntoView)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .onPreviewKeyEvent { event ->
                        val down = event.type == KeyEventType.KeyDown
                        when {
                            arranging -> {
                                when (event.key) {
                                    Key.DirectionLeft -> if (down) onMove(-1)
                                    Key.DirectionRight -> if (down) onMove(1)
                                    in ConfirmKeys -> if (!down) ui.arranging = null
                                }
                                true // nothing else happens while moving a tile
                            }
                            event.key == Key.Menu && down && ui.arranging == null -> {
                                ui.menuFor = app.component
                                true
                            }
                            else -> false
                        }
                    },
                interactionSource = interaction,
                shape = CardDefaults.shape(shape),
                colors = CardDefaults.colors(containerColor = fill, focusedContainerColor = fill),
                scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
                border = CardDefaults.border(
                    focusedBorder = Border(BorderStroke(4.px, Palette.TextBright), inset = (-4).px, shape = shape),
                ),
            ) {
                TileImage(app, images)
                if (ui.menuFor == app.component) {
                    AppMenu(app.label, isFavorite, onMenuAction, onDismiss = { ui.menuFor = null })
                }
            }
            TileLabel(app.label, focused)
        }
        // Beside the scaled-up tile (it spans -16..336), overlapping it by 12 like the design.
        if (arranging) {
            if (canMoveLeft) MoveArrow(Icons.ChevronLeft, -1, Modifier.offset(x = (-60).px, y = 62.px))
            if (canMoveRight) MoveArrow(Icons.ChevronRight, 1, Modifier.offset(x = 324.px, y = 62.px))
        }
    }
}

/** Round hint beside a tile being arranged, nudging in its direction. */
@Composable
private fun MoveArrow(icon: ImageVector, direction: Int, modifier: Modifier) {
    val nudge by rememberInfiniteTransition(label = "nudge").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "nudge",
    )
    val shift = with(LocalDensity.current) { 5.px.toPx() }
    Box(
        modifier
            .graphicsLayer { translationX = direction * shift * nudge }
            .size(56.px)
            .background(Palette.TextBright, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(28.px), colorFilter = ColorFilter.tint(Palette.Background))
    }
}

@Composable
internal fun TileImage(app: AppEntry, images: AppImageSource) {
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

/** Toasts, and the key hints while a favorite is being arranged. */
@Composable
private fun BottomPill(ui: HomeUi, rows: List<HomeRow>) {
    val arranging = ui.arranging?.let { c -> rows.firstNotNullOfOrNull { r -> r.apps.firstOrNull { it.component == c } } }
    val toast = ui.toast
    if (arranging == null && toast == null) return
    Box(Modifier.fillMaxSize().padding(bottom = 56.px), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .background(Palette.Surface, RoundedCornerShape(40.px))
                .border(BorderStroke(1.px, Palette.Border), RoundedCornerShape(40.px))
                .padding(horizontal = 32.px, vertical = 16.px),
            horizontalArrangement = Arrangement.spacedBy(32.px),
        ) {
            if (arranging != null) {
                Text(stringResource(R.string.arranging, arranging.label), style = Type.Pill, color = Palette.TextBright)
                Text(stringResource(R.string.arranging_move), style = Type.Pill, color = Palette.TextMuted)
                Text(stringResource(R.string.arranging_done), style = Type.Pill, color = Palette.TextMuted)
            } else if (toast != null) {
                Text(stringResource(toast), style = Type.Pill, color = Palette.TextBright)
            }
        }
    }
}

/**
 * The processed wallpaper in the top-right frame: fades in once decoded, and out as rows scroll up,
 * in step with the clock. Everything else about it was baked in when it was chosen.
 */
@Composable
private fun BackdropImage(frame: ImageBitmap?, collapse: () -> Float) {
    frame ?: return
    val shown = remember(frame) { Animatable(0f) }
    LaunchedEffect(frame) { shown.animateTo(1f, tween(200)) }
    Image(
        frame,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset(x = WallpaperPipeline.FRAME_LEFT.px)
            .size(WallpaperPipeline.FRAME_WIDTH.px, WallpaperPipeline.FRAME_HEIGHT.px)
            .graphicsLayer { alpha = shown.value * (1f - collapse()) },
    )
}

/**
 * Rows fade in under the clock and out at the bottom. Painted in the background color rather than
 * masked, so no offscreen layer is needed while scrolling; the top one only shows once scrolled,
 * which keeps it off the wallpaper.
 */
@Composable
private fun ViewportEdges(collapse: () -> Float) {
    val backdrop = LocalBackdrop.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(top = VIEWPORT_TOP.px)
                .fillMaxWidth()
                .height(72.px)
                .graphicsLayer { alpha = collapse() }
                .background(Brush.verticalGradient(listOf(backdrop, backdrop.copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(64.px)
                .background(Brush.verticalGradient(listOf(backdrop.copy(alpha = 0f), backdrop))),
        )
    }
}
