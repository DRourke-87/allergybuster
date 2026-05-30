import SwiftUI
import shared

/// Detail sheet shown when a pollen contributor chip is tapped on the home
/// screen. Mirrors the Android `PollenDetailSheet`: today's level, a recent /
/// forecast trend, seasonality, cross-reactions and the user's learned
/// sensitivity for this pollen type.
struct PollenDetailView: View {
    let type: PollenTypeInfo
    let recentForecasts: [DailyPollen]
    let userWeights: UserWeights

    private var today: String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: Date())
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header

                let todayPollen = recentForecasts.first { $0.date == today }
                let todayRaw  = todayPollen.map { type.raw(from: $0) } ?? 0
                let todayNorm = type.normalise(todayRaw)
                TodayLevelCard(normValue: todayNorm, rawValue: todayRaw)

                let sorted = recentForecasts.sorted { $0.date < $1.date }
                if sorted.count >= 2 {
                    TrendCard(type: type, forecasts: sorted, today: today)
                }

                InfoCard(icon: "calendar", label: "Season", detail: type.seasonality)
                InfoCard(icon: "arrow.triangle.branch", label: "Cross-reactions", detail: type.crossReactions)
                SensitivityCard(weight: type.weight(from: userWeights))
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .background(AppTheme.background.ignoresSafeArea())
        .presentationDragIndicator(.visible)
    }

    private var header: some View {
        HStack(spacing: 12) {
            Text(type.icon)
                .font(.title2)
                .frame(width: 48, height: 48)
                .background(AppTheme.primaryContainer, in: Circle())
            VStack(alignment: .leading) {
                Text(type.displayName)
                    .font(.title).fontWeight(.bold)
                    .foregroundStyle(AppTheme.primary)
                Text("Pollen details")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
    }
}

private func levelColour(_ normValue: Float) -> Color {
    if normValue < 1 { return AppTheme.primary }   // low
    if normValue < 2 { return AppTheme.tertiary }  // moderate
    return AppTheme.error                            // high
}

private func levelLabel(_ normValue: Float) -> String {
    if normValue < 1 { return "Low" }
    if normValue < 2 { return "Moderate" }
    return "High"
}

private struct TodayLevelCard: View {
    let normValue: Float
    let rawValue: Float

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Today")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(Int(rawValue)) grains/m³")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ProgressBar(fraction: CGFloat(min(max(normValue / 3, 0), 1)), color: levelColour(normValue), height: 8)
            Text(levelLabel(normValue))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(levelColour(normValue))
        }
        .cardBackground()
    }
}

private struct TrendCard: View {
    let type: PollenTypeInfo
    let forecasts: [DailyPollen]
    let today: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recent & forecast")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(forecasts, id: \.date) { pollen in
                    let norm = type.normalise(type.raw(from: pollen))
                    let fraction = CGFloat(min(max(norm / 3, 0.03), 1))
                    let isToday  = pollen.date == today
                    let isFuture = pollen.date > today
                    let color: Color = isToday
                        ? levelColour(norm)
                        : (isFuture ? levelColour(norm).opacity(0.55) : Color.secondary.opacity(0.45))
                    VStack(spacing: 4) {
                        Spacer(minLength: 0)
                        RoundedRectangle(cornerRadius: 3)
                            .fill(color)
                            .frame(height: max(4, 80 * fraction))
                        Text(weekday(pollen.date))
                            .font(.caption2)
                            .fontWeight(isToday ? .bold : .regular)
                            .foregroundStyle(isToday ? AppTheme.primary : Color.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 100, alignment: .bottom)
                }
            }
            HStack(spacing: 12) {
                LegendDot(color: Color.secondary.opacity(0.5), label: "Past")
                LegendDot(color: levelColour(1.5), label: "Today")
                LegendDot(color: levelColour(1.5).opacity(0.5), label: "Forecast")
            }
        }
        .cardBackground()
    }

    private func weekday(_ iso: String) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        guard let date = fmt.date(from: iso) else { return "" }
        let out = DateFormatter()
        out.dateFormat = "EEE"
        return out.string(from: date)
    }
}

private struct LegendDot: View {
    let color: Color
    let label: String
    var body: some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 10, height: 10)
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
    }
}

private struct InfoCard: View {
    let icon: String
    let label: String
    let detail: String
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(AppTheme.secondary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Text(detail).font(.body)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardBackground()
    }
}

private struct SensitivityCard: View {
    let weight: Float

    private var description: String {
        if weight < 0.7 {
            return "You appear less reactive to this pollen than average — it contributes less to your personalised risk score."
        } else if weight > 1.4 {
            return "You appear more sensitive to this pollen than average — it carries extra weight in your personalised forecast."
        } else {
            return "Your sensitivity to this pollen is close to average."
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "target")
                .font(.title3)
                .foregroundStyle(AppTheme.secondary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 8) {
                Text("Your sensitivity")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Text(description).font(.body)
                ProgressBar(
                    fraction: CGFloat(min(max((weight - 0.1) / (5.0 - 0.1), 0), 1)),
                    color: AppTheme.primary,
                    height: 6
                )
                Text(String(format: "Sensitivity index: %.2f× (default 1.00×)", Double(weight)))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardBackground()
    }
}

private struct ProgressBar: View {
    let fraction: CGFloat
    let color: Color
    let height: CGFloat
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.secondary.opacity(0.15))
                Capsule().fill(color).frame(width: geo.size.width * fraction)
            }
        }
        .frame(height: height)
    }
}

private extension View {
    func cardBackground() -> some View {
        self
            .padding(16)
            .background(AppTheme.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
