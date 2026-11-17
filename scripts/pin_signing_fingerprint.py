#!/usr/bin/env python3
"""Pins the APK signing certificate SHA-256 into the app resources.

Called by the tagged-release GitHub Actions step, AFTER the static checks and
BEFORE assembleRelease. The value comes from the very keystore Gradle will use,
so the built APK can never ship with a stale / mismatched fingerprint.

Usage:
    python3 scripts/pin_signing_fingerprint.py FE:7B:35:...:57
"""

from pathlib import Path
import re
import sys

STRINGS = Path(__file__).resolve().parents[1] / "app/src/main/res/values/strings.xml"
KEY = "expected_signing_sha256"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: pin_signing_fingerprint.py <sha256-fingerprint>")
        return 2
    raw = sys.argv[1].strip().replace(":", "").replace(" ", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", raw):
        print(f"invalid SHA-256 fingerprint: {sys.argv[1]!r}")
        return 2

    text = STRINGS.read_text(encoding="utf-8")
    pattern = re.compile(r'(<string name="' + KEY + r'">)[^<]*(</string>)')
    updated, count = pattern.subn(lambda m: m.group(1) + raw + m.group(2), text, count=1)
    if count != 1:
        print(f"could not find <string name=\"{KEY}\"> in {STRINGS}")
        return 2
    STRINGS.write_text(updated, encoding="utf-8")
    print(f"pinned {KEY} = {raw}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
