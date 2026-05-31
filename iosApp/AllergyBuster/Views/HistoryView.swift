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
        .tint(AppTheme.primary)
    }
}

private struct HistoryRow: View {
    let day: HistoryDay

    private var levelColor: Color {
        AppTheme.levelAccent(day.recommendation.level)
    }

    private var levelIcon: String {
        switch day.recommendation.level {
        case 0: "leaf.fill"; case 1: "wind"; case 2: "sun.max.fill"; default: "hourglass"
        }
    }

    private var formattedDate: String {
        let iso = day.recommendation.date
        let parts = iso.split(separator: "-")
        guard parts.count == 3 else { return iso }
        return "\(parts[2])/\(parts[1])/\(parts[0])"
    }

    private var topContributors: [String] { day.recommendation.topContributors }

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
                    Text(topContributors.prefix(2).joined(separator: ", "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            if let fb = day.feedback {
                VStack(alignment: .trailing, spacing: 2) {
                    Circle()
                        .fill(severityColor(Int(fb.severity)))
                        .frame(width: 10, height: 10)
                    Text(severityLabel(Int(fb.severity)))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func severityColor(_ s: Int) -> Color {
        switch s { case 0: AppTheme.primary; case 1: AppTheme.tertiary; default: AppTheme.error }
    }

    private func severityLabel(_ s: Int) -> String {
        switch s { case 0: "Fine"; case 1: "Mild"; default: "Bad" }
    }
}
