package ji.minitvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COLUMNS = 5
private val CARD_WIDTH = 160.dp

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppRepository
    private var apps by mutableStateOf<List<AppEntry>?>(null)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(applicationContext)
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BackHandler { /* Home stays put */ }
                HomeScreen(apps, repository, ::launch)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun reload() {
        Thread { repository.loadApps().also { runOnUiThread { apps = it } } }.start()
    }

    private fun launch(entry: AppEntry) {
        try {
            startActivity(repository.launchIntent(entry))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
            reload()
        } catch (_: SecurityException) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun HomeScreen(apps: List<AppEntry>?, repository: AppRepository, onLaunch: (AppEntry) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111318))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Text(
            stringResource(R.string.apps),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when {
            apps == null -> Unit
            apps.isEmpty() -> Text(stringResource(R.string.no_apps), color = Color.White)
            else -> AppGrid(apps, repository, onLaunch)
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, repository: AppRepository, onLaunch: (AppEntry) -> Unit) {
    val firstItem = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(apps, key = { it.component.flattenToString() }) { entry ->
            val modifier = if (entry == apps.first()) Modifier.focusRequester(firstItem) else Modifier
            AppCard(entry, repository, onLaunch, modifier)
        }
    }
    LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
}

@Composable
private fun AppCard(entry: AppEntry, repository: AppRepository, onLaunch: (AppEntry) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val image by produceState(repository.cachedImage(entry), entry) {
        if (value != null) return@produceState
        val w = with(density) { CARD_WIDTH.roundToPx() }
        value = withContext(Dispatchers.IO) { runCatching { repository.loadImage(entry, w, w * 9 / 16) }.getOrNull() }
    }

    Card(
        onClick = { onLaunch(entry) },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        colors = CardDefaults.colors(containerColor = Color(0xFF2A2E37)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val (bitmap, isBanner) = image ?: (null to false)
            when {
                bitmap != null && isBanner ->
                    Image(bitmap, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap != null) Image(bitmap, null)
                    Text(
                        entry.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
