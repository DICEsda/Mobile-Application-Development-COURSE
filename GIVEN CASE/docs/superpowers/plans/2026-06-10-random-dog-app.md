# Random Dog App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a buildable Android app that shows a random dog, fetches a new one, favourites a dog, and shows a favourites gallery — as a showcase of Google's recommended architecture with MVI, Hilt, Room, Retrofit, and coroutines.

**Architecture:** Single-module, package-by-layer (`ui → domain → data`). UI uses MVI (immutable State + sealed Intent + sealed Effect + reducer) on a reusable `MviViewModel` base. Domain holds use cases over a domain `Dog`. Data holds the `DogRepository` interface + impl over Retrofit (dog.ceo) and Room (favourites). Hilt wires everything.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + OkHttp + kotlinx.serialization, Room (KSP), Coil, Coroutines/Flow. Tests: JUnit4, kotlinx-coroutines-test, Turbine, MockK, Room-testing.

> **Build/verify note:** All `./gradlew …` commands assume the Android SDK + JDK 17 are available (Android Studio, or `ANDROID_HOME` set). If the toolchain is absent on this machine, the implementer builds/verifies in Android Studio; do **not** claim a step passed without seeing the command output. Unit tests run on the JVM (`testDebugUnitTest`); the one Room test is instrumented (`connectedDebugAndroidTest`) and needs a device/emulator.

**Base package:** `com.example.randomdog`
**SDKs:** compileSdk 35, minSdk 24, targetSdk 35, JDK 17

---

## File structure

```
settings.gradle.kts                         root settings + repos
build.gradle.kts                            root, plugin aliases
gradle/libs.versions.toml                   version catalog (single source of versions)
gradle.properties
app/build.gradle.kts                        module config + deps
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/randomdog/
  RandomDogApplication.kt                   @HiltAndroidApp
  MainActivity.kt                           @AndroidEntryPoint, hosts NavGraph
  data/
    remote/DogApi.kt                        Retrofit interface
    remote/dto/DogImageDto.kt               wire model
    local/FavouriteDogEntity.kt             @Entity
    local/FavouriteDogDao.kt                DAO
    local/AppDatabase.kt                    @Database
    mapper/DogMappers.kt                    DTO/Entity ↔ Dog + breed parsing
    repository/DogRepository.kt             interface
    repository/DogRepositoryImpl.kt         impl
  domain/
    model/Dog.kt
    usecase/GetRandomDogUseCase.kt
    usecase/ToggleFavouriteUseCase.kt
    usecase/GetFavouriteDogsUseCase.kt
    usecase/ObserveIsFavouriteUseCase.kt
  ui/
    mvi/MviViewModel.kt
    home/HomeContract.kt
    home/HomeViewModel.kt
    home/HomeScreen.kt
    favourites/FavouritesContract.kt
    favourites/FavouritesViewModel.kt
    favourites/FavouritesScreen.kt
    components/DogStateViews.kt             Loading/Error/DogCard
    navigation/Screen.kt
    navigation/NavGraph.kt
    theme/Color.kt theme/Theme.kt theme/Type.kt
  di/
    Qualifiers.kt                           @IoDispatcher
    DispatcherModule.kt
    NetworkModule.kt
    DatabaseModule.kt
    RepositoryModule.kt
app/src/test/java/com/example/randomdog/    JVM unit tests
app/src/androidTest/java/com/example/randomdog/  instrumented (Room) test
ARCHITECTURE.md
DECISIONS.md
```

All code/docs live under `GIVEN CASE/` (the case folder). Run `git init` there first if version control is wanted; commits below assume a repo exists.

---

## Task 1: Project scaffold (Gradle + Hilt app shell)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/randomdog/RandomDogApplication.kt`
- Create: `app/src/main/java/com/example/randomdog/MainActivity.kt`
- Create: `app/src/main/java/com/example/randomdog/ui/theme/{Color,Theme,Type}.kt`

- [ ] **Step 1: Create the version catalog** `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.5"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
retrofitSerialization = "1.0.0"
room = "2.6.1"
coil = "2.7.0"
coroutines = "1.9.0"
junit = "4.13.2"
turbine = "1.2.0"
mockk = "1.13.13"
androidxJunit = "1.2.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version.ref = "retrofitSerialization" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RandomDog"
include(":app")
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.randomdog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.randomdog"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 6: Create `app/proguard-rules.pro`** (empty placeholder is fine)

```proguard
# Keep default rules; minify disabled for this case build.
```

- [ ] **Step 7: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".RandomDogApplication"
        android:allowBackup="true"
        android:label="Random Dog"
        android:supportsRtl="true"
        android:theme="@style/Theme.RandomDog">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.RandomDog">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 8: Create theme files**

`app/src/main/java/com/example/randomdog/ui/theme/Color.kt`
```kotlin
package com.example.randomdog.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)
```

`app/src/main/java/com/example/randomdog/ui/theme/Type.kt`
```kotlin
package com.example.randomdog.ui.theme

