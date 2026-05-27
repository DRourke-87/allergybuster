from __future__ import annotations

import base64
import csv
import html
import json
import math
import re
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parents[1]
OUTPUT_DIR = ROOT / "dist"
OUTPUT_FILE = OUTPUT_DIR / "index.html"
APP_ICON = REPO_ROOT / "docs" / "images" / "icon.png"
REPORT_PATTERN = "stats_installs_installs_*.csv"

BRAND = {
    "forest": "#2E6B1A",
    "forest_dark": "#17370E",
    "leaf": "#4EA534",
    "leaf_soft": "#B4DFAA",
    "bark": "#7A5840",
    "gold": "#A07828",
    "gold_soft": "#FDEFC5",
    "parchment": "#F8F5ED",
    "surface": "#FDFAF4",
    "sage": "#E2ECD8",
    "ink": "#0E1A09",
    "muted": "#5A6A52",
    "outline": "#DDE8D3",
    "terracotta": "#B53B2A",
}

COUNTRY_NAMES = {
    "AE": "United Arab Emirates",
    "AU": "Australia",
    "CA": "Canada",
    "DE": "Germany",
    "ES": "Spain",
    "FR": "France",
    "GB": "United Kingdom",
    "IE": "Ireland",
    "IN": "India",
    "NL": "Netherlands",
    "NZ": "New Zealand",
    "US": "United States",
    "ZA": "South Africa",
}


@dataclass(frozen=True)
class Report:
    name: str
    dimension: str
    rows: list[dict]


def main() -> None:
    reports = load_reports(ROOT)
    if not reports:
        raise SystemExit(f"No Play Console CSV files found in {ROOT}")

    data = build_dashboard_data(reports)
    html_text = render_html(data)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text(html_text, encoding="utf-8")
    print(f"Wrote {OUTPUT_FILE}")


def load_reports(folder: Path) -> list[Report]:
    reports: list[Report] = []
    for path in sorted(folder.glob(REPORT_PATTERN)):
        if path.name.lower() == "supported_devices.csv":
            continue
        rows = read_csv(path)
        if not rows:
            continue
        reports.append(Report(name=path.name, dimension=infer_dimension(path.name, rows[0].keys()), rows=rows))
    return reports


def read_csv(path: Path) -> list[dict]:
    content = path.read_bytes()
    text = decode_text(content)
    separator = detect_separator(text)
    reader = csv.DictReader(text.splitlines(), delimiter=separator)
    rows: list[dict] = []
    for raw in reader:
        row = {normalise_key(key): normalise_value(value) for key, value in raw.items() if key is not None}
        if any(value not in ("", None) for value in row.values()):
            rows.append(row)
    return rows


def decode_text(content: bytes) -> str:
    for encoding in candidate_encodings(content):
        try:
            return content.decode(encoding)
        except UnicodeDecodeError:
            continue
    return content.decode("latin1")


def candidate_encodings(content: bytes) -> list[str]:
    if content.startswith(b"\xff\xfe"):
        return ["utf-16", "utf-16-le", "utf-8-sig", "cp1252", "latin1"]
    if content.startswith(b"\xfe\xff"):
        return ["utf-16", "utf-16-be", "utf-8-sig", "cp1252", "latin1"]
    if content.startswith(b"\xef\xbb\xbf"):
        return ["utf-8-sig", "utf-8", "cp1252", "latin1"]

    sample = content[:2000]
    even_nulls = sample[0::2].count(0)
    odd_nulls = sample[1::2].count(0)
    if even_nulls + odd_nulls > len(sample) * 0.2:
        if odd_nulls > even_nulls:
            return ["utf-16-le", "utf-16", "utf-8-sig", "cp1252", "latin1"]
        return ["utf-16-be", "utf-16", "utf-8-sig", "cp1252", "latin1"]

    return ["utf-8-sig", "utf-8", "cp1252", "latin1", "utf-16"]


def detect_separator(text: str) -> str:
    first_line = next((line for line in text.splitlines() if line.strip()), "")
    return max([",", "\t", ";"], key=first_line.count)


def normalise_key(value: str) -> str:
    return (
        str(value)
        .strip()
        .lower()
        .replace("%", "percent")
        .replace("/", "_")
        .replace("-", "_")
        .replace(" ", "_")
    )


