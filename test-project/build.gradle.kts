import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm") version libs.versions.kotlin
}

repositories {
	gradlePluginPortal()
	mavenCentral()
//	mavenLocal()
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}