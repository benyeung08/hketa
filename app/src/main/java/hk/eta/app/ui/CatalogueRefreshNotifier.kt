package hk.eta.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * LAUNCH_CATALOGUE_REFRESH — 開 App 背景更新完成後通知搜尋頁即刻用新快照。
 *
 * 背景 refresh 喺 `Application.onCreate` 開出嘅協程跑，完成時間唔確定；
 * `MainActivity.onCreate` 可能已經用舊快照／live fallback 預載咗。呢個
 * Application 級 singleton 用 LiveData 把「完成」事件交畀 Activity，
 * `observe(this)` 會喺 Activity DESTROY 自動移除，唔會揸住 Activity 造成洩漏。
 */
object CatalogueRefreshNotifier {

    private val _completed = MutableLiveData<Long>()
    val completed: LiveData<Long> = _completed

    /** 由背景 thread 呼叫；postValue 會自動切返主線程。 */
    fun notifyCompleted(atMillis: Long) {
        _completed.postValue(atMillis)
    }
}
