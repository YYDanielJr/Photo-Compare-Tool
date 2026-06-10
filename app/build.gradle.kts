import com.android.build.api.variant.FilterConfiguration
import org.gradle.kotlin.dsl.java
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.photocomparetool"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yydaniel.photocomparetool"
        minSdk = 29
        targetSdk = 36
        versionCode = 20260610
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true    // 删除未使用的资源
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    splits {
        // Configures multiple APKs based on ABI.
        abi {
            // Enables building multiple APKs per ABI.
            isEnable = true
            // By default all ABIs are included, so use reset() and include to specify that you only
            // want APKs for x86 and x86_64.
            // Resets the list of ABIs for Gradle to create APKs for to none.
            reset()
            // Specifies a list of ABIs for Gradle to create APKs for.
            include("x86", "x86_64", "arm", "armeabi-v7a", "arm64-v8a")

            // Specifies that you don't want to also generate a universal APK that includes all ABIs.
            isUniversalApk = true
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val buildDate = SimpleDateFormat("yyyyMMdd").format(Date())
            val buildType = variant.buildType
            // 修正：使用枚举比较
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            output.outputFileName = "PhotoCompareTool_${android.defaultConfig.versionName}_${buildDate}_${buildType}_${abi}.apk"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.material)
    // implementation(libs.androidx.room3.compiler.processing.testing)
    // implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // 图标库：
    // 1) 核心图标集（仅包含默认常用图标，体积很小）
    // implementation(libs.androidx.compose.material.icons.core)
    // 2) 扩展图标集（包含全部 Material 图标，约 9000+，体积较大）
    // 通常建议用这个，方便随时选用任何图标
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    // implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
}