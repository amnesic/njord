import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
}

group = "io.madrona"
version = "${properties["version"]}"

repositories {
    google()
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val name = "arch"
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64(name)
        hostOs == "Mac OS X" && !isArm64 -> macosX64(name)
        hostOs == "Linux" && isArm64 -> linuxArm64(name)
        hostOs == "Linux" && !isArm64 -> linuxX64(name)
        isMingwX64 -> mingwX64(name)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val libgdal by creating {
                    if (NativeLibResolver.isMacOS) {
                        NativeLibResolver.resolve("gdal")?.let { flags ->
                            // Handle flat header layout (Conda: include/gdal.h instead of include/gdal/gdal.h)
                            val includeDir = flags.compilerOpts
                                .firstOrNull { it.startsWith("-I") }
                                ?.removePrefix("-I")
                            if (includeDir != null && !File("$includeDir/gdal/gdal.h").exists()) {
                                val compatDir = File(project.layout.buildDirectory.asFile.get(), "gdal-include-compat/gdal")
                                if (!compatDir.exists()) {
                                    compatDir.parentFile.mkdirs()
                                    Files.createSymbolicLink(
                                        compatDir.toPath(),
                                        Paths.get(includeDir)
                                    )
                                }
                                compilerOpts("-I${compatDir.parentFile.absolutePath}")
                            }
                            compilerOpts(*flags.compilerOpts.toTypedArray())
                        }
                    }
                }
            }
        }
        binaries {
            staticLib {
                baseName = "gdal"
                if (NativeLibResolver.isMacOS) {
                    linkerOpts(*NativeLibResolver.macOsLinkerPaths.toTypedArray())
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
            implementation(project(":shared"))
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ./gradlew publishAllPublicationsToGitHubPackagesRepository
// ./gradlew publishToMavenLocal
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/manimaul/ktgdal")
            credentials {
                username = System.getenv("GH_USER")
                password = System.getenv("GH_TOKEN")
            }
        }
    }
}
