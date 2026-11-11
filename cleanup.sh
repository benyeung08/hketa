#!/usr/bin/env bash
# HKETA 目前 main — 本機自查（唔改任何 code 檔）
# 喺 repo 根目錄執行：bash cleanup.sh
set -u
KEEP="app/src/main/assets/hketa.json"
DUP="app/src/main/assets/shketa.json"

echo "── 資料檔 ───────────────────────────────"
if [ -f "$KEEP" ]; then echo "✅ $KEEP 存在 ($(wc -c < "$KEEP") bytes)"; else echo "❌ 缺 $KEEP"; fi
if [ -f "$DUP" ]; then
  echo "⚠️ 仍有重複檔 $DUP → git rm app/src/main/assets/shketa.json"
else
  echo "✅ 重複檔 shketa.json 已清"
fi

echo ""
echo "── 靜態驗證 ─────────────────────────────"
python3 -m json.tool "$KEEP" > /dev/null && echo "✅ json.tool OK" || echo "❌ json.tool 失敗"
python3 scripts/validate.py 2>&1 | tail -4

echo ""
echo "── 目前 main 唯一阻斷：4 個 Secrets ──────"
cat <<'TODO'
CI 最新 run 42839995848 實測死喺 step 9「Verify signing secrets」。
去 GitHub → Settings → Secrets and variables → Actions 加：
  HKETA_KEYSTORE_BASE64 / HKETA_KEYSTORE_PASSWORD
  HKETA_KEY_ALIAS      / HKETA_KEY_PASSWORD
先用  bash scripts/gen_keystore.sh  產生「新」keystore（舊 key 已外洩）。

之後：
  git push → 等 Actions → Artifacts 拎 HKETA-debug.apk / HKETA-release.apk
  git tag v2.0.0 && git push origin v2.0.0   ← 首個 Release（目前 0 個）
  依 SECURITY.md 用 git filter-repo 清歷史、刪 ci-logs / apk 分支
TODO
