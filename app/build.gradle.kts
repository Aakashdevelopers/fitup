import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val oneSignalRestApiKey: String = localProperties.getProperty("ONESIGNAL_REST_API_KEY") ?: ""
val openRouterApiKey: String = localProperties.getProperty("OPENROUTER_API_KEY") ?: ""

android {
    namespace = "com.amstudio.fitbond"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amstudio.fitbond"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ONESIGNAL_REST_API_KEY", "\"$oneSignalRestApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")

        // Retain only English resources to strip unused third-party translations
        androidResources {
            localeFilters += listOf("en")
        }

        // Limit ABIs to ARM architectures (strips emulator x86/x86_64 native binaries)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // ABI splitting for APK generation (disabled for App Bundles to allow resource shrinking)
    splits {
        abi {
            isEnable = false
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    // Enable App Bundle (AAB) configuration splits
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    // Exclude redundant META-INF metadata files
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation("com.google.firebase:firebase-functions")
    implementation(libs.play.services.auth)

    // Tencent IM Chat SDK
    implementation(libs.tencent.imsdk)

    // ZegoCloud Express Video/Audio SDK
    implementation("im.zego:express-video:3.21.0")

    // Image loading
    implementation(libs.glide)

    // Shimmer effect
    implementation(libs.shimmer)

    // Lottie animations
    implementation(libs.lottie)

    // AndroidSVG for SVG rendering
    implementation("com.caverock:androidsvg-aar:1.4")

    // CameraX dependencies
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.concurrent.futures)

    // Google Play In-App Updates
    implementation(libs.play.app.update)

    // OneSignal Push Notification SDK
    implementation("com.onesignal:OneSignal:[5.0.0, 5.99.99]")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}