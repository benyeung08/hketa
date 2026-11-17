#!/usr/bin/env python3
"""Distils the REAL cause out of a Gradle build log.

With --stacktrace (older workflow) the tail was dozens of internal frames like
`DefaultConditionalExecutionQueue$ExecutionRunner.run(...)`; those are NOT the
error. The CI no longer passes --stacktrace, so Gradle's own tail is already
the answer -- this script is kept as a belt-and-braces summary for BOTH shapes
of log. It scans the whole log and prints, as GitHub Actions ::error::
annotations, the lines that actually matter:

  FAILURE: / * What went wrong: / Execution failed for task
  Caused by: / e: ... (Kotlin) / error: ... (Java)
  AAPT2 / resource linking / Keystore / Could not resolve / SDK location

It also emits a short "ROOT CAUSE" table into $GITHUB_STEP_SUMMARY and a
category-specific hint. Always exits 0 so it never masks the original failure.

Usage (CI):  python3 scripts/show_build_error.py build-debug.log build-release.log
"""

from pathlib import Path
import os
import re
import sys

PATTERNS = [
    r"^\*?\s*FAILURE:",
    r"^\*\s*What went wrong:",
    r"^\s*Execution failed for task",
    r"^\s*Caused by:",
    r"^\s*e: file:",                       # Kotlin compiler error
    r"^\s*e: ",                           # Kotlin compiler error (short form)
    r"^\s*error:",                        # javac / general
    r"Unresolved reference",
    r"cannot find symbol",
    r"AAPT2|Android resource linking failed|resource .* not found",
    r"Keystore was tampered|Failed to read key|Invalid keystore|"
    r"keystore password was incorrect|Keystore file .* not found",
    r"Could not resolve|Could not GET|Read timed out|Connection reset|"
    r"Received status code 5\d\d",
    r"SDK location not found|Failed to find Platform SDK|"
    r"has not been accepted|license",
    r"No installed build tools|Installed Build Tools revision .* is corrupted",
    r"^\s*BUILD FAILED",
]

HINTS = [
    ("Unresolved reference", "Kotlin 編譯錯誤：九成係新舊檔案混住（例如新 SettingsActivity 配舊 AppUpdatePrefs）。"
     "唯一解法：由 hketa-github.zip 全量 git push，唔好逐個檔 upload。"),
    ("cannot find symbol", "Java 編譯錯誤：同上，檢查是否只更新咗部分 .java 檔。"),
    ("AAPT2", "Android 資源合併失敗：檢查 res/ 下檔名、strings.xml  escaping、mipmap 圖示係咪完整。"),
    ("Keystore", "簽名金鑰有問題：app/hketa-debug.keystore 必須係原始二進位檔（PKCS12，"
     "2790 bytes，開頭 30 82）。網頁上傳有機會變成文字／損毀 → 由 zip 重新覆蓋。"),
    ("Could not resolve", "依賴下載失敗／網絡暫時性：直接撳 Re-run jobs 多數就過。"),
    ("SDK location", "Android SDK 路徑／授權問題：睇返「Locate Android SDK」同「Accept licenses」兩 step 嘅輸出。"),
    ("license", "Android SDK 授權未接受：睇返「Accept licenses and install SDK 34」step。"),
]

MAX_LINES = 45


def main() -> int:
    logs = [Path(a) for a in sys.argv[1:] if Path(a).is_file()]
    if not logs:
        print("::error::冇搵到任何 Gradle log（失敗可能發生喺打包之前，請睇上一個紅色 step）")
        return 0

    all_lines = []
    for log in logs:
        all_lines.extend(log.read_text(encoding="utf-8", errors="replace").splitlines())
    print(f"::error::Gradle log 共 {len(all_lines)} 行；以下係真正嘅錯誤摘要（完整 log 會上傳成 Artifact）")
    print("::error::提示：如果 log 最尾仍然係 'at org.gradle.internal...'，代表新 workflow 未生效，"
          "請確認 .github/workflows/build-apk.yml 已經上傳")

    regex = re.compile("|".join(f"(?:{p})" for p in PATTERNS), re.IGNORECASE)
    seen = set()
    hits = []
    for i, line in enumerate(all_lines):
        if regex.search(line):
            clean = line.strip()[:220]
            if clean and clean not in seen:
                seen.add(clean)
                hits.append((i + 1, clean))
    if not hits:
        print("::error::喺 log 搵唔到標準錯誤關鍵字；請直接下載 Artifact 入面嘅完整 log，"
              "由頭搜尋 'What went wrong' / 'Caused by'。")
        # Still show the first 25 non-stacktrace lines as a fallback.
        for i, line in enumerate(all_lines[:25], 1):
            print(f"::error::L{i}: {line.strip()[:200]}")
    else:
        for lineno, text in hits[:MAX_LINES]:
            print(f"::error::L{lineno}: {text}")
        if len(hits) > MAX_LINES:
            print(f"::error::… 另外 {len(hits) - MAX_LINES} 條，詳見完整 log")

    joined = "\n".join(t for _, t in hits)
    print("::error::── 針對性建議 ──")
    matched = False
    for needle, hint in HINTS:
        if needle.lower() in joined.lower():
            print(f"::error::{hint}")
            matched = True
    if not matched:
        print("::error::貼上面 Lxxx 幾行畀我，即可精準改；唔好只貼 Gradle 內部 stack frame。")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("\n### Gradle 失敗摘要\n\n")
            f.write("| 行號 | 內容 |\n| --- | --- |\n")
            for lineno, text in hits[:25]:
                safe = text.replace("|", "\\|")
                f.write(f"| {lineno} | {safe} |\n")
            f.write("\n完整 log：`hketa-gradle-logs` Artifact。\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
