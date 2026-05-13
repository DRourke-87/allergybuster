# AllergyBuster — Privacy Policy

**Effective date:** _TBD — set on first publication_

AllergyBuster ("the app") is published by Tarn Labs. This policy explains exactly what data the app handles and where it goes.

## What the app collects

| Data | When | Why |
|------|------|-----|
| **Approximate device location** (city / town level — `ACCESS_COARSE_LOCATION`) | One-shot, only when you open the app or the home-screen widget refreshes | To look up the local pollen forecast |
| **Your daily symptom feedback** (the levels you tap inside the app) | When you submit it | To learn which pollens affect you, on-device only |

The app does **not** collect:

- Names, email addresses, phone numbers, accounts, or identifiers of any kind
- Precise GPS / fine location
- Photos, contacts, microphone, or camera data
- Advertising IDs (no ads SDK is bundled)
- Crash reports or analytics (no analytics SDK is bundled)

## Where the data goes

- **Stays on your device:** All symptom feedback, the on-device learning model, and your last-known location are stored locally using Android's standard `DataStore` and `Room` database. Nothing is sent to a Tarn Labs server — Tarn Labs operates no backend for this app.
- **Sent to Open-Meteo:** Your approximate latitude and longitude (rounded) are sent over HTTPS to `https://air-quality-api.open-meteo.com/` to fetch the pollen forecast. Open-Meteo is a non-commercial weather/air-quality service; their privacy policy is at <https://open-meteo.com/en/terms#privacy>.
- **Android Auto-Backup:** If you have Google's auto-backup enabled on your device (Settings → Google → Backup), Android may include this app's local data in your encrypted Google Drive backup. This is controlled by the OS, not by AllergyBuster.

## Retention & deletion

- All in-app data is removed when you uninstall the app.
- You can also clear data at any time via Settings → Apps → AllergyBuster → Storage → Clear data.

## Children

AllergyBuster is not directed at children under 13 and does not knowingly collect data from them.

## Contact

Questions or requests: open an issue at <https://github.com/DRourke-87/allergybuster/issues>.

## Changes

If this policy changes materially, the in-app About screen will be updated and the effective date above will change.
