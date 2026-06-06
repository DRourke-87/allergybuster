import Foundation
import UserNotifications
import shared

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var notificationHour: Int   = 7
    @Published var notificationMinute: Int = 0
    @Published var locationName: String    = ""
    @Published var latitude: Double        = 0
    @Published var longitude: Double       = 0
    @Published var locationError: String?  = nil

    private let locationService: LocationService
    private let recRepo:      RecommendationRepository
    private let pollenRepo:   PollenRepository
    private let computeUseCase: ComputeRecommendationUseCase

    init(container: ServiceContainer = .shared) {
        recRepo      = container.recommendationRepository
        pollenRepo   = container.pollenRepository
        computeUseCase = container.computeRecommendationUseCase
        locationService = container.locationService
        loadSavedSettings()
    }

    private func loadSavedSettings() {
        let defaults = UserDefaults(suiteName: AppGroupId)
        notificationHour   = defaults?.integer(forKey: "notifHour")   ?? 7
        notificationMinute = defaults?.integer(forKey: "notifMinute") ?? 0
        locationName       = defaults?.string(forKey: "locationName") ?? ""
        latitude           = defaults?.double(forKey: "latitude")     ?? 0
        longitude          = defaults?.double(forKey: "longitude")    ?? 0
    }

    func saveNotificationTime() {
        let defaults = UserDefaults(suiteName: AppGroupId)
        defaults?.set(notificationHour,   forKey: "notifHour")
        defaults?.set(notificationMinute, forKey: "notifMinute")
        scheduleNotification()
    }

    private func scheduleNotification() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        let content = UNMutableNotificationContent()
        content.title = "AllergyBuster"
        content.body  = "Check today's pollen forecast"
        content.sound = .default
        var components = DateComponents()
        components.hour   = notificationHour
        components.minute = notificationMinute
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        let request = UNNotificationRequest(identifier: "daily-pollen", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted { Task { @MainActor in self.scheduleNotification() } }
        }
    }

    func refreshLocation() {
        locationError = nil
        switch locationService.authorizationStatus {
        case .notDetermined:
            locationService.requestAuthorization()
        case .denied, .restricted:
            locationError = "Location access is disabled. Go to Settings → AllergyBuster to enable it."
        default:
            Task { await self.fetchForCurrentLocation() }
        }
    }

    private func fetchForCurrentLocation() async {
        guard let loc = await locationService.currentLocation() else {
            locationError = "Couldn't determine your location. Please try again."
            return
        }
        let lat = loc.coordinate.latitude
        let lon = loc.coordinate.longitude
        latitude  = lat
        longitude = lon
        let defaults = UserDefaults(suiteName: AppGroupId)
        defaults?.set(lat, forKey: "latitude")
        defaults?.set(lon, forKey: "longitude")

        // Fetch pollen on the raw coords first so the network call never waits on geocoding.
        let pollen = try? await pollenRepo.fetchAndStore(lat: lat, lon: lon)

        let name = await locationService.reverseGeocode(loc)
            ?? String(format: "%.2f°, %.2f°", lat, lon)
        locationName = name
        defaults?.set(name, forKey: "locationName")

        if let pollen {
            let rec = try? await computeUseCase.invoke(pollen: pollen, isStale: false, locationName: name)
            if let rec { try? await recRepo.save(rec: rec) }
        }
    }
}
