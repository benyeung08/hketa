#!/usr/bin/env python3
"""
PUBLISHED_RELEASE_BACKFILL — add the SIGNED PRODUCTION (release) APK to a
GitHub Release that has ALREADY been published.

Why this exists
---------------
The normal tagged-release job attaches `hketa-vX.Y.Z-release.apk` when a NEW
`v*` tag is pushed. But a Release that was published earlier (e.g. v1.0.0)
may still carry only a debug APK, or no APK at all. The in-app updater
(`AppUpdate.latest()`) takes the FIRST `.apk` asset of the latest Release, so
an old/debug asset would silently win.

This module lets a `workflow_dispatch` run pick an ALREADY-PUBLISHED Release,
rebuild the signed production APK **from that tag's own source** (so
tag == versionName == aapt badging still holds), remove conflicting `.apk`
assets and upload exactly ONE `-release.apk`. It NEVER creates a new Release,
NEVER moves the tag, NEVER bumps a version and NEVER rewrites the Release
body (the bilingual v1.0.0 notes are preserved).

Pure standard library, offline-testable. The shell steps in the workflow do
the actual `gh` / Gradle work; this module only decides WHAT to do.

CLI
---
  resolve --json releases.json --requested TAG --include-prerelease BOOL \
          --out "$GITHUB_OUTPUT"
  prune   --json release.json --keep NAME --out prune.txt
  final   --json release.json --expect NAME
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

APK_SUFFIX = ".apk"
PRODUCTION_MARKER = "-release.apk"


def parse_releases(text: str) -> list[dict]:
    """Accept the GitHub `/releases` array or a single release object."""
    data = json.loads(text)
    if isinstance(data, list):
        return [r for r in data if isinstance(r, dict)]
    if isinstance(data, dict):
        # gh release view --json / a single /releases/tags/... response
        return [data]
    return []


def is_published(release: dict) -> bool:
    """A published Release is not a draft and has a published_at timestamp."""
    if not isinstance(release, dict):
        return False
    if bool(release.get("draft")):
        return False
    return bool(release.get("published_at") or release.get("created_at"))


def is_prerelease(release: dict) -> bool:
    return bool(release.get("prerelease"))


def tag_of(release: dict) -> str:
    return str(release.get("tag_name") or "").strip()


def _sort_key(release: dict) -> str:
    return str(release.get("published_at")
               or release.get("created_at") or "")


def select_target(releases: list[dict], requested: str,
                  include_prerelease: bool = False) -> tuple[dict | None,
                                                             list[str]]:
    """Return (release, errors). `requested=''` means newest published."""
    usable = [r for r in releases if is_published(r)]
    if not usable:
        return None, ["搵唔到任何已發佈嘅 Release（全部係 draft 或無 published_at）。"
                      "先建立一個 Release，或改用新 tag 走正常發布流程。"]

    req = (requested or "").strip()
    if req:
        hit = next((r for r in usable if tag_of(r) == req), None)
        if hit is None:
            draft = next((r for r in releases
                          if tag_of(r) == req and bool(r.get("draft"))), None)
            if draft is not None:
                return None, [f"版本「{req}」存在但係 draft，未發佈；"
                              "先的手動 Publish，或改用另一個已發佈 tag。"]
            available = ", ".join(tag_of(r) for r in usable if tag_of(r))
            return None, [f"搵唔到已發佈版本「{req}」。可用：{available or '(無)'}"]
        if is_prerelease(hit) and not include_prerelease:
            return None, [f"「{req}」係 prerelease；要補正式版請勾選 "
                          "include_prerelease，或改用正式 Release。"]
        return hit, []

    pool = usable if include_prerelease else [
        r for r in usable if not is_prerelease(r)]
    if not pool:
        return None, ["只有 prerelease；要補正式版請勾選 include_prerelease。"]
    return max(pool, key=_sort_key), []


def assets_of(release: dict) -> list[dict]:
    assets = release.get("assets") or []
    return [a for a in assets if isinstance(a, dict)]


def apk_assets(release: dict) -> list[dict]:
    return [a for a in assets_of(release)
            if str(a.get("name") or "").lower().endswith(APK_SUFFIX)]


def is_production_apk(name: str) -> bool:
    n = (name or "").lower()
    return n.endswith(APK_SUFFIX) and PRODUCTION_MARKER in n


def prune_ids(release: dict, keep_name: str) -> list[str]:
    """Asset IDs of OTHER .apk assets that must be removed before uploading.

    The in-app updater takes the FIRST .apk asset, so leaving a debug APK (or
    an older release APK) next to the new one would make it download the wrong
    file. Non-APK assets (VERSION_INFO.txt, SIGNING_FINGERPRINT.txt, bilingual
    notes, anything the maintainer uploaded) are never touched.
    """
    keep = (keep_name or "").strip()
    out = []
    for a in apk_assets(release):
        name = str(a.get("name") or "")
        if name == keep:
            continue          # --clobber replaces it in place
        aid = a.get("id")
        if aid is not None:
            out.append(str(aid))
    return out


def final_audit(release: dict, expect_name: str) -> tuple[bool, list[str]]:
    """After upload: exactly ONE .apk, and it IS the signed production APK."""
    apks = apk_assets(release)
    errors: list[str] = []
    if len(apks) != 1:
        errors.append(f"最終 Release 有 {len(apks)} 個 .apk asset，應該得 1 個："
                      + ", ".join(str(a.get('name')) for a in apks))
    name = str(apks[0].get("name") or "") if apks else ""
    if name and name != expect_name:
        errors.append(f"最終 .apk 名係「{name}」，期望「{expect_name}」")
    if name and not is_production_apk(name):
        errors.append(f"「{name}」唔係正式版命名（應含 -release.apk）")
    return (not errors), errors


def version_source_hint(tag: str) -> str:
    return (f"PUBLISHED_RELEASE_BACKFILL：用 tag「{tag}」本身嘅 source 構建，"
            "版本對應由 verify_release_version.py 三重校驗 "
            "(tag ↔ build.gradle.kts ↔ aapt badging)；唔會用 main 嘅新 code "
            "扮舊版本。")


def _write_output(path: str, pairs: list[tuple[str, str]]) -> None:
    lines = [f"{k}={v}" for k, v in pairs]
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    for line in lines:
        print(line)


def cmd_resolve(a: argparse.Namespace) -> int:
    text = Path(a.json).read_text(encoding="utf-8", errors="replace")
    try:
        releases = parse_releases(text)
    except json.JSONDecodeError as e:
        print(f"::error::GitHub Releases JSON 解析失敗：{e}")
        return 1
    incl = str(a.include_prerelease).lower() in ("1", "true", "yes", "on")
    release, errors = select_target(releases, a.requested, incl)
    if errors:
        for e in errors:
            print(f"::error::{e}")
        print("::error::PUBLISHED_RELEASE_BACKFILL 停止：唔會新建 Release、"
              "唔會改 tag、唔會上傳任何 APK。")
        return 1
    tag = tag_of(release)
    apks = [str(x.get("name") or "") for x in apk_assets(release)]
    print(f"[OK] 已發佈版本：{tag}"
          f"（{'prerelease' if is_prerelease(release) else '正式 Release'}）")
    print(f"[OK] 現有 .apk assets：{', '.join(apks) or '(無)'}")
    print(version_source_hint(tag))
    _write_output(a.out, [
        ("tag", tag),
        ("release_name", str(release.get("name") or tag)),
        ("published_at", str(release.get("published_at") or "")),
        ("is_prerelease", "true" if is_prerelease(release) else "false"),
        ("existing_apks", ",".join(apks)),
        ("asset_count", str(len(assets_of(release)))),
    ])
    return 0


def cmd_prune(a: argparse.Namespace) -> int:
    data = json.loads(Path(a.json).read_text(encoding="utf-8",
                                            errors="replace"))
    release = data[0] if isinstance(data, list) else data
    ids = prune_ids(release, a.keep)
    Path(a.out).write_text("\n".join(ids) + "\n", encoding="utf-8")
    print(f"[OK] 要移除嘅舊 .apk assets：{len(ids)} 個"
          + (f" ({', '.join(ids)})" if ids else ""))
    print("[OK] 非 .apk assets（說明／指紋／用家檔案）完全保留，Release body 唔改")
    return 0


def cmd_final(a: argparse.Namespace) -> int:
    data = json.loads(Path(a.json).read_text(encoding="utf-8",
                                            errors="replace"))
    release = data[0] if isinstance(data, list) else data
    ok, errors = final_audit(release, a.expect)
    if not ok:
        for e in errors:
            print(f"::error::{e}")
        print("::error::PUBLISHED_RELEASE_BACKFILL 最終檢查失敗："
              "App 內更新有可能下載錯 APK。")
        return 1
    print(f"[OK] 已發佈 Release 而家只有一個正式版 APK：{a.expect}")
    print("[OK] App 內更新（releases/latest 第一個 .apk）會攞到呢個正式版")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("resolve")
    p.add_argument("--json", required=True)
    p.add_argument("--requested", default="")
    p.add_argument("--include-prerelease", default="false")
    p.add_argument("--out", default="")
    p.set_defaults(func=cmd_resolve)

    p = sub.add_parser("prune")
    p.add_argument("--json", required=True)
    p.add_argument("--keep", required=True)
    p.add_argument("--out", required=True)
    p.set_defaults(func=cmd_prune)

    p = sub.add_parser("final")
    p.add_argument("--json", required=True)
    p.add_argument("--expect", required=True)
    p.set_defaults(func=cmd_final)

    a = ap.parse_args()
    return a.func(a)


if __name__ == "__main__":
    raise SystemExit(main())