def normalise_value(value):
    if value is None:
        return ""
    text = str(value).strip()
    if text == "":
        return ""
    if re.fullmatch(r"-?\d+", text.replace(",", "")):
        return int(text.replace(",", ""))
    if re.fullmatch(r"-?\d+\.\d+", text.replace(",", "")):
        return float(text.replace(",", ""))
    return text


def infer_dimension(file_name: str, columns: Iterable[str]) -> str:
    lowered = file_name.lower()
    for dimension in ["country", "app_version", "os_version", "device", "language", "carrier"]:
        if dimension in lowered:
            return "android_os_version" if dimension == "os_version" else dimension
    column_set = set(columns)
    for dimension in ["country", "app_version_code", "android_os_version", "device", "language", "carrier"]:
        if dimension in column_set:
            return dimension
    return "overview"


def build_dashboard_data(reports: list[Report]) -> dict:
    by_dimension = {report.dimension: report for report in reports}
    overview = by_dimension.get("overview", reports[0])
    overview_rows = sorted(overview.rows, key=lambda row: row.get("date", ""))

    latest_day = latest_date(overview_rows)
    first_day = first_date(overview_rows)
    latest_overview = rows_for_date(overview_rows, latest_day)
    first_overview = rows_for_date(overview_rows, first_day)

    latest_active = sum_metric(latest_overview, "active_device_installs")
    first_active = sum_metric(first_overview, "active_device_installs")
    installs = sum_metric(overview_rows, "daily_user_installs")
    device_installs = sum_metric(overview_rows, "daily_device_installs")
    uninstalls = sum_metric(overview_rows, "daily_user_uninstalls")
    install_events = sum_metric(overview_rows, "install_events")
    update_events = sum_metric(overview_rows, "update_events")
    net_user_installs = installs - uninstalls
    growth_delta = latest_active - first_active
    growth_percent = (growth_delta / first_active * 100) if first_active else None

    country_rows = by_dimension.get("country", Report("", "country", [])).rows
    country_summary = summarise_dimension(country_rows, "country", latest_day)
    app_versions = summarise_dimension(by_dimension.get("app_version", Report("", "app_version", [])).rows, "app_version_code", latest_day)
    os_versions = summarise_dimension(by_dimension.get("android_os_version", Report("", "android_os_version", [])).rows, "android_os_version", latest_day)
    languages = summarise_dimension(by_dimension.get("language", Report("", "language", [])).rows, "language", latest_day)
    devices = summarise_dimension(by_dimension.get("device", Report("", "device", [])).rows, "device", latest_day)
    carriers = summarise_dimension(by_dimension.get("carrier", Report("", "carrier", [])).rows, "carrier", latest_day)

    timeline = [
        {
            "date": row.get("date"),
            "active": as_number(row.get("active_device_installs")),
            "installs": as_number(row.get("daily_user_installs")),
            "uninstalls": as_number(row.get("daily_user_uninstalls")),
            "installEvents": as_number(row.get("install_events")),
        }
        for row in overview_rows
    ]

    return {
        "generatedAt": datetime.now().strftime("%d %b %Y %H:%M"),
        "periodStart": friendly_date(first_day),
        "periodEnd": friendly_date(latest_day),
        "periodLabel": period_label(first_day, latest_day),
        "latestDay": latest_day,
        "kpis": {
            "activeInstalls": latest_active,
            "totalUserInstalls": installs,
            "deviceInstalls": device_installs,
            "userUninstalls": uninstalls,
            "netUserInstalls": net_user_installs,
            "installEvents": install_events,
            "updateEvents": update_events,
            "growthDelta": growth_delta,
            "growthPercent": growth_percent,
            "countryCount": len([item for item in country_summary if item["active"] > 0 or item["installs"] > 0]),
        },
        "timeline": timeline,
        "countries": country_summary,
        "appVersions": app_versions,
        "osVersions": os_versions,
        "languages": languages,
        "devices": devices[:10],
        "carriers": carriers[:10],
        "availableReports": sorted(report.name for report in reports),
        "iconDataUri": image_data_uri(APP_ICON),
    }


def latest_date(rows: list[dict]) -> str:
    dates = sorted({str(row.get("date", "")) for row in rows if row.get("date")})
    return dates[-1] if dates else ""


