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
REPORT_PATTERN = "*.csv"

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
    if text in {"", "-", "\u2013", "\u2014"}:
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
    if any(activity_metric_kind(column) for column in column_set):
        return "activity"
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
    activity_metrics = extract_activity_metrics(reports)

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
        "activityMetrics": activity_metrics,
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


def extract_activity_metrics(reports: list[Report]) -> dict:
    dau = latest_activity_metric_from_reports(reports, "dau")
    mau = latest_activity_metric_from_reports(reports, "mau")
    stickiness = None
    if dau and mau and mau["value"]:
        stickiness = dau["value"] / mau["value"] * 100
    available = bool(dau or mau)

    return {
        "dau": dau,
        "mau": mau,
        "stickiness": stickiness,
        "available": available,
        "note": (
            "DAU/MAU active-user metrics are available from the uploaded Play Console export."
            if available
            else (
                "DAU/MAU were not present in the uploaded Play install CSVs. "
                "These install exports show installed audience and install momentum, not app-open activity."
            )
        ),
    }


def latest_activity_metric_from_reports(reports: list[Report], kind: str) -> dict | None:
    preferred_reports = sorted(reports, key=lambda report: 0 if report.dimension == "activity" else 1)
    for report in preferred_reports:
        metric = best_activity_column(report.rows, kind)
        if not metric:
            continue
        dated_rows = [row for row in report.rows if row.get(metric) not in ("", None)]
        if not dated_rows:
            continue
        dates = sorted({str(row.get("date", "")) for row in dated_rows if row.get("date")}, key=date_sort_key)
        selected_rows = rows_for_date(dated_rows, dates[-1]) if dates else dated_rows
        value = int(sum(as_number(row.get(metric)) for row in selected_rows))
        return {
            "value": value,
            "date": dates[-1] if dates else "",
            "source": report.name,
            "column": metric,
            "segment": activity_segment(metric),
        }
    return None


def best_activity_column(rows: list[dict], kind: str) -> str | None:
    columns = {
        column
        for row in rows
        for column in row
        if activity_metric_kind(column) == kind
    }
    if not columns:
        return None
    return sorted(columns, key=activity_column_score)[0]


def activity_metric_kind(column: str) -> str | None:
    key = searchable_key(column)
    if (
        "monthly_active_users" in key
        or "monthly_active_user" in key
        or "monthly_active_devices" in key
        or "28_day_active_users" in key
        or "28d_active_users" in key
        or re.search(r"(^|_)mau($|_)", key)
    ):
        return "mau"
    if (
        "daily_active_users" in key
        or "daily_active_user" in key
        or "daily_active_devices" in key
        or "daily_active_device_installs" in key
        or re.search(r"(^|_)dau($|_)", key)
    ):
        return "dau"
    return None


def activity_column_score(column: str) -> tuple[int, int]:
    key = searchable_key(column)
    if "all_countries_regions" in key or ("all_countries" in key and "regions" in key):
        priority = 0
    elif "all_countries" in key or "total" in key or "overall" in key:
        priority = 1
    elif key in {"dau", "mau", "daily_active_users", "monthly_active_users"}:
        priority = 2
    else:
        priority = 3
    return (priority, len(key))


def activity_segment(column: str) -> str:
    key = searchable_key(column)
    if "all_countries_regions" in key or ("all_countries" in key and "regions" in key):
        return "All countries / regions"
    if "united_kingdom" in key:
        return "United Kingdom"
    if "united_states" in key:
        return "United States"
    if "ireland" in key:
        return "Ireland"
    return "Selected export"


def searchable_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", str(value).lower()).strip("_")


def date_sort_key(value: str) -> tuple[int, str]:
    parsed = parse_date(value)
    if parsed:
        return (1, parsed.isoformat())
    return (0, str(value))


def parse_date(value: str) -> date | None:
    text = str(value or "").strip()
    if not text:
        return None
    for pattern in ("%Y-%m-%d", "%d %b %Y", "%d %B %Y"):
        try:
            return datetime.strptime(text, pattern).date()
        except ValueError:
            continue
    return None


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
        return "Current aggregate audience snapshot"
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
  <title>AllergyBuster Partner Opportunity</title>
  <style>{css()}</style>