import androidx.compose.material3.Typography

val Typography = Typography()
```

`app/src/main/java/com/example/randomdog/ui/theme/Theme.kt`
```kotlin
package com.example.randomdog.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val DarkColors = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
private val LightColors = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

@Composable
fun RandomDogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```

- [ ] **Step 9: Create the Hilt Application** `RandomDogApplication.kt`

```kotlin
package com.example.randomdog

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RandomDogApplication : Application()
```

- [ ] **Step 10: Create a minimal `MainActivity.kt`** (placeholder UI; navigation wired in Task 13)

```kotlin
package com.example.randomdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.randomdog.ui.theme.RandomDogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RandomDogTheme {
                Surface { Text("Random Dog") }
            }
        }
    }
}
```

- [ ] **Step 11: Build to verify the shell assembles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (If the Android SDK is unavailable, verify in Android Studio: Gradle sync succeeds and the app installs showing "Random Dog".)

- [ ] **Step 12: Commit**

```bash
git add .
git commit -m "chore: scaffold Compose + Hilt project shell"
```

---

## Task 2: Domain model + remote data source

**Files:**
- Create: `domain/model/Dog.kt`
- Create: `data/remote/dto/DogImageDto.kt`
- Create: `data/remote/DogApi.kt`

- [ ] **Step 1: Create the domain model** `domain/model/Dog.kt`

```kotlin
package com.example.randomdog.domain.model

data class Dog(
    val imageUrl: String,
    val breed: String?,
)
```

- [ ] **Step 2: Create the DTO** `data/remote/dto/DogImageDto.kt`

```kotlin
package com.example.randomdog.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DogImageDto(
    val message: String,
    val status: String,
)
```

- [ ] **Step 3: Create the Retrofit API** `data/remote/DogApi.kt`

```kotlin
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
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/randomdog/domain app/src/main/java/com/example/randomdog/data/remote
git commit -m "feat: add Dog domain model and dog.ceo Retrofit API"
```

---

## Task 3: Local data source (Room)

**Files:**
- Create: `data/local/FavouriteDogEntity.kt`
- Create: `data/local/FavouriteDogDao.kt`
- Create: `data/local/AppDatabase.kt`

- [ ] **Step 1: Create the entity** `data/local/FavouriteDogEntity.kt`

```kotlin
package com.example.randomdog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourites")
data class FavouriteDogEntity(
    @PrimaryKey val imageUrl: String,
    val breed: String?,
    val addedAt: Long,
)
```

- [ ] **Step 2: Create the DAO** `data/local/FavouriteDogDao.kt`

```kotlin
package com.example.randomdog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDogDao {

    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    suspend fun isFavourite(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dog: FavouriteDogEntity)

    @Query("DELETE FROM favourites WHERE imageUrl = :url")
    suspend fun deleteByUrl(url: String)
}
```

- [ ] **Step 3: Create the database** `data/local/AppDatabase.kt`

```kotlin
package com.example.randomdog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavouriteDogEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favouriteDogDao(): FavouriteDogDao

    companion object {
        const val NAME = "random_dog.db"
    }
}
```

- [ ] **Step 4: Build to verify Room codegen succeeds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` and a generated schema at `app/schemas/...AppDatabase/1.json`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/randomdog/data/local app/schemas
git commit -m "feat: add Room favourites entity, DAO, and database"
```

---

## Task 4: Mappers (TDD — breed parsing is pure logic)

**Files:**
- Create: `data/mapper/DogMappers.kt`
- Test: `app/src/test/java/com/example/randomdog/data/mapper/DogMappersTest.kt`

- [ ] **Step 1: Write the failing test** `DogMappersTest.kt`

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.data.mapper.DogMappersTest"`
Expected: FAIL — `toDog`/`toEntity` unresolved.

- [ ] **Step 3: Write the implementation** `data/mapper/DogMappers.kt`

```kotlin
package com.example.randomdog.data.mapper

import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog

fun DogImageDto.toDog(): Dog = Dog(imageUrl = message, breed = parseBreed(message))

fun FavouriteDogEntity.toDog(): Dog = Dog(imageUrl = imageUrl, breed = breed)

fun Dog.toEntity(addedAt: Long): FavouriteDogEntity =
    FavouriteDogEntity(imageUrl = imageUrl, breed = breed, addedAt = addedAt)

/**
 * dog.ceo encodes breed in the path: .../breeds/<breed>[-<subbreed>]/<file>.jpg
 * "hound-afghan" -> reversed words -> "Afghan Hound". Returns null if not parseable.
 */
internal fun parseBreed(url: String): String? {
    val slug = url.substringAfter("/breeds/", "")
        .substringBefore("/", "")
        .takeIf { it.isNotBlank() } ?: return null
    return slug.split("-")
        .reversed()
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.data.mapper.DogMappersTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/randomdog/data/mapper app/src/test/java/com/example/randomdog/data/mapper
git commit -m "feat: add DTO/Entity/domain mappers with breed parsing"
```

