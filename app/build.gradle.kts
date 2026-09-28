@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("jacoco")
}

android {
    namespace = "com.example.fineweather"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fineweather"
        minSdk = 27
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

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
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
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
            "**/BuildConfig.*",
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
            "**/*\$Lambda\$*.*",
            "**/*\$inlined\$*.*",
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