def first_date(rows: list[dict]) -> str:
    dates = sorted({str(row.get("date", "")) for row in rows if row.get("date")})
    return dates[0] if dates else ""


def rows_for_date(rows: list[dict], day: str) -> list[dict]:
    return [row for row in rows if row.get("date") == day]


def sum_metric(rows: list[dict], metric: str) -> int:
    return int(sum(as_number(row.get(metric)) for row in rows))


def as_number(value) -> float:
    if isinstance(value, (int, float)):
        return value
    if value in ("", None):
        return 0
    try:
        return float(str(value).replace(",", ""))
    except ValueError:
        return 0


def summarise_dimension(rows: list[dict], dimension: str, latest_day: str) -> list[dict]:
    grouped: dict[str, dict] = {}
    latest_rows = rows_for_date(rows, latest_day) or rows
    for row in latest_rows:
        key = str(row.get(dimension, "Unknown") or "Unknown")
        entry = grouped.setdefault(
            key,
            {
                "key": key,
                "label": dimension_label(dimension, key),
                "active": 0,
                "installs": 0,
                "uninstalls": 0,
                "installEvents": 0,
                "updateEvents": 0,
            },
        )
        entry["active"] += as_number(row.get("active_device_installs"))
        entry["installs"] += as_number(row.get("daily_user_installs"))
        entry["uninstalls"] += as_number(row.get("daily_user_uninstalls"))
        entry["installEvents"] += as_number(row.get("install_events"))
        entry["updateEvents"] += as_number(row.get("update_events"))

    values = sorted(grouped.values(), key=lambda item: (item["active"], item["installs"], item["installEvents"]), reverse=True)
    total_active = sum(item["active"] for item in values)
    for item in values:
        item["share"] = (item["active"] / total_active * 100) if total_active else 0
        for metric in ["active", "installs", "uninstalls", "installEvents", "updateEvents"]:
            item[metric] = int(item[metric])
    return values


def dimension_label(dimension: str, key: str) -> str:
    if dimension == "country":
        return COUNTRY_NAMES.get(key, key)
    if dimension == "language":
        return key.replace("_", "-")
    if dimension == "android_os_version":
        return f"Android {key}"
    if dimension == "app_version_code":
        return f"Version code {key}"
    return key


def period_label(start: str, end: str) -> str:
    if not start or not end:
        return "Current Play Console export"
    if start == end:
        return friendly_date(start)
    return f"{friendly_date(start)} to {friendly_date(end)}"


def friendly_date(value: str) -> str:
    if not value:
        return "Unknown"
    try:
        return datetime.strptime(value, "%Y-%m-%d").strftime("%d %b %Y")
    except ValueError:
        return value


def image_data_uri(path: Path) -> str:
    if not path.exists():
        return ""
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def render_html(data: dict) -> str:
    payload = json_for_script(data)
    icon = data.get("iconDataUri") or ""
    icon_html = f'<img src="{icon}" alt="" class="brand-icon">' if icon else '<div class="brand-icon fallback">AB</div>'

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>AllergyBuster Sponsorship Dashboard</title>
  <style>{css()}</style>
