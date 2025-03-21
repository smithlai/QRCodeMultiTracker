apply(from = "../versions.mlkit.gradle")

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
//    id("org.jetbrains.dokka")
//    id("com.vanniktech.maven.publish")
}

// 正确获取 build_versions 变量
val buildVersions = project.extra.get("build_versions") as Map<*, *>

android {
    namespace = "com.king.mlkit.vision.common"
    compileSdk = buildVersions["compileSdk"] as Int

    defaultConfig {
        minSdk = buildVersions["minSdk"] as Int
        targetSdk = buildVersions["targetSdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // 正确获取 deps 变量
    val depsMap = project.extra.get("deps") as Map<*, *>
    val testMap = depsMap["test"] as Map<*, *>

    // 使用 toString() 将 GString 转换为 String
    testImplementation(testMap["junit"].toString())
    androidTestImplementation(testMap["android_ext_junit"].toString())
    androidTestImplementation(testMap["espresso"].toString())

    api(depsMap["google_mlkit_vision_common"].toString())
    api(depsMap["google_mlkit_vision_interfaces"].toString())

    api(depsMap["camera_scan"].toString())
}