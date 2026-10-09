#!/usr/bin/env bash
# 一鍵清理所有「錯位／重複」檔案（會造成 Redeclaration / Conflicting overloads）
set -e
cd "$(dirname "$0")"
echo "===== 1) 刪除錯位嘅 Screen 檔案 ====="
for f in EtaScreen FavoritesScreen HomeScreen SearchScreen SettingsScreen RailScreen \
         NearbyScreen RouteStopsScreen AboutScreen; do
  for d in data vm ui util location widget; do
    p="app/src/main/java/com/hketa/app/$d/$f.kt"
    [ -f "$p" ] && { git rm -f "$p" 2>/dev/null || rm -f "$p"; echo "  已刪 $p"; }
  done
done
echo
echo "===== 2) 刪除重複嘅 Changelog.kt（淨保留 data/）====="
for d in vm ui util location widget; do
  p="app/src/main/java/com/hketa/app/$d/Changelog.kt"
  [ -f "$p" ] && { git rm -f "$p" 2>/dev/null || rm -f "$p"; echo "  已刪 $p"; }
done
echo
echo "===== 3) 同名 .kt 檢查 ====="
dup=$(find app/src/main/java -name "*.kt" -printf "%f\n" | sort | uniq -d)
[ -n "$dup" ] && { echo "  ⚠ $dup"; } || echo "  ✔ 無同名 .kt"
echo
echo "===== 4) 最終 Changelog.kt ====="
find app/src/main/java -name "Changelog.kt" | sed 's/^/  /'
