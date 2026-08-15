import java.net.URI
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
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.11.1")
    implementation("org.jetbrains.compose.animation:animation-desktop:1.11.1")

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

val ffmpegBinary: Provider<RegularFile> = layout.buildDirectory.file("ffmpeg/ffmpeg")

val downloadFfmpeg = tasks.register("downloadFfmpeg") {
    outputs.file(ffmpegBinary)
    doLast {
        val bin = ffmpegBinary.get().asFile
        if (bin.exists()) return@doLast
        bin.parentFile.mkdirs()
        val archive = bin.parentFile.resolve("ffmpeg.tar.xz")
        try {
            val url = URI(
                "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
            ).toURL()
            url.openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            check(archive.length() > 0) { "downloaded ffmpeg archive is empty" }
            val exit = ProcessBuilder(
                "tar", "-xJf", archive.absolutePath, "-C", bin.parentFile.absolutePath
            ).start().waitFor()
            check(exit == 0) { "could not extract ffmpeg archive" }
            val extracted = bin.parentFile.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.startsWith("ffmpeg-") }
                ?: error("ffmpeg archive did not contain an ffmpeg directory")
            val ffmpeg = extracted.resolve("ffmpeg")
            check(ffmpeg.isFile) { "ffmpeg binary not found in archive" }
            ffmpeg.copyTo(bin, overwrite = true)
            bin.setExecutable(true)
            archive.delete()
            extracted.deleteRecursively()
        } catch (e: Exception) {
            logger.warn("Could not bundle a static ffmpeg (${e.message}); the app will fall back to a system ffmpeg")
            bin.delete()
            archive.delete()
        }
    }
}

tasks.named<org.gradle.api.tasks.Copy>("processResources") {
    dependsOn(downloadFfmpeg)
    from(ffmpegBinary) { into("native") }
}
