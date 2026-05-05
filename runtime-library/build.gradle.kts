@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("module.publication")
    id(MyPublishingInfo.plugin_id) version PROJ_VERSION
}


//publishing {
//    publications {
//        withType<MavenPublication> {
//            // Stub javadoc.jar artifact
//            artifact(tasks.register("${name}JavadocJar", Jar::class) {
//                archiveClassifier.set("javadoc")
//                archiveAppendix.set(this@withType.name)
//            })
//        }
//    }
//}

kotlin {
    explicitApi()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js().nodejs()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    linuxArm64()
    linuxX64()

    macosArm64()
    macosX64()

    mingwX64()

    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    wasmJs().nodejs()
    wasmWasi().nodejs()

    watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    watchosX64()

    applyDefaultHierarchyTemplate()
    
    dependencies {
        implementation(project(":runtime-core"))
    }
}

