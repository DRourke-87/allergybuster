import Foundation
import UserNotifications

/// Owns the repeating "check today's pollen" local notification. Used by the
/// Settings screen, onboarding (first permission ask) and app launch (repair
/// path so existing users who granted permission keep their reminder).
enum NotificationScheduler {
    static let requestId = "daily-pollen"
    static let defaultHour = 7
    static let defaultMinute = 0

    /// Saved reminder time, defaulting to 07:00 when never configured.
    /// `object(forKey:)` rather than `integer(forKey:)` so a missing key
    /// falls back to the default instead of midnight.
    static func savedTime() -> (hour: Int, minute: Int) {
        let defaults = UserDefaults(suiteName: AppGroupId)
        let hour   = defaults?.object(forKey: "notifHour")   as? Int ?? defaultHour
        let minute = defaults?.object(forKey: "notifMinute") as? Int ?? defaultMinute
        return (hour, minute)
    }

    /// (Re)schedules the repeating daily reminder at the saved time.
    static func scheduleDailyReminder() {
        let time = savedTime()
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [requestId])
        let content = UNMutableNotificationContent()
        content.title = "AllergyBuster"
        content.body  = "Check today's pollen forecast"
        content.sound = .default
        var components = DateComponents()
        components.hour   = time.hour
        components.minute = time.minute
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        center.add(UNNotificationRequest(identifier: requestId, content: content, trigger: trigger))
    }

    /// Requests notification permission if needed and schedules the reminder
    /// once granted (also reschedules when permission was already granted).
    static func requestPermissionAndSchedule() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted { scheduleDailyReminder() }
        }
    }

    /// Reschedules at launch when permission is already granted, so the
    /// reminder survives upgrades for users who enabled it before the
    /// onboarding ask existed.
    static func rescheduleIfAuthorized() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized else { return }
            scheduleDailyReminder()
        }
    }
}
