import Foundation
import shared

let AppGroupId = "group.com.tarnlabs.allergybuster"

final class ServiceContainer {
    static let shared = ServiceContainer()

    let pollenRepository: PollenRepository
    let feedbackRepository: FeedbackRepository
    let recommendationRepository: RecommendationRepository
    let submitFeedbackUseCase: SubmitFeedbackUseCase
    let applyDailyBayesianUseCase: ApplyDailyBayesianUseCase
    let computeRecommendationUseCase: ComputeRecommendationUseCase

    private init() {
        let db = AllergyBusterDatabase(driver: DatabaseDriverFactory().createDriver())
        let api = OpenMeteoApiClient()

        pollenRepository            = PollenRepository(db: db, api: api)
        feedbackRepository          = FeedbackRepository(db: db)
        recommendationRepository    = RecommendationRepository(db: db)
        submitFeedbackUseCase       = SubmitFeedbackUseCase(feedbackRepository: feedbackRepository)
        applyDailyBayesianUseCase   = ApplyDailyBayesianUseCase(
            feedbackRepository:     feedbackRepository,
            pollenRepository:       pollenRepository,
            recommendationRepository: recommendationRepository
        )
        computeRecommendationUseCase = ComputeRecommendationUseCase(
            feedbackRepository: feedbackRepository
        )
    }
}
