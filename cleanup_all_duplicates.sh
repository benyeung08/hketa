#!/usr/bin/env bash
# 一鍵清理所有「錯位／重複」檔案。
# 呢啲檔案係之前上傳時放錯目錄造成嘅，會令 Kotlin 報
#   "Redeclaration: object/class XXX" 或 "Conflicting overloads"
set -e
cd "$(dirname "$0")"

echo "===== 1) 刪除錯位嘅 Screen 檔案（原本應該喺 ui/screens/）====="
for f in EtaScreen FavoritesScreen HomeScreen SearchScreen SettingsScreen RailScreen \
         NearbyScreen RouteStopsScreen AboutScreen; do
  for d in data vm ui util location widget; do
    p="app/src/main/java/com/hketa/app/$d/$f.kt"
    if [ -f "$p" ]; then
      git rm -f "$p" 2>/dev/null || rm -f "$p"
      echo "  已刪 $p"
    fi
  done
done

echo
echo "===== 2) 刪除重複嘅 Changelog.kt（淨保留 data/ 嗰份）====="
for d in vm ui util location widget; do
  p="app/src/main/java/com/hketa/app/$d/Changelog.kt"
  if [ -f "$p" ]; then
    git rm -f "$p" 2>/dev/null || rm -f "$p"
    echo "  已刪 $p"
  fi
done

echo
echo "===== 3) 檢查仲有冇重複嘅頂層 class / object 名 ====="
dup=$(find app/src/main/java -name "*.kt" -printf "%f\n" | sort | uniq -d)
if [ -n "$dup" ]; then
  echo "  ⚠ 仲有同名檔案："
  echo "$dup" | sed 's/^/    /'
else
  echo "  ✔ 無任何同名 .kt 檔案"
fi

echo
echo "===== 4) 最終 Changelog.kt ====="
find app/src/main/java -name "Changelog.kt" | sed 's/^/  /'
