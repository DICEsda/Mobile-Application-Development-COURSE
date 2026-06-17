package com.example.randomdog.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DogImageDto(
    val message: String,
    val status: String,
)