</head>
<body>
  <script id="dashboard-data" type="application/json">{payload}</script>

  <header class="hero">
    <nav class="topbar">
      <div class="brand">{icon_html}<span>AllergyBuster</span></div>
      <div class="report-date">Play Console export &bull; {html.escape(data["periodLabel"])}</div>
    </nav>

    <section class="hero-grid">
      <div>
        <p class="eyebrow">Sponsorship readiness</p>
        <h1>Privacy-first allergy reach, packaged for high-pollen day partnerships.</h1>
        <p class="hero-copy">A partner-facing view of AllergyBuster's Google Play footprint. Built from aggregate Play Console exports only, with no ad SDK, no live billing dependency, and no user-level health data.</p>
        <div class="hero-actions">
          <a href="#markets" class="button primary">View markets</a>
          <a href="#sponsorship" class="button secondary">Partner model</a>
        </div>
      </div>
      <aside class="hero-panel">
        <div class="panel-label">Current audience base</div>
        <div class="hero-number" data-format="integer" data-value="{data["kpis"]["activeInstalls"]}"></div>
        <div class="hero-subtitle">active device installs at latest export date</div>
        <div class="micro-grid">
          <div><span data-format="integer" data-value="{data["kpis"]["totalUserInstalls"]}"></span><small>user installs</small></div>
          <div><span data-format="integer" data-value="{data["kpis"]["installEvents"]}"></span><small>install events</small></div>
          <div><span data-format="integer" data-value="{data["kpis"]["countryCount"]}"></span><small>active markets</small></div>
        </div>
      </aside>
    </section>
  </header>

  <main>
    <section class="kpi-strip" aria-label="Key metrics">
      {kpi_card("Active device installs", data["kpis"]["activeInstalls"], "Latest Play Console snapshot", "leaf")}
      {kpi_card("User installs", data["kpis"]["totalUserInstalls"], "During selected export window", "gold")}
      {kpi_card("Net user installs", data["kpis"]["netUserInstalls"], "Installs minus uninstalls", "forest")}
      {kpi_card("Install events", data["kpis"]["installEvents"], "Demand signal across devices", "bark")}
    </section>

    <section class="section-grid">
      <article class="module wide">
        <div class="module-head">
          <div>
            <p class="eyebrow">Growth pulse</p>
            <h2>Install momentum</h2>
          </div>
          <p class="module-note">Daily Play Console install export, {html.escape(data["periodLabel"])}</p>
        </div>
        <div id="timelineChart" class="chart"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Partner signal</p>
            <h2>Why this inventory matters</h2>
          </div>
        </div>
        <div class="sponsor-points">
          <div><strong>Contextual timing</strong><span>Paid recommendations can appear around high-pollen moments rather than broad demographic targeting.</span></div>
          <div><strong>Trust-preserving placement</strong><span>Aggregate reporting keeps the privacy-first product promise intact.</span></div>
          <div><strong>Category alignment</strong><span>Pharmacy, OTC allergy care, air filtration, and eye-care partners fit the core use case.</span></div>
        </div>
      </article>
    </section>

    <section class="section-grid" id="markets">
      <article class="module wide">
        <div class="module-head">
          <div>
            <p class="eyebrow">Market footprint</p>
            <h2>Country split</h2>
          </div>
          <p class="module-note">All countries in the uploaded export</p>
        </div>
        <div id="countryChart" class="chart tall"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Top markets</p>
            <h2>Active installs</h2>
          </div>
        </div>
        <div id="countryTable" class="rank-list"></div>
      </article>
    </section>

    <section class="section-grid">
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Release quality</p>
            <h2>Version adoption</h2>
          </div>
        </div>
        <div id="versionChart" class="chart small"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Device base</p>
            <h2>Android versions</h2>
          </div>
        </div>
        <div id="osChart" class="chart small"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Audience context</p>
            <h2>Languages</h2>
          </div>
        </div>
        <div id="languageChart" class="chart small"></div>
      </article>
    </section>

    <section class="sponsor-band" id="sponsorship">
      <div>
        <p class="eyebrow">Commercial package</p>
        <h2>Recommended long-term sponsorship format</h2>
        <p>Sell one clearly-labelled allergy-care partner slot around high-pollen risk moments, measured with aggregate reach, click, and redemption reporting. Keep the first offer simple: seasonal exclusivity, product education, and a privacy-safe recommendation surface.</p>
      </div>
      <div class="package-grid">
        <div><span>1</span><strong>High-pollen card</strong><small>Sponsored recommendation in the app experience.</small></div>
        <div><span>2</span><strong>Partner landing page</strong><small>Education, coupon, or store locator destination.</small></div>
        <div><span>3</span><strong>Aggregate report</strong><small>Views, taps, market mix, and campaign-period trend.</small></div>
      </div>
    </section>

    <section class="section-grid">
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Devices</p>
            <h2>Top device models</h2>
          </div>
        </div>
        <div id="deviceTable" class="rank-list"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Network context</p>
            <h2>Top carriers</h2>
          </div>
        </div>
        <div id="carrierTable" class="rank-list"></div>
      </article>
      <article class="module privacy-card">
        <p class="eyebrow">Privacy position</p>
        <h2>What this report does not use</h2>
        <ul>
          <li>No ad service data</li>
          <li>No symptom history</li>
          <li>No precise location</li>
          <li>No user-level profiles</li>
        </ul>
      </article>
    </section>
  </main>

  <footer>
    <span>Generated {html.escape(data["generatedAt"])}</span>
    <span>Input reports: {len(data["availableReports"])}</span>
  </footer>

  <script>{javascript()}</script>
