import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val debugLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.reader(Charsets.UTF_8).use(::load)
    }
}

fun debugLocalProperty(name: String): String =
    debugLocalProperties.getProperty(name).orEmpty()

fun String.asBuildConfigString(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")

val debugLlmApiKey = debugLocalProperty("reverseTutorDebugLlmApiKey")
val debugLlmBaseUrl = debugLocalProperty("reverseTutorDebugLlmBaseUrl")
val debugLlmDefaultModel = debugLocalProperty("reverseTutorDebugLlmDefaultModel")
val debugLlmFallbackModels = debugLocalProperty("reverseTutorDebugLlmFallbackModels")

val onlineApiBaseUrl = providers.gradleProperty("reverseTutorOnlineBaseUrl")
    .orElse(providers.environmentVariable("REVERSE_TUTOR_ONLINE_BASE_URL"))
    .orElse("")
    .get()
val escapedOnlineApiBaseUrl = onlineApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.reversetutor.preview"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.reversetutor.preview"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-newmp"
        buildConfigField("String", "ONLINE_API_BASE_URL", "\"$escapedOnlineApiBaseUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            // V2 记忆测试专用身份：与正式版并排安装、数据完全隔离，不覆盖原版本。
            applicationIdSuffix = ".memtest"
            versionNameSuffix = "-memtest"
            resValue("string", "app_name", "反转家教·记忆测试")
            buildConfigField("String", "DEBUG_LLM_API_KEY", "\"${debugLlmApiKey.asBuildConfigString()}\"")
            buildConfigField("String", "DEBUG_LLM_BASE_URL", "\"${debugLlmBaseUrl.asBuildConfigString()}\"")
            buildConfigField("String", "DEBUG_LLM_DEFAULT_MODEL", "\"${debugLlmDefaultModel.asBuildConfigString()}\"")
            buildConfigField("String", "DEBUG_LLM_FALLBACK_MODELS", "\"${debugLlmFallbackModels.asBuildConfigString()}\"")
        }
        create("full") {
            // 全量版（2026-09-25 用户拍板）：独立身份 .full，与「记忆测试」并存，
            // 用户卸载旧包后单独安装验证；继承 debug 的全部调试配置。
            // 库模块只发布 debug/release 变体，消费侧回退到 debug 解析。
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug", "release")
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            resValue("string", "app_name", "全量版")
        }
        getByName("release") {
            buildConfigField("String", "DEBUG_LLM_API_KEY", "\"\"")
            buildConfigField("String", "DEBUG_LLM_BASE_URL", "\"\"")
            buildConfigField("String", "DEBUG_LLM_DEFAULT_MODEL", "\"\"")
            buildConfigField("String", "DEBUG_LLM_FALLBACK_MODELS", "\"\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:llm"))
    implementation(project(":core:remote"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:sources"))
    implementation(project(":feature:settings"))

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