---

## Task 5: Repository (TDD with fakes)

**Files:**
- Create: `di/Qualifiers.kt`
- Create: `data/repository/DogRepository.kt`
- Create: `data/repository/DogRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/randomdog/data/repository/DogRepositoryImplTest.kt`

- [ ] **Step 1: Create the IO dispatcher qualifier** `di/Qualifiers.kt`

```kotlin
package com.example.randomdog.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
```

- [ ] **Step 2: Create the repository interface** `data/repository/DogRepository.kt`

```kotlin
package com.example.randomdog.data.repository

import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.flow.Flow

interface DogRepository {
    suspend fun getRandomDog(): Result<Dog>
    fun observeFavourites(): Flow<List<Dog>>
    fun observeIsFavourite(imageUrl: String): Flow<Boolean>
    suspend fun toggleFavourite(dog: Dog)
}
```

- [ ] **Step 3: Write the failing test** `DogRepositoryImplTest.kt`

```kotlin
package com.example.randomdog.data.repository

import app.cash.turbine.test
import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeDogApi(
    var result: () -> DogImageDto = { DogImageDto("https://images.dog.ceo/breeds/pug/x.jpg", "success") },
) : DogApi {
    override suspend fun getRandomDog(): DogImageDto = result()
}

private class FakeFavouriteDao : FavouriteDogDao {
    val items = MutableStateFlow<List<FavouriteDogEntity>>(emptyList())
    override fun observeAll(): Flow<List<FavouriteDogEntity>> = items
    override fun observeIsFavourite(url: String): Flow<Boolean> =
        items.map { list -> list.any { it.imageUrl == url } }
    override suspend fun isFavourite(url: String): Boolean = items.value.any { it.imageUrl == url }
    override suspend fun insert(dog: FavouriteDogEntity) {
        items.value = (items.value.filterNot { it.imageUrl == dog.imageUrl } + dog)
    }
    override suspend fun deleteByUrl(url: String) {
        items.value = items.value.filterNot { it.imageUrl == url }
    }
}

class DogRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun repo(api: DogApi = FakeDogApi(), dao: FakeFavouriteDao = FakeFavouriteDao()) =
        DogRepositoryImpl(api, dao, dispatcher)

    @Test
    fun `getRandomDog success maps dto to Dog`() = runTest(dispatcher) {
        val result = repo().getRandomDog()
        assertTrue(result.isSuccess)
        assertEquals("Pug", result.getOrNull()?.breed)
    }

    @Test
    fun `getRandomDog wraps network error in failure`() = runTest(dispatcher) {
        val api = FakeDogApi(result = { throw IOException("offline") })
        val result = repo(api = api).getRandomDog()
        assertTrue(result.isFailure)
    }

    @Test
    fun `toggleFavourite adds then removes`() = runTest(dispatcher) {
        val dao = FakeFavouriteDao()
        val r = repo(dao = dao)
        val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

        r.toggleFavourite(dog)
        assertEquals(1, dao.items.value.size)

        r.toggleFavourite(dog)
        assertEquals(0, dao.items.value.size)
    }

    @Test
    fun `observeFavourites emits mapped domain models`() = runTest(dispatcher) {
        val dao = FakeFavouriteDao()
        val r = repo(dao = dao)
        r.toggleFavourite(Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug"))

        r.observeFavourites().test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("Pug", first.first().breed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.data.repository.DogRepositoryImplTest"`
Expected: FAIL — `DogRepositoryImpl` unresolved.

- [ ] **Step 5: Write the implementation** `data/repository/DogRepositoryImpl.kt`

```kotlin
package com.example.randomdog.data.repository

import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.mapper.toDog
import com.example.randomdog.data.mapper.toEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.di.IoDispatcher
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DogRepositoryImpl @Inject constructor(
    private val api: DogApi,
    private val dao: FavouriteDogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DogRepository {

    override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
        runCatching { api.getRandomDog().toDog() }
    }

    override fun observeFavourites(): Flow<List<Dog>> =
        dao.observeAll().map { entities -> entities.map { it.toDog() } }

    override fun observeIsFavourite(imageUrl: String): Flow<Boolean> =
        dao.observeIsFavourite(imageUrl)

    override suspend fun toggleFavourite(dog: Dog) = withContext(ioDispatcher) {
        if (dao.isFavourite(dog.imageUrl)) {
            dao.deleteByUrl(dog.imageUrl)
        } else {
            dao.insert(dog.toEntity(addedAt = System.currentTimeMillis()))
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.data.repository.DogRepositoryImplTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/randomdog/data/repository app/src/main/java/com/example/randomdog/di/Qualifiers.kt app/src/test/java/com/example/randomdog/data/repository
git commit -m "feat: add DogRepository interface and impl with tests"
```

