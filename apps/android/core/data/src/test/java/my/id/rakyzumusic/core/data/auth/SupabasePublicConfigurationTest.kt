package my.id.rakyzumusic.core.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabasePublicConfigurationTest {
    @Test
    fun hostedHttpsConfigurationIsAccepted() {
        val configuration = SupabasePublicConfiguration(
            url = "https://project.supabase.co",
            publishableKey = "sb_publishable_example",
        )

        assertTrue(configuration.isValid())
    }

    @Test
    fun insecureOrIncompleteConfigurationIsRejected() {
        assertFalse(SupabasePublicConfiguration("http://project.supabase.co", "key").isValid())
        assertFalse(SupabasePublicConfiguration("https://project.supabase.co", "").isValid())
        assertFalse(SupabasePublicConfiguration("not a url", "key").isValid())
    }
}
