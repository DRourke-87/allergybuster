import Foundation
import shared

struct LearningProgressState {
    let daysElapsed: Int
    let feedbackCount: Int
    let progressFraction: Float

    var isMature: Bool { progressFraction >= 1.0 }

    static let initial = LearningProgressState(daysElapsed: 0, feedbackCount: 0, progressFraction: 0)

    static func from(daysElapsed: Int, feedbackCount: Int, windowDays: Int = 30) -> LearningProgressState {
        let dayFrac = min(Float(daysElapsed) / Float(windowDays), 1)
        let fbFrac  = min(Float(feedbackCount) / Float(windowDays), 1)
        return LearningProgressState(
            daysElapsed:      daysElapsed,
            feedbackCount:    feedbackCount,
            progressFraction: min((dayFrac + fbFrac) / 2, 1)
        )
    }
}

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var todayRecommendation: Recommendation? = nil
    @Published var todayFeedback: DailyFeedback?        = nil
    @Published var recentForecasts: [DailyPollen]       = []
    @Published var learningProgress: LearningProgressState = .initial

    private static let learningWindow = 30
    private let today: String

    private let pollenRepo:      PollenRepository
    private let feedbackRepo:    FeedbackRepository
    private let recRepo:         RecommendationRepository
    private let submitUseCase:   SubmitFeedbackUseCase
    private var tasks: [Task<Void, Never>] = []

    init(container: ServiceContainer = .shared) {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        today        = fmt.string(from: Date())
        pollenRepo   = container.pollenRepository
        feedbackRepo = container.feedbackRepository
        recRepo      = container.recommendationRepository
        submitUseCase = container.submitFeedbackUseCase
        ensureLearningStarted()
        startObserving()
    }

    private func ensureLearningStarted() {
        let defaults = UserDefaults(suiteName: AppGroupId)
        if (defaults?.double(forKey: "learningStartedAt") ?? 0) == 0 {
            defaults?.set(Date().timeIntervalSince1970 * 1000, forKey: "learningStartedAt")
        }
    }

    private func startObserving() {
        tasks.append(Task {
            for await recs in recRepo.observeRecent(limit: 1) {
                self.todayRecommendation = recs.first(where: { $0.date == self.today })
            }
        })
        tasks.append(Task {
            for await feedbacks in feedbackRepo.observeRecentFeedback(limit: 1) {
                self.todayFeedback = feedbacks.first(where: { $0.date == self.today })
            }
        })
        tasks.append(Task {
            for await pollen in pollenRepo.observeRecent(limit: 14) {
                self.recentForecasts = pollen
            }
        })
        tasks.append(Task {
            for await count in feedbackRepo.observeFeedbackCount() {
                let fbCount = Int(count)
                let startMs = UserDefaults(suiteName: AppGroupId)?.double(forKey: "learningStartedAt") ?? 0
                var rawDays = 0
                if startMs > 0 {
                    let start = Date(timeIntervalSince1970: startMs / 1000)
                    rawDays = max(Calendar.current.dateComponents([.day], from: start, to: Date()).day ?? 0, 0)
                }
                let d = min(max(rawDays, fbCount), Self.learningWindow)
                self.learningProgress = LearningProgressState.from(daysElapsed: d, feedbackCount: fbCount)
            }
        })
    }

    deinit { tasks.forEach { $0.cancel() } }

    func submitFeedback(severity: Int) {
        Task { try? await submitUseCase.invoke(date: today, severity: Int32(severity)) }
    }
}
