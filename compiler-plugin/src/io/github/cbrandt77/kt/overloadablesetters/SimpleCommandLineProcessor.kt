/*
 * Copyright (C) 2025 Caleb Brandt
 *
 * This file is part of Overloadable Setters.
 *
 * Overloadable Setters is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Overloadable Setters is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.overloadablesetters

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@Suppress("unused") // Used via reflection.
class SimpleCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = OverloadableSettersBuildConfig.KOTLIN_PLUGIN_ID
    
    override val pluginOptions: Collection<CliOption> = listOf()

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
    
    }
}
