package my.id.rakyzumusic.core.data.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAuthorizationTest {
    @Test
    fun `listener context cannot expose privileged controls`() {
        val context = StaffAccessContext(
            isStaff = false,
            role = null,
            displayRole = null,
            fullAccess = false,
            permissions = emptySet(),
        )

        assertFalse(context.can(StaffPermission.AdminAccess))
        assertFalse(context.can(StaffPermission.CatalogPublish))
    }

    @Test
    fun `officer receives only explicit moderation capability`() {
        val context = StaffAccessContext(
            isStaff = true,
            role = StaffRole.Officer,
            displayRole = "Officer",
            fullAccess = false,
            permissions = setOf(
                StaffPermission.AdminAccess,
                StaffPermission.ModerationView,
                StaffPermission.ModerationTriage,
            ),
        )

        assertTrue(context.can(StaffPermission.ModerationTriage))
        assertFalse(context.can(StaffPermission.ModerationDecide))
        assertFalse(context.can(StaffPermission.CatalogDraft))
        assertFalse(context.can(StaffPermission.ContentEnforce))
        assertFalse(context.can(StaffPermission.AuditExport))
    }

    @Test
    fun `scoped governance permissions do not imply CEO authority`() {
        val context = StaffAccessContext(
            isStaff = true,
            role = StaffRole.CLevelExecutive,
            displayRole = "C-Level Executive",
            fullAccess = false,
            permissions = setOf(
                StaffPermission.AdminAccess,
                StaffPermission.CatalogReview,
                StaffPermission.AuditExport,
            ),
        )

        assertTrue(context.can(StaffPermission.CatalogReview))
        assertTrue(context.can(StaffPermission.AuditExport))
        assertFalse(context.can(StaffPermission.GovernanceManage))
        assertFalse(context.can(StaffPermission.StaffManage))
    }

    @Test
    fun `CEO full access remains authoritative for every known permission`() {
        val context = StaffAccessContext(
            isStaff = true,
            role = StaffRole.Ceo,
            displayRole = "CEO",
            fullAccess = true,
            permissions = emptySet(),
        )

        StaffPermission.entries.forEach { permission ->
            assertTrue(context.can(permission))
        }
    }
}
