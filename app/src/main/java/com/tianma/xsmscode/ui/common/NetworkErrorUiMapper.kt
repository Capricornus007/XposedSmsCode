package com.tianma.xsmscode.ui.common

import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.http.NetworkError

fun NetworkError.toUiMessage(): UiMessage {
    return when (this) {
        is NetworkError.Http -> UiMessage(R.string.network_error_http, arrayOf(code))
        is NetworkError.Io -> UiMessage(R.string.network_error_io)
        is NetworkError.Serialization -> UiMessage(R.string.network_error_serialization)
        is NetworkError.Unexpected -> UiMessage(R.string.network_error_unexpected)
        is NetworkError.MultiSource -> UiMessage(R.string.network_error_multi_source)
    }
}
