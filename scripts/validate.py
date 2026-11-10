# -*- coding: utf-8 -*-
import json, os, re, glob, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/main")
errors = []

# 1. JSON integrity
with open(os.path.join(MAIN, "assets/hketa.json"), encoding="utf-8") as f:
    db = json.load(f)
ops = {o["id"] for o in db["operators"]}
for r in db["routes"]:
    if r["co"] not in ops:
        errors.append("route %s has unknown co %s" % (r["id"], r["co"]))
    for b in r["bounds"]:
        for sid in b["stopIds"]:
            if sid not in db["stops"]:
                errors.append("route %s references missing stop %s" % (r["id"], sid))
for sid, se in db["etas"].items():
    if sid not in db["stops"]:
        errors.append("eta key %s not in stops" % sid)
    if se["stopId"] != sid:
        errors.append("eta key/stopId mismatch: %s" % sid)
# 1b. 四類路線資料（巴士／小巴／地鐵／輕鐵）+ 收費 + 服務班次
MODE_IDS = ["bus", "minibus", "metro", "light_rail"]
mode_ids = [m["id"] for m in db.get("modes", [])]
for m in MODE_IDS:
    if m not in mode_ids:
        errors.append("hketa.json missing mode %s" % m)
    if not any(r.get("mode") == m for r in db["routes"]):
        errors.append("hketa.json has no route for mode %s" % m)
for r in db["routes"]:
    rid = r["id"]
    if r.get("mode") not in MODE_IDS:
        errors.append("route %s has invalid mode %r" % (rid, r.get("mode")))
    f = r.get("fare")
    if not isinstance(f, dict) or float(f.get("full", 0)) <= 0:
        errors.append("route %s missing valid fare" % rid)
    svc = r.get("services")
    if not isinstance(svc, list) or not svc:
        errors.append("route %s missing services" % rid)
    else:
        for s in svc:
            if not s.get("first") or not s.get("last"):
                errors.append("route %s service missing first/last" % rid)
    if not r.get("bounds"):
        errors.append("route %s has no bounds" % rid)
if "fareDisclaimer" not in db:
    errors.append("hketa.json missing fareDisclaimer")
by_mode = {m: sum(1 for r in db["routes"] if r.get("mode") == m) for m in MODE_IDS}
print("JSON: %d routes, %d stops, %d eta stops | modes %s" %
      (len(db["routes"]), len(db["stops"]), len(db["etas"]),
       "/".join("%s:%d" % (k, v) for k, v in by_mode.items())))

# 2. XML parse + collect defined resources
import xml.etree.ElementTree as ET
xml_files = glob.glob(os.path.join(MAIN, "res/**/*.xml"), recursive=True) + \
            [os.path.join(MAIN, "AndroidManifest.xml")]
defined_ids, layouts, drawables, colors, strings, styles = set(), set(), set(), set(), set(), set()
for f in xml_files:
    try:
        ET.parse(f)
    except Exception as e:
        errors.append("XML parse %s: %s" % (f, e))
    base = os.path.basename(f)
    parent = os.path.basename(os.path.dirname(f))
    if parent == "layout":
        layouts.add(base[:-4])
    elif parent == "drawable":
        drawables.add(base[:-4])
    text = open(f, encoding="utf-8").read()
    defined_ids.update(re.findall(r'@\+id/(\w+)', text))
    for m in re.findall(r'@color/(\w+)', text):
        colors.add(m)
    for m in re.findall(r'@drawable/(\w+)', text):
        drawables.add(m)
for f in glob.glob(os.path.join(MAIN, "res/values/*.xml")):
    text = open(f, encoding="utf-8").read()
    colors.update(re.findall(r'<color name="(\w+)"', text))
    strings.update(re.findall(r'<string name="(\w+)"', text))
    styles.update(re.findall(r'<style name="(\w+)"', text))

# res 內實際存在的 drawable / mipmap 資源（含 PNG）
res_drawables, res_mipmaps = set(), set()
for f in glob.glob(os.path.join(MAIN, "res/**/*"), recursive=True):
    if not os.path.isfile(f):
        continue
    stem = os.path.splitext(os.path.basename(f))[0]
    parent = os.path.basename(os.path.dirname(f))
    if parent.startswith("drawable"):
        res_drawables.add(stem)
    elif parent.startswith("mipmap"):
        res_mipmaps.add(stem)
