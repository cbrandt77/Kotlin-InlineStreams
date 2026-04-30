package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isTopLevel

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
	
	val parameters: Sequence<IrValueParameter>
		get() = ir.parameters.asSequence().filter { it.kind != IrParameterKind.ExtensionReceiver }
}

class IntermediateOperationLoopFunction(private val internal: StreamOperationLoopFunction) {
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	context(_: StreamOpContext)
	val outputElementType: IrType
		get() = TODO()
	
	val parameters get() = internal.parameters
	
	
	/**
	 * arguments passed to the original dsl function, e.g. `map({ it -> it.foo })`
	 *
	 * These will always be the first parameters in the sequence.
	 */
	context(data: StreamOpContext)
	val dslFunArgs get() = with (data) { parameters.takeWhile { it.isDslFunParameter } }
	
	/** `el`, `breakLoop`, `downstreamAction`. These are actually the only parameters that don't have special names. */
	val standardArgs: Sequence<IrValueParameter>
		get() = parameters.dropWhile { it.name.isSpecial }.takeWhile { !it.name.isSpecial }
	
	context(data: StreamOpContext)
	val gettersByStateName
		get() = with (data) { parameters.mapNotNull { item -> item.name.nameThisIsGetterFor?.let { it to item } } }
	
	context(data: StreamOpContext)
	val settersByStateName
		get() = with (data) { parameters.mapNotNull { item -> item.name.nameThisIsSetterFor?.let { it to item } } }
}