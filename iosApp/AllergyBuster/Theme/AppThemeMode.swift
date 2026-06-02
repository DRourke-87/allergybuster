import SwiftUI

/// User-selectable appearance mode. Persisted as a raw string in the App Group
/// `UserDefaults` so it is shared with any future extensions, and surfaced to
/// SwiftUI via `@AppStorage`.
enum AppThemeMode: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "System"
        case .light:  return "Light"
        case .dark:   return "Dark"
        }
    }

    /// The `ColorScheme` to force, or `nil` to follow the system setting.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }
}
