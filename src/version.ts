/** 與 config.xml 的 widget version 同步；瀏覽器預覽時 Kotlin 拿不到 PackageInfo，用這裡的值。 */
export const FALLBACK_APP_VERSION = '1.0.0';

/** 發行版所在的 GitHub repo；使用者可在「設定」頁覆寫，存進 localStorage。
 *  預設指向本專案實際 repo，避免開箱即打 404。 */
export const DEFAULT_OWNER = 'benyeung08';
export const DEFAULT_REPO = 'hketa';

/** workflow 上傳到 Release 的 APK 檔名。 */
export const RELEASE_ASSET_NAME = 'HKETA-release.apk';

/** 只接受這些 host 的下載／開啟連結（Kotlin 原生端會再驗一次）。 */
export const ALLOWED_RELEASE_HOSTS = [
  'github.com',
  'objects.githubusercontent.com',
  'api.github.com'
] as const;

/** 單一 APK 大小上限，避免誤抓到巨型檔（100 MB）。 */
export const MAX_APK_BYTES = 100 * 1024 * 1024;

/** owner / repo 輸入白名單，fetch 與設定頁共用。 */
export const REPO_NAME_RE = /^[A-Za-z0-9_.-]+$/;
