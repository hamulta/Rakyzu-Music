package my.id.rakyzumusic.core.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PasswordRecoveryState(
    private val encryptedStore: EncryptedAuthStore,
) {
    private val mutableRecoveryRequired = MutableStateFlow(
        encryptedStore.getString(STORAGE_KEY) == STORAGE_VALUE_REQUIRED,
    )
    val isRecoveryRequired: StateFlow<Boolean> = mutableRecoveryRequired.asStateFlow()

    fun requireRecovery() {
        encryptedStore.putString(STORAGE_KEY, STORAGE_VALUE_REQUIRED)
        mutableRecoveryRequired.value = true
    }

    fun complete() {
        encryptedStore.remove(STORAGE_KEY)
        mutableRecoveryRequired.value = false
    }

    private companion object {
        const val STORAGE_KEY = "password_recovery"
        const val STORAGE_VALUE_REQUIRED = "required"
    }
}
