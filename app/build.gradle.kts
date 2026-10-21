plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin 支持，无需再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sodamusic.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sodamusic.app"
        minSdk = 26
        targetSdk = 36
        // 版本号可由 CI 通过环境变量注入（打包脚本按 tag 传入）
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            // 签名信息来自 gradle 属性或环境变量：
            // 本地用 -PRELEASE_STORE_FILE=... 或环境变量；
            // GitHub Actions 用 Secrets 注入，绝不写进仓库。
            val storePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
                ?: System.getenv("RELEASE_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩（正式发布必须开启）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只有配置了密钥才签名，否则产出未签名包（CI 会据此报错）
            signingConfigs.findByName("release")?.storeFile?.let {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Release 构建不跑 Lint 校验：它会显著拖慢打包，且 R8 阶段本身已做校验
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Haze 2.0 的 Glass（折射/镜面高光）与 Compose 共享元素动画仍标记为实验性
        optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
        optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        // TextLayoutResult 的行级度量（分页所需）仍为实验性
        optIn.add("androidx.compose.ui.text.ExperimentalTextApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.okhttp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.guava:guava:33.3.1-android")
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)
    implementation(libs.haze.glass)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    // 单元测试里 org.json 是 Android stub（抛异常），用真实实现覆盖
    testImplementation("org.json:json:20250107")
}

