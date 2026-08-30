package my.id.rakyzumusic.feature.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutSpecTest {
    @Test
    fun standardWidthAndFontScaleKeepDenseShelfLayout() {
        val spec = resolveHomeLayoutSpec(availableWidth = 390.dp, fontScale = 1f)

        assertEquals(HomeLayoutSpec.Standard, spec)
        assertFalse(spec.useStackedFeaturedCard)
    }

    @Test
    fun compactScreenUsesReducedPaddingAndWiderCards() {
        val spec = resolveHomeLayoutSpec(availableWidth = 320.dp, fontScale = 1f)

        assertEquals(16.dp, spec.horizontalPadding)
        assertEquals(220.dp, spec.trackCardWidth)
        assertEquals(2, spec.trackTextMaxLines)
        assertTrue(spec.useStackedFeaturedCard)
    }

    @Test
    fun largeTextTriggersAdaptiveLayoutAtBreakpoint() {
        val spec = resolveHomeLayoutSpec(availableWidth = 390.dp, fontScale = 1.3f)

        assertEquals(20.dp, spec.horizontalPadding)
        assertEquals(220.dp, spec.trackCardWidth)
        assertEquals(2, spec.trackTextMaxLines)
        assertTrue(spec.useStackedFeaturedCard)
    }
}
