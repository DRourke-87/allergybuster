import WidgetKit
import SwiftUI
import shared

// MARK: - Timeline Entry

struct PollenEntry: TimelineEntry {
    let date: Date
    let level: Int          // 0=low 1=moderate 2=high, -1=unknown
    let topContributors: [String]
}

// MARK: - Timeline Provider

struct PollenTimelineProvider: TimelineProvider {

    func placeholder(in context: Context) -> PollenEntry {
        PollenEntry(date: Date(), level: -1, topContributors: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (PollenEntry) -> Void) {
        completion(latestEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PollenEntry>) -> Void) {
        let entry = latestEntry()
        // Refresh at the next 6 am
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        comps.hour = 6; comps.minute = 0
        let todaySix = Calendar.current.date(from: comps)!
        let next = todaySix > Date() ? todaySix : Calendar.current.date(byAdding: .day, value: 1, to: todaySix)!
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func latestEntry() -> PollenEntry {
        let db = AllergyBusterDatabase(driver: DatabaseDriverFactory().createDriver())
        let today = isoToday()
        guard let row = db.recommendationQueries.getForDate(today).executeAsOneOrNull() else {
            return PollenEntry(date: Date(), level: -1, topContributors: [])
        }
        let contributors: [String]
        if let data = row.topContributors.data(using: .utf8),
           let arr = try? JSONDecoder().decode([String].self, from: data) {
            contributors = arr
        } else {
            contributors = []
        }
        return PollenEntry(date: Date(), level: Int(row.level), topContributors: contributors)
    }

    private func isoToday() -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: Date())
    }
}

// MARK: - Widget View

struct PollenWidgetView: View {
    let entry: PollenEntry
    @Environment(\.widgetFamily) var family

    private var bgColor: Color {
        switch entry.level {
        case 0: Color.green.opacity(0.2)
        case 1: Color.yellow.opacity(0.25)
        case 2: Color.orange.opacity(0.25)
        default: Color(.systemGray5)
        }
    }

    private var emoji: String  { switch entry.level { case 0: "✅"; case 1: "⚠️"; case 2: "🟠"; default: "⏳" } }
    private var label: String  { switch entry.level { case 0: "Low"; case 1: "Moderate"; case 2: "High"; default: "…" } }

    var body: some View {
        ZStack {
            ContainerRelativeShape().fill(bgColor)
            VStack(spacing: 4) {
                Text(emoji).font(.system(size: 28))
                Text("\(label) pollen")
                    .font(.caption).fontWeight(.bold)
                ForEach(entry.topContributors.prefix(2), id: \.self) { name in
                    Text(name).font(.system(size: 10))
                }
            }
            .padding(8)
        }
    }
}

// MARK: - Widget Configuration

struct AllergyBusterWidget: Widget {
    let kind = "AllergyBusterWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PollenTimelineProvider()) { entry in
            PollenWidgetView(entry: entry)
        }
        .configurationDisplayName("AllergyBuster")
        .description("Shows today's pollen risk level at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
