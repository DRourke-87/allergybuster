from __future__ import annotations

import io
import os
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

import pandas as pd
import plotly.express as px
import streamlit as st


DEFAULT_GCS_PREFIX = "gs://pubsite_prod_7020116138428415210/stats/installs/"
DEFAULT_COUNTRIES = ["GB", "IE", "US"]
GCS_READ_ONLY_SCOPE = "https://www.googleapis.com/auth/devstorage.read_only"
COUNTRY_ALIASES = {
    "GB": {"GB", "GBR", "UK", "UNITED KINGDOM", "GREAT BRITAIN"},
    "IE": {"IE", "IRL", "IRELAND"},
    "US": {"US", "USA", "UNITED STATES", "UNITED STATES OF AMERICA"},
}
APP_DIR = Path(__file__).resolve().parent


@dataclass(frozen=True)
class GcsPath:
    bucket: str
    prefix: str


def parse_gcs_path(value: str) -> GcsPath:
    if not value.startswith("gs://"):
        raise ValueError("GCS path must start with gs://")
    without_scheme = value.removeprefix("gs://")
    bucket, _, prefix = without_scheme.partition("/")
    if not bucket:
        raise ValueError("GCS bucket is missing")
    return GcsPath(bucket=bucket, prefix=prefix)


def clean_column_name(name: str) -> str:
    return (
        str(name)
        .strip()
        .lower()
        .replace("%", "percent")
        .replace("/", "_")
        .replace("-", "_")
        .replace(" ", "_")
        .replace("__", "_")
    )


def normalise_report(df: pd.DataFrame, source_name: str) -> pd.DataFrame:
    df = df.copy()
    df.columns = [clean_column_name(column) for column in df.columns]
    df["source_report"] = source_name

    date_column = first_present(df, ["date", "day"])
    if date_column:
        df["date"] = pd.to_datetime(df[date_column], errors="coerce").dt.date

    country_column = first_present(df, ["country", "country_region", "region"])
    if country_column and country_column != "country":
        df["country"] = df[country_column]

    package_column = first_present(df, ["package_name", "package"])
    if package_column and package_column != "package_name":
        df["package_name"] = df[package_column]

    for column in df.columns:
        if column in {"date", "source_report", "report_dimension", "country", "package_name"}:
            continue
        if df[column].dtype == object:
            numeric = pd.to_numeric(
                df[column].astype(str).str.replace(",", "", regex=False),
                errors="coerce",
            )
            if numeric.notna().sum() > 0:
                df[column] = numeric

    df["report_dimension"] = infer_dimension(source_name, df.columns)
    return df


def first_present(df: pd.DataFrame, candidates: Iterable[str]) -> str | None:
    for candidate in candidates:
        if candidate in df.columns:
            return candidate
    return None


def infer_dimension(source_name: str, columns: Iterable[str]) -> str:
    lower_name = source_name.lower()
    for dimension in [
        "country",
        "app_version",
        "android_os_version",
        "device",
        "language",
        "carrier",
    ]:
        if dimension in lower_name:
            return dimension
    column_set = set(columns)
    for dimension in ["country", "app_version", "android_os_version", "device", "language", "carrier"]:
        if dimension in column_set:
            return dimension
    return "summary"


def read_csv_bytes(content: bytes, source_name: str) -> pd.DataFrame:
    df = pd.read_csv(io.BytesIO(content))
    return normalise_report(df, source_name)


def storage_client(credentials_path: str, project_id: str):
    import google.auth
    from google.cloud import storage

    credentials_path = credentials_path.strip()
    project_id = project_id.strip() or None

    if credentials_path:
        from google.oauth2 import service_account

        credentials = service_account.Credentials.from_service_account_file(
            credentials_path,
            scopes=[GCS_READ_ONLY_SCOPE],
        )
        return storage.Client(project=project_id or credentials.project_id, credentials=credentials)

    credentials, default_project_id = google.auth.default(scopes=[GCS_READ_ONLY_SCOPE])
    return storage.Client(project=project_id or default_project_id, credentials=credentials)


