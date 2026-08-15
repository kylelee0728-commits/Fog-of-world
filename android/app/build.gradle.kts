plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 有提供正式金鑰就用正式金鑰簽章（CI 從 secret 取得），沒有就退回 debug 簽章。
val releaseKeystore: String? = System.getenv("FOW_KEYSTORE_FILE")

android {
    namespace = "com.fogofworld"
    compileSdk = 35

    signingConfigs {
        if (releaseKeystore != null) {
            create("upload") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FOW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOW_KEY_ALIAS")
                keyPassword = System.getenv("FOW_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.fogofworld"
        minSdk = 24
        targetSdk = 35
        versionCode = 2608
        versionName = "26.8"
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "upload" else "debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
