package hk.eta.app

/** 全部需要用戶自行填寫嘅 GitHub 座標集中喺呢度，改一次就夠。 */
object AppConfig {
    /** 換成你嘅 GitHub 帳號 */
    const val OWNER = "benyeung08"
    /** 換成你嘅 repository 名稱 */
    const val REPO = "hketa"

    /** GitHub 託管嘅 hketa.json（設定頁「資料來源」用） */
    const val REMOTE_JSON_URL =
        "https://raw.githubusercontent.com/$OWNER/$REPO/main/app/src/main/assets/hketa.json"

    /** 設定頁「檢查更新」用：GitHub Releases 最新一筆 */
    const val RELEASES_API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** 設定頁「線上版本紀錄」唯一來源：全部已發布 Releases（最新 30 筆，唔快取） */
    const val RELEASES_HISTORY_API =
        "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"

    /** 未填好座標時，更新功能會提示而唔會打去一個假網址。 */
    val isConfigured: Boolean
        get() = OWNER != "OWNER" && REPO != "REPO" &&
            OWNER.isNotBlank() && REPO.isNotBlank()
}
