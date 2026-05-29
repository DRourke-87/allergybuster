import SwiftUI
import BackgroundTasks

@main
struct AllergyBusterApp: App {

    init() {
        BackgroundRefreshScheduler.registerTasks()
        BackgroundRefreshScheduler.scheduleNextRefresh()
        // Kick an immediate fetch on launch so the home screen has data without
        // waiting for the next scheduled background refresh (mirrors Android's
        // enqueueImmediatePollenFetch at startup).
        Task { await BackgroundRefreshScheduler.runImmediateFetch() }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Today", systemImage: "leaf.fill") }
            HistoryView()
                .tabItem { Label("History", systemImage: "calendar") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
    }
}
