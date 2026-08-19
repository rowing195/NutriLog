import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 簽章資訊放在 keystore.properties，該檔已被 gitignore ——
// 金鑰與密碼絕對不能進版控，外洩等於別人可以冒名發佈更新蓋掉你的 app。
// 檔案不存在時（例如別人 clone 下來）就退回 debug 簽章，讓專案照樣建得起來。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// 版號預設值只給本機建置用；CI 發版時會用
//   -PappVersionName=1.1 -PappVersionCode=<遞增值>
// 從 tag 覆蓋掉。這樣就不會發生「忘了手動改版號，結果新版被裝置當成同一版」——
// 那個症狀很難看出來：APK 檔名是新的，安裝卻悄悄沒更新。
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.watson.nutrilog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watson.nutrilog"
        // minSdk 26 = Android 8.0，涵蓋約 97% 現存裝置，
        // 同時可以只用 adaptive icon（不必準備各密度 PNG）。
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 沒有 keystore.properties 就沿用 debug 簽章。
            // 這樣至少裝得起來 —— 沒有簽章的 release APK 是**完全無法安裝**的。
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM 統一管理所有 compose 函式庫版本，底下不用寫版號
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // 飲食紀錄：逐日無上限累積，而且要「查某一天」與「近 N 天總和」，
    // 這是關聯式查詢，不適合像 LocalReader 那樣整包 JSON 塞 DataStore。
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 設定與每日目標（資料量小，用 DataStore 就好）
    implementation(libs.androidx.datastore.preferences)
    // Gemini 與 Open Food Facts 的 JSON
    implementation(libs.kotlinx.serialization.json)
    // 只有兩支端點，OkHttp 手寫即可，不必為此拉進整套 Retrofit
    implementation(libs.okhttp)
    // 條碼掃描：UI 跑在 Play 服務裡，因此不需要 CAMERA 權限也不用自己寫 CameraX 預覽
    implementation(libs.play.services.code.scanner)
}