def billing_project(client, project_id: str) -> str | None:
    return project_id.strip() or client.project


def default_credentials_path() -> str:
    env_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "").strip()
    if env_path:
        return env_path

    local_json_files = sorted(APP_DIR.glob("*.json"), key=lambda path: path.stat().st_mtime, reverse=True)
    return str(local_json_files[0]) if local_json_files else ""


@st.cache_data(show_spinner=False, ttl=900)
def load_gcs_reports(gcs_prefix: str, max_files: int, credentials_path: str, project_id: str) -> list[pd.DataFrame]:
    path = parse_gcs_path(gcs_prefix)
    client = storage_client(credentials_path, project_id)
    bucket = client.bucket(path.bucket, user_project=billing_project(client, project_id))
    blobs = [
        blob
        for blob in client.list_blobs(bucket, prefix=path.prefix)
        if blob.name.lower().endswith(".csv")
    ]
    blobs = sorted(blobs, key=lambda blob: blob.name, reverse=True)[:max_files]

    reports: list[pd.DataFrame] = []
    for blob in blobs:
        content = bucket.blob(blob.name).download_as_bytes()
        reports.append(read_csv_bytes(content, blob.name))
    return reports


def load_uploaded_reports(files) -> list[pd.DataFrame]:
    reports: list[pd.DataFrame] = []
    for uploaded_file in files:
        reports.append(read_csv_bytes(uploaded_file.getvalue(), uploaded_file.name))
    return reports


def render_gcs_error(error: Exception) -> None:
    st.error("Google Play reports could not be loaded from GCS.")
    if "storage.objects.list" in str(error):
        st.warning(
            "The service account key was read, but Google denied access to the Play Console reports bucket. "
            "In Play Console, grant the service account account-level "
            "'View app information and download bulk reports (read-only)' access."
        )
    if "billing account" in str(error).lower() or "userproject" in str(error).lower():
        st.warning(
            "Google Storage also needs a billing-enabled requester project for these report downloads. "
            "Enter a billing-enabled Google Cloud project ID in the sidebar."
        )
    st.markdown(
        """
The dashboard needs Google Cloud credentials with read access to the Play Console reports bucket.

Use one of these options:

```powershell
gcloud auth application-default login
```

or add a local service account JSON path in the sidebar:

```powershell
C:\\path\\to\\service-account.json
```

You can also switch to **CSV upload** and use exported Play Console reports without Google Cloud auth.
"""
    )
    with st.expander("Technical error"):
        st.code(f"{type(error).__name__}: {error}")


def concat_reports(reports: list[pd.DataFrame]) -> pd.DataFrame:
    if not reports:
        return pd.DataFrame()
    return pd.concat(reports, ignore_index=True, sort=False)


def numeric_columns(df: pd.DataFrame) -> list[str]:
    excluded = {"date"}
    result = []
    for column in df.columns:
        if column in excluded:
            continue
        if pd.api.types.is_numeric_dtype(df[column]):
            result.append(column)
    return result


def metric_column(df: pd.DataFrame, preferred: list[str]) -> str | None:
    for column in preferred:
        if column in df.columns and pd.api.types.is_numeric_dtype(df[column]):
            return column
    numbers = numeric_columns(df)
    return numbers[0] if numbers else None


def format_number(value) -> str:
    if pd.isna(value):
        return "-"
    try:
        return f"{int(round(float(value))):,}"
    except (TypeError, ValueError):
        return str(value)


def latest_value(df: pd.DataFrame, column: str | None):
    if not column or column not in df.columns or df.empty:
        return None
    if "date" in df.columns:
        dated = df.dropna(subset=["date"])
        if not dated.empty:
            latest_day = dated["date"].max()
            return dated.loc[dated["date"] == latest_day, column].sum()
    return df[column].sum()


