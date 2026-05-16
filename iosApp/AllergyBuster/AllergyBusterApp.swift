import SwiftUI
import BackgroundTasks

@main
struct AllergyBusterApp: App {

    init() {
        BackgroundRefreshScheduler.registerTasks()
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