for x in sorted(drawables - res_drawables):
    errors.append("@drawable/%s referenced but file missing" % x)
manifest_text = open(os.path.join(MAIN, "AndroidManifest.xml"), encoding="utf-8").read()
for x in sorted(set(re.findall(r'@mipmap/(\w+)', manifest_text)) - res_mipmaps):
    errors.append("@mipmap/%s referenced but file missing" % x)
print("Resources: %d drawables, %d mipmaps" % (len(res_drawables), len(res_mipmaps)))

# 3. Kotlin R.* references
kt_files = glob.glob(os.path.join(MAIN, "java/**/*.kt"), recursive=True)
ref_ids, ref_layouts, ref_drawables, ref_colors = set(), set(), set(), set()
for f in kt_files:
    s = open(f, encoding="utf-8").read()
    ref_ids.update(re.findall(r'R\.id\.(\w+)', s))
    ref_layouts.update(re.findall(r'R\.layout\.(\w+)', s))
    ref_drawables.update(re.findall(r'R\.drawable\.(\w+)', s))
    # rough brace balance：先剝字串（入面可能有 content:// ），再剝註解，
    # 否則字串內嘅 // 會被當成行註解切走，令括號計錯。
    stripped = re.sub(r'"(?:\\.|[^"\\\r\n])*"', '""', s)
    stripped = re.sub(r'/\*.*?\*/', '', stripped, flags=re.S)
    stripped = re.sub(r'//.*', '', stripped)
    for op, cl in [('{', '}'), ('(', ')'), ('[', ']')]:
        if stripped.count(op) != stripped.count(cl):
            errors.append("unbalanced %s%s in %s (%d/%d)" %
                          (op, cl, os.path.basename(f), stripped.count(op), stripped.count(cl)))
for x in sorted(ref_ids - defined_ids):
    errors.append("R.id.%s not defined in layouts" % x)
for x in sorted(ref_layouts - layouts):
    errors.append("R.layout.%s missing" % x)
for x in sorted(ref_drawables - drawables):
    errors.append("R.drawable.%s missing" % x)
print("Kotlin: %d files, %d R.id refs, %d layouts" %
      (len(kt_files), len(ref_ids), len(ref_layouts)))

# 4. XML @color/@drawable/@string/@style references resolve
for f in xml_files:
    text = open(f, encoding="utf-8").read()
    for c in re.findall(r'@color/(\w+)', text):
        if c not in colors:
            errors.append("%s references undefined @color/%s" % (os.path.basename(f), c))
    for d in re.findall(r'@drawable/(\w+)', text):
        if d not in drawables:
            errors.append("%s references undefined @drawable/%s" % (os.path.basename(f), d))
    for s in re.findall(r'@string/(\w+)', text):
        if s not in strings:
            errors.append("%s references undefined @string/%s" % (os.path.basename(f), s))
    for s in re.findall(r'@style/(\w+)', text):
        if s not in styles and not s.startswith("Theme"):
            errors.append("%s references undefined @style/%s" % (os.path.basename(f), s))

# 5. YAML
try:
    import yaml
    y = yaml.safe_load(open(os.path.join(ROOT, ".github/workflows/build-apk.yml"), encoding="utf-8"))
    print("YAML: parsed, jobs =", list(y.get("jobs", {}).keys()))
except ImportError:
    print("YAML: pyyaml not installed, skipped")
except Exception as e:
    errors.append("YAML parse: %s" % e)

# 6. toolchain / supported Android range consistency (8.0 = API 26 → 17 = API 37)
def strip_comments(s):
    """只移除註解、保留字串內容（toolchain 檢查要讀 "9.1.1" 呢類值）。"""
    holder = []

    def keep(m):
        holder.append(m.group(0))
        return "__HKETA_STR_%d__" % (len(holder) - 1)

    s = re.sub(r'"(?:\\.|[^"\\\r\n])*"', keep, s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'//.*', '', s)
    for i, q in enumerate(holder):
        s = s.replace("__HKETA_STR_%d__" % i, q)
    return s

