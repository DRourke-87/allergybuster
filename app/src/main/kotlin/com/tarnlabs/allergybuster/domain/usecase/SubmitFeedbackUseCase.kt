package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves the user's feeling for the given day. Bayesian weight updates are deferred to
 * ApplyDailyBayesianUseCase, which runs the next morning via PollenFetchWorker, ensuring
 * the update is based on the final value the user settled on rather than the first tap.
 */
@Singleton
class SubmitFeedbackUseCase @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) {
    suspend operator fun invoke(date: String, severity: Int) {
        feedbackRepository.saveFeedback(date, severity)
    }
}
