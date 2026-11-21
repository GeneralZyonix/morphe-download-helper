#!/usr/bin/env python3
"""Build the compact Awesome Morphe app/source index used by the Patches tab.

The upstream catalog is large. This script keeps only the source metadata and
the source-to-app relation needed for app-first search. It fetches the catalog
from the raw GitHub path so the index does not depend on the website domain.

Run:
    python scripts/build_patch_index.py
    python scripts/build_patch_index.py --upstream <local-json-or-url>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

UPSTREAM_URL = (
    "https://raw.githubusercontent.com/nvbangg/awesome-morphe/"
    "main/web/public/bundles.json"
)
DEFAULT_OUTPUT = Path("docs/data/patch-sources.json")
DEFAULT_DETAILS_OUTPUT = Path("docs/data/patch-details.json")
USER_AGENT = "Morphe-Download-Helper-Patch-Index/1.0"


def read_upstream(location: str) -> dict:
    if location.startswith("http://") or location.startswith("https://"):
        request = urllib.request.Request(location, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = response.read()
    else:
        payload = Path(location).read_bytes()
    return json.loads(payload.decode("utf-8"))


def build_catalog(upstream: dict) -> dict:
    bundles = upstream.get("bundles") or []
    compatibilities = upstream.get("compatibilities") or []
    store = upstream.get("store") or {}

    sources = []
    app_sources: dict[str, dict[int, list[int]]] = {}

    for bundle in bundles:
        source_id = len(sources)
        source = {
            "source": bundle.get("source") or "github",
            "repo": bundle.get("repo") or "",
            "name": bundle.get("name") or bundle.get("repo") or "",
            "description": bundle.get("repoDescription") or "",
            "avatar": bundle.get("avatarUrl") or "",
            "stars": int(bundle.get("stars") or 0),
            "updatedAt": int(bundle.get("updatedAt") or 0),
            "isPreRelease": bool(bundle.get("isPreRelease")),
            "isArchived": bool(bundle.get("isArchived")),
            "isOfficial": bool(bundle.get("isOfficial")),
        }
        if not source["repo"]:
            continue
        if source["isArchived"]:
            # Archived sources no longer receive patch updates and would only
            # offer stale patches.
            continue
        sources.append(source)

        patch_counts: dict[str, int] = {}
        for patch in bundle.get("patches") or []:
            key = patch.get("compatiblePackagesKey")
            if key is None:
                continue
            try:
                packages = compatibilities[key]
            except (IndexError, TypeError):
                continue
            for package in packages or []:
                package_name = package.get("packageName")
                if package_name:
                    patch_counts[package_name] = patch_counts.get(package_name, 0) + 1

        app_updates = bundle.get("appUpdates") or {}
        for package_name, patch_count in patch_counts.items():
            if package_name == "__universal__":
                continue
            updated = app_updates.get(package_name) or source["updatedAt"]
            app_sources.setdefault(package_name, {})[source_id] = [
                int(updated),
                int(patch_count),
            ]

    apps = {}
    for package_name, entries in app_sources.items():
        meta = store.get(package_name) or {}
        source_list = [
            [source_id, values[0], values[1]]
            for source_id, values in entries.items()
        ]
        source_list.sort(
            key=lambda item: (
                not sources[item[0]]["isOfficial"],
                -item[1],
                sources[item[0]]["name"].lower(),
            )
        )
        apps[package_name] = {
            "name": meta.get("name") or package_name,
            "icon": meta.get("iconUrl") or "",
            "installs": int(meta.get("minInstalls") or 0),
            "sources": source_list,
        }

    apps = dict(sorted(apps.items()))
    return {
        "version": 1,
        "sources": sources,
        "apps": apps,
    }


def build_details(upstream: dict) -> dict:
    bundles = upstream.get("bundles") or []
    compatibilities = upstream.get("compatibilities") or []

    repos = {}
    for bundle in bundles:
        repo = bundle.get("repo") or ""
        if not repo or bundle.get("isArchived"):
            continue
        repo_details = repos.setdefault(repo, {})
        for patch in bundle.get("patches") or []:
            key = patch.get("compatiblePackagesKey")
            if key is None:
                continue
            try:
                packages = compatibilities[key]
            except (IndexError, TypeError):
                continue
            name = patch.get("name") or "Patch"
            for package in packages or []:
                package_name = package.get("packageName")
                if not package_name or package_name == "__universal__":
                    continue
                versions = []
                for target in package.get("targets") or []:
                    version = target.get("version")
                    if version and version not in versions:
                        versions.append(version)
                entries = repo_details.setdefault(package_name, [])
                signature = [name, versions]
                if signature not in entries:
                    entries.append(signature)

    return {
        "version": 1,
        "repos": dict(sorted(repos.items())),
    }


def data_hash(catalog: dict) -> str:
    encoded = json.dumps(
        {
            "version": catalog["version"],
            "sources": catalog["sources"],
            "apps": catalog["apps"],
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def details_hash(details: dict) -> str:
    encoded = json.dumps(
        details, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", default=UPSTREAM_URL)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--details-output", default=str(DEFAULT_DETAILS_OUTPUT))
    args = parser.parse_args()

    try:
        upstream = read_upstream(args.upstream)
    except Exception as error:  # noqa: BLE001 - command-line tool
        print(f"Could not read upstream catalog: {error}", file=sys.stderr)
        return 1

    generated_at = (
        datetime.now(timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )

    catalog = build_catalog(upstream)
    digest = data_hash(catalog)
    output = Path(args.output)
    if output.exists():
        try:
            previous = json.loads(output.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            previous = None
        if previous and previous.get("dataHash") == digest:
            print(
                f"Catalog unchanged: {len(catalog['sources'])} sources, "
                f"{len(catalog['apps'])} apps"
            )
        else:
            write_document(output, catalog, digest, generated_at)
            print(
                f"Wrote {output}: {len(catalog['sources'])} sources, "
                f"{len(catalog['apps'])} apps, {output.stat().st_size} bytes"
            )
    else:
        write_document(output, catalog, digest, generated_at)
        print(
            f"Wrote {output}: {len(catalog['sources'])} sources, "
            f"{len(catalog['apps'])} apps, {output.stat().st_size} bytes"
        )

    details = build_details(upstream)
    detail_digest = details_hash(details)
    details_output = Path(args.details_output)
    if details_output.exists():
        try:
            previous_details = json.loads(
                details_output.read_text(encoding="utf-8")
            )
        except (OSError, json.JSONDecodeError):
            previous_details = None
        if previous_details and previous_details.get("dataHash") == detail_digest:
            print(f"Patch details unchanged: {len(details['repos'])} repos")
        else:
            write_document(
                details_output, details, detail_digest, generated_at,
                hash_name="dataHash",
            )
            print(
                f"Wrote {details_output}: {len(details['repos'])} repos, "
                f"{details_output.stat().st_size} bytes"
            )
    else:
        write_document(
            details_output, details, detail_digest, generated_at,
            hash_name="dataHash",
        )
        print(
            f"Wrote {details_output}: {len(details['repos'])} repos, "
            f"{details_output.stat().st_size} bytes"
        )

    return 0


def write_document(path: Path, payload: dict, digest: str, generated_at: str,
                   hash_name: str = "dataHash") -> None:
    document = {
        "version": payload["version"],
        "generatedAt": generated_at,
        hash_name: digest,
    }
    for key, value in payload.items():
        if key == "version":
            continue
        document[key] = value
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    temporary.replace(path)


if __name__ == "__main__":
    raise SystemExit(main())
