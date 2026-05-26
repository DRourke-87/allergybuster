import Foundation
import BackgroundTasks
import WidgetKit
import shared

enum BackgroundRefreshScheduler {
    static let taskId = "com.tarnlabs.allergybuster.pollenrefresh"

    static func registerTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
            guard let appRefreshTask = task as? BGAppRefreshTask else { return }
            Task { await runPollenRefresh(task: appRefreshTask) }
        }
    }

    static func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskId)
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour   = 6
        components.minute = 0
        let todaySix = Calendar.current.date(from: components)!
        request.earliestBeginDate = todaySix > Date() ? todaySix : Calendar.current.date(byAdding: .day, value: 1, to: todaySix)
        try? BGTaskScheduler.shared.submit(request)
    }

    @MainActor
    private static func runPollenRefresh(task: BGAppRefreshTask) async {
        scheduleNextRefresh()
        await runImmediateFetch()
        task.setTaskCompleted(success: true)
    }

    /// Performs an immediate pollen fetch + recommendation update. Used by the
    /// scheduled BGAppRefreshTask and by the home-screen Retry button.
    @MainActor
    static func runImmediateFetch() async {
        let container = ServiceContainer.shared
        let defaults  = UserDefaults(suiteName: AppGroupId)
        let lat = defaults?.double(forKey: "latitude")  ?? 54.66
        let lon = defaults?.double(forKey: "longitude") ?? -3.36
        let locationName = defaults?.string(forKey: "locationName") ?? ""

        let pollen = try? await container.pollenRepository.fetchAndStore(lat: lat, lon: lon)
        try? await container.applyDailyBayesianUseCase.invoke()

        if let pollen {
            if let rec = try? await container.computeRecommendationUseCase.invoke(
                pollen: pollen, isStale: false, locationName: locationName
            ) {
                try? await container.recommendationRepository.save(rec: rec)
            }
        }

        WidgetCenter.shared.reloadAllTimelines()
    }
}
