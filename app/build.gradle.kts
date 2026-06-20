

plugins {
    alias(libs.plugins.android.application)
    //id("kotlin-kapt")
}

android {
    namespace = "com.example.health"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.health"
        minSdk = 26
        targetSdk = 35
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    implementation(libs.leancloud.storage)
    implementation(libs.leancloud.realtime)
    implementation(libs.rxandroid)
    implementation(libs.mpandroidchart)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.glide)
    implementation(libs.swiperefreshlayout)
    

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
