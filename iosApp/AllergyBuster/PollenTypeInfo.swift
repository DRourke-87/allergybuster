import Foundation
import shared

/// Swift-side mirror of the shared `PollenType` enum. We keep the display
/// metadata and normalisation here so the UI does not depend on Kotlin enum
/// bridging, while the numeric values still come from the shared
/// `DailyPollen` / `UserWeights` models.
enum PollenTypeInfo: CaseIterable, Identifiable {
    case alder, birch, grass, mugwort, olive, ragweed

    var id: String { displayName }

    var displayName: String {
        switch self {
        case .alder:   return "Alder"
        case .birch:   return "Birch"
        case .grass:   return "Grass"
        case .mugwort: return "Mugwort"
        case .olive:   return "Olive"
        case .ragweed: return "Ragweed"
        }
    }

    var icon: String {
        switch self {
        case .alder:   return "🌳"
        case .birch:   return "🌲"
        case .grass:   return "🌾"
        case .mugwort: return "🌿"
        case .olive:   return "🫒"
        case .ragweed: return "🌻"
        }
    }

    /// low / moderate / high thresholds in grains/m³.
    var thresholds: (low: Float, moderate: Float, high: Float) {
        switch self {
        case .alder:   return (10, 50, 100)
        case .birch:   return (10, 50, 100)
        case .grass:   return (10, 30, 50)
        case .mugwort: return (5, 15, 30)
        case .olive:   return (10, 50, 100)
        case .ragweed: return (5, 15, 30)
        }
    }

    var seasonality: String {
        switch self {
        case .alder:   return "Late winter, early spring"
        case .birch:   return "Spring"
        case .grass:   return "Late spring through summer"
        case .mugwort: return "Late summer, early autumn"
        case .olive:   return "Late spring, early summer"
        case .ragweed: return "Late summer through autumn"
        }
    }

    var crossReactions: String {
        switch self {
        case .alder:   return "Birch, Hazel, Hornbeam, Beech, Willow and Oak pollen"
        case .birch:   return "Apple, Peach, Carrot, Celery, Hazel, Alder and Olive pollen"
        case .grass:   return "Cereal crops — wheat, rye and oats"
        case .mugwort: return "Ragweed, Celery, Carrot and various spices"
        case .olive:   return "Ash and Privet pollen"
        case .ragweed: return "Mugwort, Sunflower and Chrysanthemum pollen"
        }
    }

    /// Piecewise-linear normalisation: raw grains/m³ → 0.0–3.0. Mirrors
    /// `PollenType.normalise` in the shared module.
    func normalise(_ raw: Float) -> Float {
        let (low, moderate, high) = thresholds
        let result: Float
        if raw < low {
            result = raw / low
        } else if raw < moderate {
            result = 1 + (raw - low) / (moderate - low)
        } else if raw < high {
            result = 2 + (raw - moderate) / (high - moderate)
        } else {
            result = 3
        }
        return min(max(result, 0), 3)
    }

    /// Raw grains/m³ for this type from a shared `DailyPollen`.
    func raw(from pollen: DailyPollen) -> Float {
        switch self {
        case .alder:   return pollen.alderMax
        case .birch:   return pollen.birchMax
        case .grass:   return pollen.grassMax
        case .mugwort: return pollen.mugwortMax
        case .olive:   return pollen.oliveMax
        case .ragweed: return pollen.ragweedMax
        }
    }

    /// Personalised sensitivity weight for this type from shared `UserWeights`.
    func weight(from weights: UserWeights) -> Float {
        switch self {
        case .alder:   return weights.alderWeight
        case .birch:   return weights.birchWeight
        case .grass:   return weights.grassWeight
        case .mugwort: return weights.mugwortWeight
        case .olive:   return weights.oliveWeight
        case .ragweed: return weights.ragweedWeight
        }
    }

    static func from(displayName: String) -> PollenTypeInfo? {
        allCases.first { $0.displayName == displayName }
    }
}

extension UserWeights {
    /// All-default (1.0×) weights — used before the first observation arrives.
    static var defaultWeights: UserWeights {
        UserWeights(
            alderWeight: 1.0,
            birchWeight: 1.0,
            grassWeight: 1.0,
            mugwortWeight: 1.0,
            oliveWeight: 1.0,
            ragweedWeight: 1.0,
            updatedAt: 0
        )
    }
}
