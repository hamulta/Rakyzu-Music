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
    fun authenticationEntryExposesSemanticHeadings() {
        composeRule.onNode(hasText("Rakyzu Music") and isHeading()).assertExists()

        composeRule.onNodeWithText("Sign up").performClick()

        composeRule.onNode(hasText("Join Rakyzu Music") and isHeading()).assertExists()
    }

    @Test
    fun passwordRecoveryExposesSemanticHeading() {
        composeRule.onNodeWithText("Forgot password?").performClick()

        composeRule.onNode(hasText("Reset your password") and isHeading()).assertExists()
        composeRule.onNodeWithText("Back to sign in").assertExists()
    }
}
