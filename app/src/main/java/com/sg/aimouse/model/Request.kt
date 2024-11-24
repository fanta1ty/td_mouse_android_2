package com.sg.aimouse.model

import com.sg.aimouse.common.AiMouseSingleton

data class Request(
    val command: String,
    val data: String = ""
) {
    fun toJSON() = AiMouseSingleton.gson.toJson(this)
}