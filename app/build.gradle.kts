plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.quantri.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.quantri.app"
        minSdk = 26
        targetSdk = 34
        // Số phiên bản hiển thị. Đổi số này khi phát hành bản mới (trùng với tên file zip: QA LifeOS Vx.y.z).
        versionName = "2.0.1"
        // Số bản dựng: mỗi lần GitHub build lại tăng lên, nên APK mới luôn cài đè được lên bản cũ.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
    }

    // Khóa ký CỐ ĐỊNH (file quantri.keystore nằm cạnh file này) để các bản APK đều cài đè lên nhau được.
    signingConfigs {
        create("fixed") {
            storeFile = file("quantri.keystore")
            storeType = "pkcs12"
            storePassword = "quantri123"
            keyAlias = "quantri"
            keyPassword = "quantri123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
