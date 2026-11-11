/** 與 config.xml 的 widget version 同步；瀏覽器預覽時 Kotlin 拿不到 PackageInfo，用這裡的值。 */
export const FALLBACK_APP_VERSION = '1.0.0';

/** 發行版所在的 GitHub repo；使用者可在「設定」頁覆寫，存進 localStorage。 */
export const DEFAULT_OWNER = 'yourname';
export const DEFAULT_REPO = 'hketa-apk';

/** workflow 上傳到 Release 的 APK 檔名。 */
export const RELEASE_ASSET_NAME = 'HKETA-release.apk';
