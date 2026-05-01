package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.types.IrType

@JvmInline value class IntermediateOperationChainFunction(val internal: StreamOperationChainFunction) {
	val ir get() = internal.ir
	
	val receiver get() = internal.receiver
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	context(data: StreamOpContext)
	val outputElementType: IrType
		get() = data.getInlineStreamTypeParameterT(ir.returnType) ?: error("doesn't return InlineStream!")
	
	context(data: StreamOpContext)
	val preamble get() = internal.preamble
	
	val arguments get() = internal.arguments
}