import SwiftUI

/// AllergyBuster colour palette — a direct port of the Android Compose theme
/// (`ui/theme/Color.kt` + `Theme.kt`). The light values are the "Spring Meadow"
/// scheme and the dark values are "Twilight Forest", so the two platforms stay
/// visually identical. Every colour resolves automatically for light/dark mode.
enum AppTheme {

    // MARK: Primary — deep forest canopy green
    static let primary            = Color.adaptive(light: 0x2E6B1A, dark: 0x94D685)
    static let onPrimary          = Color.adaptive(light: 0xFFFFFF, dark: 0x0B3300)
    static let primaryContainer   = Color.adaptive(light: 0xB4DFAA, dark: 0x1A4A0D)
    static let onPrimaryContainer = Color.adaptive(light: 0x0A2D04, dark: 0xC2E8B8)

    // MARK: Secondary — warm tree bark brown
    static let secondary            = Color.adaptive(light: 0x7A5840, dark: 0xD8B79A)
    static let secondaryContainer   = Color.adaptive(light: 0xEDDDD4, dark: 0x4D3323)
    static let onSecondaryContainer = Color.adaptive(light: 0x2E1509, dark: 0xEDDDD4)

    // MARK: Tertiary — golden sunlight / pollen glow
    static let tertiary            = Color.adaptive(light: 0xA07828, dark: 0xE8C070)
    static let tertiaryContainer   = Color.adaptive(light: 0xFDEFC5, dark: 0x5A3E00)
    static let onTertiaryContainer = Color.adaptive(light: 0x342400, dark: 0xFDEFC5)

    // MARK: Error — warm terracotta (autumn leaf, not clinical red)
    static let error            = Color.adaptive(light: 0xB53B2A, dark: 0xFF8E78)
    static let errorContainer   = Color.adaptive(light: 0xFFD9CF, dark: 0x7A2516)
    static let onErrorContainer = Color.adaptive(light: 0x400E04, dark: 0xFFD9CF)

    // MARK: Backgrounds & surfaces — parchment / forest floor
    static let background       = Color.adaptive(light: 0xF8F5ED, dark: 0x151F10)
    static let onBackground     = Color.adaptive(light: 0x0A2D04, dark: 0xC2E8B8)
    static let surface          = Color.adaptive(light: 0xFDFAF4, dark: 0x1D2718)
    static let surfaceVariant   = Color.adaptive(light: 0xE2ECD8, dark: 0x3B4A35)
    static let onSurfaceVariant = Color.adaptive(light: 0x79866F, dark: 0x8B9A84)
    static let outline          = Color.adaptive(light: 0x79866F, dark: 0x8B9A84)

    // MARK: Tree-bark accent used by the learning-tree illustration
    static let bark = Color.adaptive(light: 0x7A5840, dark: 0xD8B79A)

    /// Per-risk-level container / on-container / accent triple, mirroring
    /// Android's `RecommendationCard` mapping (0 low, 1 moderate, 2 high).
    static func levelContainer(_ level: Int32) -> Color {
        switch level {
        case 0:  return primaryContainer
        case 1:  return secondaryContainer
        case 2:  return errorContainer
        default: return surfaceVariant
        }
    }

    static func onLevelContainer(_ level: Int32) -> Color {
        switch level {
        case 0:  return onPrimaryContainer
        case 1:  return onSecondaryContainer
        case 2:  return onErrorContainer
        default: return onSurfaceVariant
        }
    }

    /// Strong accent for icons / text that need to read against the container.
    static func levelAccent(_ level: Int32) -> Color {
        switch level {
        case 0:  return primary
        case 1:  return tertiary
        case 2:  return error
        default: return onSurfaceVariant
        }
    }
}

extension Color {
    /// Build a colour from a 24-bit RGB hex literal, e.g. `Color(hex: 0x2E6B1A)`.
    init(hex: UInt32) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1)
    }

    /// A colour that resolves to `light` in light mode and `dark` in dark mode.
    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(traits.userInterfaceStyle == .dark
                ? Color(hex: dark)
                : Color(hex: light))
        })
    }

    /// Linearly interpolate between two colours by `t` (0…1), mirroring Compose's
    /// `lerp`. Resolves each endpoint per trait collection so light/dark stay correct.
    static func lerp(_ a: Color, _ b: Color, _ t: CGFloat) -> Color {
        let f = max(0, min(1, t))
        return Color(UIColor { traits in
            let ca = UIColor(a).resolvedColor(with: traits)
            let cb = UIColor(b).resolvedColor(with: traits)
            var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
            var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
            ca.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
            cb.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
            return UIColor(
                red:   ar + (br - ar) * f,
                green: ag + (bg - ag) * f,
                blue:  ab + (bb - ab) * f,
                alpha: aa + (ba - aa) * f
            )
        })
    }
}
