apply(from = "../versions.mlkit.gradle")

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
//    id("org.jetbrains.dokka")
//    id("com.vanniktech.maven.publish")
}

// 获取 build_versions 变量
val buildVersions = project.extra.get("build_versions") as Map<*, *>

android {
    namespace = "com.king.mlkit.vision.barcode"
    compileSdk = buildVersions["compileSdk"] as Int

    defaultConfig {
        minSdk = buildVersions["minSdk"] as Int

        // 如果你的 Android Gradle 插件版本较低，可以保留这行
        // 如果是较新版本，应该移至 testOptions 和 lint 中
        // targetSdk = buildVersions["targetSdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // 针对新版本 Android Gradle 插件
    testOptions {
        targetSdk = buildVersions["targetSdk"] as Int
    }

    lint {
        targetSdk = buildVersions["targetSdk"] as Int
        abortOnError = false
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
}

dependencies {
    // 获取 deps 变量
    val depsMap = project.extra.get("deps") as Map<*, *>
    val testMap = depsMap["test"] as Map<*, *>
    val androidxMap = depsMap["androidx"] as Map<*, *>

    testImplementation(testMap["junit"].toString())
    androidTestImplementation(testMap["android_ext_junit"].toString())
    androidTestImplementation(testMap["espresso"].toString())

    compileOnly(androidxMap["appcompat"].toString())
    api(depsMap["google_mlkit_barcode_scanning"].toString())
    api(depsMap["viewfinderview"].toString())
    api(depsMap["camera_scan"].toString())

    compileOnly(project(":mlkit-common"))
}