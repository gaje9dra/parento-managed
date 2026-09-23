plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.parento.managed"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parento.managed"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("boolean", "PARENTO_FEATURE_ENROLLMENT", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_REALTIME", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_LOCATION", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_SCREEN_SHARING", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_AUDIO", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_APPLICATION_MANAGEMENT", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_WEBSITE_FILTERING", "false")
        buildConfigField("boolean", "PARENTO_FEATURE_DEVICE_RESTRICTIONS", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "PARENTO_ENVIRONMENT", "\"development\"")
            buildConfigField("String", "PARENTO_BACKEND_BASE_URL", "\"https://dev-backend.example.invalid\"")
            buildConfigField("String", "PARENTO_LOG_LEVEL", "\"DEBUG\"")
            buildConfigField("boolean", "PARENTO_LOGGING_ENABLED", "true")
            buildConfigField("boolean", "PARENTO_REQUIRE_HTTPS", "false")
            buildConfigField("boolean", "PARENTO_DEBUG_DIAGNOSTICS", "true")
        }

        create("test") {
            initWith(getByName("debug"))
            buildConfigField("String", "PARENTO_ENVIRONMENT", "\"test\"")
            buildConfigField("String", "PARENTO_BACKEND_BASE_URL", "\"https://test-backend.example.invalid\"")
            buildConfigField("String", "PARENTO_LOG_LEVEL", "\"INFO\"")
            buildConfigField("boolean", "PARENTO_DEBUG_DIAGNOSTICS", "false")
        }

        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "PARENTO_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "PARENTO_BACKEND_BASE_URL", "\"https://backend.example.invalid\"")
            buildConfigField("String", "PARENTO_LOG_LEVEL", "\"WARN\"")
            buildConfigField("boolean", "PARENTO_LOGGING_ENABLED", "true")
            buildConfigField("boolean", "PARENTO_REQUIRE_HTTPS", "true")
            buildConfigField("boolean", "PARENTO_DEBUG_DIAGNOSTICS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
