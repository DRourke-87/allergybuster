import Foundation
import shared

@MainActor
final class PlaceCheckViewModel: ObservableObject {
    enum OutlookState {
        case idle
        case loading(PlaceResult)
        case loaded(PlaceResult, [DailyOutlook])
        case error(PlaceResult)
    }

    @Published var query: String = "" {
        didSet { if query != oldValue { queryChanged() } }
    }
    @Published var results: [PlaceResult] = []
    @Published var state: OutlookState = .idle

    private let searchUseCase: SearchPlacesUseCase
    private let checkUseCase: CheckLocationOutlookUseCase
    private var searchTask: Task<Void, Never>?

    init(container: ServiceContainer = .shared) {
        searchUseCase = container.searchPlacesUseCase
        checkUseCase  = container.checkLocationOutlookUseCase
    }

    deinit {
        searchTask?.cancel()
    }

    private func queryChanged() {
        state = .idle
        searchTask?.cancel()
        let q = query
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            if Task.isCancelled { return }
            let found = (try? await self?.searchUseCase.invoke(query: q)) ?? []
            if !Task.isCancelled { self?.results = found }
        }
    }

    func select(_ place: PlaceResult) {
        state = .loading(place)
        Task {
            do {
                let outlook = try await checkUseCase.invoke(lat: place.latitude, lon: place.longitude)
                state = .loaded(place, outlook)
            } catch {
                state = .error(place)
            }
        }
    }

    func retry() {
        if case .error(let place) = state { select(place) }
    }
}
