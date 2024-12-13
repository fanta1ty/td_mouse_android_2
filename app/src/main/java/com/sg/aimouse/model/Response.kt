package com.sg.aimouse.model

import com.google.gson.annotations.SerializedName
import com.sg.aimouse.common.AiMouseSingleton

data class Response(
    @SerializedName("file") val files: List<ResponseFile>,
    @SerializedName("dir") val folders: List<String>
) {
    companion object {
        fun fromJSON(json: String) = AiMouseSingleton.gson.fromJson(json, Response::class.java)
    }
}

data class ResponseFile(
    val name: String,
    val size: Long
)