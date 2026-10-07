package com.hketa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hketa.app.R
import com.hketa.app.data.Operator

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
