
plugins {
	kotlin("jvm")
}

kotlin {
	jvmToolchain(JAVA_VERSION)
	compilerOptions {
		languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
	}
}