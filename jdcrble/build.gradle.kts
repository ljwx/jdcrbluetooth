plugins {
    alias(jdcr.plugins.android.library)
    alias(jdcr.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.jdcr.jdcrble"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    api(jdcr.jdcr.dev.base)
    api(jdcr.jdcr.log)
    api(jdcr.jdcr.permission)

    testImplementation(libs.junit)

}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"]) //release debug
                // JitPack 会自动填充 groupId 和 version，
                // 但为了本地测试，你可以保留这些：
                groupId = "com.github.jdcr"
                artifactId = "bluetooth"
                version = "1.0.0-SNAPSHOT"
            }
        }
    }
}
