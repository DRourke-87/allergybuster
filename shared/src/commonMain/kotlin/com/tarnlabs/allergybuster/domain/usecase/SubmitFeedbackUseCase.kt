package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.repository.FeedbackRepository

class SubmitFeedbackUseCase(
    private val feedbackRepository: FeedbackRepository
) {
    suspend operator fun invoke(date: String, severity: Int) {
        feedbackRepository.saveFeedback(date, severity)
    }
}
