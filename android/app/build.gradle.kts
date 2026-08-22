import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 有提供正式金鑰就用正式金鑰簽章（CI 從 secret 取得），沒有就退回 debug 簽章。
// CI 沒設定 secret 時環境變數會是空字串而不是未設定，所以空白也要當成「沒有金鑰」
val releaseKeystore: String? = System.getenv("FOW_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

/*
 * 版本規則：年份尾數.月份[.小版本]，例如 26.8、26.8.1、26.9。
 *
 * versionName 是給人看的，Android 判斷新舊看的是 versionCode，必須遞增。
 * 若直接用 2608 / 2609，小版本 26.8.1 會夾不進去，裝置就會拒絕更新，
 * 所以這裡編成 YYMMPP：26.8 → 260800、26.8.1 → 260801、26.9 → 260900。
 * 每個月最多 99 個小版本，且跨年遞增（27.1 → 270100）。
 */
val appVersionName = "26.8.11"

val appVersionCode = run {
    val m = Regex("""^(\d{2})\.(\d{1,2})(?:\.(\d{1,2}))?$""").find(appVersionName)
        ?: throw GradleException("版本號格式錯誤：$appVersionName，應為 年份尾數.月份[.小版本]，例如 26.8 或 26.8.1")
    val (yy, mm, patch) = m.destructured
    val month = mm.toInt()
    if (month !in 1..12) throw GradleException("月份必須介於 1 到 12：$appVersionName")
    yy.toInt() * 10000 + month * 100 + (patch.ifEmpty { "0" }).toInt()
}

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
        versionCode = appVersionCode
        versionName = appVersionName
        // 只保留我們自己翻譯的語言（會連帶剝掉相依套件的其他語系，縮小 APK）。
        // 漏列任何一個語言，該語言的字串會在建置時被整個剝掉。
        resourceConfigurations += listOf("en", "ja", "zh")

        // Google Maps 金鑰不進版控：本機放 local.properties，CI 走 GitHub Secret。
        // 地圖只用 Google Maps，沒有金鑰的話地圖會是空白的（迷霧與地標仍會畫）。
        val mapsKey: String = System.getenv("MAPS_API_KEY")?.takeIf { it.isNotBlank() }
            ?: runCatching {
                val props = Properties()
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { props.load(it) }
                props.getProperty("MAPS_API_KEY")
            }.getOrNull()
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
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
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    // play-services-maps 會帶進舊版 fragment，與 registerForActivityResult 不相容
    // （lint 的 InvalidFragmentVersionForActivityResult），所以明確指定新版本
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