</head>
<body>
  <script id="dashboard-data" type="application/json">{payload}</script>

  <header class="hero">
    <nav class="topbar">
      <div class="brand">{icon_html}<span>AllergyBuster</span></div>
      <div class="report-date">Aggregate audience snapshot &bull; {html.escape(data["periodLabel"])}</div>
    </nav>

    <section class="hero-grid">
      <div>
        <p class="eyebrow">Partnership opportunity</p>
        <h1>Own the high-pollen moment inside a privacy-first allergy app.</h1>
        <p class="hero-copy">AllergyBuster gives allergy-care brands a direct, trusted route to people checking pollen conditions and planning relief. Partnership inventory is intentionally limited, clearly labelled, and measured with aggregate Play Console data rather than ad-network profiling.</p>
        <div class="hero-actions">
          <a href="#sponsorship" class="button primary">See the offer</a>
          <a href="#markets" class="button secondary">Review reach</a>
        </div>
      </div>
      <aside class="hero-panel">
        <div class="panel-label">Installed audience</div>
        <div class="hero-number" data-format="integer" data-value="{data["kpis"]["activeInstalls"]}"></div>
        <div class="hero-subtitle">people with AllergyBuster installed at latest export date</div>
        <div class="micro-grid">
          <div><span data-format="integer" data-value="{data["kpis"]["totalUserInstalls"]}"></span><small>new users</small></div>
          <div><span data-format="integer" data-value="{data["kpis"]["installEvents"]}"></span><small>demand events</small></div>
          <div><span data-format="integer" data-value="{data["kpis"]["countryCount"]}"></span><small>markets reached</small></div>
        </div>
      </aside>
    </section>
  </header>

  <main>
    <section class="kpi-strip" aria-label="Key metrics">
      {kpi_card("Addressable audience", data["kpis"]["activeInstalls"], "Installed audience available for seasonal campaigns", "leaf")}
      {kpi_card("New users", data["kpis"]["totalUserInstalls"], "Fresh allergy-intent audience in this export window", "gold")}
      {activity_kpi_card("DAU", data["activityMetrics"], "dau", "Daily active users in the partner reach segment", "forest")}
      {activity_kpi_card("MAU", data["activityMetrics"], "mau", "Monthly active users in the partner reach segment", "bark")}
    </section>

    <section class="section-grid">
      <article class="module wide">
        <div class="module-head">
          <div>
            <p class="eyebrow">Audience growth</p>
            <h2>Momentum partners can buy into</h2>
          </div>
          <p class="module-note">Aggregate Play Console growth signals, {html.escape(data["periodLabel"])}</p>
        </div>
        <div id="timelineChart" class="chart"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Why invest</p>
            <h2>Built for intent, not interruption</h2>
          </div>
        </div>
        {activity_snapshot(data["activityMetrics"])}
        <div class="sponsor-points">
          <div><strong>High-intent timing</strong><span>Reach users when pollen risk is relevant and relief decisions are already in mind.</span></div>
          <div><strong>Brand-safe exclusivity</strong><span>One category-fit partner can be positioned as a helpful recommendation, not a programmatic ad.</span></div>
          <div><strong>Privacy-led measurement</strong><span>Campaign reporting can prove reach and action without collecting symptom history or personal profiles.</span></div>
        </div>
      </article>
    </section>

    <section class="proof-section" aria-label="Early user feedback">
      <div class="proof-head">
        <p class="eyebrow">User proof</p>
        <h2>AllergyBuster is already changing how people understand and act on hayfever.</h2>
        <p>For partners, this is the signal that matters: users are not passively browsing. They are connecting symptoms, triggers, treatment decisions, and real-world relief moments.</p>
      </div>
      <div class="quote-carousel" data-quote-carousel>
        <div class="quote-controls" aria-label="User feedback carousel controls">
          <button class="quote-nav" type="button" aria-label="Previous user quote" data-quote-prev>&lsaquo;</button>
          <div class="quote-dots" aria-label="Quote position"></div>
          <button class="quote-nav" type="button" aria-label="Next user quote" data-quote-next>&rsaquo;</button>
        </div>
        <div class="quote-track">
          <figure class="quote-card">
            <blockquote>&ldquo;Jumped on the beta programme to test out! Suffered with hayfever for years. I have my injection booked in soon now that its kicked off. Twas a game changer for me and I have been kicking myself that I didn't do it sooner! The prescriptions stuff didn't cut it half the time for me.&rdquo;</blockquote>
            <figcaption>Anonymised beta user feedback</figcaption>
          </figure>
          <figure class="quote-card">
            <blockquote>&ldquo;25 years in the British Army travelling the world, to then settle near my home town and be plagued with hayfever and never really understood the trigger!! From a professional point of view, Bayesian Statistics has been on the forefront of conversations and not once have I ever thought to connect the two!&rdquo;</blockquote>
            <figcaption>Anonymised early user feedback</figcaption>
          </figure>
          <figure class="quote-card">
            <blockquote>&ldquo;Great idea! I've suffered with hay-fever for years and never entirely figured out the triggers.&rdquo;</blockquote>
            <figcaption>Anonymised early user feedback</figcaption>
          </figure>
        </div>
      </div>
    </section>

    <section class="section-grid" id="markets">
      <article class="module wide">
        <div class="module-head">
          <div>
            <p class="eyebrow">Where campaigns can scale</p>
            <h2>Market reach by country</h2>
          </div>
          <p class="module-note">Use this split to shape launch markets, seasonal budgets, and territory exclusivity</p>
        </div>
        <div id="countryChart" class="chart tall"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Priority markets</p>
            <h2>Installed audience</h2>
          </div>
        </div>
        <div id="countryTable" class="rank-list"></div>
      </article>
    </section>

    <section class="section-grid">
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Delivery confidence</p>
            <h2>Version coverage</h2>
          </div>
        </div>
        <div id="versionChart" class="chart small"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Audience compatibility</p>
            <h2>Android coverage</h2>
          </div>
        </div>
        <div id="osChart" class="chart small"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Localisation signal</p>
            <h2>Audience languages</h2>
          </div>
        </div>
        <div id="languageChart" class="chart small"></div>
      </article>
    </section>

    <section class="sponsor-band" id="sponsorship">
      <div>
        <p class="eyebrow">Partnership model</p>
        <h2>A focused seasonal channel for allergy-care brands</h2>
        <p>Partner with AllergyBuster to become the named recommendation when users are checking pollen conditions and deciding what to do next. The offer is intentionally premium: limited partner slots, contextual placement, and aggregate reporting your marketing, retail, and compliance teams can trust.</p>
      </div>
      <div class="package-grid">
        <div><span>1</span><strong>Own high-pollen days</strong><small>Exclusive or priority placement when allergy intent is strongest.</small></div>
        <div><span>2</span><strong>Turn intent into action</strong><small>Route users to education, coupons, retailers, or store locators.</small></div>
        <div><span>3</span><strong>Prove the value</strong><small>Receive aggregate reach, taps, market mix, and campaign trend reporting.</small></div>
      </div>
    </section>

    <section class="section-grid">
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Technical reach</p>
            <h2>Top device models</h2>
          </div>
        </div>
        <div id="deviceTable" class="rank-list"></div>
      </article>
      <article class="module">
        <div class="module-head compact">
          <div>
            <p class="eyebrow">Distribution context</p>
            <h2>Top carriers</h2>
          </div>
        </div>
        <div id="carrierTable" class="rank-list"></div>
      </article>
      <article class="module privacy-card">
        <p class="eyebrow">Brand-safe by design</p>
        <h2>Why partners can trust the channel</h2>
        <ul>
          <li>No ad-network dependency</li>
          <li>No symptom-history targeting</li>
          <li>No precise-location profiling</li>
          <li>No user-level partner reports</li>
        </ul>
      </article>
    </section>
  </main>

  <footer>
    <span>Generated {html.escape(data["generatedAt"])}</span>
    <span>Built from {len(data["availableReports"])} aggregate source exports</span>
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