</body>
</html>"""


def kpi_card(label: str, value: int, note: str, tone: str) -> str:
    return f"""
      <article class="kpi-card {tone}">
        <span>{html.escape(label)}</span>
        <strong data-format="integer" data-value="{value}"></strong>
        <small>{html.escape(note)}</small>
      </article>
    """


def json_for_script(data: dict) -> str:
    return (
        json.dumps(data, separators=(",", ":"))
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
        .replace("</script", "<\\/script")
    )


def css() -> str:
    return f"""
:root {{
  --forest: {BRAND["forest"]};
  --forest-dark: {BRAND["forest_dark"]};
  --leaf: {BRAND["leaf"]};
  --leaf-soft: {BRAND["leaf_soft"]};
  --bark: {BRAND["bark"]};
  --gold: {BRAND["gold"]};
  --gold-soft: {BRAND["gold_soft"]};
  --parchment: {BRAND["parchment"]};
  --surface: {BRAND["surface"]};
  --sage: {BRAND["sage"]};
  --ink: {BRAND["ink"]};
  --muted: {BRAND["muted"]};
  --outline: {BRAND["outline"]};
  --terracotta: {BRAND["terracotta"]};
  --shadow: 0 18px 55px rgba(14, 26, 9, 0.14);
  --font: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}}

