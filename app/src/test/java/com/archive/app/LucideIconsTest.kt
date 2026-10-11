package com.archive.app

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import com.archive.app.ui.design.lucide.LucideIcons
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the generated Lucide icon set: every icon must build from its
 * upstream SVG geometry (a bad path string would yield zero nodes, which
 * would silently render an empty icon).
 */
class LucideIconsTest {

    private val all: Map<String, ImageVector> = mapOf(
        "back" to LucideIcons.back,
        "arrowUp" to LucideIcons.arrowUp,
        "chevronForward" to LucideIcons.chevronForward,
        "expander" to LucideIcons.expander,
        "close" to LucideIcons.close,
        "search" to LucideIcons.search,
        "link" to LucideIcons.link,
        "externalLink" to LucideIcons.externalLink,
        "home" to LucideIcons.home,
        "discover" to LucideIcons.discover,
        "favorites" to LucideIcons.favorites,
        "favoritesFill" to LucideIcons.favoritesFill,
        "alarm" to LucideIcons.alarm,
        "messages" to LucideIcons.messages,
        "settings" to LucideIcons.settings,
        "info" to LucideIcons.info,
        "help" to LucideIcons.help,
        "folder" to LucideIcons.folder,
        "downloads" to LucideIcons.downloads,
        "download" to LucideIcons.download,
        "tasks" to LucideIcons.tasks,
        "delete" to LucideIcons.delete,
        "refresh" to LucideIcons.refresh,
        "gridView" to LucideIcons.gridView,
        "listView" to LucideIcons.listView,
        "play" to LucideIcons.play,
        "collapse" to LucideIcons.collapse,
        "expand" to LucideIcons.expand,
        "check" to LucideIcons.check,
        "pause" to LucideIcons.pause,
        "mine" to LucideIcons.mine,
        "forward" to LucideIcons.forward,
        "ellipsis" to LucideIcons.ellipsis,
    )

    @Test
    fun `every lucide icon has real geometry`() {
        all.forEach { (name, icon) ->
            val paths = icon.root.filterIsInstance<VectorPath>()
            assertTrue("$name has no path nodes", paths.isNotEmpty())
            assertTrue("$name has an empty path", paths.all { it.pathData.isNotEmpty() })
        }
    }

    @Test
    fun `all icons share the 24x24 lucide viewport`() {
        all.forEach { (name, icon) ->
            assertTrue("$name viewportWidth=${icon.viewportWidth}", icon.viewportWidth == 24f)
            assertTrue("$name viewportHeight=${icon.viewportHeight}", icon.viewportHeight == 24f)
            assertTrue("$name defaultWidth=${icon.defaultWidth}", icon.defaultWidth.value == 24f)
        }
    }
}
