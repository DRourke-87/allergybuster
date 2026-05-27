# Play Console Sponsorship Dashboard

Static, sponsor-facing dashboard generated from local Google Play Console CSV exports.

This does not connect to Google Cloud, does not need a billing account, and does not add app telemetry. It uses aggregate CSV exports only.

## Generate

Put the Play Console install CSV exports in this folder, then run:

```powershell
cd tools\play-console-dashboard
python build_dashboard.py
```

Open:

```text
tools/play-console-dashboard/dist/index.html
```

## Input Files

The generator reads files matching:

```text
stats_installs_installs_*.csv
```

It ignores `supported_devices.csv`.

CSV inputs and generated output are intentionally ignored by Git because they are local business metrics.

## What It Shows

- sponsor-ready headline metrics
- active device installs
- user installs and net installs
- install momentum over time
- all-country install split
- app version adoption
- Android version, language, device, and carrier breakdowns
- privacy-safe commercial package framing

The dashboard is designed as a sponsorship/reporting artefact, not an internal analytics tool.
