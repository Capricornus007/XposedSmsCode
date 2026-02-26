package com.github.magisk317.smscode.forwarder.entity.result

data class TelegramResult(
    var ok: Boolean?,
    var message: String,
    var timestamp: Long?,
)