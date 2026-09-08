package my.id.rakyzumusic.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutSpecTest {
    @Test
    fun standardScreenRetainsDenseLayout() {
        assertEquals(
            LibraryLayoutSpec.Standard,
            resolveLibraryLayoutSpec(availableWidth = 390.dp, fontScale = 1f),
        )
    }

    @Test
    fun compactScreenReducesPaddingAndAllowsTwoTextLines() {
        val spec = resolveLibraryLayoutSpec(availableWidth = 320.dp, fontScale = 1f)

        assertEquals(16.dp, spec.horizontalPadding)
        assertEquals(72.dp, spec.rowMinimumHeight)
        assertEquals(2, spec.textMaxLines)
    }

    @Test
    fun largeTextIncreasesRowAndArtworkGeometry() {
        val spec = resolveLibraryLayoutSpec(availableWidth = 390.dp, fontScale = 1.3f)

        assertEquals(88.dp, spec.rowMinimumHeight)
        assertEquals(56.dp, spec.artworkSize)
        assertEquals(2, spec.textMaxLines)
    }
}
