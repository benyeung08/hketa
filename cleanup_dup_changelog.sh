#!/usr/bin/env bash
# 刪除錯位嘅 vm/Changelog.kt —— 佢同 data/Changelog.kt 撞咗，會報
# "Redeclaration: object Changelog"
set -e
f="app/src/main/java/com/hketa/app/vm/Changelog.kt"
if [ -f "$f" ]; then
  git rm -f "$f" 2>/dev/null || rm -f "$f"
  echo "已刪除 $f ✔"
else
  echo "唔存在，唔使理 ✔"
fi
echo "--- 剩低嘅 Changelog.kt ---"
find app/src/main/java -name "Changelog.kt"
