plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.solanamwa.godot"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // R8 disabled; consumer app handles minification.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Output AAR naming
    libraryVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "SolanaMWA-${name}.aar"
        }
    }
}

dependencies {
    // Godot engine (provided at runtime by the Godot Android export template)
    compileOnly("org.godotengine:godot:4.3.0.stable")

    // MWA 2.0 client library (P-256 ECDH, AES-128-GCM session, wallet discovery)
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.8")

    // Encrypted auth token storage. Consider migrating when security-crypto reaches EOL.
    implementation("androidx.security:security-crypto:1.1.0")

    // Kotlin coroutines for async MWA operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX core (required by MWA client library)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
