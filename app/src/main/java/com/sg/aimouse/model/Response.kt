package com.sg.aimouse.model

import com.google.gson.annotations.SerializedName
import com.sg.aimouse.common.AiMouseSingleton

data class Response(
    @SerializedName("file") val files: List<String>,
    @SerializedName("dir") val folder: List<String>
) {
    companion object {
        fun fromJSON(json: String) = AiMouseSingleton.gson.fromJson(json, Response::class.java)
    }
}