---

## Task 6: Use cases

**Files:**
- Create: `domain/usecase/GetRandomDogUseCase.kt`, `ToggleFavouriteUseCase.kt`, `GetFavouriteDogsUseCase.kt`, `ObserveIsFavouriteUseCase.kt`
- Test: `app/src/test/java/com/example/randomdog/domain/usecase/UseCasesTest.kt`

- [ ] **Step 1: Write the failing test** `UseCasesTest.kt`

```kotlin
package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {

    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    @Test
    fun `GetRandomDog delegates to repository`() = runTest {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        val result = GetRandomDogUseCase(repo)()
        assertTrue(result.isSuccess)
        assertEquals(dog, result.getOrNull())
    }

    @Test
    fun `ToggleFavourite delegates to repository`() = runTest {
        ToggleFavouriteUseCase(repo)(dog)
        coVerify { repo.toggleFavourite(dog) }
    }

    @Test
    fun `GetFavouriteDogs delegates to repository`() = runTest {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))
        val emitted = mutableListOf<List<Dog>>()
        GetFavouriteDogsUseCase(repo)().collect { emitted.add(it) }
        assertEquals(listOf(listOf(dog)), emitted)
    }

    @Test
    fun `ObserveIsFavourite delegates to repository`() = runTest {
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(true)
        val emitted = mutableListOf<Boolean>()
        ObserveIsFavouriteUseCase(repo)(dog.imageUrl).collect { emitted.add(it) }
        assertEquals(listOf(true), emitted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.domain.usecase.UseCasesTest"`
Expected: FAIL — use case classes unresolved.

- [ ] **Step 3: Create the four use cases**

`domain/usecase/GetRandomDogUseCase.kt`
```kotlin
package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import javax.inject.Inject

class GetRandomDogUseCase @Inject constructor(private val repository: DogRepository) {
    suspend operator fun invoke(): Result<Dog> = repository.getRandomDog()
}
```

`domain/usecase/ToggleFavouriteUseCase.kt`
```kotlin
package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import javax.inject.Inject

class ToggleFavouriteUseCase @Inject constructor(private val repository: DogRepository) {
    suspend operator fun invoke(dog: Dog) = repository.toggleFavourite(dog)
}
```

`domain/usecase/GetFavouriteDogsUseCase.kt`
```kotlin
package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavouriteDogsUseCase @Inject constructor(private val repository: DogRepository) {
    operator fun invoke(): Flow<List<Dog>> = repository.observeFavourites()
}
```

`domain/usecase/ObserveIsFavouriteUseCase.kt`
```kotlin
package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveIsFavouriteUseCase @Inject constructor(private val repository: DogRepository) {
    operator fun invoke(imageUrl: String): Flow<Boolean> = repository.observeIsFavourite(imageUrl)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.domain.usecase.UseCasesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/randomdog/domain/usecase app/src/test/java/com/example/randomdog/domain/usecase
git commit -m "feat: add domain use cases with tests"
```

---

## Task 7: MVI base class

**Files:**
- Create: `ui/mvi/MviViewModel.kt`

- [ ] **Step 1: Create the base** `ui/mvi/MviViewModel.kt`

```kotlin
package com.example.randomdog.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Minimal MVI base. State is the single source of truth and is only ever changed
 * through [reduce]. Intents are the only way in; effects are one-off side events.
 */
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    /** The only entry point for user actions. */
    abstract fun onIntent(intent: I)

    protected val currentState: S get() = _state.value

    protected fun reduce(block: (S) -> S) = _state.update(block)

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/mvi
git commit -m "feat: add reusable MVI ViewModel base"
```

---

## Task 8: Home screen (contract + ViewModel, TDD)

**Files:**
- Create: `ui/home/HomeContract.kt`
- Create: `ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/example/randomdog/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Create the contract** `ui/home/HomeContract.kt`

```kotlin
package com.example.randomdog.ui.home

import com.example.randomdog.domain.model.Dog

