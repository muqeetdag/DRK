plugins {
    id("com.android.application")
}

android {
    namespace = "com.drkmuqeet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drkmuqeet.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
