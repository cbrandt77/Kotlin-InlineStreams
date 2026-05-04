plugins {
//    id("root.publication")
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.build.config)
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false
}

allprojects {
    group = PROJ_GROUP
    version = PROJ_VERSION
}

tasks.register("publishToMavenLocal") {
    arrayOf(project(":compiler-plugin"), project(":gradle-plugin"), project(":runtime-core"), project(":runtime-library"), ).forEach {
        dependsOn(it.tasks["publishToMavenLocal"])
    }
    group = "custom"
    
}


tasks.register("publishForRelease") {
    group = "custom"
    dependsOn(project(":gradle-plugin").tasks["publishPlugins"])
    arrayOf(project(":compiler-plugin"), project(":runtime")).forEach {
        dependsOn(it.tasks["publishAndReleaseToMavenCentral"])
    }
    
}