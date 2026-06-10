import Foundation
import shared

let AppGroupId = "group.com.tarnlabs.allergybuster"

@MainActor
final class ServiceContainer {
    static let shared = ServiceContainer()

    let pollenRepository: PollenRepository
    let feedbackRepository: FeedbackRepository
    let recommendationRepository: RecommendationRepository
    let submitFeedbackUseCase: SubmitFeedbackUseCase
    let applyDailyBayesianUseCase: ApplyDailyBayesianUseCase
    let computeRecommendationUseCase: ComputeRecommendationUseCase
    let observeOutlookUseCase: ObserveOutlookUseCase
    let locationService: LocationService

    private init() {
        locationService = LocationService()
        let db = DatabaseDriverFactory().createDatabase()
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
        observeOutlookUseCase = ObserveOutlookUseCase(
            pollenRepository:   pollenRepository,
            feedbackRepository: feedbackRepository
        )
    }
}
