package com.hketa.app.data

/**
 * suspend 版嘅 runCatching。
 *
 * 標準庫嘅 `runCatching(block: () -> R)` 入面個 lambda **唔係** suspend 上下文，
 * 所以喺入面呼叫 `Http.get`、`repo.kmbEta`、`repo.mtrHeavyRailEta` 呢啲
 * suspend 函數會直接編譯失敗：
 *   "Suspend function can only be called from a coroutine or another suspend function"
 *
 * 呢個版本嘅 block 係 `suspend () -> R`，可以正常包住 suspend 呼叫，
 * 用法同 runCatching 一樣（.getOrNull() / .getOrDefault() / .onFailure {}）。
 */
suspend fun <R> suspendCatching(block: suspend () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
