import Foundation
import UserNotifications
import CoreLocation
import shared

@MainActor
final class SettingsViewModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var notificationHour: Int   = 7
    @Published var notificationMinute: Int = 0
    @Published var locationName: String    = ""
    @Published var locationError: String?  = nil

    private let locationManager = CLLocationManager()
    private let recRepo:      RecommendationRepository
    private let pollenRepo:   PollenRepository
    private let computeUseCase: ComputeRecommendationUseCase

    init(container: ServiceContainer = .shared) {
        recRepo      = container.recommendationRepository
        pollenRepo   = container.pollenRepository
        computeUseCase = container.computeRecommendationUseCase
        super.init()
        locationManager.delegate = self
        loadSavedSettings()
    }

    private func loadSavedSettings() {
        let defaults = UserDefaults(suiteName: AppGroupId)
        notificationHour   = defaults?.integer(forKey: "notifHour")   ?? 7
        notificationMinute = defaults?.integer(forKey: "notifMinute") ?? 0
        locationName       = defaults?.string(forKey: "locationName") ?? ""
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
        locationManager.requestWhenInUseAuthorization()
        locationManager.requestLocation()
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        let geocoder = CLGeocoder()
        geocoder.reverseGeocodeLocation(loc) { placemarks, _ in
            let name = placemarks?.first?.locality ?? placemarks?.first?.country ?? "Unknown"
            Task { @MainActor in
                self.locationName = name
                let defaults = UserDefaults(suiteName: AppGroupId)
                defaults?.set(loc.coordinate.latitude,  forKey: "latitude")
                defaults?.set(loc.coordinate.longitude, forKey: "longitude")
                defaults?.set(name,                     forKey: "locationName")
                // Refresh pollen data for new location
                let pollen = try? await self.pollenRepo.fetchAndStore(
                    lat: loc.coordinate.latitude,
                    lon: loc.coordinate.longitude
                )
                if let pollen {
                    let rec = try? await self.computeUseCase.invoke(pollen: pollen, isStale: false, locationName: name)
                    if let rec { try? await self.recRepo.save(rec: rec) }
                }
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.locationError = error.localizedDescription }
    }
}
