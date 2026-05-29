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
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("History")
        }
    }
}

private struct HistoryRow: View {
    let day: HistoryDay

    private var levelColor: Color {
        switch day.recommendation.level {
        case 0: .green; case 1: .yellow; case 2: .orange; default: .gray
        }
    }

    private var levelEmoji: String {
        switch day.recommendation.level { case 0: "✅"; case 1: "⚠️"; case 2: "🟠"; default: "⏳" }
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
            Text(levelEmoji)
                .font(.title2)
                .frame(width: 36)

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
        switch s { case 0: .green; case 1: .orange; default: .red }
    }

    private func severityLabel(_ s: Int) -> String {
        switch s { case 0: "Fine"; case 1: "Mild"; default: "Bad" }
    }
}
