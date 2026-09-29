plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.augmency_qr"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.augmency_qr"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // AndroidUSBCamera (AUSBC) -- Cyclops HMD üzerindeki UVC kamerayı
    // Android sistem kamera izinlerine gerek kalmadan açmak için.
    // Cihazın OTG (USB host) desteklemesi şart.
    implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.3.3")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}