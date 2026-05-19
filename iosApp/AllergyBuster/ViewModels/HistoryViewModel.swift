import Foundation
import shared

struct HistoryDay {
    let recommendation: Recommendation
    let feedback: DailyFeedback?
}

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published var historyDays: [HistoryDay] = []

    private let recRepo:      RecommendationRepository
    private let feedbackRepo: FeedbackRepository
    private var tasks: [Task<Void, Never>] = []

    private var latestRecs:      [Recommendation] = []
    private var latestFeedbacks: [DailyFeedback]  = []

    init(container: ServiceContainer = .shared) {
        recRepo      = container.recommendationRepository
        feedbackRepo = container.feedbackRepository
        startObserving()
    }

    private func startObserving() {
        tasks.append(Task {
            for await recs in recRepo.observeRecent(limit: 90) {
                self.latestRecs = recs
                self.rebuild()
            }
        })
        tasks.append(Task {
            for await feedbacks in feedbackRepo.observeRecentFeedback(limit: 90) {
                self.latestFeedbacks = feedbacks
                self.rebuild()
            }
        })
    }

    private func rebuild() {
        let feedbackMap = Dictionary(
            latestFeedbacks.map { ($0.date, $0) },
            uniquingKeysWith: { $1 }
        )
        historyDays = latestRecs.map { rec in
            HistoryDay(recommendation: rec, feedback: feedbackMap[rec.date])
        }
    }

    deinit { tasks.forEach { $0.cancel() } }
}