data class HomeUiState(
    val isLoading: Boolean = false,
    val dog: Dog? = null,
    val isFavourite: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeIntent {
    data object LoadNewDog : HomeIntent
    data object ToggleFavourite : HomeIntent
    data object Retry : HomeIntent
}

sealed interface HomeEffect {
    data class ShowMessage(val text: String) : HomeEffect
}
```

- [ ] **Step 2: Write the failing test** `HomeViewModelTest.kt`

```kotlin
package com.example.randomdog.ui.home

import app.cash.turbine.test
import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.domain.usecase.GetRandomDogUseCase
import com.example.randomdog.domain.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    private fun viewModel() = HomeViewModel(
        getRandomDog = GetRandomDogUseCase(repo),
        toggleFavourite = ToggleFavouriteUseCase(repo),
        observeIsFavourite = ObserveIsFavouriteUseCase(repo),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads a dog on init and exposes Success`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(false)

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(dog, state.dog)
        assertNull(state.errorMessage)
    }

    @Test
    fun `network failure produces error message`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.dog)
        assertTrue(vm.state.value.errorMessage != null)
    }

    @Test
    fun `isFavourite flag tracks repository flow`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(true)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isFavourite)
    }

    @Test
    fun `ToggleFavourite intent calls use case`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(false)

        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(HomeIntent.ToggleFavourite)
        advanceUntilIdle()

        coVerify { repo.toggleFavourite(dog) }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.ui.home.HomeViewModelTest"`
Expected: FAIL — `HomeViewModel` unresolved.

- [ ] **Step 4: Write the implementation** `ui/home/HomeViewModel.kt`

```kotlin
package com.example.randomdog.ui.home

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain.usecase.GetRandomDogUseCase
import com.example.randomdog.domain.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRandomDog: GetRandomDogUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val observeIsFavourite: ObserveIsFavouriteUseCase,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {

    private var favouriteJob: Job? = null

    init { loadNewDog() }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadNewDog -> loadNewDog()
            HomeIntent.Retry -> loadNewDog()
            HomeIntent.ToggleFavourite -> toggleCurrent()
        }
    }

    private fun loadNewDog() {
        reduce { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            getRandomDog()
                .onSuccess { dog ->
                    reduce { it.copy(isLoading = false, dog = dog) }
                    observeFavouriteFor(dog.imageUrl)
                }
                .onFailure { error ->
                    reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
                }
        }
    }

    private fun observeFavouriteFor(imageUrl: String) {
        favouriteJob?.cancel()
        favouriteJob = viewModelScope.launch {
            observeIsFavourite(imageUrl).collectLatest { fav ->
                reduce { it.copy(isFavourite = fav) }
            }
        }
    }

    private fun toggleCurrent() {
        val dog = currentState.dog ?: return
        viewModelScope.launch { toggleFavourite(dog) }
    }

    private fun Throwable.toUserMessage(): String =
        "Couldn't fetch a dog. Check your connection and try again."
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.ui.home.HomeViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/home app/src/test/java/com/example/randomdog/ui/home
git commit -m "feat: add Home MVI contract and ViewModel with tests"
```

---

## Task 9: Favourites screen (contract + ViewModel, TDD)

**Files:**
- Create: `ui/favourites/FavouritesContract.kt`
- Create: `ui/favourites/FavouritesViewModel.kt`
- Test: `app/src/test/java/com/example/randomdog/ui/favourites/FavouritesViewModelTest.kt`

- [ ] **Step 1: Create the contract** `ui/favourites/FavouritesContract.kt`

```kotlin
package com.example.randomdog.ui.favourites

import com.example.randomdog.domain.model.Dog

data class FavouritesUiState(
    val isLoading: Boolean = true,
    val favourites: List<Dog> = emptyList(),
)

sealed interface FavouritesIntent {
    data class RemoveFavourite(val dog: Dog) : FavouritesIntent
}

sealed interface FavouritesEffect {
    data class ShowMessage(val text: String) : FavouritesEffect
}
```

- [ ] **Step 2: Write the failing test** `FavouritesViewModelTest.kt`

```kotlin
package com.example.randomdog.ui.favourites

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.domain.usecase.GetFavouriteDogsUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class FavouritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    private fun viewModel() = FavouritesViewModel(
        getFavourites = GetFavouriteDogsUseCase(repo),
        toggleFavourite = ToggleFavouriteUseCase(repo),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes favourites from repository`() = runTest(dispatcher) {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(dog), vm.state.value.favourites)
    }

    @Test
    fun `RemoveFavourite intent toggles via use case`() = runTest(dispatcher) {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))

        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(FavouritesIntent.RemoveFavourite(dog))
        advanceUntilIdle()

        coVerify { repo.toggleFavourite(dog) }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.ui.favourites.FavouritesViewModelTest"`
Expected: FAIL — `FavouritesViewModel` unresolved.

- [ ] **Step 4: Write the implementation** `ui/favourites/FavouritesViewModel.kt`

