package my.id.rakyzumusic.core.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface OfflineDownloadWorkerDependencies {
    val executableOfflineDownloadRepository: ExecutableOfflineDownloadRepository
}
class OfflineDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(USER_ID_INPUT)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val trackId = inputData.getString(TRACK_ID_INPUT)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val dependencies = applicationContext as? OfflineDownloadWorkerDependencies
            ?: return Result.failure()
        return when (dependencies.executableOfflineDownloadRepository.execute(userId, trackId)) {
            DownloadExecutionResult.Success -> Result.success()
            DownloadExecutionResult.Retry -> if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else Result.failure()
            DownloadExecutionResult.Failure -> Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 3
    }
}
