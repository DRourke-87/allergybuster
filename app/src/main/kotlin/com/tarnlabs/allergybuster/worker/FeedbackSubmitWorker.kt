package com.tarnlabs.allergybuster.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tarnlabs.allergybuster.domain.usecase.SubmitFeedbackUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class FeedbackSubmitWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val submitFeedback: SubmitFeedbackUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val date     = inputData.getString("date") ?: LocalDate.now().toString()
        val severity = inputData.getInt("severity", -1)
        if (severity < 0) return Result.failure()

        return try {
            submitFeedback(date, severity)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