```kotlin
package com.example.randomdog.ui.favourites

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain.usecase.GetFavouriteDogsUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    getFavourites: GetFavouriteDogsUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
) : MviViewModel<FavouritesUiState, FavouritesIntent, FavouritesEffect>(FavouritesUiState()) {

    init {
        viewModelScope.launch {
            getFavourites().collect { dogs ->
                reduce { it.copy(isLoading = false, favourites = dogs) }
            }
        }
    }

    override fun onIntent(intent: FavouritesIntent) {
        when (intent) {
            is FavouritesIntent.RemoveFavourite ->
                viewModelScope.launch { toggleFavourite(intent.dog) }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.randomdog.ui.favourites.FavouritesViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/favourites app/src/test/java/com/example/randomdog/ui/favourites
git commit -m "feat: add Favourites MVI contract and ViewModel with tests"
```

---

## Task 10: Hilt modules (wire the graph)

**Files:**
- Create: `di/DispatcherModule.kt`, `di/NetworkModule.kt`, `di/DatabaseModule.kt`, `di/RepositoryModule.kt`

- [ ] **Step 1: Create the dispatcher module** `di/DispatcherModule.kt`

```kotlin
package com.example.randomdog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

- [ ] **Step 2: Create the network module** `di/NetworkModule.kt`

```kotlin
package com.example.randomdog.di

import com.example.randomdog.data.remote.DogApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder().addInterceptor(logging).build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(DogApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideDogApi(retrofit: Retrofit): DogApi = retrofit.create(DogApi::class.java)
}
```

- [ ] **Step 3: Create the database module** `di/DatabaseModule.kt`

```kotlin
package com.example.randomdog.di

import android.content.Context
import androidx.room.Room
import com.example.randomdog.data.local.AppDatabase
import com.example.randomdog.data.local.FavouriteDogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideFavouriteDao(db: AppDatabase): FavouriteDogDao = db.favouriteDogDao()
}
```

- [ ] **Step 4: Create the repository binding module** `di/RepositoryModule.kt`

```kotlin
package com.example.randomdog.di

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.data.repository.DogRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDogRepository(impl: DogRepositoryImpl): DogRepository
}
```

- [ ] **Step 5: Build to verify the Hilt graph resolves**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (Hilt compiles the graph; any missing binding fails here).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/randomdog/di
git commit -m "feat: wire Hilt modules (dispatcher, network, database, repository)"
```

---

## Task 11: Shared UI components

**Files:**
- Create: `ui/components/DogStateViews.kt`

- [ ] **Step 1: Create the components** `ui/components/DogStateViews.kt`

```kotlin
package com.example.randomdog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
    }
}

@Composable
fun DogImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)),
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/components
git commit -m "feat: add shared loading/error/image components"
```

---

## Task 12: Home & Favourites screens

**Files:**
- Create: `ui/home/HomeScreen.kt`
- Create: `ui/favourites/FavouritesScreen.kt`

- [ ] **Step 1: Create the Home screen** `ui/home/HomeScreen.kt`

```kotlin
package com.example.randomdog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.randomdog.ui.components.DogImage
import com.example.randomdog.ui.components.ErrorState
import com.example.randomdog.ui.components.LoadingState

@Composable
fun HomeScreen(
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.ShowMessage -> onShowMessage(effect.text)
            }
        }
    }

    when {
        state.isLoading && state.dog == null -> LoadingState(modifier)
        state.errorMessage != null && state.dog == null ->
            ErrorState(state.errorMessage!!, onRetry = { viewModel.onIntent(HomeIntent.Retry) }, modifier)
        else -> HomeContent(
            state = state,
            onNewDog = { viewModel.onIntent(HomeIntent.LoadNewDog) },
            onToggleFavourite = { viewModel.onIntent(HomeIntent.ToggleFavourite) },
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onNewDog: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        state.dog?.let { dog ->
            DogImage(url = dog.imageUrl, contentDescription = dog.breed ?: "A random dog")
            dog.breed?.let { Text(it) }
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (state.isFavourite) "Remove from favourites" else "Add to favourites",
                )
            }
        }
        Button(onClick = onNewDog) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("  New dog")
        }
    }
}
```

- [ ] **Step 2: Create the Favourites screen** `ui/favourites/FavouritesScreen.kt`

```kotlin
package com.example.randomdog.ui.favourites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.randomdog.ui.components.DogImage
import com.example.randomdog.ui.components.LoadingState

@Composable
fun FavouritesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingState(modifier)
        state.favourites.isEmpty() -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("No favourites yet — tap the heart on a dog.") }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize().padding(12.dp),
        ) {
            items(state.favourites, key = { it.imageUrl }) { dog ->
                DogImage(
                    url = dog.imageUrl,
                    contentDescription = dog.breed ?: "A favourite dog",
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/home/HomeScreen.kt app/src/main/java/com/example/randomdog/ui/favourites/FavouritesScreen.kt
git commit -m "feat: add Home and Favourites Compose screens"
```

---

## Task 13: Navigation + wire MainActivity

