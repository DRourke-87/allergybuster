import Foundation
import CoreLocation

/// Thin wrapper around CLLocationManager, shared by the launch/immediate-fetch path and
/// the Settings screen. Configured for fast, city-level fixes (the pollen API only needs
/// coarse coordinates) and exposes an async `currentLocation(timeout:)` analogous to
/// Android's `getLastKnownLocation`.
@MainActor
final class LocationService: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var pending: CheckedContinuation<CLLocation?, Never>?
    private var lastStatus: CLAuthorizationStatus?

    /// Treat a cached fix newer than this as good enough to return instantly.
    private let cacheMaxAge: TimeInterval = 15 * 60

    /// Fired once when the user transitions into an authorized status, so the app can pull
    /// a fresh forecast immediately after a permission grant (onboarding or Settings).
    var onAuthorizationGranted: (() -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        // City-level accuracy resolves in ~1s from a network/cell fix instead of waiting
        // for GPS convergence. Android only uses COARSE permission for the same purpose.
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    var authorizationStatus: CLAuthorizationStatus { manager.authorizationStatus }

    func requestAuthorization() {
        manager.requestWhenInUseAuthorization()
    }

    /// Returns the device location, preferring a recent cached fix and otherwise issuing a
    /// one-shot request bounded by `timeout`. Never prompts for permission — callers decide
    /// when to request authorization. Returns the (possibly stale) cached fix or nil when
    /// unauthorized or on timeout/failure, so callers always make progress.
    func currentLocation(timeout: TimeInterval = 3) async -> CLLocation? {
        if let cached = manager.location,
           Date().timeIntervalSince(cached.timestamp) < cacheMaxAge {
            return cached
        }

        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            break
        default:
            return manager.location
        }

        return await withCheckedContinuation { (continuation: CheckedContinuation<CLLocation?, Never>) in
            // Defensively resolve any orphaned request so we never leak a continuation.
            if let prior = pending {
                pending = nil
                prior.resume(returning: nil)
            }
            pending = continuation
            manager.requestLocation()
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                guard let self else { return }
                self.resumePending(with: self.manager.location)
            }
        }
    }

    /// Resolves a human-readable place name off the critical path. Returns nil on failure
    /// so callers can fall back to a cached name or a coordinate string.
    func reverseGeocode(_ location: CLLocation) async -> String? {
        let placemarks = try? await CLGeocoder().reverseGeocodeLocation(location)
        return placemarks?.first?.locality ?? placemarks?.first?.country
    }

    private func resumePending(with location: CLLocation?) {
        guard let continuation = pending else { return }
        pending = nil
        continuation.resume(returning: location)
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in self.resumePending(with: locations.last) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if (error as? CLError)?.code == .locationUnknown { return }
        Task { @MainActor in self.resumePending(with: self.manager.location) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            let status = manager.authorizationStatus
            let previous = self.lastStatus
            self.lastStatus = status
            let authorized = status == .authorizedWhenInUse || status == .authorizedAlways
            let wasAuthorized = previous == .authorizedWhenInUse || previous == .authorizedAlways
            // Only react to a real grant transition — skip the initial delegate callback
            // (previous == nil) so authorized users don't trigger a redundant fetch on launch.
            if authorized, previous != nil, !wasAuthorized {
                self.onAuthorizationGranted?()
            }
        }
    }
}
