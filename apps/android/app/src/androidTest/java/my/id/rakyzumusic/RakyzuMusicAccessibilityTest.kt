package my.id.rakyzumusic

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RakyzuMusicAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun topLevelNavigationExposesSemanticHeadings() {
        composeRule.onNode(hasText("Good evening") and isHeading()).assertExists()

        composeRule.onNodeWithText("Search").performClick()

        composeRule.onNode(hasText("Search") and isHeading()).assertExists()
    }
}
