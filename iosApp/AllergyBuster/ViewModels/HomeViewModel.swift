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
    @Published var userWeights: UserWeights             = .defaultWeights
    @Published var locationName: String                 = ""
    @Published var learningProgress: LearningProgressState = .initial
    @Published var showRetry: Bool  = false
    @Published var isRetrying: Bool = false

    private static let learningWindow = 30
    private static let stuckTimeoutNs: UInt64 = 30_000_000_000
    private let today: String

    private let pollenRepo:      PollenRepository
    private let feedbackRepo:    FeedbackRepository
    private let recRepo:         RecommendationRepository
    private let submitUseCase:   SubmitFeedbackUseCase
    private var tasks: [Task<Void, Never>] = []
    private var stuckTimer: Task<Void, Never>?

    init(container: ServiceContainer = .shared) {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        today        = fmt.string(from: Date())
        pollenRepo   = container.pollenRepository
        feedbackRepo = container.feedbackRepository
        recRepo      = container.recommendationRepository
        submitUseCase = container.submitFeedbackUseCase
        locationName = UserDefaults(suiteName: AppGroupId)?.string(forKey: "locationName") ?? ""
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
                let rec = recs.first(where: { $0.date == self.today })
                self.todayRecommendation = rec
                if let name = rec?.locationName, !name.isEmpty {
                    self.locationName = name
                }
                self.updateStuckTimer(recommendation: rec)
            }
        })
        tasks.append(Task {
            for await weights in feedbackRepo.observeWeights() {
                if let weights { self.userWeights = weights }
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

    deinit {
        tasks.forEach { $0.cancel() }
        stuckTimer?.cancel()
    }

    func submitFeedback(severity: Int) {
        Task { try? await submitUseCase.invoke(date: today, severity: Int32(severity)) }
    }

    func retryForecastFetch() {
        guard !isRetrying else { return }
        isRetrying = true
        Task {
            await BackgroundRefreshScheduler.runImmediateFetch()
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            await MainActor.run { self.isRetrying = false }
        }
    }

    private func updateStuckTimer(recommendation: Recommendation?) {
        stuckTimer?.cancel()
        if recommendation != nil {
            showRetry = false
            return
        }
        showRetry = false
        stuckTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.stuckTimeoutNs)
            if Task.isCancelled { return }
            await MainActor.run { self?.showRetry = true }
        }
    }
}
