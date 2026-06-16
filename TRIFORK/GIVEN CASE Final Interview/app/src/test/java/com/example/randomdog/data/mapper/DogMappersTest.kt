package com.example.randomdog.data.mapper

import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DogMappersTest {

    @Test
    fun `dto maps to Dog and parses breed from url`() {
        val dto = DogImageDto(
            message = "https://images.dog.ceo/breeds/hound-afghan/n02088094_1003.jpg",
            status = "success",
        )
        val dog = dto.toDog()
        assertEquals(dto.message, dog.imageUrl)
        assertEquals("Afghan Hound", dog.breed)
    }

    @Test
    fun `single-word breed is capitalised`() {
        val dto = DogImageDto("https://images.dog.ceo/breeds/pug/x.jpg", "success")
        assertEquals("Pug", dto.toDog().breed)
    }

    @Test
    fun `unparseable url yields null breed`() {
        val dto = DogImageDto("https://example.com/no-breeds-here.jpg", "success")
        assertNull(dto.toDog().breed)
    }

    @Test
    fun `entity and Dog round-trip`() {
        val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")
        val entity = dog.toEntity(addedAt = 42L)
        assertEquals(dog.imageUrl, entity.imageUrl)
        assertEquals(dog.breed, entity.breed)
        assertEquals(42L, entity.addedAt)
        assertEquals(dog, entity.toDog())
    }
}
