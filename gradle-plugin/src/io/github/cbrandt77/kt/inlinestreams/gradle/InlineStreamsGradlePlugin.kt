/*
 * Copyright (C) 2026 Caleb Brandt
 *
 * This file is part of Inline Streams.
 *
 * Inline Streams is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Inline Streams is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.inlinestreams.gradle

import io.github.cbrandt77.kt.inlinestreams.InlineStreamsBuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
class InlineStreamsGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
    
    }
    
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
    
    override fun getCompilerPluginId(): String = InlineStreamsBuildConfig.KOTLIN_PLUGIN_ID
    
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
		    groupId = InlineStreamsBuildConfig.KOTLIN_PLUGIN_GROUP,
		    artifactId = InlineStreamsBuildConfig.KOTLIN_PLUGIN_NAME,
		    version = InlineStreamsBuildConfig.KOTLIN_PLUGIN_VERSION,
    )
    
    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        
        kotlinCompilation.dependencies { implementation(InlineStreamsBuildConfig.ANNOTATIONS_LIBRARY_COORDINATES) }
        if (kotlinCompilation.implementationConfigurationName == "metadataCompilationImplementation") {
            project.dependencies.add("commonMainImplementation", InlineStreamsBuildConfig.ANNOTATIONS_LIBRARY_COORDINATES)
        }
        
        return project.provider {
            listOf()
        }
    }
}