app_gradle = strip_comments(open(os.path.join(ROOT, "app/build.gradle.kts"), encoding="utf-8").read())
root_gradle = strip_comments(open(os.path.join(ROOT, "build.gradle.kts"), encoding="utf-8").read())
props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
wf = open(os.path.join(ROOT, ".github/workflows/build-apk.yml"), encoding="utf-8").read()

for pat, label in [
    (r"minSdk\s*=\s*26", "minSdk 26 (Android 8.0)"),
    (r"targetSdk\s*=\s*37", "targetSdk 37 (Android 17)"),
    (r"compileSdk\s*=\s*37", "compileSdk 37"),
]:
    if not re.search(pat, app_gradle):
        errors.append("app/build.gradle.kts missing %s" % label)
if "org.jetbrains.kotlin.android" in app_gradle or "org.jetbrains.kotlin.android" in root_gradle:
    errors.append("AGP 9 forbids org.jetbrains.kotlin.android plugin")
if "kotlinOptions" in app_gradle:
    errors.append("AGP 9 removed kotlinOptions DSL")
if "9.1.1" not in root_gradle:
    errors.append("root build.gradle.kts should pin AGP 9.1.1 (first stable AGP supporting API 37)")
for flag in ["android.enableJetifier", "android.nonTransitiveRClass"]:
    if flag in props:
        errors.append("gradle.properties contains AGP 9-removed flag %s" % flag)
for pat, label in [
    (r"gradle-9\.3\.1-bin\.zip", "Gradle 9.3.1"),
    (r"platforms;android-37", "Android 17 platform"),
    (r"build-tools;36\.0\.0", "build-tools 36.0.0 (AGP 9.1.1 default)"),
]:
    if not re.search(pat, wf):
        errors.append("workflow missing %s" % label)
print("Toolchain: AGP 9.1.1 / Gradle 9.3.1 / min 26 / target 37 / compile 37")

# 7. cross-check manifest activity classes
manifest = open(os.path.join(MAIN, "AndroidManifest.xml"), encoding="utf-8").read()
for act in ["MainActivity", "RouteDetailActivity", "EtaActivity", "SettingsActivity"]:
    if 'android:name=".%s"' % act not in manifest:
        errors.append("manifest missing .%s" % act)
    if not os.path.exists(os.path.join(MAIN, "java/hk/eta/app/%s.kt" % act)):
        errors.append("missing class file %s.kt" % act)

# 8. permanent signing consistency
for pat, label in [
    (r'signingConfigs\.create\("permanent"\)', "permanent signingConfig"),
    (r'resValue\("string", "expected_signing_cert_sha256"', "cert fingerprint resValue"),
]:
    if not re.search(pat, app_gradle):
        errors.append("app/build.gradle.kts missing %s" % label)
# debug + release 都必須指向同一個 config
for bt in ["debug", "release"]:
    block = re.search(r"%s\s*\{([^}]*)\}" % bt, app_gradle)
    if not block or "signingConfig = permanentConfig" not in block.group(1):
        errors.append("buildTypes.%s not bound to permanentConfig" % bt)
if "kotlin.android" in app_gradle:
    errors.append("unexpected kotlin plugin reference in app module")
for secret in ["HKETA_KEYSTORE_BASE64", "HKETA_KEYSTORE_PASSWORD",
               "HKETA_KEY_ALIAS", "HKETA_KEY_PASSWORD"]:
    if "secrets.%s" % secret not in wf:
        errors.append("workflow missing secret %s" % secret)
for pat, label in [
    (r"HKETA_SIGNING_CERT_SHA256", "cert fingerprint injection"),
    (r"verify --print-certs", "apksigner consistency check"),
    (r"keytool -list -v", "keytool fingerprint derivation"),
]:
    if pat not in wf:
        errors.append("workflow missing %s" % label)

verifier = os.path.join(MAIN, "java/hk/eta/app/SignatureVerifier.kt")
if not os.path.exists(verifier):
    errors.append("SignatureVerifier.kt missing")
main_kt = open(os.path.join(MAIN, "java/hk/eta/app/MainActivity.kt"), encoding="utf-8").read()
if "SignatureVerifier.isConsistent(this)" not in main_kt:
    errors.append("MainActivity does not verify signature on startup")
