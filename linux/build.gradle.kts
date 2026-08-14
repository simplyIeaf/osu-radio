import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val versionProps = Properties().also { props ->
    file("version.properties").inputStream().use { props.load(it) }
}

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.osuradio.app"
version = versionProps["VERSION_NAME"].toString()

val appVersion: String = versionProps["APP_VERSION"].toString()

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.osuradio.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "osu-radio"
            packageVersion = versionProps["VERSION_NAME"].toString()
            description = "Music player that lets you play osu! beatmap songs"
            vendor = "simplyIeaf"
            licenseFile = rootProject.file("../LICENSE")
            modules("java.desktop", "java.logging", "java.xml", "jdk.unsupported", "jdk.crypto.ec")

            linux {
                iconFile.set(rootProject.file("src/main/resources/ic_app_logo.png"))
                packageName = "osu-radio"
            }
        }

        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.animation)

    // Provides Dispatchers.Main on the Swing EDT (matches coroutines-core pulled by Compose).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // mp3 + ogg/vorbis decoding through javax.sound.sampled SPI
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")

    implementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjdk-release=21")
    }
}
