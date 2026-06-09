import SwiftUI
import BackgroundTasks

@main
struct AllergyBusterApp: App {

    init() {
        BackgroundRefreshScheduler.registerTasks()
        BackgroundRefreshScheduler.scheduleNextRefresh()
        // Pull a fresh forecast as soon as the user grants location (onboarding or Settings).
        ServiceContainer.shared.locationService.addAuthorizationGrantObserver {
            Task { await BackgroundRefreshScheduler.runImmediateFetch(allowFreshLocation: true) }
        }
        // Keep the daily reminder scheduled for users who already granted
        // notification permission (it survives upgrades and time changes).
        NotificationScheduler.rescheduleIfAuthorized()
        // Kick an immediate fetch on launch so the home screen has data without
        // waiting for the next scheduled background refresh (mirrors Android's
        // enqueueImmediatePollenFetch at startup).
        Task { await BackgroundRefreshScheduler.runImmediateFetch(allowFreshLocation: true) }
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
        let bg = UIColor(AppTheme.background)

        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithOpaqueBackground()
        navAppearance.backgroundColor = bg
        navAppearance.titleTextAttributes = [.foregroundColor: green]
        navAppearance.largeTitleTextAttributes = [.foregroundColor: green]
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance
        UINavigationBar.appearance().compactAppearance = navAppearance

        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithOpaqueBackground()
        tabAppearance.backgroundColor = bg
        UITabBar.appearance().standardAppearance = tabAppearance
        UITabBar.appearance().scrollEdgeAppearance = tabAppearance
    }
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("themeMode", store: UserDefaults(suiteName: AppGroupId))
    private var themeMode: AppThemeMode = .system
    @AppStorage("onboardingDone", store: UserDefaults(suiteName: AppGroupId))
    private var onboardingDone = false

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
        .background(AppTheme.background.ignoresSafeArea())
        .preferredColorScheme(themeMode.colorScheme)
        .onChange(of: scenePhase) { phase in
            guard phase == .active, onboardingDone else { return }
            Task { await BackgroundRefreshScheduler.refreshOnForeground() }
        }
        .fullScreenCover(isPresented: Binding(
            get: { !onboardingDone },
            set: { presented in onboardingDone = !presented }
        )) {
            OnboardingView(onFinish: { onboardingDone = true })
                .preferredColorScheme(themeMode.colorScheme)
        }
    }
}
