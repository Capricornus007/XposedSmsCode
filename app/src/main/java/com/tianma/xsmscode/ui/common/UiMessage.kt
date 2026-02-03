package com.tianma.xsmscode.ui.common

import androidx.annotation.StringRes

data class UiMessage(@param:StringRes val resId: Int, val args: Array<Any> = emptyArray())
