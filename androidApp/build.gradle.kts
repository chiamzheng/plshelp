plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    id("kotlin-parcelize")
}
repositories {
    google()       // For Google's Android artifacts
    mavenCentral() // For most other common libraries, including kotlinx-io
    maven("https://api.mapbox.com/downloads/v2/releases/maven") // for mapbox import
    // If you have any other custom repositories (e.g., from MapCompose-mp documentation),
    // you would add them here as well.
}
android {
    namespace = "com.example.plshelp.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.plshelp.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.play.services.location)
    implementation(libs.androidx.activity.compose.v172)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.inappmessaging)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.navigation.compose)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.androidx.foundation.android)
    implementation(libs.coroutinesPlayServices)
    implementation("com.mapbox.maps:android:11.12.2")
    implementation("com.mapbox.extension:maps-compose:11.12.2")
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.preferences.core.android)
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation(libs.androidx.foundation.android)
    debugImplementation(libs.compose.ui.tooling)
}