def activity_kpi_card(label: str, activity: dict, metric: str, note: str, tone: str) -> str:
    item = activity.get(metric)
    if item and item.get("value") is not None:
        value_html = f'<strong data-format="integer" data-value="{int(item["value"])}"></strong>'
    else:
        value_html = "<strong>—</strong>"

    return f"""
      <article class="kpi-card {tone}">
        <span>{html.escape(label)}</span>
        {value_html}
        <small>{html.escape(note)}</small>
      </article>
    """


def activity_snapshot(activity: dict) -> str:
    dau = activity.get("dau")
    mau = activity.get("mau")
    stickiness = activity.get("stickiness")

    if not activity.get("available"):
        return """
        <div class="activity-card unavailable">
          <div>
            <span>DAU</span>
            <strong>—</strong>
          </div>
          <div>
            <span>MAU</span>
            <strong>—</strong>
          </div>
          <p>Add a Play Console active-user export to show partners the active audience available for seasonal campaigns.</p>
        </div>
        """

    dau_value = format_optional_number(dau["value"] if dau else None)
    mau_value = format_optional_number(mau["value"] if mau else None)
    stickiness_text = f"{stickiness:.1f}% daily-to-monthly engagement" if stickiness is not None else "Engagement ratio needs both DAU and MAU"
    activity_dates = [metric["date"] for metric in [dau, mau] if metric and metric.get("date")]
    latest_activity_date = sorted(activity_dates, key=date_sort_key)[-1] if activity_dates else ""
    segment = (dau or mau or {}).get("segment", "Selected export")
    detail_text = f"{stickiness_text} across {segment}"
    if latest_activity_date:
        detail_text = f"{detail_text} - Latest {friendly_date(latest_activity_date)}"

    return f"""
      <div class="activity-card">
        <div>
          <span>DAU</span>
          <strong>{html.escape(dau_value)}</strong>
        </div>
        <div>
          <span>MAU</span>
          <strong>{html.escape(mau_value)}</strong>
        </div>
        <p>{html.escape(detail_text)}</p>
      </div>
    """


