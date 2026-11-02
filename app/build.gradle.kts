@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("jacoco")
}

base {
    archivesName = "fw"
}

android {
    namespace = "com.example.fineweather"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fineweather"
        minSdk = 27
        targetSdk = 36
        versionCode = 4
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // Room's exported schemas feed MigrationTestHelper in the instrumented tests.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// room-testing (MigrationTestHelper) needs kotlinx-serialization 1.8.x, while the app resolves 1.7.3
// (via the androidx lifecycle/activity libraries); mixing them crashes with an AbstractMethodError.
// Scoped to the androidTest configurations. Remove it once the app itself resolves
// kotlinx-serialization 1.8+ (newer androidx/compose BOM) and the migration tests still pass.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization-")) {
            useVersion("1.8.1")
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val coverageExclusions =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/Manifest*.*",
            "**/*Test*.*",
            "**/ui/**",
            "**/MainActivity*",
            "**/ComposableSingletons*",
            "**/AppScreen*",
            "**/api/*ApiService*",
            "**/data/local/WeatherDatabase*",
            "**/data/local/SettingsDataStoreKt*",
            "**/data/repositories/DataStoreSettingsRepository*",
            "**/viewmodels/WeatherViewModel$1*",
            "**/viewmodels/WeatherViewModel$2*",
            "**/*_Impl*.*",
            "**/*_Factory*.*",
            "**/*_MembersInjector*.*",
            "**/*\$Lambda$*.*",
            "**/*\$inlined$*.*",
            "**/*\$Companion*.*",
        )

    val kotlinClasses =
        fileTree(
            "${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        ) {
        exclude(coverageExclusions)
    }
    val javaClasses = fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug/classes") {
        exclude(coverageExclusions)
    }

    classDirectories.setFrom(files(kotlinClasses, javaClasses))
    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
        ),
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
        },
    )
}
