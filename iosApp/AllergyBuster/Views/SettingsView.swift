import SwiftUI

struct SettingsView: View {
    @StateObject private var vm = SettingsViewModel()

    var body: some View {
        NavigationStack {
            Form {
                Section("Daily Notification") {
                    DatePicker(
                        "Reminder time",
                        selection: notificationBinding,
                        displayedComponents: .hourAndMinute
                    )
                    Button("Save & Schedule") {
                        vm.saveNotificationTime()
                        vm.requestNotificationPermission()
                    }
                }

                Section("Location") {
                    if vm.locationName.isEmpty {
                        Text("No location set").foregroundStyle(.secondary)
                    } else {
                        Label(vm.locationName, systemImage: "location.fill")
                    }
                    if let err = vm.locationError {
                        Text(err).foregroundStyle(.red).font(.caption)
                    }
                    Button("Use Current Location") { vm.refreshLocation() }
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                    LabeledContent("Build",   value: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—")
                }
            }
            .navigationTitle("Settings")
        }
    }

    private var notificationBinding: Binding<Date> {
        Binding(
            get: {
                var c = DateComponents()
                c.hour   = vm.notificationHour
                c.minute = vm.notificationMinute
                return Calendar.current.date(from: c) ?? Date()
            },
            set: { date in
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                vm.notificationHour   = c.hour   ?? 7
                vm.notificationMinute = c.minute ?? 0
            }
        )
    }
}
