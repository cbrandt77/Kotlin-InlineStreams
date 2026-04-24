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

package io.github.cbrandt77.kt.inlinestreams

import io.github.cbrandt77.kt.inlinestreams.fir.diagnostics.FirErrorTypes_InlineStreams
import io.github.cbrandt77.kt.inlinestreams.ir.transformers.IrStreamInliner
import io.github.cbrandt77.kt.inlinestreams.ir.visitors.StreamOpRuntimeVariantGenerator
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class CompilerPluginRegistrar_InlineStreams : CompilerPluginRegistrar() {
	override val supportsK2: Boolean
		get() = true
	
	override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
		FirExtensionRegistrarAdapter.registerExtension(FirRegistrar())
		IrGenerationExtension.registerExtension(IrRegistrar())
	}
	
	
	
	class FirRegistrar : FirExtensionRegistrar() {
		override fun ExtensionRegistrarContext.configurePlugin() {
			registerDiagnosticContainers(FirErrorTypes_InlineStreams)
		}
	}
	
	class IrRegistrar : IrGenerationExtension {
		override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
			moduleFragment.acceptChildrenVoid(StreamOpRuntimeVariantGenerator(pluginContext))
		}
	}
}

