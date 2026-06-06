import Foundation
import BackgroundTasks
import CoreLocation
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
        // Background tasks only have When-In-Use authorization, so a fresh fix isn't
        // available here — rely on the coords the foreground path keeps current.
        await runImmediateFetch()
        task.setTaskCompleted(success: true)
    }

    /// Performs an immediate pollen fetch + recommendation update. Used by the
    /// scheduled BGAppRefreshTask and by the home-screen Retry button.
    ///
    /// When `allowFreshLocation` is true (foreground launch / Retry), a fast city-level
    /// fix is requested and persisted before fetching, so a moved device picks up its new
    /// location without opening Settings. The pollen fetch runs on the raw coords first;
    /// reverse geocoding happens afterwards so it never blocks the network call.
    @MainActor
    static func runImmediateFetch(allowFreshLocation: Bool = false) async {
        let container = ServiceContainer.shared
        let defaults  = UserDefaults(suiteName: AppGroupId)
        var lat = defaults?.double(forKey: "latitude")  ?? 54.66
        var lon = defaults?.double(forKey: "longitude") ?? -3.36
        var locationName = defaults?.string(forKey: "locationName") ?? ""
        var freshLocation: CLLocation? = nil

        if allowFreshLocation,
           let loc = await container.locationService.currentLocation() {
            freshLocation = loc
            lat = loc.coordinate.latitude
            lon = loc.coordinate.longitude
            defaults?.set(lat, forKey: "latitude")
            defaults?.set(lon, forKey: "longitude")
        }

        let pollen = try? await container.pollenRepository.fetchAndStore(lat: lat, lon: lon)
        try? await container.applyDailyBayesianUseCase.invoke()

        if let loc = freshLocation, let name = await container.locationService.reverseGeocode(loc) {
            locationName = name
            defaults?.set(name, forKey: "locationName")
        }

        if let pollen {
            if let rec = try? await container.computeRecommendationUseCase.invoke(
                pollen: pollen, isStale: false, locationName: locationName
            ) {
                try? await container.recommendationRepository.save(rec: rec)
            }
        }
    }
}
