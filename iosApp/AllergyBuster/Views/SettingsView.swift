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
                        if vm.latitude != 0 || vm.longitude != 0 {
                            LabeledContent(
                                "Coordinates",
                                value: String(format: "%.4f, %.4f", vm.latitude, vm.longitude)
                            )
                            .font(.caption)
                        }
                    }
                    if let err = vm.locationError {
                        Text(err).foregroundStyle(AppTheme.error).font(.caption)
                    }
                    Button("Use Current Location") { vm.refreshLocation() }
                }

                Section("How AllergyBuster works") {
                    InfoRow(
                        icon: "brain.head.profile",
                        title: "How it learns",
                        detail: "Each day you rate how you felt. Over your first 30 days AllergyBuster tunes a personal sensitivity weight for every pollen type, so the risk level reflects your body, not just the raw counts."
                    )
                    InfoRow(
                        icon: "cloud.sun.fill",
                        title: "Pollen data",
                        detail: "Hourly pollen counts come from the Open-Meteo air-quality API for your location and are refreshed each morning."
                    )
                    InfoRow(
                        icon: "cross.case.fill",
                        title: "Medical disclaimer",
                        detail: "AllergyBuster provides pollen information only and is not a medical device. It does not diagnose or treat any condition — consult a healthcare professional for medical advice."
                    )
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                    LabeledContent("Build",   value: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—")
                }
            }
            .navigationTitle("Settings")
        }
        .tint(AppTheme.primary)
    }

    private struct InfoRow: View {
        let icon: String
        let title: String
        let detail: String
        var body: some View {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(AppTheme.primary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.semibold))
                    Text(detail).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 2)
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
