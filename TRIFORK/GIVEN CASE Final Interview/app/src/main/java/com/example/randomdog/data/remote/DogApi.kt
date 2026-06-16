package com.example.randomdog.data.remote

import com.example.randomdog.data.remote.dto.DogImageDto
import retrofit2.http.GET

interface DogApi {
    @GET("api/breeds/image/random")
    suspend fun getRandomDog(): DogImageDto

    companion object {
        const val BASE_URL = "https://dog.ceo/"
    }
}
