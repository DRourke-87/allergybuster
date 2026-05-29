import SwiftUI
import shared

struct HomeView: View {
    @StateObject private var vm = HomeViewModel()
    @State private var selectedType: PollenTypeInfo?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if !vm.locationName.isEmpty {
                        Label(vm.locationName, systemImage: "location.fill")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if let rec = vm.todayRecommendation {
                        RecommendationCard(rec: rec)
                        ContributorsRow(contributors: rec.topContributors) { name in
                            selectedType = PollenTypeInfo.from(displayName: name)
                        }
                        FeedbackSection(existingFeedback: vm.todayFeedback) { severity in
                            vm.submitFeedback(severity: severity)
                        }
                    } else {
                        LoadingCard(
                            message: vm.showRetry
                                ? "Still fetching — tap retry if this seems stuck."
                                : "Fetching pollen data…"
                        )
                        if vm.showRetry {
                            Button(vm.isRetrying ? "Retrying…" : "Retry") {
                                vm.retryForecastFetch()
                            }
                            .disabled(vm.isRetrying)
                            .buttonStyle(.bordered)
                        }
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
            .sheet(item: $selectedType) { type in
                PollenDetailView(
                    type: type,
                    recentForecasts: vm.recentForecasts,
                    userWeights: vm.userWeights
                )
            }
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
    let onTap: (String) -> Void

    var body: some View {
        if !contributors.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Today's main pollen sources")
                    .font(.headline)
                Text("Tap any source for details, trends and cross-reactions")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    ForEach(contributors.prefix(4), id: \.self) { name in
                        Button { onTap(name) } label: {
                            Text(name)
                                .font(.caption)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.accentColor.opacity(0.15))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
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

    // Matches the shared model + Android: 0=Fine, 1=Mild, 2=Bad.
    private let labels  = ["🌿 Fine", "🌾 Mild", "🌻 Bad"]
    private let colors: [Color] = [.green, .orange, .red]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How are you feeling today?").font(.headline)
            if existingFeedback != nil {
                Text("Tap again to change your answer.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("Your feedback helps personalise your pollen sensitivity.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                ForEach(0..<3) { i in
                    Button {
                        selectedSeverity = i
                        onSubmit(i)
                    } label: {
                        Text(labels[i])
                            .font(.subheadline.weight(.medium))
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(colors[i].opacity(selectedSeverity == i ? 0.9 : 0.15))
                            .foregroundStyle(selectedSeverity == i ? .white : .primary)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
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
                        let total = day.alderMax + day.birchMax + day.grassMax
                                  + day.mugwortMax + day.oliveMax + day.ragweedMax
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
            LearningTreeView(progress: progress.progressFraction)
            ProgressView(value: Double(progress.progressFraction))
                .tint(.green)
            Text(progress.isMature
                 ? "Fully personalised to your responses."
                 : "\(progress.feedbackCount) feedback entries · \(progress.daysElapsed) days")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct LoadingCard: View {
    var message: String = "Fetching pollen data…"

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