**Files:**
- Create: `ui/navigation/Screen.kt`
- Create: `ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/randomdog/MainActivity.kt`

- [ ] **Step 1: Create the routes** `ui/navigation/Screen.kt`

```kotlin
package com.example.randomdog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Favourites : Screen("favourites", "Favourites", Icons.Filled.Favorite)

    companion object {
        val bottomBar = listOf(Home, Favourites)
    }
}
```

- [ ] **Step 2: Create the nav graph** `ui/navigation/NavGraph.kt`

```kotlin
package com.example.randomdog.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.randomdog.ui.favourites.FavouritesScreen
import com.example.randomdog.ui.home.HomeScreen
import kotlinx.coroutines.launch

@Composable
fun RandomDogApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Screen.bottomBar.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } })
            }
            composable(Screen.Favourites.route) {
                FavouritesScreen()
            }
        }
    }
}
```

- [ ] **Step 3: Replace `MainActivity.kt` body to host the app**

```kotlin
package com.example.randomdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.randomdog.ui.navigation.RandomDogApp
import com.example.randomdog.ui.theme.RandomDogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RandomDogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RandomDogApp()
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build and run to verify end-to-end**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Then install on a device/emulator: a dog loads, "New dog" fetches another, the heart toggles, the Favourites tab shows the grid. (Verify manually in Android Studio if no SDK on this machine.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/randomdog/ui/navigation app/src/main/java/com/example/randomdog/MainActivity.kt
git commit -m "feat: add bottom-nav navigation and wire MainActivity"
```

---

## Task 14: Room DAO instrumented test (device/emulator)

**Files:**
- Test: `app/src/androidTest/java/com/example/randomdog/data/local/FavouriteDogDaoTest.kt`

- [ ] **Step 1: Write the instrumented test** `FavouriteDogDaoTest.kt`

```kotlin
package com.example.randomdog.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavouriteDogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FavouriteDogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.favouriteDogDao()
    }

    @After fun tearDown() = db.close()

    @Test
    fun insert_then_observe_returns_row() = runTest {
        val entity = FavouriteDogEntity("url-1", "Pug", 1L)
        dao.insert(entity)

        dao.observeAll().test {
            assertEquals(listOf(entity), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(dao.isFavourite("url-1"))
    }

    @Test
    fun delete_removes_row() = runTest {
        dao.insert(FavouriteDogEntity("url-1", "Pug", 1L))
        dao.deleteByUrl("url-1")
        assertFalse(dao.isFavourite("url-1"))
    }
}
```

