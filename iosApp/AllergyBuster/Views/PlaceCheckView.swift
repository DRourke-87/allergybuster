import SwiftUI
import shared

struct PlaceCheckView: View {
    @StateObject private var vm = PlaceCheckViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var selectedDate: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    searchField
                    content
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Check another location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .tint(AppTheme.primary)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AppTheme.onSurfaceVariant)
            TextField("Search a city or town…", text: $vm.query)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
        }
        .padding(12)
        .background(AppTheme.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var content: some View {
        switch vm.state {
        case .idle:
            if vm.results.isEmpty {
                Text("Planning a trip? Search a place to see its pollen outlook, scored with your own sensitivity profile.")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            } else {
                resultsList
            }
        case .loading(let place):
            VStack(spacing: 12) {
                ProgressView().tint(AppTheme.primary)
                Text("Fetching forecast for \(place.displayName)…")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 40)
        case .error(let place):
            VStack(spacing: 12) {
                Text("Couldn't load the forecast for \(place.displayName). Check your connection and try again.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Button("Retry") { vm.retry() }
                    .buttonStyle(.bordered)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 40)
        case .loaded(let place, let outlook):
            placeOutlook(place: place, outlook: outlook)
        }
    }

    private var resultsList: some View {
        VStack(spacing: 6) {
            ForEach(Array(vm.results.enumerated()), id: \.offset) { _, place in
                Button { vm.select(place) } label: {
                    HStack(spacing: 10) {
                        Text("📍")
                        VStack(alignment: .leading, spacing: 2) {
                            Text(place.name)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(AppTheme.onBackground)
                            let detail = [place.region, place.country]
                                .filter { !$0.isEmpty }
                                .joined(separator: ", ")
                            if !detail.isEmpty {
                                Text(detail)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.onSurfaceVariant)
                            }
                        }
                        Spacer()
                    }
                    .padding(12)
                    .background(AppTheme.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func placeOutlook(place: PlaceResult, outlook: [DailyOutlook]) -> some View {
        let selectedDay = outlook.first(where: { $0.date == selectedDate }) ?? outlook.first

        Text("📍 \(place.displayName)")
            .font(.headline)
            .foregroundStyle(AppTheme.onBackground)

        OutlookStripView(
            outlook: outlook,
            title: "Pollen outlook (local days)",
            startsToday: true
        ) { day in
            selectedDate = day.date
        }

        if let day = selectedDay {
            DayBreakdownView(day: day)
        }

        Text("Based on your sensitivity profile — pollen information only, not medical advice.")
            .font(.caption)
            .foregroundStyle(AppTheme.onSurfaceVariant.opacity(0.7))
    }
}

private struct DayBreakdownView: View {
    let day: DailyOutlook

    private var active: [(type: PollenTypeInfo, level: Int32)] {
        PollenTypeInfo.allCases
            .filter { $0.raw(from: day.pollen) > 0 }
            .map { type in
                let norm = type.normalise(type.raw(from: day.pollen))
                let level: Int32 = norm < 1 ? 0 : (norm < 2 ? 1 : 2)
                return (type, level)
            }
            .sorted { $0.1 > $1.1 }
    }

    private func levelLabel(_ level: Int32) -> String {
        switch level {
        case 0:  return "Low"
        case 1:  return "Moderate"
        default: return "High"
        }
    }

    var body: some View {
        if active.isEmpty {
            Text("No significant pollen on \(day.date).")
                .font(.subheadline)
                .foregroundStyle(AppTheme.onSurfaceVariant)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Active pollen on \(day.date)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 130), spacing: 8)],
                    alignment: .leading,
                    spacing: 8
                ) {
                    ForEach(active, id: \.type) { item in
                        let accent = AppTheme.levelAccent(item.level)
                        Text("\(item.type.icon) \(item.type.displayName) · \(levelLabel(item.level))")
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .foregroundStyle(AppTheme.onBackground)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(accent.opacity(0.18))
                            .overlay(Capsule().stroke(accent.opacity(0.6), lineWidth: 1))
                            .clipShape(Capsule())
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
