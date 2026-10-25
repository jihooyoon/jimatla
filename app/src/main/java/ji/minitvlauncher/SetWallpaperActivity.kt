package ji.minitvlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import ji.minitvlauncher.data.Settings
import ji.minitvlauncher.wallpaper.Wallpapers
import kotlinx.coroutines.launch

/**
 * Share target ("Set as TV wallpaper"): how pictures reach the launcher on TVs without a system
 * file picker, e.g. from a file manager. No UI beyond a toast.
 */
class SetWallpaperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.data
        if (uri == null) {
            finish()
            return
        }
        Toast.makeText(this, R.string.wall_processing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val ok = Wallpapers(applicationContext, Settings(applicationContext)).setCustom(uri)
            Toast.makeText(applicationContext, if (ok) R.string.wall_set else R.string.wall_failed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
