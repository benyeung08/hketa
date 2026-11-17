#!/usr/bin/env python3
"""One-shot repair for the 3 files that keep staying OLD after partial uploads.

Idempotent: safe to run repeatedly. It only patches the three simple,
unambiguous edits and merely REPORTS the fourth (build.gradle.kts signingConfigs
is too structural to rewrite safely).

    python3 scripts/fix_drift.py
    python3 scripts/preflight.py          # should now be OK
    git add . && git commit -m "Full sync" && git push origin main

The real fix is still a full-directory push; this script just unblocks a repo
that was assembled file-by-file through the GitHub web UI.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
changed = []


def patch(rel: str, apply, label: str):
    p = ROOT / rel
    if not p.is_file():
        print(f"SKIP  {rel} (不存在)")
        return
    before = p.read_text(encoding="utf-8")
    after = apply(before)
    if after != before:
        p.write_text(after, encoding="utf-8")
        changed.append(rel)
        print(f"FIXED {rel}: {label}")
    else:
        print(f"OK    {rel}: 已經係新版")


def fix_gitignore(s: str) -> str:
    if "/app/hketa-release.keystore" in s:
        return s
    if not s.endswith("\n"):
        s += "\n"
    return s + "\n# CI-injected release keystore. Never commit a real signing key.\n" \
               "/app/hketa-release.keystore\n"


def fix_manifest(s: str) -> str:
    return re.sub(
        r'android:roundIcon="@mipmap/ic_launcher"',
        'android:roundIcon="@mipmap/ic_launcher_round"',
        s,
    )


def fix_strings(s: str) -> str:
    return re.sub(
        r'(<string name="default_github_repo">)[^<]*(</string>)',
        r"\g<1>benyeung08/hketa\g<2>",
        s,
        count=1,
    )


def main() -> int:
    patch(".gitignore", fix_gitignore, "加入 /app/hketa-release.keystore")
    patch("app/src/main/AndroidManifest.xml", fix_manifest,
          "roundIcon → ic_launcher_round")
    patch("app/src/main/res/values/strings.xml", fix_strings,
          "default_github_repo → benyeung08/hketa")

    gradle = ROOT / "app/build.gradle.kts"
    if gradle.is_file() and "hketaDev" not in gradle.read_text(encoding="utf-8"):
        print("WARN  app/build.gradle.kts 仍缺 signingConfigs(hketaDev)："
              "呢個唔自動改，請由 hketa-github.zip 直接覆蓋成個檔")
    else:
        print("OK    app/build.gradle.kts: 已有 hketaDev")

    print()
    if changed:
        print(f"已修正 {len(changed)} 個檔。跟住執行：")
    else:
        print("冇嘢要改。跟住執行：")
    print("  python3 scripts/preflight.py && python3 scripts/check_api_fields.py")
    print('  git add . && git commit -m "Full sync" && git push origin main')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