* {{ box-sizing: border-box; }}
html {{ scroll-behavior: smooth; }}
body {{
  margin: 0;
  font-family: var(--font);
  color: var(--ink);
  background: var(--parchment);
  line-height: 1.45;
  -webkit-font-smoothing: antialiased;
}}
a {{ color: inherit; }}
.hero {{
  color: white;
  background:
    linear-gradient(130deg, rgba(23,55,14,0.94), rgba(46,107,26,0.93)),
    radial-gradient(circle at 82% 18%, rgba(253,239,197,0.28), transparent 32%),
    linear-gradient(160deg, var(--forest-dark), var(--forest));
  min-height: 620px;
  padding: 22px 32px 58px;
  position: relative;
  overflow: hidden;
}}
.hero::after {{
  content: "";
  position: absolute;
  inset: auto -8vw -18vw auto;
  width: 44vw;
  height: 44vw;
  border-radius: 999px;
  background: rgba(180, 223, 170, 0.22);
  filter: blur(4px);
}}
.topbar, .hero-grid, main, footer {{
  max-width: 1180px;
  margin: 0 auto;
}}
.topbar {{
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  position: relative;
  z-index: 2;
}}
.brand {{
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-weight: 760;
  font-size: 1.05rem;
}}
.brand-icon {{
  width: 38px;
  height: 38px;
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(0,0,0,0.22);
}}
.brand-icon.fallback {{
  display: grid;
  place-items: center;
  color: var(--forest);
  background: white;
}}
.report-date {{
  color: rgba(255,255,255,0.78);
  font-size: 0.9rem;
}}
.hero-grid {{
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(320px, 0.68fr);
  gap: 46px;
  align-items: end;
  padding-top: 92px;
  position: relative;
  z-index: 2;
}}
.eyebrow {{
  margin: 0 0 9px;
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--gold);
}}
.hero .eyebrow {{ color: var(--gold-soft); }}
h1, h2 {{
  margin: 0;
  line-height: 1.04;
  letter-spacing: -0.02em;
}}
h1 {{
  max-width: 790px;
  font-size: clamp(2.85rem, 7vw, 5.85rem);
}}
h2 {{
  font-size: clamp(1.55rem, 2.6vw, 2.25rem);
}}
.hero-copy {{
  max-width: 710px;
  margin: 24px 0 0;
  color: rgba(255,255,255,0.82);
  font-size: 1.08rem;
}}
.hero-actions {{
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 32px;
}}
.button {{
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 18px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 750;
}}
.button.primary {{
  color: var(--forest-dark);
  background: white;
}}
.button.secondary {{
  border: 1px solid rgba(255,255,255,0.42);
  color: white;
  background: rgba(255,255,255,0.08);
}}
.hero-panel {{
  border: 1px solid rgba(255,255,255,0.24);
  background: rgba(253,250,244,0.12);
  border-radius: 8px;
  padding: 26px;
  backdrop-filter: blur(18px);
  box-shadow: 0 24px 70px rgba(0,0,0,0.22);
}}
.panel-label {{
  color: rgba(255,255,255,0.68);
  font-size: 0.86rem;
  font-weight: 700;
}}
.hero-number {{
  font-size: clamp(4rem, 9vw, 6rem);
  line-height: 1;
  font-weight: 850;
  letter-spacing: -0.04em;
  margin: 8px 0 10px;
}}
.hero-subtitle {{
  color: rgba(255,255,255,0.76);
  font-size: 0.95rem;
}}
.micro-grid {{
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 22px;
}}
.micro-grid div {{
  border-top: 1px solid rgba(255,255,255,0.2);
  padding-top: 12px;
}}
.micro-grid span {{
  display: block;
  font-size: 1.28rem;
  font-weight: 820;
}}
.micro-grid small {{
  display: block;
  color: rgba(255,255,255,0.66);
  font-size: 0.72rem;
}}
main {{
  padding: 34px 24px 60px;
}}
.kpi-strip {{
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: -82px auto 28px;
  position: relative;
  z-index: 4;
}}
.kpi-card, .module {{
  background: rgba(253,250,244,0.94);
  border: 1px solid var(--outline);
  border-radius: 8px;
  box-shadow: var(--shadow);
}}
.kpi-card {{
  min-height: 148px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  position: relative;
}}
.kpi-card::after {{
  content: "";
  position: absolute;
  right: -28px;
  top: -28px;
  width: 96px;
  height: 96px;
  border-radius: 999px;
  opacity: 0.18;
}}
.kpi-card.leaf::after {{ background: var(--leaf); }}
.kpi-card.gold::after {{ background: var(--gold); }}
.kpi-card.forest::after {{ background: var(--forest); }}
.kpi-card.bark::after {{ background: var(--bark); }}
.kpi-card span {{
  color: var(--muted);
  font-size: 0.84rem;
  font-weight: 740;
}}
.kpi-card strong {{
  font-size: 2.3rem;
  line-height: 1;
  letter-spacing: -0.04em;
}}
.kpi-card small {{
  color: var(--muted);
  font-size: 0.78rem;
}}
.section-grid {{
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}}
.module {{
  padding: 22px;
  min-height: 280px;
}}
.module.wide {{
  grid-column: span 2;
}}
.module-head {{
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}}
.module-head.compact {{
  margin-bottom: 14px;
}}
.module-note {{
  margin: 0;
  color: var(--muted);
  font-size: 0.86rem;
  text-align: right;
}}
.chart {{
  width: 100%;
  min-height: 300px;
}}
.chart.tall {{ min-height: 370px; }}
.chart.small {{ min-height: 245px; }}
svg {{ overflow: visible; }}
.axis-label, .chart-label {{
  fill: var(--muted);
  font-size: 12px;
}}
.chart-value {{
  fill: var(--ink);
  font-size: 12px;
  font-weight: 720;
}}
.sponsor-points {{
  display: grid;
  gap: 12px;
}}
.sponsor-points div {{
  padding: 16px;
  background: white;
  border: 1px solid var(--outline);
  border-radius: 8px;
}}
.sponsor-points strong {{
  display: block;
  margin-bottom: 4px;
}}
.sponsor-points span {{
  color: var(--muted);
  font-size: 0.9rem;
}}
.rank-list {{
  display: grid;
  gap: 10px;
}}
.rank-row {{
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--outline);
  background: white;
  border-radius: 8px;
}}
.rank-row strong {{
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}}
.rank-row small {{
  color: var(--muted);
}}
.rank-value {{
  font-weight: 820;
  color: var(--forest);
}}
.bar-track {{
  grid-column: 1 / -1;
  height: 7px;
  background: var(--sage);
  border-radius: 999px;
  overflow: hidden;
}}
.bar-fill {{
  height: 100%;
  background: linear-gradient(90deg, var(--forest), var(--leaf));
}}
.sponsor-band {{
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 28px;
  align-items: center;
  margin-top: 18px;
  color: white;
  background:
    linear-gradient(135deg, rgba(23,55,14,0.96), rgba(46,107,26,0.96)),
    linear-gradient(120deg, var(--forest-dark), var(--forest));
  border-radius: 8px;
  padding: 30px;
  box-shadow: var(--shadow);
}}
.sponsor-band p {{
  color: rgba(255,255,255,0.78);
  margin: 12px 0 0;
}}
.sponsor-band .eyebrow {{ color: var(--gold-soft); }}
.package-grid {{
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}}
.package-grid div {{
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.22);
  border-radius: 8px;
  padding: 16px;
}}
.package-grid span {{
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--forest-dark);
  background: var(--gold-soft);
  border-radius: 999px;
  font-weight: 850;
  margin-bottom: 14px;
}}
.package-grid strong {{
  display: block;
  margin-bottom: 6px;
}}
.package-grid small {{
  color: rgba(255,255,255,0.72);
}}
.privacy-card ul {{
  list-style: none;
  padding: 0;
  margin: 20px 0 0;
  display: grid;
  gap: 10px;
}}
.privacy-card li {{
  padding: 11px 12px;
  border-radius: 8px;
  background: white;
  border: 1px solid var(--outline);
  color: var(--muted);
}}
footer {{
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 24px;
  color: var(--muted);
  font-size: 0.86rem;
}}
@media (max-width: 980px) {{
  .hero-grid, .sponsor-band {{
    grid-template-columns: 1fr;
  }}
  .kpi-strip, .section-grid, .package-grid {{
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }}
  .module.wide {{
    grid-column: span 2;
  }}
}}
@media (max-width: 640px) {{
  .hero {{
    padding: 18px 18px 52px;
  }}
  .hero-grid {{
    padding-top: 54px;
  }}
  .topbar {{
    align-items: start;
    flex-direction: column;
  }}
  main {{
    padding-inline: 14px;
  }}
  .kpi-strip, .section-grid, .package-grid {{
    grid-template-columns: 1fr;
  }}
  .module.wide {{
    grid-column: span 1;
  }}
  footer {{
    flex-direction: column;
  }}
}}
"""


def javascript() -> str:
    return """
