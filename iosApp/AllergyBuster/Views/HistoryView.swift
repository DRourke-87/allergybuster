import SwiftUI
import shared

struct HistoryView: View {
    @StateObject private var vm = HistoryViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if vm.historyDays.isEmpty {
                    if #available(iOS 17, *) {
                        ContentUnavailableView(
                            "No History Yet",
                            systemImage: "calendar",
                            description: Text("Your pollen history will appear here as you use the app.")
                        )
                    } else {
                        VStack(spacing: 12) {
                            Image(systemName: "calendar")
                                .font(.largeTitle)
                                .foregroundStyle(.secondary)
                            Text("No History Yet")
                                .font(.headline)
                            Text("Your pollen history will appear here as you use the app.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                    }
                } else {
                    List(vm.historyDays, id: \.recommendation.date) { day in
                        HistoryRow(day: day)
                            .listRowBackground(AppTheme.surfaceVariant)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("History")
        }
        .background(AppTheme.background.ignoresSafeArea())
        .tint(AppTheme.primary)
    }
}

private struct HistoryRow: View {
    let day: HistoryDay

    private var levelColor: Color { AppTheme.levelAccent(day.recommendation.level) }

    private var levelIcon: String {
        switch day.recommendation.level {
        case 0: "leaf.fill"; case 1: "wind"; case 2: "sun.max.fill"; default: "hourglass"
        }
    }

    private var levelLabel: String {
        switch day.recommendation.level {
        case 0: "Clear"; case 1: "Moderate"; default: "High"
        }
    }

    private var formattedDate: String {
        let parts = day.recommendation.date.split(separator: "-")
        guard parts.count == 3 else { return day.recommendation.date }
        return "\(parts[2])/\(parts[1])/\(parts[0])"
    }

    private var topContributors: [String] {
        Array(day.recommendation.topContributors.prefix(2))
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: levelIcon)
                .font(.title3)
                .foregroundStyle(levelColor)
                .frame(width: 36, height: 36)
                .background(levelColor.opacity(0.15), in: Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(formattedDate).font(.subheadline).fontWeight(.semibold)
                if !topContributors.isEmpty {
                    Text(topContributors.joined(separator: ", "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if !day.recommendation.locationName.isEmpty {
                    Text("📍 \(day.recommendation.locationName)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                HistoryChip(label: levelLabel, color: levelColor)
                if let fb = day.feedback {
                    let sev = Int(fb.severity)
                    let label = sev == 0 ? "🌿 Fine" : sev == 1 ? "🌾 Mild" : "🌻 Bad"
                    let color: Color = sev == 0 ? AppTheme.primary : sev == 1 ? AppTheme.tertiary : AppTheme.error
                    HistoryChip(label: label, color: color)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

private struct HistoryChip: View {
    let label: String
    let color: Color
    var body: some View {
        Text(label)
            .font(.caption2)
            .fontWeight(.medium)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
    }
}
