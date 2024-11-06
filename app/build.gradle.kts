plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.garbagedataclassificationcollection"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.garbagedataclassificationcollection"
        minSdk = 28
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    // fix DocumentFile Bug (fromTreeUri returns root DocumentFile instead of the current one
    implementation("androidx.documentfile:documentfile:1.1.0-alpha01")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}