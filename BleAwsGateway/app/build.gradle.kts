plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.have_no_eyes_deer.bleawsgateway"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.have_no_eyes_deer.bleawsgateway"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("com.amazonaws:aws-android-sdk-iot:2.16.12")
    implementation("com.amazonaws:aws-android-sdk-mobile-client:2.16.12")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("com.amazonaws:aws-android-sdk-iot:2.46.0")
    implementation("com.google.android.material:material:1.8.0")
}