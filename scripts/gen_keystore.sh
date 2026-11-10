#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# 產生 HKETA「永久簽名金鑰」。只需執行一次，之後所有版本共用。
#
# 用法：
#   export HKETA_KEYSTORE_PASSWORD='你的 store 密碼'
#   export HKETA_KEY_PASSWORD='你的 key 密碼'
#   bash scripts/gen_keystore.sh
#
# 會輸出：
#   hketa-release.jks            金鑰本體（切勿 commit、切勿遺失）
#   hketa-release.jks.base64.txt 供貼上 GitHub Secret 的 base64
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ALIAS="${HKETA_KEY_ALIAS:-hketa}"
OUT="${HKETA_KEYSTORE_FILE:-hketa-release.jks}"
VALIDITY_DAYS="${HKETA_VALIDITY_DAYS:-36500}"   # 100 年；技術上無「永久」，但已足夠

: "${HKETA_KEYSTORE_PASSWORD:?請先 export HKETA_KEYSTORE_PASSWORD}"
: "${HKETA_KEY_PASSWORD:?請先 export HKETA_KEY_PASSWORD}"

if [ -f "$OUT" ]; then
  echo "❌ $OUT 已存在。永久簽名金鑰不應重建，否則舊用戶無法覆蓋升級。" >&2
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$OUT" \
  -storetype JKS \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity "$VALIDITY_DAYS" \
  -dname "CN=HKETA, OU=HKETA, O=HKETA, L=Hong Kong, ST=Hong Kong, C=HK" \
  -storepass "$HKETA_KEYSTORE_PASSWORD" \
  -keypass "$HKETA_KEY_PASSWORD"

chmod 600 "$OUT"
base64 < "$OUT" | tr -d '\n' > "$OUT.base64.txt"
chmod 600 "$OUT.base64.txt"

echo ""
echo "✅ 已建立：$OUT（有效期 $VALIDITY_DAYS 天，alias=$ALIAS）"
echo "✅ base64 已寫入：$OUT.base64.txt"
echo ""
echo "下一步：把以下 4 個值填入 GitHub → Settings → Secrets and variables → Actions"
echo "  HKETA_KEYSTORE_BASE64    ← $OUT.base64.txt 內容"
echo "  HKETA_KEYSTORE_PASSWORD  ← 你設的 store 密碼"
echo "  HKETA_KEY_ALIAS          ← $ALIAS"
echo "  HKETA_KEY_PASSWORD       ← 你設的 key 密碼"
echo ""
echo "⚠️  .jks / .base64.txt 已被 .gitignore 排除。請另外離線備份金鑰與密碼；"
echo "    一旦遺失，已安裝用戶將永遠無法升級，只能換 package name 重新上架。"
