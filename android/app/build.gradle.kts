plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ruko.voice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ruko.voice"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Configurable via gradle.properties or -P flag (never hard-code secrets here)
        val serverBaseUrl: String = project.findProperty("SERVER_BASE_URL") as String? ?: "http://10.0.2.2:3000"
        val twilioIdentity: String = project.findProperty("TWILIO_IDENTITY") as String? ?: "alice"
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
        buildConfigField("String", "TWILIO_IDENTITY", "\"$twilioIdentity\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking (token fetch from backend)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Twilio Voice Android SDK
    implementation("com.twilio:voice-android:6.5.0")

    // Firebase Cloud Messaging (BOM manages versions)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
