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
        Self.applyAppearance()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }

    /// Tints UIKit-backed chrome (navigation large titles) with the forest-green
    /// palette so the system bars match the SwiftUI screens.
    private static func applyAppearance() {
        let green = UIColor(AppTheme.primary)
        let appearance = UINavigationBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.titleTextAttributes = [.foregroundColor: green]
        appearance.largeTitleTextAttributes = [.foregroundColor: green]
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
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
        .tint(AppTheme.primary)
    }
}
