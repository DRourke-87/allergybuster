import SwiftUI
import shared

struct HomeView: View {
    @StateObject private var vm = HomeViewModel()
    @State private var selectedType: PollenTypeInfo?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    AppHeader(locationName: vm.locationName)

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
                            .tint(AppTheme.primary)
                        }
                    }

                    LearningProgressCard(progress: vm.learningProgress)

                    Spacer(minLength: 8)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .background(AppTheme.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $selectedType) { type in
                PollenDetailView(
                    type: type,
                    recentForecasts: vm.recentForecasts,
                    userWeights: vm.userWeights
                )
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .tint(AppTheme.primary)
    }
}

// MARK: - Sub-components

private struct AppHeader: View {
    let locationName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("AllergyBuster")
                .font(.title2).fontWeight(.bold)
                .foregroundStyle(AppTheme.primary)
            if !locationName.isEmpty {
                Text(locationName)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct RecommendationCard: View {
    let rec: Recommendation_

    private var container: Color   { AppTheme.levelContainer(rec.level) }
    private var onContainer: Color { AppTheme.onLevelContainer(rec.level) }
    private var accent: Color      { AppTheme.levelAccent(rec.level) }

    private var icon: String {
        switch rec.level {
        case 0:  return "leaf.fill"
        case 1:  return "wind"
        case 2:  return "sun.max.fill"
        default: return "hourglass"
        }
    }

    private var subtitle: String? {
        switch rec.level {
        case 0:  return "Get out and enjoy the fresh air!"
        case 1:  return "Hay fever sufferers may wish to take precautions"
        case 2:  return "High risk for hay fever sufferers today"
        default: return nil
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 64, height: 64)
                .background(accent.opacity(0.15), in: Circle())

            Text(rec.advice)
                .font(.title3).fontWeight(.bold)
                .multilineTextAlignment(.center)
                .foregroundStyle(onContainer)

            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(onContainer.opacity(0.8))
            }
            if rec.isStale {
                Text("Based on yesterday's data")
                    .font(.caption)
                    .foregroundStyle(onContainer.opacity(0.65))
            }
            Text("Pollen information only — not medical advice")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(onContainer.opacity(0.5))
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .background(container)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

private struct ContributorsRow: View {
    let contributors: [String]
    let onTap: (String) -> Void

    var body: some View {
        if !contributors.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("Today's main pollen sources")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Text("Tap any source for details, trends and cross-reactions")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.7))
                HStack(spacing: 8) {
                    ForEach(contributors.prefix(4), id: \.self) { name in
                        Button { onTap(name) } label: {
                            Text(name)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(AppTheme.onPrimaryContainer)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(AppTheme.primaryContainer)
                                .overlay(
                                    Capsule().stroke(AppTheme.primary.opacity(0.3), lineWidth: 1)
                                )
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 4)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppTheme.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

private struct FeedbackSection: View {
    let existingFeedback: DailyFeedback?
    let onSubmit: (Int) -> Void
    @State private var selectedSeverity: Int? = nil

    // Matches the shared model + Android: 0=Fine, 1=Mild, 2=Bad.
    private let labels  = ["Fine", "Mild", "Bad"]
    private let icons   = ["leaf.fill", "wind", "sun.max.fill"]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How are you feeling today?")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Text(existingFeedback != nil
                 ? "Tap again to change your answer."
                 : "Your feedback helps personalise your pollen sensitivity.")
                .font(.caption)
                .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.7))
            HStack(spacing: 8) {
                ForEach(0..<3) { i in
                    let isSelected = selectedSeverity == i
                    Button {
                        selectedSeverity = i
                        onSubmit(i)
                    } label: {
                        Label(labels[i], systemImage: icons[i])
                            .labelStyle(.titleAndIcon)
                            .font(.subheadline.weight(.medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .foregroundStyle(isSelected ? AppTheme.onPrimary : AppTheme.primary)
                            .background(isSelected ? AppTheme.primary : Color.clear)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(AppTheme.primary.opacity(isSelected ? 0 : 0.6), lineWidth: 1.5)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .background(AppTheme.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onAppear { selectedSeverity = existingFeedback.map { Int($0.severity) } }
    }
}

private struct LearningProgressCard: View {
    let progress: LearningProgressState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(progress.isMature ? "Fully personalised" : "Learning your allergies")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Spacer()
                Text("\(Int(progress.progressFraction * 100))%")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.7))
            }
            HStack(spacing: 14) {
                LearningTreeView(progress: progress.progressFraction)
                    .frame(width: 88)
                VStack(alignment: .leading, spacing: 6) {
                    ProgressView(value: Double(progress.progressFraction))
                        .tint(AppTheme.primary)
                    Text(progress.isMature
                         ? "Fully personalised to your responses."
                         : "Day \(progress.daysElapsed) of 30 · \(progress.feedbackCount) check-ins")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.8))
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct LoadingCard: View {
    var message: String = "Fetching pollen data…"

    var body: some View {
        VStack(spacing: 16) {
            ProgressView().tint(AppTheme.primary)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(AppTheme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(AppTheme.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
