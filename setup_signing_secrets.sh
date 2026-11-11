#!/usr/bin/env bash
# HKETA — 產生全新簽名 keystore，並列出要填入 GitHub Secrets 嘅 4 個值。
# 喺 repo 根目錄執行：bash setup_signing_secrets.sh
# 密碼用 read -s，唔會顯示；產出嘅 .jks / .base64.txt 已被 .gitignore 擋。
set -euo pipefail

OUT="${1:-hketa-release.jks}"
B64="${OUT}.base64.txt"
ALIAS="hketa"

command -v keytool >/dev/null 2>&1 || { echo "❌ 搵唔到 keytool，請先裝 JDK 17"; exit 1; }
if [ -f "$OUT" ]; then
  echo "⚠️ $OUT 已存在，唔會覆蓋（避免換咗 key 令已裝用戶升唔到級）。"
  echo "   真係要重產，先人手刪走佢，或改用：bash $0 另一個檔名.jks"
  exit 1
fi

echo "=== 設定新密碼（唔會顯示，請離線備份）==="
read -rsp "STORE PASSWORD: " STORE_PWD; echo
read -rsp "KEY PASSWORD（可同 store 唔同）: " KEY_PWD; echo
[ -n "$STORE_PWD" ] && [ -n "$KEY_PWD" ] || { echo "❌ 密碼唔可以留空"; exit 1; }

echo ""
echo "=== 產生 keystore：RSA 4096 / 36500 日 ≈ 100 年 ==="
keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storetype JKS \
  -dname "CN=HKETA, O=HKETA, C=HK" \
  -storepass "$STORE_PWD" -keypass "$KEY_PWD" >/dev/null

echo "✅ $OUT ($(wc -c < "$OUT") bytes)"

echo "=== 轉單行 base64 ==="
if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w0 "$OUT" > "$B64"
else
  base64 < "$OUT" | tr -d '\n' > "$B64"
fi
echo "✅ $B64 ($(wc -c < "$B64") chars，無換行)"

echo ""
echo "=== 指紋（對照 CI step 10 / step 19 用）==="
keytool -list -v -keystore "$OUT" -storepass "$STORE_PWD" -alias "$ALIAS" 2>/dev/null \
  | grep -m1 'SHA256:' || true

echo ""
echo "================================================================"
echo "去 GitHub → Settings → Secrets and variables → Actions"
echo "→ Repository secrets → New repository secret，逐個加："
echo "================================================================"
printf '  %-28s = %s\n' "HKETA_KEYSTORE_BASE64"    "（貼 $B64 嘅全部內容）"
printf '  %-28s = %s\n' "HKETA_KEYSTORE_PASSWORD"  "（你頭先輸入嘅 STORE PASSWORD）"
printf '  %-28s = %s\n' "HKETA_KEY_ALIAS"          "$ALIAS"
printf '  %-28s = %s\n' "HKETA_KEY_PASSWORD"       "（你頭先輸入嘅 KEY PASSWORD）"
echo ""
echo "⚠️ 注意："
echo "  · 要設喺「Repository secrets」，唔係 Environment / Dependabot secrets"
echo "  · 名稱要逐字一樣；fork 嘅 PR 拎唔到 secrets，用 Run workflow 或 push main"
echo "  · 設完要重新 Run workflow，舊 run 唔會自動重跑"
echo "  · $OUT / $B64 唔好 commit；離線備份，遺失 = 用戶永久升唔到級"
echo "  · 舊 keystore/hketa.jks（密碼 hketa2026）已外洩，唔好再用"
echo ""