const data = JSON.parse(document.getElementById('dashboard-data').textContent);
const fmt = new Intl.NumberFormat('en-GB');
document.querySelectorAll('[data-format="integer"]').forEach(el => {
  el.textContent = fmt.format(Number(el.dataset.value || 0));
});

function svgEl(tag, attrs = {}, children = []) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
  children.forEach(child => el.appendChild(child));
  return el;
}

function mountSvg(targetId, height, draw) {
  const target = document.getElementById(targetId);
  target.innerHTML = '';
  const width = Math.max(target.clientWidth, 320);
  const svg = svgEl('svg', { width, height, viewBox: `0 0 ${width} ${height}`, role: 'img' });
  draw(svg, width, height);
  target.appendChild(svg);
}

function maxValue(items, keys) {
  return Math.max(1, ...items.flatMap(item => keys.map(key => Number(item[key] || 0))));
}

function linePath(points) {
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
}

function drawTimeline() {
  const rows = data.timeline || [];
  mountSvg('timelineChart', 320, (svg, width, height) => {
    const pad = { left: 44, right: 18, top: 18, bottom: 42 };
    const plotW = width - pad.left - pad.right;
    const plotH = height - pad.top - pad.bottom;
    const maxY = maxValue(rows, ['active', 'installs', 'uninstalls']);
    const x = index => pad.left + (rows.length <= 1 ? 0 : index * plotW / (rows.length - 1));
    const y = value => pad.top + plotH - (Number(value || 0) / maxY) * plotH;

    for (let i = 0; i <= 4; i++) {
      const yy = pad.top + (plotH * i / 4);
      svg.appendChild(svgEl('line', { x1: pad.left, x2: width - pad.right, y1: yy, y2: yy, stroke: '#DDE8D3', 'stroke-width': 1 }));
    }

    const series = [
      ['active', '#2E6B1A', 'Active installs'],
      ['installs', '#A07828', 'Daily installs'],
      ['uninstalls', '#B53B2A', 'Uninstalls'],
    ];

    series.forEach(([key, color]) => {
      const points = rows.map((row, index) => ({ x: x(index), y: y(row[key]) }));
      svg.appendChild(svgEl('path', { d: linePath(points), fill: 'none', stroke: color, 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
      points.forEach(point => svg.appendChild(svgEl('circle', { cx: point.x, cy: point.y, r: 4, fill: color, stroke: 'white', 'stroke-width': 2 })));
    });

    rows.forEach((row, index) => {
      if (index === 0 || index === rows.length - 1 || rows.length <= 8) {
        const text = svgEl('text', { x: x(index), y: height - 13, 'text-anchor': index === 0 ? 'start' : index === rows.length - 1 ? 'end' : 'middle', class: 'axis-label' });
        text.textContent = String(row.date || '').slice(5);
        svg.appendChild(text);
      }
    });

    const legend = svgEl('g', { transform: `translate(${pad.left}, 12)` });
    series.forEach(([key, color, label], index) => {
      const gx = index * 132;
      legend.appendChild(svgEl('circle', { cx: gx, cy: 0, r: 4, fill: color }));
      const text = svgEl('text', { x: gx + 10, y: 4, class: 'chart-label' });
      text.textContent = label;
      legend.appendChild(text);
    });
    svg.appendChild(legend);
  });
}

function drawBarChart(targetId, rows, metric = 'active', limit = 8) {
  const visible = rows.filter(row => Number(row[metric] || 0) > 0).slice(0, limit);
  mountSvg(targetId, Math.max(220, visible.length * 38 + 34), (svg, width, height) => {
    const pad = { left: 150, right: 44, top: 14, bottom: 18 };
    const max = Math.max(1, ...visible.map(row => Number(row[metric] || 0)));
    visible.forEach((row, index) => {
      const y = pad.top + index * 38;
      const barW = (width - pad.left - pad.right) * Number(row[metric] || 0) / max;
      const label = svgEl('text', { x: 0, y: y + 21, class: 'chart-label' });
      label.textContent = row.label || row.key;
      svg.appendChild(label);
      svg.appendChild(svgEl('rect', { x: pad.left, y: y + 7, width: width - pad.left - pad.right, height: 14, rx: 7, fill: '#E2ECD8' }));
      svg.appendChild(svgEl('rect', { x: pad.left, y: y + 7, width: Math.max(barW, 2), height: 14, rx: 7, fill: '#2E6B1A' }));
      const value = svgEl('text', { x: width - 4, y: y + 21, 'text-anchor': 'end', class: 'chart-value' });
      value.textContent = fmt.format(Number(row[metric] || 0));
      svg.appendChild(value);
    });
  });
}

function renderRankList(targetId, rows, metric = 'active', limit = 6) {
  const target = document.getElementById(targetId);
  const visible = rows.filter(row => Number(row[metric] || 0) > 0).slice(0, limit);
  const max = Math.max(1, ...visible.map(row => Number(row[metric] || 0)));
  if (!visible.length) {
    target.innerHTML = '<p class="module-note" style="text-align:left">No data in this export.</p>';
    return;
  }
  target.innerHTML = visible.map(row => {
    const value = Number(row[metric] || 0);
    const share = Math.round(value / max * 100);
    const label = escapeHtml(row.label || row.key);
    const sub = row.key && row.key !== row.label ? escapeHtml(row.key) : `${Number(row.share || 0).toFixed(1)}% share`;
    return `<div class="rank-row"><div><strong>${label}</strong><small>${sub}</small></div><div class="rank-value">${fmt.format(value)}</div><div class="bar-track"><div class="bar-fill" style="width:${share}%"></div></div></div>`;
  }).join('');
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

function redraw() {
  drawTimeline();
  drawBarChart('countryChart', data.countries || [], 'active', 10);
  drawBarChart('versionChart', data.appVersions || [], 'active', 6);
  drawBarChart('osChart', data.osVersions || [], 'active', 6);
  drawBarChart('languageChart', data.languages || [], 'active', 6);
  renderRankList('countryTable', data.countries || [], 'active', 8);
  renderRankList('deviceTable', data.devices || [], 'active', 7);
  renderRankList('carrierTable', data.carriers || [], 'active', 7);
}

window.addEventListener('resize', () => {
  clearTimeout(window.__abResize);
  window.__abResize = setTimeout(redraw, 140);
});
redraw();
"""


if __name__ == "__main__":
    main()
