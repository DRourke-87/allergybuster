# Play Console Dashboard

Private dashboard for Google Play Console bulk reports.

This reads aggregate Play Console reports outside the app. It does not add app telemetry and does not change what the mobile app collects.

## What It Can Show

From the installs reports under:

```text
gs://pubsite_prod_7020116138428415210/stats/installs/
```

the dashboard can show:

- install and uninstall trends
- active device installs
- current user installs, when present in the report
- country split, including UK, Ireland, and US
- app version split, when app-version reports are available
- device / OS / carrier / language split, when those report dimensions are available
- seasonal date-window views, such as March to August

DAU, MAU, notification permission rate, widget impressions, and high-pollen forecast impressions are not reliably available from these bulk install reports. Those require either Play Console UI exports or privacy-preserving first-party app metrics.

## Setup

Create a virtual environment:

```powershell
cd tools\play-console-dashboard
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Run:

```powershell
streamlit run play_console_dashboard.py
```

## Google Cloud Auth

Use one of these options:

1. Application default credentials:

```powershell
gcloud auth application-default login
```

If Google asks for a project, use any Google Cloud project that your account can access. The dashboard only needs a project to initialise the Storage client; the report data comes from the Play Console bucket.

2. Service account JSON stored locally, never committed. Either set an environment variable:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\service-account.json"
```

or paste the JSON path into the dashboard sidebar.

The Google identity needs read access to the Play Console report bucket.

You can also skip GCS auth and upload CSV files manually in the dashboard.

If you see `DefaultCredentialsError`, no Google credentials are configured for this machine. Run `gcloud auth application-default login`, set `GOOGLE_APPLICATION_CREDENTIALS`, or use the sidebar service-account path.
