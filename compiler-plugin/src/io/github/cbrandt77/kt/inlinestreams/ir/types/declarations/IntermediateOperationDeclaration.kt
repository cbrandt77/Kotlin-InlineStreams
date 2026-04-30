package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

class StreamOperationChainFunction(val ir: IrFunction) {
	init {
		require(ir.isInline) { "Expected to be inline" }
		require(ir.isTopLevel) { "Expected to be top-level declaration" }
	}
	
}

class StreamOperationLoopFunction(val ir: IrFunction) {
	init {
		require(ir.isInline) { "Expected to be inline." }
		require(ir.isTopLevel) { "Expected to be top-level declaration" }
	}
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = TODO()
	
	
}

class IntermediateOperationLoopFunction(private val internal: StreamOperationLoopFunction) {
	companion object {
		val getterRegex = Regex("^get-.+")
		val setterRegex = Regex("^set-.+")
		
	}
	
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	/**
	 * arguments passed to the original dsl function, e.g. `map({ it -> it.foo })`
	 */
	context(data: StreamOpContext)
	val chainFunctionArgs get() = with (data) { ir.parameters.asSequence()
		.filter { it.kind == IrParameterKind.Regular }
		.takeWhile { it.isDslFunParameter } }
	
	
	
	/** `el`, `breakLoop`, `downstreamAction` */
	val standardArgs: Sequence<IrValueParameter>
		get() = TODO()
}