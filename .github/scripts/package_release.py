#!/usr/bin/env python3
"""Package only the runtime files needed to install Gladiator Society."""

import json
import re
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MOD_ROOT_NAME = "GladiatorSociety"
RUNTIME_PATHS = (
    "mod_info.json",
    "gladiatorsociety.version",
    "icon.png",
    "data",
    "graphics",
    "jars/GladiatorSociety.jar",
)


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"Could not read {path.name}: {error}")


def version_string(parts):
    try:
        return ".".join(str(int(parts[key])) for key in ("major", "minor", "patch"))
    except (KeyError, TypeError, ValueError):
        fail("Version must contain integer major, minor, and patch fields.")


def main():
    if len(sys.argv) != 2:
        fail("Usage: package_release.py VERSION")

    version = sys.argv[1]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        fail(f"Tag '{version}' must use the numeric MAJOR.MINOR.PATCH format (without v).")

    mod_info = read_json(ROOT / "mod_info.json")
    version_info = read_json(ROOT / "gladiatorsociety.version")
    if version_string(mod_info.get("version", {})) != version:
        fail("Git tag does not match mod_info.json version.")
    if version_string(version_info.get("modVersion", {})) != version:
        fail("Git tag does not match gladiatorsociety.version modVersion.")

    expected_url = (
        "https://github.com/OpenChatGit/GladiatorSociety/releases/download/"
        f"{version}/GladiatorSociety-{version}.zip"
    )
    if version_info.get("directDownloadURL") != expected_url:
        fail("gladiatorsociety.version directDownloadURL does not match this release tag.")

    archive_path = ROOT / "dist" / f"GladiatorSociety-{version}.zip"
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for entry in RUNTIME_PATHS:
            source = ROOT / entry
            if not source.exists():
                fail(f"Required runtime path is missing: {entry}")
            paths = sorted(source.rglob("*")) if source.is_dir() else [source]
            for path in paths:
                if path.is_file():
                    relative_path = path.relative_to(ROOT).as_posix()
                    archive.write(path, f"{MOD_ROOT_NAME}/{relative_path}")

    print(f"Created {archive_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
