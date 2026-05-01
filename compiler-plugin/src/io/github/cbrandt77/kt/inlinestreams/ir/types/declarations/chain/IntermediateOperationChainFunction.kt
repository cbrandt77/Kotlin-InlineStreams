package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrType

@JvmInline value class IntermediateOperationChainFunction(val internal: StreamOperationChainFunction) {
	companion object {
		context(_: StreamOpContext)
	    operator fun invoke(ir: IrFunction): Result<IntermediateOperationChainFunction> {
	        return StreamOperationChainFunction.invoke(ir).map { IntermediateOperationChainFunction(it) }
	    }
	}
	
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
	
	context(_: StreamOpContext)
	val identifier get() = internal.identifier
}