package com.hketa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hketa.app.R
import com.hketa.app.data.Operator
import com.hketa.app.data.FavoriteStop
import com.hketa.app.data.RailLine
import com.hketa.app.data.StopDef
import com.hketa.app.util.AppLocale

/**
 * 營辦商顯示名。用 stringResource 而唔係讀 enum 欄位，
 * 語言切換時（configuration change）會自動 recompose 成另一種語言。
 */
@Composable
fun Operator.labelText(): String = stringResource(
    when (this) {
        Operator.KMB -> R.string.op_kmb
        Operator.CTB -> R.string.op_ctb
        Operator.NLB -> R.string.op_nlb
        Operator.GMB -> R.string.op_gmb
        Operator.MTR_BUS -> R.string.op_mtr_bus
        Operator.MTR_HR -> R.string.op_mtr_hr
        Operator.LRT -> R.string.op_lrt
    }
)

/**
 * 車站顯示名：英文介面且官方有提供英文站名就用英文，否則用中文原名。
 *
 * 九巴、城巴、專線小巴接口都有 name_en；嶼巴同鐵路只提供中文，
 * 嗰啲會照舊顯示中文 —— 唔會自己硬翻，避免同官方站牌對唔上。
 */
@Composable
fun StopDef.displayName(): String {
    val en = AppLocale.isEnglish()
    return if (en && nameEn.isNotBlank()) nameEn else name
}

/** 非 Composable 場合（Widget、ViewModel）用嘅版本 */
fun StopDef.displayName(en: Boolean): String =
    if (en && nameEn.isNotBlank()) nameEn else name

/** 鐵路線名：英文介面下顯示英文線名（如 Tuen Ma Line），否則中文 */
@Composable
fun RailLine.displayName(): String =
    if (AppLocale.isEnglish() && nameEn.isNotBlank()) nameEn else name

/** 鐵路總站名：英文介面下若只得中文就照顯示中文 */
@Composable
fun RailLine.displayTermini(): String =
    if (orig.isBlank() && dest.isBlank()) "" else "$orig ⇄ $dest"

/** 收藏車站嘅顯示名（英文介面用官方英文站名） */
@Composable
fun FavoriteStop.displayName(): String =
    if (AppLocale.isEnglish() && nameEn.isNotBlank()) nameEn else name
