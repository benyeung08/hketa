#!/usr/bin/env python3
"""
RELEASE_VERSION_GATE — make sure the signed production APK attached to a
GitHub Release really IS that version.

Three independent sources are cross-checked:

    tag (GITHUB_REF_NAME, e.g. v1.0.0)
      == versionName in app/build.gradle.kts
      == versionName in `aapt dump badging` of the ACTUAL release APK

and versionCode / packageName are checked between Gradle and the APK too.
A mismatch FAILS the workflow BEFORE softprops/action-gh-release uploads
anything, so a Release can never contain an APK whose version does not
match its tag. The in-app updater compares tag_name with versionName, so
this gate is what keeps "check for updates" honest.

On success it writes VERSION_INFO.txt (attached to the Release as an asset)
with the version triple, SDK levels, APK SHA-256, signing certificate
SHA-256, keystore source, commit and build time. Everything uses only the
Python standard library, so it runs on the runner without extra deps.

Usage (see .github/workflows/build-apk.yml):
    python3 scripts/verify_release_version.py \
        --tag "$GITHUB_REF_NAME" \
        --gradle app/build.gradle.kts \
        --badging apk-badging.txt \
        --apk "hketa-${GITHUB_REF_NAME}-release.apk" \
        --signing apksigner-output.txt \
        --out VERSION_INFO.txt \
        --commit "$GITHUB_SHA" \
        --keystore-source "$HKETA_CUSTOM_KEY"
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

EXPECTED_PACKAGE = "hk.eta.app"


def normalize_tag(tag: str) -> str:
    """v1.0.0 -> 1.0.0. Mirrors AppUpdate.compareVersions()' v-prefix rule."""
    t = (tag or "").strip()
    if t[:1] in ("v", "V"):
        t = t[1:]
    return t


def parse_gradle(text: str) -> dict:
    """Read versionCode / versionName / minSdk / targetSdk / applicationId."""
    def grab(pattern: str, cast=str):
        m = re.search(pattern, text)
        return cast(m.group(1)) if m else None

    return {
        "versionCode": grab(r"\bversionCode\s*=\s*(\d+)", int),
        "versionName": grab(r'\bversionName\s*=\s*"([^"]+)"'),
        "applicationId": grab(r'\bapplicationId\s*=\s*"([^"]+)"'),
        "minSdk": grab(r"\bminSdk\s*=\s*(\d+)", int),
        "targetSdk": grab(r"\btargetSdk\s*=\s*(\d+)", int),
    }


def parse_badging(text: str) -> dict:
    """
    Parse the `package:` line plus sdkVersion / targetSdkVersion /
    application-label from `aapt dump badging` output.
    Example package line:
      package: name='hk.eta.app' versionCode='1' versionName='1.0.0' ...
    """
    out = {"package": None, "versionCode": None, "versionName": None,
           "minSdk": None, "targetSdk": None, "label": None}
    pkg = re.search(r"^package:\s*(.+)$", text, re.M)
    if pkg:
        line = pkg.group(1)
        for key, pat in (
            ("package", r"name='([^']+)'"),
            ("versionCode", r"versionCode='(\d+)'"),
            ("versionName", r"versionName='([^']+)'"),
        ):
            m = re.search(pat, line)
            if m:
                out[key] = int(m.group(1)) if key == "versionCode" else m.group(1)
    for key, pat in (
        ("minSdk", r"^sdkVersion:'(\d+)'"),
        ("targetSdk", r"^targetSdkVersion:'(\d+)'"),
        ("label", r"^application-label(?:-[a-zA-Z-]+)?:'([^']+)'"),
    ):
        m = re.search(pat, text, re.M)
        if m:
            out[key] = int(m.group(1)) if key in ("minSdk", "targetSdk") \
                else m.group(1)
    return out


def signing_fingerprint(text: str) -> str:
    m = re.search(r"SHA-256 digest:\s*([0-9A-Fa-f: ]+)", text)
    if not m:
        return ""
    return re.sub(r"[^0-9A-Fa-f]", "", m.group(1)).upper()


