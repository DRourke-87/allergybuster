import SwiftUI
import shared

struct HomeView: View {
    @StateObject private var vm = HomeViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let rec = vm.todayRecommendation {
                        RecommendationCard(rec: rec)
                        ContributorsRow(contributors: rec.topContributors)
                        FeedbackSection(existingFeedback: vm.todayFeedback) { severity in
                            vm.submitFeedback(severity: severity)
                        }
                    } else {
                        LoadingCard()
                    }

                    if !vm.recentForecasts.isEmpty {
                        ForecastMiniChart(forecasts: vm.recentForecasts)
                    }

                    LearningProgressCard(progress: vm.learningProgress)

                    Spacer(minLength: 8)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .background(Color(.systemBackground))
            .navigationTitle("AllergyBuster")
            .navigationBarTitleDisplayMode(.large)
        }
    }
}

// MARK: - Sub-components

private struct RecommendationCard: View {
    let rec: Recommendation

    var levelColor: Color {
        switch rec.level {
        case 0: return .green.opacity(0.15)
        case 1: return .yellow.opacity(0.2)
        case 2: return .orange.opacity(0.2)
        default: return Color(.systemGray6)
        }
    }

    var emoji: String {
        switch rec.level { case 0: "✅"; case 1: "⚠️"; case 2: "🟠"; default: "⏳" }
    }

    var levelLabel: String {
        switch rec.level { case 0: "Low Risk"; case 1: "Moderate Risk"; case 2: "High Risk"; default: "Unknown" }
    }

    var body: some View {
        VStack(spacing: 12) {
            Text(emoji).font(.system(size: 52))
            Text(levelLabel)
                .font(.title2).fontWeight(.bold)
            Text(rec.advice)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            if rec.isStale {
                Label("Based on yesterday's data", systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(levelColor)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct ContributorsRow: View {
    let contributors: [String]

    var body: some View {
        if !contributors.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Top Contributors")
                    .font(.headline)
                HStack(spacing: 8) {
                    ForEach(contributors.prefix(3), id: \.self) { name in
                        Text(name)
                            .font(.caption)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color(.systemGray5))
                            .clipShape(Capsule())
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct FeedbackSection: View {
    let existingFeedback: DailyFeedback?
    let onSubmit: (Int) -> Void
    @State private var selectedSeverity: Int? = nil

    private let labels = ["None", "Mild", "Moderate", "Severe"]
    private let colors: [Color] = [.green, .yellow, .orange, .red]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How are you feeling today?").font(.headline)
            if let fb = existingFeedback {
                let label = labels[min(fb.severity, 3)]
                Label("Logged: \(label)", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .font(.subheadline)
            }
            LazyVGrid(columns: Array(repeating: .init(.flexible()), count: 4), spacing: 8) {
                ForEach(0..<4) { i in
                    Button {
                        selectedSeverity = i
                        onSubmit(i)
                    } label: {
                        VStack(spacing: 4) {
                            Circle()
                                .fill(colors[i].opacity(selectedSeverity == i ? 1.0 : 0.25))
                                .frame(width: 40, height: 40)
                                .overlay(
                                    Text("\(i)").font(.headline).foregroundStyle(selectedSeverity == i ? .white : .primary)
                                )
                            Text(labels[i]).font(.caption2)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onAppear { selectedSeverity = existingFeedback.map { Int($0.severity) } }
    }
}

private struct ForecastMiniChart: View {
    let forecasts: [DailyPollen]

    private let colors: [Color] = [.green, .yellow, .orange, .red, .purple]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("7-Day Forecast").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(forecasts.suffix(7), id: \.date) { day in
                        let total = day.alderMax + day.birchMax + day.grassMax + day.mugwortMax
                        let barHeight = max(4, min(60, CGFloat(total / 50) * 60))
                        VStack(spacing: 4) {
                            Spacer(minLength: 0)
                            RoundedRectangle(cornerRadius: 3)
                                .fill(Color.green.opacity(0.7))
                                .frame(width: 24, height: barHeight)
                            Text(shortDate(day.date))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        .frame(height: 80, alignment: .bottom)
                    }
                }
            }
        }
        .padding(16)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func shortDate(_ iso: String) -> String {
        let parts = iso.split(separator: "-")
        guard parts.count == 3 else { return iso }
        return "\(parts[2])/\(parts[1])"
    }
}

private struct LearningProgressCard: View {
    let progress: LearningProgressState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(progress.isMature ? "🌳 Learning Complete" : "🌱 Learning…")
                    .font(.headline)
                Spacer()
                Text("\(Int(progress.progressFraction * 100))%")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ProgressView(value: Double(progress.progressFraction))
                .tint(.green)
            Text("\(progress.feedbackCount) feedback entries · \(progress.daysElapsed) days")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct LoadingCard: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text("Fetching pollen data…")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