def apply_filters(df: pd.DataFrame, package_name: str, countries: list[str], start: date, end: date) -> pd.DataFrame:
    filtered = df.copy()
    if package_name and "package_name" in filtered.columns:
        filtered = filtered[filtered["package_name"].astype(str).str.contains(package_name, case=False, na=False)]
    if "country" in filtered.columns and countries:
        allowed = set()
        for country in countries:
            allowed.update(COUNTRY_ALIASES.get(country.upper(), {country.upper()}))
        country_tokens = filtered["country"].astype(str).str.strip().str.upper()
        filtered = filtered[country_tokens.isin(allowed)]
    if "date" in filtered.columns:
        filtered = filtered[(filtered["date"] >= start) & (filtered["date"] <= end)]
    return filtered


def render_metric_cards(df: pd.DataFrame) -> None:
    active_installs = metric_column(df, ["active_device_installs", "active_devices", "active_device_installs_current"])
    current_user_installs = metric_column(df, ["current_user_installs", "total_user_installs"])
    daily_user_installs = metric_column(df, ["daily_user_installs", "user_installs"])
    daily_user_uninstalls = metric_column(df, ["daily_user_uninstalls", "user_uninstalls"])

    cols = st.columns(4)
    cols[0].metric("Active Device Installs", format_number(latest_value(df, active_installs)))
    cols[1].metric("Current User Installs", format_number(latest_value(df, current_user_installs)))
    cols[2].metric("Daily User Installs", format_number(latest_value(df, daily_user_installs)))
    cols[3].metric("Daily User Uninstalls", format_number(latest_value(df, daily_user_uninstalls)))


def render_time_series(df: pd.DataFrame) -> None:
    if "date" not in df.columns:
        st.info("No date column found in the loaded reports.")
        return

    metric_options = numeric_columns(df)
    if not metric_options:
        st.info("No numeric metric columns found in the loaded reports.")
        return

    default_metrics = [
        metric for metric in ["active_device_installs", "daily_user_installs", "daily_user_uninstalls"]
        if metric in metric_options
    ]
    selected = st.multiselect("Trend metrics", metric_options, default=default_metrics or metric_options[:1])
    if not selected:
        return

    group_columns = ["date"]
    trend = df.groupby(group_columns, dropna=False)[selected].sum(numeric_only=True).reset_index()
    melted = trend.melt(id_vars="date", value_vars=selected, var_name="metric", value_name="value")
    fig = px.line(melted, x="date", y="value", color="metric", markers=True)
    fig.update_layout(height=420, legend_title_text="", margin=dict(l=20, r=20, t=30, b=20))
    st.plotly_chart(fig, use_container_width=True)


def render_country_split(df: pd.DataFrame) -> None:
    if "country" not in df.columns:
        st.info("No country column found. Load country-level install reports to see UK/Ireland/US split.")
        return

    metric = metric_column(df, ["active_device_installs", "current_user_installs", "total_user_installs"])
    if not metric:
        st.info("No install-count metric found for country split.")
        return

    latest = df
    if "date" in df.columns and not df.dropna(subset=["date"]).empty:
        latest_day = df["date"].max()
        latest = df[df["date"] == latest_day]

    by_country = latest.groupby("country", dropna=False)[metric].sum().reset_index()
    by_country = by_country.sort_values(metric, ascending=False)

    cols = st.columns([0.58, 0.42])
    with cols[0]:
        fig = px.bar(by_country.head(20), x="country", y=metric)
        fig.update_layout(height=360, xaxis_title="", yaxis_title=metric, margin=dict(l=20, r=20, t=30, b=20))
        st.plotly_chart(fig, use_container_width=True)
    with cols[1]:
        st.dataframe(by_country, use_container_width=True, hide_index=True)


