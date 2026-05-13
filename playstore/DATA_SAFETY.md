# Play Console — Data Safety form answers

Paste these into **Play Console → App content → Data safety**. They mirror
the actual code in `data/location/LocationProvider.kt`, `di/NetworkModule.kt`,
and `data/local/`.

## Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (location) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only — only Open-Meteo endpoint, no cleartext) |
| Do you provide a way for users to request that their data be deleted? | **Yes** — uninstall removes everything; in-app Settings → Clear data also clears it |

## Data types

### Location → Approximate location

| Field | Answer |
|---|---|
| Collected | **Yes** |
| Shared | **No** (sent only to Open-Meteo for the forecast lookup, not shared with any third party for their own use) |
| Processed ephemerally | **No** (last-known coarse location is cached on device) |
| Required or optional | **Optional** — app degrades to "Set location manually" if denied |
| Purposes | **App functionality** |

Add nothing else. The app does **not** collect:

- Personal info (name / email / phone / address / user ID / etc.)
- Financial info
- Health and fitness
- Messages
- Photos or videos
- Audio
- Files and docs
- Calendar
- Contacts
- App activity (in-app search / interactions)
- Web browsing
- App info & performance (no crash reporting / analytics)
- Device or other IDs (no AAID / IMEI / etc.)

## Security practices

- Data encrypted in transit: **Yes**
- Users can request data deletion: **Yes** (uninstall or clear app data)
- Independent security review: **No**
- Committed to Play Families Policy: **No** (app is not directed at children)

## Notes

- The pollen forecast service (Open-Meteo) is referenced in the privacy policy.
- There is no advertising SDK, no analytics SDK, and no third-party network
  calls besides `https://air-quality-api.open-meteo.com/`.
