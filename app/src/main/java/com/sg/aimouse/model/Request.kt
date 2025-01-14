package com.sg.aimouse.model

import com.sg.aimouse.common.AiMouseSingleton

data class Request(
    val command: String,
    val data: String = "",
    val size: Long = 0
) {
    fun toJSON() = AiMouseSingleton.gson.toJson(this)
}