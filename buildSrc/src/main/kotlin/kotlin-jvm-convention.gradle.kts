import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm")
}

kotlin {
	jvmToolchain(JAVA_VERSION)
}