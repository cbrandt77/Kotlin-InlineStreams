package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.types.classOrNull

class StreamOpContext(context: IrPluginContext) {
	val inlineStreamClassSymbol by lazy {
		context.referenceClass(NamesAndIds.InlineStreamClassId)
	}
	
	fun actsOnInlineStream(function: IrFunction): Boolean = function.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.classOrNull == inlineStreamClassSymbol
	
}