import SwiftUI
import shared

struct OutlookStripView: View {
    let outlook: [DailyOutlook]
    var title: String = "Next days"
    let onTap: (DailyOutlook) -> Void

    private static let isoFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt
    }()

    /// Labels each chip by comparing its real calendar date to the current day,
    /// so "Today"/"Tomorrow" stay correct regardless of the array's contents or a
    /// day rollover while the view is alive.
    private func dayLabel(for day: DailyOutlook) -> String {
        let cal = Calendar.current
        let todayString = Self.isoFormatter.string(from: Date())
        let tomorrowString = cal.date(byAdding: .day, value: 1, to: Date())
            .map { Self.isoFormatter.string(from: $0) }
        if day.date == todayString { return "Today" }
        if day.date == tomorrowString { return "Tomorrow" }
        guard let date = Self.isoFormatter.date(from: day.date) else { return day.date }
        let fmt = DateFormatter()
        fmt.dateFormat = "EEE"
        return fmt.string(from: date)
    }

    var body: some View {
        if !outlook.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                HStack(spacing: 8) {
                    ForEach(outlook, id: \.date) { day in
                        OutlookDayChip(day: day, dayLabel: dayLabel(for: day)) {
                            onTap(day)
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppTheme.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

private struct OutlookDayChip: View {
    let day: DailyOutlook
    let dayLabel: String
    let onTap: () -> Void

    private var levelLabel: String {
        switch day.level {
        case 0:  return "Low"
        case 1:  return "Moderate"
        default: return "High"
        }
    }

    private var icon: String {
        switch day.level {
        case 0:  return "leaf.fill"
        case 1:  return "wind"
        default: return "sun.max.fill"
        }
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 3) {
                Text(dayLabel)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.onLevelContainer(day.level).opacity(0.8))
                    .lineLimit(1)
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(AppTheme.levelAccent(day.level))
                Text(levelLabel)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(AppTheme.onLevelContainer(day.level))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if let top = day.topContributors.first {
                    Text(top)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.onLevelContainer(day.level).opacity(0.7))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
            .padding(.vertical, 10)
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity)
            .background(AppTheme.levelContainer(day.level))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