def apk_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def fail(errors: list[str]) -> None:
    for e in errors:
        print(f"::error::{e}")
    print("::error::正式版 APK 版本對應驗證失敗：唔會上傳到 Releases，"
          "避免 Release 版本同 APK 內容不一致。")
    print("::error::修復：先改 app/build.gradle.kts 嘅 versionCode / versionName，"
          "commit + push，再重新打同一個版本 tag（例如 git tag -f v1.0.1 && "
          "git push -f origin v1.0.1）。")
    sys.exit(1)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--gradle", required=True)
    ap.add_argument("--badging", required=True)
    ap.add_argument("--apk", required=True)
    ap.add_argument("--signing", default="")
    ap.add_argument("--out", default="VERSION_INFO.txt")
    ap.add_argument("--commit", default="")
    ap.add_argument("--keystore-source", default="")
    a = ap.parse_args()

    tag = a.tag.strip()
    expected = normalize_tag(tag)
    gradle_path = Path(a.gradle)
    badging_path = Path(a.badging)
    apk_path = Path(a.apk)

    for p, label in ((gradle_path, "app/build.gradle.kts"),
                     (badging_path, "apk-badging.txt"),
                     (apk_path, "正式版 APK")):
        if not p.is_file():
            fail([f"{label} 唔存在：{p}（可能 assembleRelease 失敗或 aapt 無輸出）"])

    gradle = parse_gradle(gradle_path.read_text(encoding="utf-8"))
    apkinfo = parse_badging(badging_path.read_text(encoding="utf-8",
                                                   errors="replace"))
    errors: list[str] = []

    if not re.fullmatch(r"\d+(?:\.\d+)*(?:-[0-9A-Za-z.-]+)?", expected):
        errors.append(f"tag「{tag}」唔係有效版本號（期望 v1.0.0 呢類格式）")
    if gradle["versionName"] is None or gradle["versionCode"] is None:
        errors.append("app/build.gradle.kts 讀唔到 versionName / versionCode")
    if apkinfo["versionName"] is None or apkinfo["versionCode"] is None:
        errors.append("aapt dump badging 讀唔到 versionName / versionCode")

    if not errors:
        # 1) tag <-> build.gradle
        if gradle["versionName"] != expected:
            errors.append(
                f"tag 與 Gradle 版本唔對應：tag「{tag}」→ {expected}，"
                f"但 versionName = {gradle['versionName']}。"
                f"要 bump 版本先至打 tag。"
            )
        # 2) Gradle <-> actual APK
        if apkinfo["versionName"] != gradle["versionName"]:
            errors.append(
                f"APK 內容與 Gradle 版本唔對應：APK versionName = "
                f"{apkinfo['versionName']}，Gradle = {gradle['versionName']}"
            )
        if apkinfo["versionCode"] != gradle["versionCode"]:
            errors.append(
                f"versionCode 唔對應：APK = {apkinfo['versionCode']}，"
                f"Gradle = {gradle['versionCode']}"
            )
        # 3) package + SDK sanity
        if apkinfo["package"] != EXPECTED_PACKAGE:
            errors.append(
                f"APK package 唔對應：{apkinfo['package']}（期望 {EXPECTED_PACKAGE}）"
            )
        if gradle["applicationId"] and \
                gradle["applicationId"] != EXPECTED_PACKAGE:
            errors.append(
                f"Gradle applicationId 唔對應：{gradle['applicationId']}"
            )
        if apkinfo["minSdk"] != gradle["minSdk"]:
            errors.append(
                f"minSdk 唔對應：APK = {apkinfo['minSdk']}，"
                f"Gradle = {gradle['minSdk']}"
            )
        if apkinfo["targetSdk"] != gradle["targetSdk"]:
            errors.append(
                f"targetSdk 唔對應：APK = {apkinfo['targetSdk']}，"
                f"Gradle = {gradle['targetSdk']}"
            )

    if errors:
        fail(errors)

    fp = ""
    if a.signing and Path(a.signing).is_file():
        fp = signing_fingerprint(Path(a.signing).read_text(encoding="utf-8",
                                                          errors="replace"))
    digest = apk_sha256(apk_path)
    now = datetime.now(timezone.utc)
    hkt = now.astimezone(timezone(timedelta(hours=8)))
    keystore = ("injected HKETA_KEYSTORE_BASE64 (production keystore)"
                if a.keystore_source.strip() == "true"
                else "committed hketa-debug.keystore (shared dev certificate)")

    lines = [
        "HK ETA — 正式版 APK 版本對應資訊 / Release build manifest",
        "==========================================================",
        f"Release tag          : {tag}",
        f"versionName          : {gradle['versionName']}  "
        "(tag == build.gradle.kts == aapt badging)",
        f"versionCode          : {gradle['versionCode']}  "
        "(build.gradle.kts == aapt badging)",
        f"Package              : {apkinfo['package']}",
        f"minSdk / targetSdk   : {apkinfo['minSdk']} / {apkinfo['targetSdk']}",
        f"Application label    : {apkinfo['label'] or 'HK ETA'}",
        "Build type           : release (signed production APK, NOT debug)",
        f"APK asset            : {apk_path.name}",
        f"APK SHA-256          : {digest}",
        f"Signing cert SHA-256 : {fp or '(由 SIGNING_FINGERPRINT.txt 提供)'}",
        f"Keystore             : {keystore}",
        f"Commit               : {a.commit or '(local build)'}",
        f"Built (UTC)          : {now.strftime('%Y-%m-%d %H:%M:%S UTC')}",
        f"Built (HKT)          : {hkt.strftime('%Y-%m-%d %H:%M:%S HKT')}",
        "Verification         : PASS  "
        "(tag ↔ app/build.gradle.kts ↔ actual APK badging)",
        "",
        "This file is attached to the GitHub Release together with the signed",
        "release APK. The workflow refuses to publish when any of the three",
        "version sources disagree, so the APK in this Release IS this version.",
    ]
    out = Path(a.out)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"[OK] 版本對應：{tag} ↔ versionName {gradle['versionName']} "
          f"↔ versionCode {gradle['versionCode']} ↔ {apkinfo['package']}")
    print(f"[OK] APK SHA-256: {digest}")
    print(f"[OK] 已寫入 {out}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"\n### 正式版 APK 版本對應 ✅\n\n"
                    f"- Release `{tag}` = `versionName {gradle['versionName']}` "
                    f"= APK badging `versionCode {gradle['versionCode']}`\n"
                    f"- `{apk_path.name}` SHA-256 `{digest}`\n"
                    f"- 詳情見 Release asset `VERSION_INFO.txt`\n")


if __name__ == "__main__":
    main()
