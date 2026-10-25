package ji.minitvlauncher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeRowsTest {
    private fun app(name: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED) =
        AppEntry(ComponentName("pkg.$name", "pkg.$name.Main"), name, isLeanback = true, category = category)

    private val movies = app("Movies", ApplicationInfo.CATEGORY_VIDEO)
    private val tv = app("TV", ApplicationInfo.CATEGORY_VIDEO)
    private val music = app("Music", ApplicationInfo.CATEGORY_AUDIO)
    private val browser = app("Browser")

    @Test
    fun `favorites come first in user order and leave their category rows`() {
        val rows = buildRows(listOf(browser, movies, music, tv), listOf(tv.component, browser.component))

        assertEquals(
            listOf(RowKey.Favorites, RowKey.Category(AppCategory.Video), RowKey.Category(AppCategory.Audio)),
            rows.map { it.key },
        )
        assertEquals(listOf(tv, browser), rows[0].apps)
        assertEquals(listOf(movies), rows[1].apps)
    }

    @Test
    fun `empty favorites row is kept and uninstalled favorites are skipped`() {
        val gone = ComponentName("pkg.gone", "pkg.gone.Main")
        val rows = buildRows(listOf(browser), listOf(gone))

        assertEquals(RowKey.Favorites, rows[0].key)
        assertEquals(emptyList<AppEntry>(), rows[0].apps)
        assertEquals(RowKey.Category(AppCategory.Other), rows[1].key)
    }

    @Test
    fun `unknown category codes fall into Other`() {
        assertEquals(AppCategory.Other, AppCategory.of(42))
    }

    @Test
    fun `moving a favorite swaps it with its neighbor and stops at the ends`() {
        val favs = listOf(tv.component, browser.component, music.component)

        assertEquals(listOf(browser.component, tv.component, music.component), favs.moved(browser.component, -1))
        assertEquals(listOf(tv.component, music.component, browser.component), favs.moved(browser.component, 1))
        assertEquals(favs, favs.moved(tv.component, -1))
        assertEquals(favs, favs.moved(music.component, 1))
        assertEquals(favs, favs.moved(movies.component, 1))
    }

    @Test
    fun `search ignores case and Vietnamese accents`() {
        assertEquals("dau tieng viet", searchKey("  Đầu Tiếng Việt "))
        val phim = app("Phim truyện")
        assertTrue(phim.matchesQuery("phim"))
        assertTrue(phim.matchesQuery("TRUYEN"))
        assertTrue(phim.matchesQuery(""))
        assertFalse(phim.matchesQuery("nhạc"))
    }
}