# resValue 生成的資源不可在 XML 重複定義
strings_xml = open(os.path.join(MAIN, "res/values/strings.xml"), encoding="utf-8").read()
if 'name="expected_signing_cert_sha256"' in strings_xml:
    errors.append("expected_signing_cert_sha256 must come from resValue only")
# 金鑰本體絕對不能進 repo
ignore = open(os.path.join(ROOT, ".gitignore"), encoding="utf-8").read()
for pat in ["*.jks", "*.keystore", "*.p12", "*.base64.txt"]:
    if pat not in ignore:
        errors.append(".gitignore missing %s" % pat)
leaked = [p for p in glob.glob(os.path.join(ROOT, "**/*"), recursive=True)
          if p.endswith((".jks", ".keystore", ".p12", ".pfx", ".base64.txt"))]
if leaked:
    errors.append("private key material found in repo: %s" % leaked)
print("Signing: permanent config + in-app SHA-256 check + CI verification + no key in repo")

# 9. Settings page + signature-gated in-app updater
java_dir = os.path.join(MAIN, "java/hk/eta/app")
for f in ["SettingsActivity.kt", "UpdateChecker.kt", "ApkFileProvider.kt", "AppConfig.kt"]:
    if not os.path.exists(os.path.join(java_dir, f)):
        errors.append("missing %s" % f)
for pat, label in [
    (r"REQUEST_INSTALL_PACKAGES", "install permission (Android 8+)"),
    (r'android:name="\.ApkFileProvider"', "ApkFileProvider registration"),
    (r'\$\{applicationId\}\.apkprovider', "manifest applicationId placeholder"),
    (r'android:grantUriPermissions="true"', "grantUriPermissions"),
]:
    if not re.search(pat, manifest):
        errors.append("manifest missing %s" % label)
if "R.id.settingsBtn" not in main_kt or "SettingsActivity::class.java" not in main_kt:
    errors.append("MainActivity has no Settings entry point")
if "R.layout.activity_settings" not in open(os.path.join(java_dir, "SettingsActivity.kt"), encoding="utf-8").read():
    errors.append("SettingsActivity does not use activity_settings layout")

uc = open(os.path.join(java_dir, "UpdateChecker.kt"), encoding="utf-8").read()
for pat, label in [
    (r"AppConfig\.RELEASES_API", "GitHub Releases API"),
    (r"checkApk\(", "candidate APK check"),
    (r"getPackageArchiveInfo", "archive signature read"),
    (r"FLAG_GRANT_READ_URI_PERMISSION", "content Uri permission grant"),
]:
    if not re.search(pat, uc):
        errors.append("UpdateChecker missing %s" % label)
sv = open(os.path.join(java_dir, "SignatureVerifier.kt"), encoding="utf-8").read()
if "actualOfArchive" not in sv:
    errors.append("SignatureVerifier missing archive certificate read")
settings_kt = open(os.path.join(java_dir, "SettingsActivity.kt"), encoding="utf-8").read()
if "CheckCode.OK" not in settings_kt or "verInstallBtn.visibility = View.VISIBLE" not in settings_kt:
    errors.append("install button is not gated behind signature check")
# AppConfig 與 EtaRepository 必須共用同一座標，避免兩處唔同步
er = open(os.path.join(java_dir, "EtaRepository.kt"), encoding="utf-8").read()
if "AppConfig.REMOTE_JSON_URL" not in er:
    errors.append("EtaRepository not using AppConfig.REMOTE_JSON_URL")
if "HKETA-release.apk" not in wf:
    errors.append("workflow does not publish HKETA-release.apk for in-app updater")
print("Settings: signature status + GitHub update check + gated install + content provider")

# 10. App version display + SINGLE online release history (no on-device log)
cfg = open(os.path.join(java_dir, "AppConfig.kt"), encoding="utf-8").read()
if "RELEASES_HISTORY_API" not in cfg:
    errors.append("AppConfig missing RELEASES_HISTORY_API")
for pat, label in [
    (r"data class AppVersion", "AppVersion model"),
    (r"fun appVersion\(ctx: Context\)", "appVersion() (name/code/install times/cert)"),
    (r"fun fetchHistory\(\)", "online fetchHistory()"),
    (r"Cache-Control.*no-cache", "no-cache header"),
    (r"longVersionCode", "versionCode (API 28+)"),
    (r"firstInstallTime", "first install time"),
]:
    if not re.search(pat, uc):
        errors.append("UpdateChecker missing %s" % label)

