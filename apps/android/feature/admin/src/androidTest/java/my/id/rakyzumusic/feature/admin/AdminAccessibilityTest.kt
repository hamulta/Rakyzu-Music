package my.id.rakyzumusic.feature.admin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.data.admin.AdminDashboard
import my.id.rakyzumusic.core.data.admin.StaffAccessContext
import my.id.rakyzumusic.core.data.admin.StaffPermission
import my.id.rakyzumusic.core.data.admin.StaffRole
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listenerBoundaryNeverRendersPrivilegedActions() {
        setContent(
            AdminUiState(
                isLoading = false,
                dashboard = AdminDashboard(
                    StaffAccessContext(false, null, null, false, emptySet()),
                ),
            ),
        )

        composeRule.onNodeWithText("Admin Panel unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Publish album and tracks").assertCountEquals(0)
        composeRule.onAllNodesWithText("Apply role assignment").assertCountEquals(0)
    }

    @Test
    fun CEOReceivesAccessibleFullControlActions() {
        setContent(
            AdminUiState(
                isLoading = false,
                dashboard = AdminDashboard(
                    StaffAccessContext(
                        true,
                        StaffRole.Ceo,
                        "CEO",
                        true,
                        StaffPermission.entries.toSet(),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Full organization access").assertIsDisplayed()
        composeRule.onNodeWithText("Create artist profile").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText("Record enforcement").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText("Export bounded CSV").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText("Apply role assignment").performScrollTo().assertHasClickAction()
    }

    private fun setContent(state: AdminUiState) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                AdminScreen(
                    state = state,
                    onRefresh = {},
                    onCreateArtist = {},
                    onCreateAlbum = { _, _, _ -> },
                    onCreateTrack = { _, _, _, _, _, _ -> },
                    onUploadAudio = { _, _, _ -> },
                    onPublishAlbum = {},
                    onCreateModerationCase = { _, _, _, _ -> },
                    onModerate = { _, _, _ -> },
                    onAssignStaff = { _, _, _ -> },
                    onEnforceContent = { _, _, _, _, _ -> },
                    onAssignCatalogTeam = { _, _, _, _, _ -> },
                    onCreateCatalogLabel = {},
                    onLinkCatalogLabelArtist = { _, _ -> },
                    onUploadArtwork = { _, _ -> },
                    onSubmitReview = { _, _, _ -> },
                    onDecideReview = { _, _, _ -> },
                    onScheduleAlbum = { _, _ -> },
                    onExportAudit = { _, _ -> },
                    onSetAuditRetention = {},
                )
            }
        }
    }
}
