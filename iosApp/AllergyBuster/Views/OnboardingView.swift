import SwiftUI

private struct OnboardingPage: Identifiable {
    let id = UUID()
    let emoji: String
    let title: String
    let body: String
}

private let onboardingPages: [OnboardingPage] = [
    OnboardingPage(
        emoji: "🌿",
        title: "Your daily pollen risk",
        body: "Every morning AllergyBuster gives you a clear pollen risk level for your exact location, so you can plan your day with confidence."
    ),
    OnboardingPage(
        emoji: "🌱",
        title: "It learns your allergies",
        body: "Tell us how you felt each day. AllergyBuster quietly tunes a personal sensitivity weight for every pollen type — after about 30 days the advice reflects your body, not just the raw counts."
    ),
    OnboardingPage(
        emoji: "🔔",
        title: "Gentle reminders",
        body: "Get a single morning alert and an at-a-glance look at today's level. Everything stays private and on your device."
    )
]

struct OnboardingView: View {
    /// Called when the user skips or finishes the walkthrough.
    let onFinish: () -> Void

    @State private var selection = 0

    private var isLastPage: Bool { selection == onboardingPages.count - 1 }

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Button("Skip", action: onFinish)
                    .padding()
            }

            TabView(selection: $selection) {
                ForEach(Array(onboardingPages.enumerated()), id: \.element.id) { index, page in
                    VStack(spacing: 16) {
                        Text(page.emoji)
                            .font(.system(size: 64))
                        Text(page.title)
                            .font(.title.weight(.bold))
                            .foregroundStyle(AppTheme.primary)
                            .multilineTextAlignment(.center)
                        Text(page.body)
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 32)
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            Button(action: {
                if isLastPage {
                    onFinish()
                } else {
                    withAnimation { selection += 1 }
                }
            }) {
                Text(isLastPage ? "Get started" : "Next")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.primary)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .background(AppTheme.background)
    }
}
