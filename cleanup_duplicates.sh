#!/usr/bin/env bash
# 刪除放錯目錄嘅 Screen 重複檔（佢哋令 Kotlin 報 "Conflicting overloads"）
set -e
cd "$(dirname "$0")"
BASE="app/src/main/java/com/hketa/app"

for f in EtaScreen FavoritesScreen HomeScreen SearchScreen; do
  if [ -f "$BASE/data/$f.kt" ]; then
    rm -f "$BASE/data/$f.kt"
    echo "已刪除 data/$f.kt"
  fi
done

echo
echo "剩低嘅 data/ 檔案："
ls -1 "$BASE/data"