# 線上版本紀錄係唯一來源：本機 log 類別、SharedPreferences、相關 id 一律唔可以再存在
if os.path.exists(os.path.join(java_dir, "UpdateLog.kt")):
    errors.append("UpdateLog.kt should be removed (online history is the only source)")
all_kt = "\n".join(open(f, encoding="utf-8").read() for f in glob.glob(os.path.join(java_dir, "*.kt")))
for pat in ["UpdateLog", "SharedPreferences", "renderLog(", "localizedEvent(", "eventLabels"]:
    if pat in all_kt:
        errors.append("on-device log residue in Kotlin: %s" % pat)

for pat, label in [
    (r"R\.id\.verAppDetail", "App version detail view"),
    (r"renderAppVersion\(\)", "renderAppVersion()"),
    (r"R\.id\.verHistoryBtn", "online history reload button"),
    (r"R\.id\.verHistoryStatus", "online connection status"),
    (r"renderHistoryStatus\(\)", "renderHistoryStatus()"),
    (r"R\.id\.verHistoryList", "online history list"),
    (r"loadHistory\(\)", "loadHistory()"),
    (r"renderHistory\(\)", "renderHistory()"),
    (r"expanded\.contains\(idx\)", "expandable release notes"),
    (r"compareVersion\(r\.version, current\)", "installed/newer/older tags"),
    (r"線上版本紀錄", "bilingual 'Online release history' label"),
]:
    if not re.search(pat, settings_kt):
        errors.append("SettingsActivity missing %s" % label)
for pat in ["R.id.verLog", "本機更新紀錄", "實時版本紀錄"]:
    if pat in settings_kt:
        errors.append("SettingsActivity still references removed local log: %s" % pat)

settings_xml = open(os.path.join(MAIN, "res/layout/activity_settings.xml"), encoding="utf-8").read()
for rid in ["verAppTitle", "verAppDetail", "verHistoryTitle", "verHistoryHint",
            "verHistoryBtn", "verHistoryStatus", "verHistoryTime", "verHistoryList"]:
    if 'android:id="@+id/%s"' % rid not in settings_xml:
        errors.append("activity_settings.xml missing @+id/%s" % rid)
for rid in ["verLogTitle", "verLogList", "verLogClearBtn"]:
    if rid in settings_xml:
        errors.append("activity_settings.xml still has removed @+id/%s" % rid)
print("Version: name/code/install times + SINGLE online GitHub history (no on-device log)")

# 11. Route data update in Settings (bus / minibus / metro / light_rail)
rdu_path = os.path.join(java_dir, "RouteDataUpdate.kt")
if not os.path.exists(rdu_path):
    errors.append("missing RouteDataUpdate.kt")
else:
    rdu = open(rdu_path, encoding="utf-8").read()
    for pat, label in [
        (r"val MODE_IDS = listOf\(\"bus\", \"minibus\", \"metro\", \"light_rail\"\)", "four mode ids"),
        (r"fun stats\(", "per-mode stats"),
        (r"fun validate\(", "data validation"),
        (r"缺少有效收費 fare|fare.full <= 0", "fare validation"),
        (r"缺少服務班次 services", "service validation"),
        (r"fun fetchRemoteText\(", "online fetch"),
        (r"fun applyRemote\(", "apply + cache"),
        (r"fun loadInitial\(", "cache-first startup load"),
        (r"fun restoreBundled\(", "restore bundled"),
        (r"fun diff\(", "before/after diff"),
        (r"Cache-Control.*no-cache", "no-cache header"),
    ]:
        if not re.search(pat, rdu):
            errors.append("RouteDataUpdate missing %s" % label)
    # 資料快取係資料本身，唔可以退化成操作紀錄
    for pat in ["SharedPreferences", "UpdateLog", "renderLog("]:
        if pat in rdu:
            errors.append("RouteDataUpdate contains on-device log residue: %s" % pat)

