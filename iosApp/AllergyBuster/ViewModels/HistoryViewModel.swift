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

    init(container: ServiceContainer = .shared) {
        recRepo      = container.recommendationRepository
        feedbackRepo = container.feedbackRepository
        startObserving()
    }

    private func startObserving() {
        tasks.append(Task {
            for await (recs, feedbacks) in zip(
                recRepo.observeRecent(limit: 90),
                feedbackRepo.observeRecentFeedback(limit: 90)
            ) {
                let feedbackMap = Dictionary(feedbacks.map { ($0.date, $0) }, uniquingKeysWith: { $1 })
                self.historyDays = recs.map { rec in
                    HistoryDay(recommendation: rec, feedback: feedbackMap[rec.date])
                }
            }
        })
    }

    deinit { tasks.forEach { $0.cancel() } }
}