- [ ] **Step 2: Run the instrumented test (needs a device/emulator)**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS (2 tests). If no device is available, mark this task blocked and note it — do not claim it passed.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/randomdog/data/local
git commit -m "test: add Room DAO instrumented test"
```

---

## Task 15: ARCHITECTURE.md

**Files:**
- Create: `GIVEN CASE/ARCHITECTURE.md`

- [ ] **Step 1: Write `ARCHITECTURE.md`** with Mermaid diagrams and a glossary. Include these sections (use the audiobook project's `ARCHITECTURE.md` as the style reference — note cards, "what each term means" grouped by layer, an "About —" card on each diagram):

  1. **Layered architecture** — `flowchart TB`: UI (screens + MVI ViewModels) → Domain (use cases + `Dog`) → Data (repository → `DogApi` + `FavouriteDogDao`); DI cross-cutting; dog.ceo as the only external actor.
  2. **Compartment note-cards** — one card per package (`ui` / `domain` / `data` / `di`): its one-sentence job + the rule it obeys ("UI talks only to a ViewModel", "domain is framework-light use cases", "data is the single source of truth", "di is the one composition root — Hilt").
  3. **Hilt dependency graph** — `flowchart TD`: `SingletonComponent` provides `OkHttpClient → Retrofit → DogApi`, `AppDatabase → FavouriteDogDao`, `@IoDispatcher`; `DogRepositoryImpl` injected and `@Binds` to `DogRepository`; use cases inject the repository; `@HiltViewModel` ViewModels inject use cases. Note the seam: callers depend on `DogRepository`, never `DogRepositoryImpl`.
  4. **MVI loop** — `flowchart LR`: `View --Intent--> ViewModel --reduce--> State --render--> View`, with a side `Effect` channel for snackbars.
  5. **Random-dog sequence** — `sequenceDiagram`: User → HomeScreen `onIntent(LoadNewDog)` → HomeViewModel `reduce(isLoading)` → GetRandomDogUseCase → DogRepository → DogApi → dog.ceo, then map → `reduce(dog)` → recompose; include the failure branch (`Result.failure` → `reduce(errorMessage)` + `ShowMessage` effect).
  6. **Favourite & gallery data flow** — `flowchart LR`: toggle intent → ToggleFavouriteUseCase → Room insert/delete; Room `Flow` → GetFavouriteDogsUseCase → FavouritesViewModel `StateFlow` → grid; emphasise Room as the single source of truth (no manual refresh).
  7. **Glossary** — grouped by layer, each term as *what it is · why it's here · one-line interview answer*: Compose, ViewModel, StateFlow/UiState, MVI (Intent/Effect/reducer), UDF, UseCase, Repository, Room/Entity/DAO, Retrofit, kotlinx.serialization, Hilt (`@HiltAndroidApp`/`@Module`/`@Binds`/`@HiltViewModel`), Coil, Coroutines/Flow/Dispatchers, `Result`.

Each diagram must be valid Mermaid that renders on GitHub. Verify by pasting into a Mermaid live editor or the GitHub preview.

- [ ] **Step 2: Commit**

```bash
git add "GIVEN CASE/ARCHITECTURE.md"
git commit -m "docs: add ARCHITECTURE.md with Mermaid diagrams and glossary"
```

---

## Task 16: DECISIONS.md

**Files:**
- Create: `GIVEN CASE/DECISIONS.md`

- [ ] **Step 1: Write `DECISIONS.md`** as short ADRs (context / decision / alternatives considered / why / when-to-revisit / consequences), matching the audiobook project's format. Write these entries:

  1. **Hilt for DI** — required by the brief and the standard production choice; explicitly contrast with the audiobook project's manual `AppContainer` and state *when the trade-off flips* (multiple developers, compile-time graph validation, framework-managed scopes).
  2. **MVI (single immutable state + intents + reducer + effects)** — why chosen over plain MVVM; why it's still Google-UDF-aligned; honest trade-off (more boilerplate, justified as a deliberate showcase and because it scales). Note it diverges from the audiobook's MVVM decision on purpose.
  3. **Room for favourites** — vs. Preferences/Proto DataStore; relational collection, reactive `Flow`, single source of truth.
  4. **Domain layer with use cases** — why included on a trivial app (thin ViewModels, testable seams, full recommended architecture) + the honest "is this over-engineering?" counter-argument and when you'd drop it.
  5. **Google recommended architecture vs. strict Clean Architecture** — where they differ (repository interface in the data layer here, not the domain; single module, not a pure `:domain` module) and why we aligned with the brief.
  6. **Retrofit + kotlinx.serialization** — vs. Ktor / Moshi / Gson.
  7. **`Result`-based error handling** — errors as first-class UI state, no exceptions leaking to Compose; the Retry intent.
  8. **Coil for image loading** — vs. Glide; Compose-native `AsyncImage`.
  9. **Single-module, package-by-layer** — when multi-module or package-by-feature becomes correct.
  10. **Single-Activity + Compose Navigation** — sealed `Screen` routes, bottom nav.
  11. **What I'd do differently** — honest list: Compose UI/screenshot tests, paging if the gallery grew large, offline image caching strategy, CI (lint + tests on PR), type-safe nav args.

- [ ] **Step 2: Commit**

```bash
git add "GIVEN CASE/DECISIONS.md"
git commit -m "docs: add DECISIONS.md architecture decision records"
```

---

## Task 17: Final verification

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — DogMappersTest (4), DogRepositoryImplTest (4), UseCasesTest (4), HomeViewModelTest (4), FavouritesViewModelTest (2).

- [ ] **Step 2: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Manual smoke test (device/emulator)**

Install and confirm: a dog appears on launch; "New dog" loads another; the heart toggles and persists; the Favourites grid reflects toggles live; airplane-mode shows the error state with a working Retry.

- [ ] **Step 4: Final commit / tag**

```bash
git add -A
git commit -m "chore: random dog app complete — app + architecture docs" || echo "nothing to commit"
```

---

## Self-review notes (author)

- **Spec coverage:** every spec section maps to a task — data layer (T2–T5), domain (T6), MVI UI (T7–T9, T12–T13), Hilt (T10), tests (T4–T9 unit, T14 instrumented), ARCHITECTURE.md (T15), DECISIONS.md (T16), verification caveat (header + T14/T17 notes).
- **Type consistency:** `DogRepository` signatures (`getRandomDog(): Result<Dog>`, `observeFavourites()`, `observeIsFavourite(String)`, `toggleFavourite(Dog)`) are identical across the interface (T5), impl (T5), use cases (T6), and ViewModel tests (T8–T9). `HomeUiState`/`HomeIntent`/`HomeEffect` and `FavouritesUiState`/`FavouritesIntent`/`FavouritesEffect` are referenced exactly as defined. `MviViewModel<S,I,E>(initialState)` matches both ViewModel subclasses.
- **No placeholders:** all code steps contain complete code; the two docs tasks specify exact section lists and diagram contents rather than prose to be invented.
```