def render_dimension_breakdown(df: pd.DataFrame) -> None:
    dimension_options = [
        column
        for column in ["app_version", "android_os_version", "device", "language", "carrier", "report_dimension"]
        if column in df.columns
    ]
    metric = metric_column(df, ["active_device_installs", "current_user_installs", "total_user_installs"])
    if not dimension_options or not metric:
        return

    dimension = st.selectbox("Breakdown", dimension_options)
    latest = df
    if "date" in df.columns and not df.dropna(subset=["date"]).empty:
        latest = df[df["date"] == df["date"].max()]
    breakdown = latest.groupby(dimension, dropna=False)[metric].sum().reset_index()
    breakdown = breakdown.sort_values(metric, ascending=False).head(25)

    fig = px.bar(breakdown, y=dimension, x=metric, orientation="h")
    fig.update_layout(height=520, yaxis={"categoryorder": "total ascending"}, margin=dict(l=20, r=20, t=30, b=20))
    st.plotly_chart(fig, use_container_width=True)


def safe_package_hint(df: pd.DataFrame) -> str:
    if "package_name" not in df.columns:
        return ""
    packages = sorted(str(value) for value in df["package_name"].dropna().unique())
    for package in packages:
        if "allergybuster" in package:
            return package
    return packages[0] if packages else ""


def main() -> None:
    st.set_page_config(page_title="AllergyBuster Play Console", layout="wide")
    st.title("AllergyBuster Play Console Dashboard")
    st.caption("Aggregate Play Console reports only. No app telemetry or user-level data.")

    with st.sidebar:
        st.header("Data Source")
        source = st.radio("Load reports from", ["GCS bucket", "CSV upload"], horizontal=False)
        gcs_prefix = st.text_input("GCS prefix", DEFAULT_GCS_PREFIX)
        credentials_path = st.text_input("Service account JSON path", default_credentials_path())
        project_id = st.text_input("Billing/user project ID", "")
        max_files = st.slider("Max latest GCS CSV files", min_value=1, max_value=200, value=60, step=5)
        uploaded_files = st.file_uploader("Upload Play Console CSVs", type=["csv"], accept_multiple_files=True)

        st.header("Filters")
        today = date.today()
        default_start = date(today.year, 3, 1)
        default_end = date(today.year, 8, 31)
        start = st.date_input("Start date", default_start)
        end = st.date_input("End date", default_end)
        country_text = st.text_input("Countries", ",".join(DEFAULT_COUNTRIES))
        countries = [item.strip().upper() for item in re.split(r"[, ]+", country_text) if item.strip()]

    reports: list[pd.DataFrame] = []
    if source == "GCS bucket":
        if st.button("Load GCS reports", type="primary"):
            with st.spinner("Loading Play Console reports from GCS..."):
                try:
                    reports = load_gcs_reports(gcs_prefix, max_files, credentials_path, project_id)
                    st.session_state["reports"] = reports
                except Exception as error:
                    render_gcs_error(error)
                    reports = []
    else:
        if uploaded_files:
            reports = load_uploaded_reports(uploaded_files)
            st.session_state["reports"] = reports

    reports = st.session_state.get("reports", reports)
    df = concat_reports(reports)

    if df.empty:
        st.info("Load GCS reports or upload Play Console CSV files to start.")
        st.stop()

    with st.sidebar:
        package_hint = safe_package_hint(df)
        package_name = st.text_input("Package filter", package_hint)

    filtered = apply_filters(df, package_name, countries, start, end)
    st.caption(f"Loaded {len(df):,} rows from {len(reports):,} report files. Showing {len(filtered):,} filtered rows.")

    render_metric_cards(filtered)

    tab_trends, tab_country, tab_breakdowns, tab_raw = st.tabs(
        ["Trends", "Country Split", "Breakdowns", "Raw Data"]
    )

    with tab_trends:
        render_time_series(filtered)
        st.info(
            "High-pollen-day reach can be estimated by filtering this view to known high-pollen dates. "
            "True forecast-card reach needs app-side aggregate counters."
        )

    with tab_country:
        render_country_split(filtered)

    with tab_breakdowns:
        render_dimension_breakdown(filtered)

    with tab_raw:
        st.dataframe(filtered, use_container_width=True)
        st.download_button(
            "Download filtered CSV",
            filtered.to_csv(index=False).encode("utf-8"),
            "allergybuster-play-console-filtered.csv",
            "text/csv",
        )


if __name__ == "__main__":
    main()
