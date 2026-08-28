package my.id.rakyzumusic.core.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DisplayNameTest {
    @Test
    fun validNameIsTrimmedAndWhitespaceIsCollapsed() {
        val result = DisplayName("  Rakyzu   Listener  ").validate()

        assertEquals(DisplayNameValidation.Valid("Rakyzu Listener"), result)
    }

    @Test
    fun oneCharacterNameIsRejected() {
        assertSame(DisplayNameValidation.Invalid, DisplayName("R").validate())
    }

    @Test
    fun nameLongerThanDatabaseLimitIsRejected() {
        assertSame(
            DisplayNameValidation.Invalid,
            DisplayName("R".repeat(DisplayName.MAXIMUM_LENGTH + 1)).validate(),
        )
    }

    @Test
    fun controlCharactersAreRejected() {
        assertSame(DisplayNameValidation.Invalid, DisplayName("Rakyzu\u0000Music").validate())
    }
}
