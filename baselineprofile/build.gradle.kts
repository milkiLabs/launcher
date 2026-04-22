plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.milki.launcher.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("release")
        }
    }

    testOptions {
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

detekt {
    baseline = rootProject.layout.projectDirectory.file("config/detekt/baselineprofile-baseline.xml").asFile
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    basePath.set(rootProject.projectDir)
    source.setFrom(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/androidTest/java",
        "src/androidTest/kotlin",
    )
    ignoredBuildTypes = listOf("benchmark")
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation("androidx.test:runner:1.7.0")
    implementation(libs.androidx.test.uiautomator)
}
