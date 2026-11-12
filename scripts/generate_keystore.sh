#!/bin/bash
# ============================================================
#  HK ETA — 簽名金鑰生成腳本
#  用途：生成長期使用的簽名金鑰，並輸出 GitHub Secrets 格式
#  使用方式：
#    chmod +x scripts/generate_keystore.sh
#    ./scripts/generate_keystore.sh
# ============================================================

set -e

echo "=============================================="
echo "  HK ETA - 生成長期簽名金鑰"
echo "=============================================="
echo ""

# --- 互動式輸入 ---
read -p "Keystore 密碼: " STORE_PASS
read -p "金鑰別名 (預設: hketa): " KEY_ALIAS
KEY_ALIAS=${KEY_ALIAS:-hketa}
read -p "金鑰密碼 (留空則與 Keystore 密碼相同): " KEY_PASS
KEY_PASS=${KEY_PASS:-$STORE_PASS}
read -p "有效期（年，預設 30）: " VALIDITY
VALIDITY=${VALIDITY:-30}

echo ""
echo "--- 填寫憑證資訊（可留空）---"
read -p "您的姓名/組織: " CN
read -p "組織單位: " OU
read -p "組織: " O
read -p "城市: " L
read -p "省份/州: " S
read -p "國家代碼 (HK): " C
C=${C:-HK}

# --- 生成 Keystore ---
KEYSTORE_FILE="hketa-release-key.jks"
DAYS=$((VALIDITY * 365))

echo ""
echo "正在生成簽名金鑰..."
keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity "$DAYS" \
  -dname "CN=$CN, OU=$OU, O=$O, L=$L, S=$S, C=$C"

echo ""
echo "✅ Keystore 已生成: $KEYSTORE_FILE"
echo ""

# --- 輸出 GitHub Secrets ---
echo "=============================================="
echo "  請將以下內容添加到 GitHub Secrets："
echo "  Repo → Settings → Secrets and variables → Actions"
echo "=============================================="
echo ""

echo "--- SIGNING_KEYSTORE ---"
echo "（Base64 編碼的 keystore 檔案內容）"
echo ""

base64 "$KEYSTORE_FILE" | tr -d '\n' > /tmp/keystore_b64.txt
echo "✅ Base64 已保存到 /tmp/keystore_b64.txt"
echo "   請複製其內容到 GitHub Secret: SIGNING_KEYSTORE"
echo ""

echo "--- SIGNING_STORE_PASSWORD ---"
echo "$STORE_PASS"
echo ""
echo "--- SIGNING_KEY_ALIAS ---"
echo "$KEY_ALIAS"
echo ""
echo "--- SIGNING_KEY_PASSWORD ---"
echo "$KEY_PASS"
echo ""
echo "=============================================="
echo "  重要提醒："
echo "  1. 請妥善備份 $KEYSTORE_FILE 和密碼"
echo "  2. 每次建置都使用同一個 keystore，才能覆蓋安裝"
echo "  3. 如果更換 keystore，舊版本 App 必須先卸載"
echo "  4. 建議將 keystore 和密碼存放在安全的地方"
echo "=============================================="