def format_optional_number(value) -> str:
    if value is None:
        return "—"
    return f"{int(value):,}"


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
.axis-title {{
  fill: var(--muted);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}}
.chart-value {{
  fill: var(--ink);
  font-size: 12px;
  font-weight: 720;
}}
.activity-card {{
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 12px;
  margin-bottom: 14px;
  border-radius: 8px;
  border: 1px solid var(--outline);
  background: linear-gradient(135deg, white, var(--green-50, #F3F8EF));
}}
.activity-card div {{
  padding: 12px;
  border-radius: 8px;
  background: var(--surface);
  border: 1px solid var(--outline);
}}
.activity-card span {{
  display: block;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 850;
  letter-spacing: 0.12em;
}}
.activity-card strong {{
  display: block;
  margin-top: 4px;
  color: var(--forest);
  font-size: 1.75rem;
  line-height: 1;
  letter-spacing: -0.04em;
}}
.activity-card p {{
  grid-column: 1 / -1;
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
}}
.activity-card.unavailable strong {{
  color: var(--muted);
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
.proof-section {{
  display: grid;
  grid-template-columns: minmax(0, 0.78fr) minmax(0, 1.22fr);
  gap: 22px;
  align-items: start;
  margin-top: 26px;
  padding: 10px 0;
}}
.proof-head h2 {{
  max-width: 520px;
}}
.proof-head p:not(.eyebrow) {{
  margin: 14px 0 0;
  color: var(--muted);
}}
.quote-carousel {{
  min-width: 0;
}}
.quote-controls {{
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 12px;
}}
.quote-nav {{
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--outline);
  border-radius: 999px;
  color: var(--forest);
  background: white;
  font-size: 1.45rem;
  line-height: 1;
  cursor: pointer;
}}
.quote-nav:disabled {{
  color: var(--muted);
  cursor: default;
  opacity: 0.45;
}}
.quote-dots {{
  display: flex;
  gap: 6px;
  align-items: center;
}}
.quote-dots button {{
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: var(--outline);
  cursor: pointer;
}}
.quote-dots button.active {{
  width: 22px;
  background: var(--forest);
}}
.quote-track {{
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 100%;
  gap: 14px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-behavior: smooth;
  scrollbar-width: none;
}}
.quote-track::-webkit-scrollbar {{
  display: none;
}}
.quote-card {{
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 255px;
  margin: 0;
  padding: 20px;
  border: 1px solid var(--outline);
  border-radius: 8px;
  background: white;
  scroll-snap-align: start;
}}
.quote-card blockquote {{
  margin: 0;
  color: var(--ink);
  font-size: 0.98rem;
}}
.quote-card figcaption {{
  margin-top: 18px;
  color: var(--gold);
  font-size: 0.75rem;
  font-weight: 840;
  letter-spacing: 0.11em;
  text-transform: uppercase;
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
  .hero-grid, .sponsor-band, .proof-section {{
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
  .quote-controls {{
    justify-content: space-between;
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

function niceCeil(value) {
  if (value <= 5) return 5;
  const exponent = Math.floor(Math.log10(value));
  const base = Math.pow(10, exponent);
  const fraction = value / base;
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return niceFraction * base;
}

function tickValues(max, count = 4) {
  const niceMax = niceCeil(max);
  return Array.from({ length: count + 1 }, (_, index) => Math.round(niceMax * index / count));
}

function formatAxis(value) {
  if (value >= 1000000) return `${(value / 1000000).toFixed(value % 1000000 === 0 ? 0 : 1)}m`;
  if (value >= 1000) return `${(value / 1000).toFixed(value % 1000 === 0 ? 0 : 1)}k`;
  return fmt.format(value);
}

function drawTimeline() {
  const rows = data.timeline || [];
  mountSvg('timelineChart', 360, (svg, width, height) => {
    const pad = { left: 64, right: 26, top: 54, bottom: 52 };
    const plotW = width - pad.left - pad.right;
    const plotH = height - pad.top - pad.bottom;
    const rawMaxY = maxValue(rows, ['active', 'installs', 'uninstalls']);
    const ticks = tickValues(rawMaxY, 4);
    const maxY = ticks[ticks.length - 1];
    const x = index => pad.left + (rows.length <= 1 ? 0 : index * plotW / (rows.length - 1));
    const y = value => pad.top + plotH - (Number(value || 0) / maxY) * plotH;

    ticks.forEach(value => {
      const yy = y(value);
      svg.appendChild(svgEl('line', { x1: pad.left, x2: width - pad.right, y1: yy, y2: yy, stroke: '#DDE8D3', 'stroke-width': 1 }));
      const label = svgEl('text', { x: pad.left - 12, y: yy + 4, 'text-anchor': 'end', class: 'axis-label' });
      label.textContent = formatAxis(value);
      svg.appendChild(label);
    });

    svg.appendChild(svgEl('line', { x1: pad.left, x2: pad.left, y1: pad.top, y2: pad.top + plotH, stroke: '#C9D7BE', 'stroke-width': 1 }));
    svg.appendChild(svgEl('line', { x1: pad.left, x2: width - pad.right, y1: pad.top + plotH, y2: pad.top + plotH, stroke: '#C9D7BE', 'stroke-width': 1 }));

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

    const labelStep = Math.max(1, Math.ceil(rows.length / 6));
    rows.forEach((row, index) => {
      if (index === 0 || index === rows.length - 1 || index % labelStep === 0) {
        const text = svgEl('text', { x: x(index), y: height - 13, 'text-anchor': index === 0 ? 'start' : index === rows.length - 1 ? 'end' : 'middle', class: 'axis-label' });
        text.textContent = String(row.date || '').slice(5);
        svg.appendChild(text);
      }
    });

    const title = svgEl('text', { x: pad.left, y: 18, class: 'axis-title' });
    title.textContent = 'Installs';
    svg.appendChild(title);

    const legend = svgEl('g', { transform: `translate(${pad.left}, 38)` });
    series.forEach(([key, color, label], index) => {
      const gx = index * 132;
      legend.appendChild(svgEl('circle', { cx: gx, cy: 0, r: 4, fill: color }));
      const text = svgEl('text', { x: gx + 10, y: 4, class: 'chart-label' });
      text.textContent = label;
      legend.appendChild(text);
    });
    svg.appendChild(legend);

    if (rows.length) {
      const last = rows[rows.length - 1];
      const lastX = x(rows.length - 1);
      [
        ['active', '#2E6B1A'],
        ['installs', '#A07828'],
        ['uninstalls', '#B53B2A'],
      ].forEach(([key, color], index) => {
        const value = Number(last[key] || 0);
        const label = svgEl('text', { x: Math.min(width - pad.right, lastX + 8), y: y(value) + 4 + index * 2, fill: color, class: 'chart-value' });
        label.textContent = formatAxis(value);
        svg.appendChild(label);
      });
    }
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

function initQuoteCarousel() {
  const carousel = document.querySelector('[data-quote-carousel]');
  if (!carousel || carousel.dataset.ready === 'true') return;

  const track = carousel.querySelector('.quote-track');
  const slides = Array.from(carousel.querySelectorAll('.quote-card'));
  const prev = carousel.querySelector('[data-quote-prev]');
  const next = carousel.querySelector('[data-quote-next]');
  const dots = carousel.querySelector('.quote-dots');
  if (!track || !slides.length || !prev || !next || !dots) return;

  carousel.dataset.ready = 'true';
  dots.innerHTML = slides.map((_, index) => `<button type="button" aria-label="Show user quote ${index + 1}" data-quote-dot="${index}"></button>`).join('');
  const dotButtons = Array.from(dots.querySelectorAll('[data-quote-dot]'));
  const slideLeft = slide => slide.offsetLeft - track.offsetLeft;
  const autoScrollMs = 3000;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let autoTimer = null;
  let isPaused = false;

  function currentIndex() {
    const left = track.scrollLeft;
    return slides.reduce((bestIndex, slide, index) => {
      const bestDistance = Math.abs(slideLeft(slides[bestIndex]) - left);
      const distance = Math.abs(slideLeft(slide) - left);
      return distance < bestDistance ? index : bestIndex;
    }, 0);
  }

  function goTo(index) {
    const target = (index + slides.length) % slides.length;
    track.scrollTo({ left: slideLeft(slides[target]), behavior: 'smooth' });
    update(target);
  }

  function update(index = currentIndex()) {
    prev.disabled = slides.length <= 1;
    next.disabled = slides.length <= 1;
    dotButtons.forEach((dot, dotIndex) => {
      dot.classList.toggle('active', dotIndex === index);
      dot.setAttribute('aria-current', dotIndex === index ? 'true' : 'false');
    });
  }

  function startAutoScroll() {
    if (reduceMotion || slides.length <= 1 || autoTimer || isPaused) return;
    autoTimer = window.setInterval(() => goTo(currentIndex() + 1), autoScrollMs);
  }

  function stopAutoScroll() {
    window.clearInterval(autoTimer);
    autoTimer = null;
  }

  function pauseAutoScroll() {
    isPaused = true;
    stopAutoScroll();
  }

  function resumeAutoScroll() {
    isPaused = false;
    startAutoScroll();
  }

  prev.addEventListener('click', () => goTo(currentIndex() - 1));
  next.addEventListener('click', () => goTo(currentIndex() + 1));
  dotButtons.forEach((dot, index) => dot.addEventListener('click', () => goTo(index)));
  track.addEventListener('scroll', () => {
    window.requestAnimationFrame(() => update());
  }, { passive: true });
  carousel.addEventListener('pointerenter', pauseAutoScroll);
  carousel.addEventListener('pointerleave', resumeAutoScroll);
  carousel.addEventListener('focusin', pauseAutoScroll);
  carousel.addEventListener('focusout', resumeAutoScroll);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      stopAutoScroll();
    } else {
      startAutoScroll();
    }
  });
  update(0);
  startAutoScroll();
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
  initQuoteCarousel();
}

window.addEventListener('resize', () => {
  clearTimeout(window.__abResize);
  window.__abResize = setTimeout(redraw, 140);
});
redraw();
"""


if __name__ == "__main__":
    main()