du = open(os.path.join(java_dir, "DataUpdater.kt"), encoding="utf-8").read()
for pat, label in [
    (r"RouteDataUpdate\.fetchRemoteText\(\)", "remote fetch via updater"),
    (r"RouteDataUpdate\.applyRemote\(", "validated apply via updater"),
    (r"RouteDataUpdate\.loadInitial\(", "cache-first reload"),
]:
    if not re.search(pat, du):
        errors.append("DataUpdater missing %s" % label)

for pat, label in [
    (r"RouteDataUpdate\.loadInitial\(this\)", "MainActivity cache-first init"),
    (r"AppState\.dataCached", "MainActivity cached source label"),
    (r'putExtra\("focus", "data"\)', "MainActivity focus=data entry"),
]:
    if not re.search(pat, main_kt):
        errors.append("MainActivity missing %s" % label)

for pat, label in [
    (r"R\.id\.dataUpdateBtn", "data update button"),
    (r"R\.id\.restoreRouteData|R\.id\.dataRestoreBtn", "data restore button"),
    (r"updateRouteData\(\)", "updateRouteData()"),
    (r"restoreRouteData\(\)", "restoreRouteData()"),
    (r"renderDataCard\(\)", "renderDataCard()"),
    (r"statLine\(", "four-mode stat rows"),
    (r"RouteDataUpdate\.diff\(", "update diff display"),
    (r"getStringExtra\(\"focus\"\)", "focus=data scroll"),
    (r"RouteDataUpdate\.stats\(AppState\.get\(\)\)", "before-stats snapshot"),
]:
    if not re.search(pat, settings_kt):
        errors.append("SettingsActivity missing %s" % label)

for rid in ["settingsScroll", "dataSectionTitle", "dataHint", "dataStatus",
            "dataVersion", "dataStats", "dataNotes", "dataUpdateBtn", "dataRestoreBtn"]:
    if 'android:id="@+id/%s"' % rid not in settings_xml:
        errors.append("activity_settings.xml missing @+id/%s" % rid)
print("Data update: 4 modes + fares/schedules validated + online fetch + cache + restore + focus")

# 12. Bilingual READMEs (zh + en) must both exist and cross-link
zh_readme = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
en_path = os.path.join(ROOT, "README.en.md")
if not os.path.exists(en_path):
    errors.append("missing README.en.md (English README)")
else:
    en_readme = open(en_path, encoding="utf-8").read()
    if "English README" not in zh_readme or "](README.en.md)" not in zh_readme:
        errors.append("README.md missing link to English README")
    if "繁體中文 README" not in en_readme or "](README.md)" not in en_readme:
        errors.append("README.en.md missing link back to Chinese README")
    for pat, label in [
        (r"Permanent signing consistency", "signing section"),
        (r"Route data update", "route data update section"),
        (r"Online release history", "online history section"),
        (r"JSON is the database", "JSON schema section"),
        (r"Getting the APK", "build/artifacts section"),
        (r"Connecting real live ETA", "real-data integration section"),
        (r"Verification status", "honest verification-status section"),
        (r"README\.en\.md", "self-reference in project tree"),
        (r"minSdk = 26", "minSdk 26"),
        (r"targetSdk = 37", "targetSdk 37"),
        (r"AGP 9\.1\.1", "toolchain pin"),
        (r"30 demo routes", "route count"),
        (r"355 stops", "stop count"),
        (r"HKETA-release\.apk", "release APK name"),
        (r"REQUEST_INSTALL_PACKAGES", "install permission"),
        (r"60 requests", "GitHub rate-limit notice"),
    ]:
        if not re.search(pat, en_readme):
            errors.append("README.en.md missing %s" % label)
    for stale in ["TODO", "TBD", "Lorem ipsum"]:
        if stale in en_readme:
            errors.append("README.en.md contains placeholder %s" % stale)
    # 英文版章節數要同中文版對得上（兩邊都有 6 部分設定說明）
    if "### ④ Settings → Route data update" not in en_readme:
        errors.append("README.en.md missing settings section ④")
    if "**④ 設定 → 路線資料更新**" not in zh_readme:
        errors.append("README.md missing settings section ④")
print("Docs: zh README + en README with cross-links and matching facts")

print("-" * 40)
if errors:
    for e in errors:
        print("ERROR:", e)
    sys.exit(1)
print("ALL CHECKS PASSED")
